package com.qrmenu.kartaai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrmenu.common.ConflictException;
import com.qrmenu.common.NotFoundException;
import com.qrmenu.kartaai.ExtractionDtos.DraftResponse;
import com.qrmenu.kartaai.ExtractionDtos.ExtractedMenu;
import com.qrmenu.media.MediaAsset;
import com.qrmenu.media.MediaService;
import com.qrmenu.menu.Menu;
import com.qrmenu.menu.MenuDraftConsumer;
import com.qrmenu.menu.MenuRepository;
import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantOffer;
import com.qrmenu.restaurant.RestaurantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Cycle de vie du brouillon KartaAI : import, relecture, abandon.
 *
 * Le parcours est strictement :
 * <pre>
 * PDF → KartaAI → JSON → validation serveur → brouillon → Review → PUT /menu → menu
 * </pre>
 *
 * Ce service <strong>n'écrit jamais</strong> dans {@code menus}, {@code menu_categories}
 * ni {@code menu_items}, et ne touche jamais au statut de publication. L'écriture du menu
 * reste le seul fait du {@code PUT .../menu} existant, déclenché par le restaurateur
 * depuis la Review. Aucune publication automatique n'est donc possible, même après une
 * extraction parfaite.
 */
@Service
public class MenuDraftService implements MenuDraftConsumer {

    private final MenuDraftRepository draftRepository;
    private final MenuExtractor extractor;
    private final ExtractionValidator validator;
    private final MediaService mediaService;
    private final MenuRepository menuRepository;
    private final RestaurantService restaurantService;
    private final ObjectMapper objectMapper;

    public MenuDraftService(
            MenuDraftRepository draftRepository,
            MenuExtractor extractor,
            ExtractionValidator validator,
            MediaService mediaService,
            MenuRepository menuRepository,
            RestaurantService restaurantService,
            ObjectMapper objectMapper
    ) {
        this.draftRepository = draftRepository;
        this.extractor = extractor;
        this.validator = validator;
        this.mediaService = mediaService;
        this.menuRepository = menuRepository;
        this.restaurantService = restaurantService;
        this.objectMapper = objectMapper;
    }

    /**
     * Lance l'extraction sur la carte PDF déjà enregistrée pour ce client.
     *
     * Le PDF n'est pas transmis par l'appelant : il est relu depuis
     * {@code menus.pdf_asset_id}, ce qui garantit qu'on n'analyse jamais que le document
     * de ce client-là. Un identifiant d'asset fourni de l'extérieur permettrait de faire
     * analyser le PDF d'un autre restaurant.
     */
    @Transactional
    public DraftResponse importFromPdf(UUID restaurantId) {
        Restaurant restaurant = requireStructuredOffer(restaurantId);

        Menu menu = menuRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new ConflictException(
                        "Aucune carte PDF pour ce client. Importez d'abord le PDF du menu."));
        UUID assetId = menu.getPdfAssetId();
        if (assetId == null) {
            throw new ConflictException(
                    "Aucune carte PDF pour ce client. Importez d'abord le PDF du menu.");
        }

        MediaAsset asset = mediaService.getOrThrow(assetId);
        // Ceinture et bretelles : l'asset vient du menu du client, mais on vérifie tout
        // de même son rattachement avant de le lire.
        if (!asset.getRestaurantId().equals(restaurant.getId())) {
            throw new ConflictException("Ce document n'appartient pas à ce client.");
        }

        ExtractedMenu extracted = validator.validate(
                extractor.extract(mediaService.readContent(asset), asset.getOriginalFilename()));

        String payload = serialize(extracted);
        MenuDraft draft = draftRepository.findByRestaurantId(restaurantId)
                .map(existing -> {
                    existing.replace(asset.getId(), asset.getOriginalFilename(), payload);
                    return existing;
                })
                .orElseGet(() -> new MenuDraft(
                        restaurantId, asset.getId(), asset.getOriginalFilename(), payload));

        return DraftResponse.of(draftRepository.save(draft), extracted);
    }

    /** Brouillon en attente, pour reprendre une Review interrompue. */
    @Transactional(readOnly = true)
    public DraftResponse getDraft(UUID restaurantId) {
        restaurantService.getOrThrow(restaurantId); // 404 explicite si le client n'existe pas
        MenuDraft draft = draftRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new NotFoundException("Aucun brouillon KartaAI pour ce client."));
        return DraftResponse.of(draft, deserialize(draft.getPayload()));
    }

    @Transactional(readOnly = true)
    public boolean hasDraft(UUID restaurantId) {
        return draftRepository.findByRestaurantId(restaurantId).isPresent();
    }

    /** Abandonne la Review en cours. Ne touche pas au menu ni au PDF source. */
    @Transactional
    public void discard(UUID restaurantId) {
        restaurantService.getOrThrow(restaurantId);
        draftRepository.deleteByRestaurantId(restaurantId);
    }

    /**
     * Consomme le brouillon une fois le menu réellement enregistré.
     *
     * Appelé <strong>à la fin</strong> de l'écriture du menu, dans la même transaction :
     * si l'enregistrement échoue, la transaction est annulée et le brouillon reste en
     * base — le restaurateur peut reprendre sa Review au lieu de tout reperdre.
     */
    @Override
    @Transactional
    public void consume(UUID restaurantId) {
        draftRepository.deleteByRestaurantId(restaurantId);
    }

    /** KartaAI est-il utilisable sur ce serveur ? (clé configurée) */
    public boolean isEnabled() {
        return extractor.isAvailable();
    }

    // ---------------------------------------------------------------- internes

    /**
     * KartaAI produit un menu structuré : réservé aux offres PRO et PREMIUM.
     * BASIC diffuse son PDF tel quel — il n'y a rien à structurer.
     */
    private Restaurant requireStructuredOffer(UUID restaurantId) {
        Restaurant restaurant = restaurantService.getOrThrow(restaurantId);
        if (restaurant.getOffer() == RestaurantOffer.BASIC) {
            throw new ConflictException("KartaAI est réservé aux offres PRO et PREMIUM.");
        }
        return restaurant;
    }

    private String serialize(ExtractedMenu menu) {
        try {
            return objectMapper.writeValueAsString(menu);
        } catch (Exception e) {
            throw new ExtractionException("Le brouillon n'a pas pu être enregistré.", e);
        }
    }

    private ExtractedMenu deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, ExtractedMenu.class);
        } catch (Exception e) {
            throw new ExtractionException("Le brouillon enregistré est illisible.", e);
        }
    }
}
