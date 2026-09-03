package com.qrmenu.render;

import com.qrmenu.common.PublicUrlBuilder;
import com.qrmenu.menu.Menu;
import com.qrmenu.menu.MenuCategory;
import com.qrmenu.menu.MenuCategoryRepository;
import com.qrmenu.menu.MenuItem;
import com.qrmenu.menu.MenuDesign;
import com.qrmenu.menu.MenuItemRepository;
import com.qrmenu.menu.MenuRepository;
import com.qrmenu.menu.MenuType;
import com.qrmenu.qrcode.QrCode;
import com.qrmenu.qrcode.QrCodeRepository;
import com.qrmenu.render.PublicMenuDtos.PublicCategory;
import com.qrmenu.render.PublicMenuDtos.PublicItem;
import com.qrmenu.render.PublicMenuDtos.PublicMenu;
import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantOffer;
import com.qrmenu.restaurant.RestaurantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Construit la vue publique d'un menu structuré.
 *
 * Deux entrées, une seule logique de construction :
 * <ul>
 *   <li>{@link #findPublic(String)} — accès public, n'accepte qu'un menu PUBLISHED ;</li>
 *   <li>{@link #buildPreview(UUID, com.qrmenu.menu.MenuDesign)} — aperçu du studio, tout statut.</li>
 * </ul>
 *
 * C'est ici — et pas dans le template — que le contenu non diffusable est écarté :
 * une catégorie masquée n'atteint jamais le HTML, même pas en commentaire.
 */
@Service
public class PublicMenuService {

    private final QrCodeRepository qrCodeRepository;
    private final MenuRepository menuRepository;
    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository itemRepository;
    private final RestaurantService restaurantService;
    private final PublicUrlBuilder urlBuilder;
    private final MenuThemeResolver themeResolver;

    public PublicMenuService(
            QrCodeRepository qrCodeRepository,
            MenuRepository menuRepository,
            MenuCategoryRepository categoryRepository,
            MenuItemRepository itemRepository,
            RestaurantService restaurantService,
            PublicUrlBuilder urlBuilder,
            MenuThemeResolver themeResolver
    ) {
        this.qrCodeRepository = qrCodeRepository;
        this.menuRepository = menuRepository;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.restaurantService = restaurantService;
        this.urlBuilder = urlBuilder;
        this.themeResolver = themeResolver;
    }

    /**
     * Menu diffusable derrière un code QR, ou {@link Optional#empty()}.
     *
     * Vide (donc « indisponible ») si : le code est inconnu, le QR est désactivé,
     * le client n'a pas de menu, le menu n'est pas structuré, ou il n'est pas publié.
     * Volontairement un seul résultat pour tous ces cas : le public n'a pas à savoir
     * lequel s'applique.
     */
    @Transactional(readOnly = true)
    public Optional<PublicMenu> findPublic(String qrCode) {
        Optional<QrCode> qr = qrCodeRepository.findByCode(qrCode).filter(QrCode::isActive);
        if (qr.isEmpty()) {
            return Optional.empty();
        }
        UUID restaurantId = qr.get().getRestaurantId();

        return menuRepository.findByRestaurantId(restaurantId)
                .filter(menu -> menu.getType() == MenuType.STRUCTURED)
                .filter(Menu::isPublished)
                .map(menu -> build(restaurantService.getOrThrow(restaurantId), menu, null));
    }

    /**
     * Aperçu du studio : rend le menu quel que soit son statut, sans rien publier ni
     * enregistrer.
     *
     * {@code overrides} porte les choix d'apparence <strong>non enregistrés</strong> —
     * c'est ce qui permet à l'aperçu de suivre un clic sur un preset avant même que le
     * restaurateur n'ait cliqué sur « Enregistrer ». Rien n'est écrit en base.
     *
     * Un client PRO / PREMIUM qui n'a pas encore de ligne {@code menus} obtient un aperçu
     * vide plutôt qu'un 404 : le studio doit être utilisable dès la première visite,
     * sinon choisir un style imposerait d'abord de saisir une carte.
     */
    @Transactional(readOnly = true)
    public Optional<PublicMenu> buildPreview(UUID restaurantId, MenuDesign overrides) {
        Restaurant restaurant = restaurantService.getOrThrow(restaurantId);
        Optional<Menu> menu = menuRepository.findByRestaurantId(restaurantId)
                .filter(m -> m.getType() == MenuType.STRUCTURED);

        if (menu.isPresent()) {
            return menu.map(m -> build(restaurant, m, overrides));
        }
        if (restaurant.getOffer() == RestaurantOffer.BASIC) {
            return Optional.empty(); // BASIC n'a pas de page HTML : le PDF est le menu
        }
        return Optional.of(assemble(restaurant, MenuDesign.defaults().mergedWith(overrides), List.of()));
    }

    /**
     * Le menu de ce client est-il actuellement diffusé ?
     *
     * Sert au seul bandeau d'aperçu : dire « ce menu n'est pas publié » à un
     * restaurateur dont la carte est en ligne est un mensonge, et il en tirerait de
     * mauvaises conclusions. Volontairement hors du {@link PublicMenu} : un statut
     * d'administration n'a rien à faire dans la vue publique.
     */
    @Transactional(readOnly = true)
    public boolean isPublished(UUID restaurantId) {
        return menuRepository.findByRestaurantId(restaurantId).map(Menu::isPublished).orElse(false);
    }

    // ---------------------------------------------------------------- construction

    private PublicMenu build(Restaurant restaurant, Menu menu, MenuDesign overrides) {
        List<MenuCategory> categories = categoryRepository
                .findByMenuIdOrderBySortOrderAscNameAsc(menu.getId()).stream()
                .filter(MenuCategory::isVisible) // masquée = jamais rendue publiquement
                .toList();

        Map<UUID, List<MenuItem>> itemsByCategory = loadItems(categories);

        List<PublicCategory> publicCategories = categories.stream()
                .map(category -> new PublicCategory(
                        category.getName(),
                        category.getDescription(),
                        itemsByCategory.getOrDefault(category.getId(), List.of()).stream()
                                .map(this::toPublicItem)
                                .toList()))
                .toList();

        return assemble(restaurant, menu.getDesign().mergedWith(overrides), publicCategories);
    }

    /**
     * Assemble la vue publique une fois les catégories filtrées. Passage obligé du rendu
     * public comme de l'aperçu : le thème y est résolu une seule fois, au même endroit.
     */
    private PublicMenu assemble(Restaurant restaurant, MenuDesign design, List<PublicCategory> categories) {
        MenuDesign effective = themeResolver.effectiveDesign(design, restaurant.getOffer());
        String displayName = effective.brandName() == null || effective.brandName().isBlank()
                ? restaurant.getName()
                : effective.brandName();

        return new PublicMenu(
                displayName,
                dominantCurrency(categories),
                themeResolver.resolve(design, restaurant.getOffer()),
                categories);
    }

    private Map<UUID, List<MenuItem>> loadItems(List<MenuCategory> categories) {
        if (categories.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = categories.stream().map(MenuCategory::getId).toList();
        return itemRepository.findByCategoryIdInOrderBySortOrderAscNameAsc(ids).stream()
                .collect(Collectors.groupingBy(MenuItem::getCategoryId, LinkedHashMap::new, Collectors.toList()));
    }

    private PublicItem toPublicItem(MenuItem item) {
        return new PublicItem(
                item.getName(),
                item.getDescription(),
                item.getPriceCents(),
                item.getCurrency(),
                formatPrice(item.getPriceCents(), item.getCurrency()),
                item.getImageAssetId() == null ? null : urlBuilder.forAsset(item.getImageAssetId()),
                item.isAvailable());
    }

    /**
     * Centimes entiers vers libellé monétaire, sans jamais passer par un flottant :
     * {@code BigDecimal.valueOf(1290, 2)} vaut exactement 12.90.
     */
    static String formatPrice(int priceCents, String currencyCode) {
        BigDecimal amount = BigDecimal.valueOf(priceCents, 2);
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.FRANCE);
        format.setCurrency(Currency.getInstance(currencyCode));
        return format.format(amount);
    }

    private static String dominantCurrency(List<PublicCategory> categories) {
        return categories.stream()
                .flatMap(category -> category.items().stream())
                .map(PublicItem::currency)
                .findFirst()
                .orElse("EUR");
    }
}
