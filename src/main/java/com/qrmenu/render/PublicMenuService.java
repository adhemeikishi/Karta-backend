package com.qrmenu.render;

import com.qrmenu.common.PublicUrlBuilder;
import com.qrmenu.menu.Menu;
import com.qrmenu.menu.MenuCategory;
import com.qrmenu.menu.MenuCategoryRepository;
import com.qrmenu.menu.MenuItem;
import com.qrmenu.menu.MenuItemRepository;
import com.qrmenu.menu.MenuRepository;
import com.qrmenu.menu.MenuType;
import com.qrmenu.qrcode.QrCode;
import com.qrmenu.qrcode.QrCodeRepository;
import com.qrmenu.render.PublicMenuDtos.PublicCategory;
import com.qrmenu.render.PublicMenuDtos.PublicItem;
import com.qrmenu.render.PublicMenuDtos.PublicMenu;
import com.qrmenu.restaurant.Restaurant;
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
 *   <li>{@link #buildPreview(UUID)} — aperçu back-office, accepte n'importe quel statut.</li>
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

    public PublicMenuService(
            QrCodeRepository qrCodeRepository,
            MenuRepository menuRepository,
            MenuCategoryRepository categoryRepository,
            MenuItemRepository itemRepository,
            RestaurantService restaurantService,
            PublicUrlBuilder urlBuilder
    ) {
        this.qrCodeRepository = qrCodeRepository;
        this.menuRepository = menuRepository;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.restaurantService = restaurantService;
        this.urlBuilder = urlBuilder;
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
                .map(menu -> build(restaurantService.getOrThrow(restaurantId), menu));
    }

    /** Aperçu back-office : rend le menu quel que soit son statut, sans le publier. */
    @Transactional(readOnly = true)
    public Optional<PublicMenu> buildPreview(UUID restaurantId) {
        Restaurant restaurant = restaurantService.getOrThrow(restaurantId);
        return menuRepository.findByRestaurantId(restaurantId)
                .filter(menu -> menu.getType() == MenuType.STRUCTURED)
                .map(menu -> build(restaurant, menu));
    }

    // ---------------------------------------------------------------- construction

    private PublicMenu build(Restaurant restaurant, Menu menu) {
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

        return new PublicMenu(
                restaurant.getName(),
                dominantCurrency(publicCategories),
                MenuPreset.DEFAULT.name().toLowerCase(),
                publicCategories);
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
