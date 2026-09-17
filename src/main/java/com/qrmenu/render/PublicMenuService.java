package com.qrmenu.render;

import com.qrmenu.common.PublicUrlBuilder;
import com.qrmenu.kartapay.ModifierDtos.ModifierGroupResponse;
import com.qrmenu.kartapay.ModifierDtos.ModifierOptionResponse;
import com.qrmenu.kartapay.ModifierGroup;
import com.qrmenu.kartapay.ModifierGroupRepository;
import com.qrmenu.kartapay.ModifierOption;
import com.qrmenu.kartapay.ModifierOptionRepository;
import com.qrmenu.menu.Menu;
import com.qrmenu.menu.MenuCategory;
import com.qrmenu.menu.MenuCategoryRepository;
import com.qrmenu.menu.MenuItem;
import com.qrmenu.menu.MenuDesign;
import com.qrmenu.menu.MenuItemRepository;
import com.qrmenu.menu.MenuLanguage;
import com.qrmenu.menu.MenuRepository;
import com.qrmenu.menu.MenuType;
import com.qrmenu.menu.Translation;
import com.qrmenu.qrcode.QrCode;
import com.qrmenu.qrcode.QrCodeRepository;
import com.qrmenu.render.PublicMenuDtos.PublicCategory;
import com.qrmenu.render.PublicMenuDtos.PublicItem;
import com.qrmenu.render.PublicMenuDtos.PublicLanguage;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Construit la vue publique d'un menu structuré.
 *
 * Deux entrées, une seule logique de construction :
 * <ul>
 *   <li>{@link #findPublic(String)} — accès public, n'accepte qu'un menu PUBLISHED ;</li>
 *   <li>{@link #buildPreview(UUID, com.qrmenu.menu.MenuDesign, String)} — aperçu du studio, tout statut.</li>
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
    private final ModifierGroupRepository modifierGroupRepository;
    private final ModifierOptionRepository modifierOptionRepository;
    private final RestaurantService restaurantService;
    private final PublicUrlBuilder urlBuilder;
    private final MenuThemeResolver themeResolver;

    public PublicMenuService(
            QrCodeRepository qrCodeRepository,
            MenuRepository menuRepository,
            MenuCategoryRepository categoryRepository,
            MenuItemRepository itemRepository,
            ModifierGroupRepository modifierGroupRepository,
            ModifierOptionRepository modifierOptionRepository,
            RestaurantService restaurantService,
            PublicUrlBuilder urlBuilder,
            MenuThemeResolver themeResolver
    ) {
        this.qrCodeRepository = qrCodeRepository;
        this.menuRepository = menuRepository;
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.modifierGroupRepository = modifierGroupRepository;
        this.modifierOptionRepository = modifierOptionRepository;
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
        return findPublic(qrCode, null);
    }

    /** @param lang code ISO demandé ({@code ?lang=}) ; inconnu, non activé ou absent = français. */
    @Transactional(readOnly = true)
    public Optional<PublicMenu> findPublic(String qrCode, String lang) {
        Optional<QrCode> qr = qrCodeRepository.findByCode(qrCode).filter(QrCode::isActive);
        if (qr.isEmpty()) {
            return Optional.empty();
        }
        UUID restaurantId = qr.get().getRestaurantId();

        return menuRepository.findByRestaurantId(restaurantId)
                .filter(menu -> menu.getType() == MenuType.STRUCTURED)
                .filter(Menu::isPublished)
                .map(menu -> build(restaurantService.getOrThrow(restaurantId), menu, null, lang));
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
    public Optional<PublicMenu> buildPreview(UUID restaurantId, MenuDesign overrides, String lang) {
        Restaurant restaurant = restaurantService.getOrThrow(restaurantId);
        Optional<Menu> menu = menuRepository.findByRestaurantId(restaurantId)
                .filter(m -> m.getType() == MenuType.STRUCTURED);

        if (menu.isPresent()) {
            return menu.map(m -> build(restaurant, m, overrides, lang));
        }
        if (restaurant.getOffer() == RestaurantOffer.BASIC) {
            return Optional.empty(); // BASIC n'a pas de page HTML : le PDF est le menu
        }
        MenuDesign design = MenuDesign.defaults().mergedWith(overrides);
        return Optional.of(assemble(restaurant, design, languageFor(restaurant, design, lang), List.of()));
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

    private PublicMenu build(Restaurant restaurant, Menu menu, MenuDesign overrides, String lang) {
        MenuDesign design = menu.getDesign().mergedWith(overrides);
        MenuLanguage language = languageFor(restaurant, design, lang);
        // Photos : PREMIUM. Hors PREMIUM elles restent en base mais n'atteignent pas le HTML.
        boolean photos = restaurant.getOffer() == RestaurantOffer.PREMIUM;

        List<MenuCategory> categories = categoryRepository
                .findByMenuIdOrderBySortOrderAscNameAsc(menu.getId()).stream()
                .filter(MenuCategory::isVisible) // masquée = jamais rendue publiquement
                .toList();

        Map<UUID, List<MenuItem>> itemsByCategory = loadItems(categories);
        Map<UUID, List<ModifierGroupResponse>> modifierGroupsByItem = restaurant.isKartaPayEnabled()
                ? loadModifierGroups(itemsByCategory.values().stream().flatMap(List::stream).toList())
                : Map.of();

        List<PublicCategory> publicCategories = categories.stream()
                .map(category -> {
                    Translation t = category.getTranslations().get(language.code());
                    return new PublicCategory(
                            translated(t == null ? null : t.name(), category.getName()),
                            translated(t == null ? null : t.description(), category.getDescription()),
                            itemsByCategory.getOrDefault(category.getId(), List.of()).stream()
                                    .map(item -> toPublicItem(item, language, photos, modifierGroupsByItem))
                                    .toList());
                })
                .toList();

        return assemble(restaurant, design, language, publicCategories);
    }

    /**
     * Langue de la vue : celle demandée si le client l'a activée (PREMIUM), sinon le
     * français. Un {@code ?lang=} hors catalogue ne produit jamais d'erreur.
     */
    private MenuLanguage languageFor(Restaurant restaurant, MenuDesign design, String lang) {
        MenuLanguage requested = MenuLanguage.fromCode(lang);
        List<MenuLanguage> enabled = themeResolver.effectiveDesign(design, restaurant.getOffer()).languagesOrEmpty();
        return requested != null && enabled.contains(requested) ? requested : MenuLanguage.BASE;
    }

    /**
     * Assemble la vue publique une fois les catégories filtrées. Passage obligé du rendu
     * public comme de l'aperçu : le thème y est résolu une seule fois, au même endroit.
     */
    private PublicMenu assemble(
            Restaurant restaurant,
            MenuDesign design,
            MenuLanguage language,
            List<PublicCategory> categories
    ) {
        MenuDesign effective = themeResolver.effectiveDesign(design, restaurant.getOffer());
        String displayName = effective.brandName() == null || effective.brandName().isBlank()
                ? restaurant.getName()
                : effective.brandName();

        List<PublicLanguage> languages = new ArrayList<>();
        Stream.concat(Stream.of(MenuLanguage.BASE), effective.languagesOrEmpty().stream())
                .forEach(l -> languages.add(new PublicLanguage(l.code(), l.shortLabel())));

        return new PublicMenu(
                displayName,
                dominantCurrency(categories),
                themeResolver.resolve(design, restaurant.getOffer()),
                new PublicLanguage(language.code(), language.shortLabel()),
                languages,
                MenuLabels.of(language),
                categories,
                restaurant.isKartaPayEnabled());
    }

    /** Une traduction absente ou vide retombe sur le texte de base — jamais une chaîne vide. */
    private static String translated(String translation, String base) {
        return translation == null || translation.isBlank() ? base : translation;
    }

    private Map<UUID, List<MenuItem>> loadItems(List<MenuCategory> categories) {
        if (categories.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = categories.stream().map(MenuCategory::getId).toList();
        return itemRepository.findByCategoryIdInOrderBySortOrderAscNameAsc(ids).stream()
                .collect(Collectors.groupingBy(MenuItem::getCategoryId, LinkedHashMap::new, Collectors.toList()));
    }

    private PublicItem toPublicItem(
            MenuItem item,
            MenuLanguage language,
            boolean photos,
            Map<UUID, List<ModifierGroupResponse>> modifierGroupsByItem
    ) {
        Translation t = item.getTranslations().get(language.code());
        return new PublicItem(
                item.getId(),
                translated(t == null ? null : t.name(), item.getName()),
                translated(t == null ? null : t.description(), item.getDescription()),
                item.getPriceCents(),
                item.getCurrency(),
                formatPrice(item.getPriceCents(), item.getCurrency()),
                photos && item.getImageAssetId() != null ? urlBuilder.forAsset(item.getImageAssetId()) : null,
                item.isAvailable(),
                modifierGroupsByItem.getOrDefault(item.getId(), List.of()));
    }

    /** Comme {@code ModifierGroupService.load}, mais en un aller-retour pour tous les produits du menu. */
    private Map<UUID, List<ModifierGroupResponse>> loadModifierGroups(List<MenuItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        List<UUID> itemIds = items.stream().map(MenuItem::getId).toList();
        List<ModifierGroup> groups = modifierGroupRepository.findByItemIdInOrderBySortOrderAscNameAsc(itemIds);
        if (groups.isEmpty()) {
            return Map.of();
        }
        List<UUID> groupIds = groups.stream().map(ModifierGroup::getId).toList();
        Map<UUID, List<ModifierOption>> optionsByGroup = modifierOptionRepository
                .findByGroupIdInOrderBySortOrderAscNameAsc(groupIds).stream()
                .collect(Collectors.groupingBy(ModifierOption::getGroupId, LinkedHashMap::new, Collectors.toList()));
        return groups.stream().collect(Collectors.groupingBy(
                ModifierGroup::getItemId,
                LinkedHashMap::new,
                Collectors.mapping(group -> toModifierGroupResponse(group, optionsByGroup), Collectors.toList())));
    }

    private ModifierGroupResponse toModifierGroupResponse(
            ModifierGroup group,
            Map<UUID, List<ModifierOption>> optionsByGroup
    ) {
        List<ModifierOptionResponse> options = optionsByGroup.getOrDefault(group.getId(), List.of()).stream()
                .map(o -> new ModifierOptionResponse(
                        o.getId(), o.getName(), o.getPriceDeltaCents(), o.isAvailable(), o.getSortOrder()))
                .toList();
        return new ModifierGroupResponse(
                group.getId(), group.getName(), group.getSelectionType(),
                group.getMinSelect(), group.getMaxSelect(), group.getSortOrder(), options);
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
