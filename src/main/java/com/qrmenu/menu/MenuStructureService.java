package com.qrmenu.menu;

import com.qrmenu.common.InvalidMenuException;
import com.qrmenu.common.NotFoundException;
import com.qrmenu.media.MediaAsset;
import com.qrmenu.media.MediaService;
import com.qrmenu.common.PublicUrlBuilder;
import com.qrmenu.menu.MenuDtos.CategoryResponse;
import com.qrmenu.menu.MenuDtos.ItemResponse;
import com.qrmenu.menu.MenuDtos.MenuStructure;
import com.qrmenu.menu.MenuDtos.SaveCategoryRequest;
import com.qrmenu.menu.MenuDtos.SaveItemRequest;
import com.qrmenu.restaurant.Restaurant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Contenu d'un menu structuré : catégories et produits.
 *
 * Séparé de {@link MenuService} (qui gère le cycle PDF / publication / destination du QR)
 * pour que les deux formes de menu n'aient pas à se connaître.
 *
 * L'écriture se fait par <strong>document complet</strong> : le payload décrit l'état
 * final voulu. Les identifiants fournis sont conservés, les absents sont supprimés.
 */
@Service
public class MenuStructureService {

    static final String DEFAULT_CURRENCY = "EUR";

    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository itemRepository;
    private final MediaService mediaService;
    private final PublicUrlBuilder mediaUrlBuilder;

    public MenuStructureService(
            MenuCategoryRepository categoryRepository,
            MenuItemRepository itemRepository,
            MediaService mediaService,
            PublicUrlBuilder mediaUrlBuilder
    ) {
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.mediaService = mediaService;
        this.mediaUrlBuilder = mediaUrlBuilder;
    }

    // ---------------------------------------------------------------- lecture

    /** Structure complète, triée, prête à être sérialisée en JSON canonique. */
    @Transactional(readOnly = true)
    public MenuStructure load(Restaurant restaurant, Menu menu) {
        List<MenuCategory> categories = categoryRepository.findByMenuIdOrderBySortOrderAscNameAsc(menu.getId());
        Map<UUID, List<MenuItem>> itemsByCategory = loadItemsByCategory(categories);

        List<CategoryResponse> categoryResponses = categories.stream()
                .map(category -> new CategoryResponse(
                        category.getId(),
                        category.getName(),
                        category.getDescription(),
                        category.getSortOrder(),
                        category.isVisible(),
                        itemsByCategory.getOrDefault(category.getId(), List.of()).stream()
                                .map(this::toItemResponse)
                                .toList()))
                .toList();

        return new MenuStructure(restaurant.getName(), dominantCurrency(itemsByCategory), categoryResponses);
    }

    /** Structure vide d'un client qui n'a pas encore de menu en base. */
    public MenuStructure emptyStructure(Restaurant restaurant) {
        return new MenuStructure(restaurant.getName(), DEFAULT_CURRENCY, List.of());
    }

    public boolean hasContent(UUID menuId) {
        return categoryRepository.countByMenuId(menuId) > 0;
    }

    // ---------------------------------------------------------------- écriture

    /**
     * Remplace l'intégralité du contenu du menu.
     *
     * Les catégories / produits dont l'{@code id} est fourni sont mis à jour en place
     * (l'identité survit à un réordonnancement ou à un changement de catégorie) ;
     * ceux qui ne figurent pas dans le payload sont supprimés.
     */
    @Transactional
    public void replace(Restaurant restaurant, Menu menu, List<SaveCategoryRequest> requestedCategories) {
        List<SaveCategoryRequest> incoming = requestedCategories == null ? List.of() : requestedCategories;

        Map<UUID, MenuCategory> existingCategories = categoryRepository
                .findByMenuIdOrderBySortOrderAscNameAsc(menu.getId()).stream()
                .collect(Collectors.toMap(MenuCategory::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<UUID, MenuItem> existingItems = loadItems(existingCategories.keySet()).stream()
                .collect(Collectors.toMap(MenuItem::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        Set<UUID> keptCategories = new LinkedHashSet<>();
        Set<UUID> keptItems = new LinkedHashSet<>();

        for (int i = 0; i < incoming.size(); i++) {
            SaveCategoryRequest request = incoming.get(i);
            MenuCategory category = resolveCategory(menu.getId(), existingCategories, keptCategories, request);

            category.update(
                    requireName(request.name(), "le nom de la catégorie est obligatoire"),
                    blankToNull(request.description()),
                    requireSortOrder(request.sortOrder(), i),
                    request.visible() == null || request.visible());
            categoryRepository.save(category);

            List<SaveItemRequest> items = request.items() == null ? List.of() : request.items();
            for (int j = 0; j < items.size(); j++) {
                SaveItemRequest itemRequest = items.get(j);
                MenuItem item = resolveItem(category.getId(), existingItems, keptItems, itemRequest);

                item.update(
                        category.getId(),
                        requireName(itemRequest.name(), "le nom du produit est obligatoire"),
                        blankToNull(itemRequest.description()),
                        requirePrice(itemRequest.price()),
                        normalizeCurrency(itemRequest.currency()),
                        validateImage(restaurant.getId(), itemRequest.imageAssetId()),
                        requireSortOrder(itemRequest.sortOrder(), j),
                        itemRequest.available() == null || itemRequest.available());
                itemRepository.save(item);
            }
        }

        // Suppression explicite des produits puis des catégories absents du payload :
        // on ne dépend pas de l'ordre du CASCADE.
        existingItems.values().stream()
                .filter(item -> !keptItems.contains(item.getId()))
                .forEach(itemRepository::delete);
        existingCategories.values().stream()
                .filter(category -> !keptCategories.contains(category.getId()))
                .forEach(categoryRepository::delete);
    }

    /** Vide le contenu d'un menu (utilisé avant suppression du menu lui-même). */
    @Transactional
    public void deleteContent(UUID menuId) {
        List<MenuCategory> categories = categoryRepository.findByMenuIdOrderBySortOrderAscNameAsc(menuId);
        itemRepository.deleteAll(loadItems(categories.stream().map(MenuCategory::getId).toList()));
        categoryRepository.deleteAll(categories);
    }

    // ---------------------------------------------------------------- internes

    private MenuCategory resolveCategory(
            UUID menuId,
            Map<UUID, MenuCategory> existing,
            Set<UUID> kept,
            SaveCategoryRequest request
    ) {
        if (request.id() == null) {
            MenuCategory created = new MenuCategory(menuId);
            kept.add(created.getId());
            return created;
        }
        MenuCategory category = existing.get(request.id());
        if (category == null) {
            throw new InvalidMenuException("Catégorie inconnue pour ce menu: " + request.id());
        }
        if (!kept.add(category.getId())) {
            throw new InvalidMenuException("Catégorie envoyée deux fois: " + request.id());
        }
        return category;
    }

    private MenuItem resolveItem(
            UUID categoryId,
            Map<UUID, MenuItem> existing,
            Set<UUID> kept,
            SaveItemRequest request
    ) {
        if (request.id() == null) {
            MenuItem created = new MenuItem(categoryId);
            kept.add(created.getId());
            return created;
        }
        MenuItem item = existing.get(request.id());
        if (item == null) {
            throw new InvalidMenuException("Produit inconnu pour ce menu: " + request.id());
        }
        if (!kept.add(item.getId())) {
            throw new InvalidMenuException("Produit envoyé deux fois: " + request.id());
        }
        return item;
    }

    private Map<UUID, List<MenuItem>> loadItemsByCategory(List<MenuCategory> categories) {
        return loadItems(categories.stream().map(MenuCategory::getId).toList()).stream()
                .collect(Collectors.groupingBy(MenuItem::getCategoryId, LinkedHashMap::new, Collectors.toList()));
    }

    private List<MenuItem> loadItems(Collection<UUID> categoryIds) {
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        return itemRepository.findByCategoryIdInOrderBySortOrderAscNameAsc(categoryIds);
    }

    private ItemResponse toItemResponse(MenuItem item) {
        return new ItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getPriceCents(),
                item.getCurrency(),
                item.getImageAssetId(),
                item.getImageAssetId() == null ? null : mediaUrlBuilder.forAsset(item.getImageAssetId()),
                item.getSortOrder(),
                item.isAvailable());
    }

    /**
     * V1 : une seule devise par menu en pratique. On expose celle du premier produit
     * pour que le renderer n'ait pas à parcourir tout le document.
     */
    private String dominantCurrency(Map<UUID, List<MenuItem>> itemsByCategory) {
        return itemsByCategory.values().stream()
                .flatMap(List::stream)
                .map(MenuItem::getCurrency)
                .findFirst()
                .orElse(DEFAULT_CURRENCY);
    }

    private static String requireName(String raw, String message) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new InvalidMenuException(message);
        }
        return value;
    }

    private static String blankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    private static int requirePrice(Integer price) {
        if (price == null) {
            throw new InvalidMenuException("le prix est obligatoire");
        }
        if (price < 0) {
            throw new InvalidMenuException("le prix doit être >= 0");
        }
        return price;
    }

    private static int requireSortOrder(Integer sortOrder, int fallback) {
        if (sortOrder == null) {
            return fallback;
        }
        if (sortOrder < 0) {
            throw new InvalidMenuException("sortOrder doit être >= 0");
        }
        return sortOrder;
    }

    private static String normalizeCurrency(String raw) {
        String code = (raw == null || raw.isBlank())
                ? DEFAULT_CURRENCY
                : raw.trim().toUpperCase(Locale.ROOT);
        Currency currency;
        try {
            currency = Currency.getInstance(code);
        } catch (IllegalArgumentException e) {
            throw new InvalidMenuException("Devise invalide: " + code);
        }
        // Écarte les pseudo-devises ISO-4217 (XXX « aucune devise », XAU, DTS...) :
        // sans nombre de décimales défini, elles ne peuvent pas exprimer un prix en centimes.
        if (currency.getDefaultFractionDigits() < 0) {
            throw new InvalidMenuException("Devise invalide: " + code);
        }
        return code;
    }

    private UUID validateImage(UUID restaurantId, UUID imageAssetId) {
        if (imageAssetId == null) {
            return null;
        }
        MediaAsset asset;
        try {
            asset = mediaService.getOrThrow(imageAssetId);
        } catch (NotFoundException e) {
            throw new InvalidMenuException("Image introuvable: " + imageAssetId);
        }
        if (!asset.getRestaurantId().equals(restaurantId)) {
            throw new InvalidMenuException("Image non rattachée à ce client: " + imageAssetId);
        }
        return imageAssetId;
    }
}
