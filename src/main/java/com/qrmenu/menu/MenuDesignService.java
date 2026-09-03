package com.qrmenu.menu;

import com.qrmenu.common.ConflictException;
import com.qrmenu.common.PublicUrlBuilder;
import com.qrmenu.media.MediaService;
import com.qrmenu.menu.MenuDesignDtos.Customization;
import com.qrmenu.menu.MenuDesignDtos.DesignResponse;
import com.qrmenu.menu.MenuDesignDtos.SaveDesignRequest;
import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantOffer;
import com.qrmenu.restaurant.RestaurantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Apparence du menu : lecture et enregistrement du preset et de l'identité PREMIUM.
 *
 * Séparé de {@link MenuService} (cycle de vie, publication, destination du QR) et de
 * {@link MenuStructureService} (contenu) parce que le design est le seul des trois à
 * pouvoir changer sans impact sur ce qui est diffusé : enregistrer un style ne publie
 * rien et ne modifie aucun prix.
 *
 * Deux règles portées ici :
 * <ul>
 *   <li>le studio est réservé aux offres PRO / PREMIUM — BASIC diffuse un PDF, il n'y a
 *       pas de HTML à styliser ;</li>
 *   <li>hors PREMIUM, seul le preset est écrit. La personnalisation déjà stockée est
 *       <strong>conservée intacte</strong> plutôt que rejetée : un client rétrogradé qui
 *       change de style ne doit pas perdre son logo, ni recevoir une erreur pour un champ
 *       que son interface ne lui laisse pas modifier.</li>
 * </ul>
 */
@Service
public class MenuDesignService {

    private final MenuRepository menuRepository;
    private final RestaurantService restaurantService;
    private final MediaService mediaService;
    private final PublicUrlBuilder urlBuilder;

    public MenuDesignService(
            MenuRepository menuRepository,
            RestaurantService restaurantService,
            MediaService mediaService,
            PublicUrlBuilder urlBuilder
    ) {
        this.menuRepository = menuRepository;
        this.restaurantService = restaurantService;
        this.mediaService = mediaService;
        this.urlBuilder = urlBuilder;
    }

    @Transactional(readOnly = true)
    public DesignResponse getDesign(UUID restaurantId) {
        Restaurant restaurant = restaurantService.getOrThrow(restaurantId);
        MenuDesign design = menuRepository.findByRestaurantId(restaurantId)
                .map(Menu::getDesign)
                .orElseGet(MenuDesign::defaults);
        return toResponse(restaurant, design);
    }

    /**
     * Enregistre l'apparence. Crée la ligne {@code menus} au premier appel : choisir un
     * style ne doit pas obliger à saisir d'abord une carte.
     *
     * N'affecte jamais le statut : un menu publié reste en ligne avec son ancien style
     * tant que « Publier » n'a pas été rejoué.
     */
    @Transactional
    public DesignResponse saveDesign(UUID restaurantId, SaveDesignRequest request) {
        Restaurant restaurant = requireStructuredOffer(restaurantId);
        Menu menu = menuRepository.findByRestaurantId(restaurantId)
                .orElseGet(() -> menuRepository.save(new Menu(restaurantId, MenuType.STRUCTURED)));
        if (menu.getType() != MenuType.STRUCTURED) {
            throw new ConflictException("Le menu de ce client n'est pas un menu structuré.");
        }

        menu.applyDesign(merge(restaurant, menu.getDesign(), request));
        return toResponse(restaurant, menuRepository.save(menu).getDesign());
    }

    // ---------------------------------------------------------------- internes

    /**
     * Design à écrire : le preset vient toujours de la requête, l'identité seulement si
     * le client est PREMIUM.
     */
    private MenuDesign merge(Restaurant restaurant, MenuDesign current, SaveDesignRequest request) {
        if (restaurant.getOffer() != RestaurantOffer.PREMIUM) {
            return new MenuDesign(
                    request.preset(),
                    current.brandName(),
                    current.primaryColor(),
                    current.secondaryColor(),
                    current.logoAssetId(),
                    current.heroAssetId());
        }
        return new MenuDesign(
                request.preset(),
                blankToNull(request.brandName()),
                upperOrNull(request.primaryColor()),
                upperOrNull(request.secondaryColor()),
                mediaService.requireOwnedImage(restaurant.getId(), request.logoAssetId()),
                mediaService.requireOwnedImage(restaurant.getId(), request.heroAssetId()));
    }

    private Restaurant requireStructuredOffer(UUID restaurantId) {
        Restaurant restaurant = restaurantService.getOrThrow(restaurantId);
        if (restaurant.getOffer() == RestaurantOffer.BASIC) {
            throw new ConflictException("Le studio de design est réservé aux offres PRO et PREMIUM.");
        }
        return restaurant;
    }

    private DesignResponse toResponse(Restaurant restaurant, MenuDesign design) {
        return new DesignResponse(
                restaurant.getOffer(),
                restaurant.getOffer() == RestaurantOffer.PREMIUM,
                design.preset(),
                DesignResponse.catalogue(),
                new Customization(
                        design.brandName(),
                        design.primaryColor(),
                        design.secondaryColor(),
                        design.logoAssetId(),
                        design.logoAssetId() == null ? null : urlBuilder.forAsset(design.logoAssetId()),
                        design.heroAssetId(),
                        design.heroAssetId() == null ? null : urlBuilder.forAsset(design.heroAssetId())));
    }

    private static String blankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    private static String upperOrNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim().toUpperCase(Locale.ROOT);
    }
}
