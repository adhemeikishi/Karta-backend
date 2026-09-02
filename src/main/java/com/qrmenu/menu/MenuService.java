package com.qrmenu.menu;

import com.qrmenu.common.ConflictException;
import com.qrmenu.common.NotFoundException;
import com.qrmenu.media.MediaAsset;
import com.qrmenu.common.PublicUrlBuilder;
import com.qrmenu.media.MediaService;
import com.qrmenu.menu.MenuDtos.MenuResponse;
import com.qrmenu.menu.MenuDtos.MenuStructure;
import com.qrmenu.menu.MenuDtos.PdfInfo;
import com.qrmenu.menu.MenuDtos.SaveCategoryRequest;
import com.qrmenu.qrcode.QrCode;
import com.qrmenu.qrcode.QrCodeRepository;
import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantOffer;
import com.qrmenu.restaurant.RestaurantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cycle de vie du menu d'un client.
 *
 * Deux formes, un seul menu par client :
 * <ul>
 *   <li>BASIC → menu {@code PDF} : publication, destination du QR, fallback ;</li>
 *   <li>PRO / PREMIUM → menu {@code STRUCTURED} : contenu délégué à
 *       {@link MenuStructureService}.</li>
 * </ul>
 *
 * Maintient {@code qr_codes.destination_url} comme cache de la destination effective :
 * le hot-path {@code /q/{code}} reste un simple lookup, sans jointure supplémentaire.
 *
 * La publication d'un menu STRUCTURED n'est volontairement pas ouverte tant que le
 * renderer HTML n'existe pas : le QR ne doit jamais pointer vers une page inexistante.
 */
@Service
public class MenuService {

    private final MenuRepository menuRepository;
    private final MenuStructureService structureService;
    private final MediaService mediaService;
    private final PublicUrlBuilder urlBuilder;
    private final RestaurantService restaurantService;
    private final QrCodeRepository qrCodeRepository;

    public MenuService(
            MenuRepository menuRepository,
            MenuStructureService structureService,
            MediaService mediaService,
            PublicUrlBuilder urlBuilder,
            RestaurantService restaurantService,
            QrCodeRepository qrCodeRepository
    ) {
        this.menuRepository = menuRepository;
        this.structureService = structureService;
        this.mediaService = mediaService;
        this.urlBuilder = urlBuilder;
        this.restaurantService = restaurantService;
        this.qrCodeRepository = qrCodeRepository;
    }

    // ---------------------------------------------------------------- lecture

    @Transactional(readOnly = true)
    public MenuResponse getMenu(UUID restaurantId) {
        Restaurant restaurant = restaurantService.getOrThrow(restaurantId);
        return menuRepository.findByRestaurantId(restaurantId)
                .map(menu -> toResponse(restaurant, menu))
                .orElseGet(() -> emptyResponse(restaurant));
    }

    // ---------------------------------------------------------------- menu (toutes formes)

    /**
     * Crée le menu du client. Le type découle de l'offre — un client n'a jamais à le choisir.
     * Rejeté si un menu existe déjà : 1 client = 1 menu.
     */
    @Transactional
    public MenuResponse createMenu(UUID restaurantId) {
        Restaurant restaurant = restaurantService.getOrThrow(restaurantId);
        if (menuRepository.findByRestaurantId(restaurantId).isPresent()) {
            throw new ConflictException("Ce client possède déjà un menu.");
        }
        Menu menu = menuRepository.save(new Menu(restaurantId, typeFor(restaurant.getOffer())));
        return toResponse(restaurant, menu);
    }

    /** Supprime le menu et tout son contenu, en rendant au QR sa destination d'origine. */
    @Transactional
    public void deleteMenu(UUID restaurantId) {
        restaurantService.getOrThrow(restaurantId);
        Menu menu = menuRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new NotFoundException("Aucun menu pour ce client."));

        String fallbackUrl = menu.getFallbackUrl();
        if (fallbackUrl != null) {
            qrCodeRepository.findFirstByRestaurantId(restaurantId).ifPresent(qr -> {
                qr.updateDestination(fallbackUrl);
                qrCodeRepository.save(qr);
            });
        }

        UUID pdfAssetId = menu.getPdfAssetId();
        structureService.deleteContent(menu.getId());
        menuRepository.delete(menu);
        if (pdfAssetId != null) {
            deleteAssetQuietly(pdfAssetId);
        }
    }

    // ---------------------------------------------------------------- menu structuré

    /**
     * Remplace l'intégralité de la structure. Crée le menu au premier appel : la Review
     * et le futur import KartaAI n'ont pas à orchestrer une création préalable.
     */
    @Transactional
    public MenuResponse saveStructure(UUID restaurantId, List<SaveCategoryRequest> categories) {
        Restaurant restaurant = requireStructured(restaurantId);
        Menu menu = menuRepository.findByRestaurantId(restaurantId)
                .orElseGet(() -> menuRepository.save(new Menu(restaurantId, MenuType.STRUCTURED)));
        if (menu.getType() != MenuType.STRUCTURED) {
            throw new ConflictException("Le menu de ce client n'est pas un menu structuré.");
        }

        structureService.replace(restaurant, menu, categories);
        menu.bumpVersion();
        menu.applyContentState(structureService.hasContent(menu.getId()));
        return toResponse(restaurant, menuRepository.save(menu));
    }

    // ---------------------------------------------------------------- menu PDF (offre BASIC)

    @Transactional
    public MenuResponse uploadPdf(UUID restaurantId, byte[] content, String contentType, String originalFilename) {
        Restaurant restaurant = requireBasic(restaurantId);
        Menu menu = menuRepository.findByRestaurantId(restaurantId)
                .orElseGet(() -> new Menu(restaurantId, MenuType.PDF));

        UUID previousAssetId = menu.getPdfAssetId();
        MediaAsset asset = mediaService.storePdf(restaurantId, content, contentType, originalFilename);
        menu.attachPdf(asset.getId());
        Menu saved = menuRepository.save(menu);

        if (previousAssetId != null) {
            deleteAssetQuietly(previousAssetId);
        }
        recomputeEffectiveDestination(restaurantId, saved);
        return toResponse(restaurant, saved);
    }

    @Transactional
    public MenuResponse deletePdf(UUID restaurantId) {
        Restaurant restaurant = requireBasic(restaurantId);
        Menu menu = menuRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new ConflictException("Aucun menu à modifier."));

        UUID assetId = menu.getPdfAssetId();
        menu.detachPdf();
        Menu saved = menuRepository.save(menu);

        if (assetId != null) {
            deleteAssetQuietly(assetId);
        }
        recomputeEffectiveDestination(restaurantId, saved);
        return toResponse(restaurant, saved);
    }

    // ---------------------------------------------------------------- publication (les deux formes)

    /**
     * Publie le menu, quelle que soit sa forme, et fait pointer le QR dessus.
     *
     * Le contenu est exigé : un PDF pour un menu BASIC, au moins une catégorie pour un
     * menu structuré. Publier un menu vide ferait scanner un QR vers une page creuse.
     */
    @Transactional
    public MenuResponse publish(UUID restaurantId) {
        Restaurant restaurant = restaurantService.getOrThrow(restaurantId);
        Menu menu = menuRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new ConflictException("Aucun menu à publier."));
        requireTypeMatchesOffer(restaurant, menu);
        requirePublishableContent(menu);

        menu.publish();
        Menu saved = menuRepository.save(menu);
        recomputeEffectiveDestination(restaurantId, saved);
        return toResponse(restaurant, saved);
    }

    /** Dépublie : le menu repasse en READY (ou DRAFT s'il est vide) et le QR retrouve son repli. */
    @Transactional
    public MenuResponse unpublish(UUID restaurantId) {
        Restaurant restaurant = restaurantService.getOrThrow(restaurantId);
        Menu menu = menuRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new ConflictException("Aucun menu à dépublier."));
        requireTypeMatchesOffer(restaurant, menu);

        menu.unpublish();
        if (menu.getType() == MenuType.STRUCTURED) {
            menu.applyContentState(structureService.hasContent(menu.getId()));
        }
        Menu saved = menuRepository.save(menu);
        recomputeEffectiveDestination(restaurantId, saved);
        return toResponse(restaurant, saved);
    }

    private void requireTypeMatchesOffer(Restaurant restaurant, Menu menu) {
        if (menu.getType() != typeFor(restaurant.getOffer())) {
            throw new ConflictException("Le menu ne correspond pas à l'offre actuelle du client.");
        }
    }

    private void requirePublishableContent(Menu menu) {
        boolean hasContent = switch (menu.getType()) {
            case PDF -> menu.getPdfAssetId() != null;
            case STRUCTURED -> structureService.hasContent(menu.getId());
        };
        if (!hasContent) {
            throw new ConflictException(menu.getType() == MenuType.PDF
                    ? "Aucun PDF à publier."
                    : "Le menu ne contient aucune catégorie.");
        }
    }

    // ---------------------------------------------------------------- internes

    /** Le type de menu découle de l'offre : BASIC → PDF, PRO / PREMIUM → structuré. */
    private static MenuType typeFor(RestaurantOffer offer) {
        return offer == RestaurantOffer.BASIC ? MenuType.PDF : MenuType.STRUCTURED;
    }

    private Restaurant requireBasic(UUID restaurantId) {
        Restaurant restaurant = restaurantService.getOrThrow(restaurantId);
        if (restaurant.getOffer() != RestaurantOffer.BASIC) {
            throw new ConflictException("Le menu PDF est réservé à l'offre BASIC.");
        }
        return restaurant;
    }

    private Restaurant requireStructured(UUID restaurantId) {
        Restaurant restaurant = restaurantService.getOrThrow(restaurantId);
        if (typeFor(restaurant.getOffer()) != MenuType.STRUCTURED) {
            throw new ConflictException("Le menu structuré est réservé aux offres PRO et PREMIUM.");
        }
        return restaurant;
    }

    private void deleteAssetQuietly(UUID assetId) {
        try {
            mediaService.delete(mediaService.getOrThrow(assetId));
        } catch (RuntimeException ignored) {
            // asset déjà absent : rien à faire
        }
    }

    private void recomputeEffectiveDestination(UUID restaurantId, Menu menu) {
        Optional<QrCode> qrOpt = qrCodeRepository.findFirstByRestaurantId(restaurantId);
        if (qrOpt.isEmpty()) {
            return;
        }
        QrCode qr = qrOpt.get();

        String effective = effectiveDestination(menu, qr);

        if (effective != null) {
            menu.rememberFallback(qr.getDestinationUrl());
            menuRepository.save(menu);
            qr.updateDestination(effective);
            qrCodeRepository.save(qr);
        } else if (menu.getFallbackUrl() != null) {
            qr.updateDestination(menu.getFallbackUrl());
            qrCodeRepository.save(qr);
        }
        // sinon : on ne touche jamais destination_url — le QR n'est jamais cassé.
    }

    /**
     * Où le QR doit pointer, ou {@code null} si le menu ne prend pas le relais.
     *
     * Un menu structuré publié envoie vers la page HTML {@code /m/{code}}. Le
     * {@code RedirectController} n'est pas modifié : le hot-path du scan reste un
     * simple lookup de {@code destination_url}, sans jointure supplémentaire.
     */
    private String effectiveDestination(Menu menu, QrCode qr) {
        if (!menu.isPublished()) {
            return null;
        }
        return switch (menu.getType()) {
            case PDF -> menu.getPdfAssetId() == null ? null : urlBuilder.forAsset(menu.getPdfAssetId());
            case STRUCTURED -> urlBuilder.forMenu(qr.getCode());
        };
    }

    /** État renvoyé pour un client qui n'a pas encore de ligne {@code menus}. */
    private MenuResponse emptyResponse(Restaurant restaurant) {
        MenuType type = typeFor(restaurant.getOffer());
        MenuStructure structure = type == MenuType.STRUCTURED ? structureService.emptyStructure(restaurant) : null;
        return new MenuResponse(
                restaurant.getOffer(), type, MenuStatus.DRAFT, 0, false, null, null, structure);
    }

    private MenuResponse toResponse(Restaurant restaurant, Menu menu) {
        PdfInfo pdf = null;
        if (menu.getPdfAssetId() != null) {
            MediaAsset asset = mediaService.getOrThrow(menu.getPdfAssetId());
            pdf = new PdfInfo(
                    asset.getId(),
                    urlBuilder.forAsset(asset.getId()),
                    asset.getOriginalFilename(),
                    asset.getSizeBytes(),
                    asset.getCreatedAt());
        }
        MenuStructure structure = menu.getType() == MenuType.STRUCTURED
                ? structureService.load(restaurant, menu)
                : null;

        return new MenuResponse(
                restaurant.getOffer(),
                menu.getType(),
                menu.getStatus(),
                menu.getVersion(),
                menu.isPublished(),
                menu.getPublishedAt(),
                pdf,
                structure);
    }
}
