package com.qrmenu.menu;

import com.qrmenu.common.ConflictException;
import com.qrmenu.common.InvalidMenuException;
import com.qrmenu.common.NotFoundException;
import com.qrmenu.menu.MenuDtos.CategoryResponse;
import com.qrmenu.menu.MenuDtos.ItemResponse;
import com.qrmenu.menu.MenuDtos.MenuResponse;
import com.qrmenu.menu.MenuDtos.SaveCategoryRequest;
import com.qrmenu.menu.MenuDtos.SaveItemRequest;
import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantOffer;
import com.qrmenu.restaurant.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fondation du menu structuré : relation Client -> Menu -> Catégories -> Produits,
 * unicité du menu, validations et prix en centimes.
 */
@SpringBootTest
@ActiveProfiles("test")
class MenuStructureServiceTest {

    @Autowired
    private MenuService menuService;
    @Autowired
    private RestaurantService restaurantService;
    @Autowired
    private MenuRepository menuRepository;
    @Autowired
    private MenuCategoryRepository categoryRepository;

    private Restaurant proRestaurant() {
        return restaurantService.create("Resto PRO " + System.nanoTime(), RestaurantOffer.PRO);
    }

    private static SaveItemRequest item(String name, Integer price) {
        return new SaveItemRequest(null, name, null, price, null, null, null, null);
    }

    private static SaveCategoryRequest category(String name, SaveItemRequest... items) {
        return new SaveCategoryRequest(null, name, null, null, null, List.of(items));
    }

    // ------------------------------------------------------------ création / unicité

    @Test
    void createsStructuredMenuForProClient() {
        Restaurant r = proRestaurant();

        MenuResponse menu = menuService.createMenu(r.getId());

        assertThat(menu.type()).isEqualTo(MenuType.STRUCTURED);
        assertThat(menu.status()).isEqualTo(MenuStatus.DRAFT);
        assertThat(menu.version()).isEqualTo(1);
        assertThat(menu.published()).isFalse();
        assertThat(menu.pdf()).isNull();
        assertThat(menu.structure()).isNotNull();
        assertThat(menu.structure().restaurantName()).isEqualTo(r.getName());
        assertThat(menu.structure().currency()).isEqualTo("EUR");
        assertThat(menu.structure().categories()).isEmpty();
    }

    /**
     * Le chemin manuel (l'éditeur du studio) n'a jamais besoin d'un menu préexistant : le
     * {@code PUT} qu'il envoie crée le menu au premier appel, exactement comme le fait
     * KartaAI après Review. Un client PRO tout neuf peut donc créer sa carte entièrement
     * à la main — sans passer par KartaAI, et sans {@link #createsStructuredMenuForProClient}
     * (aucun {@code POST} de création préalable requis).
     */
    @Test
    void manualEditorCanCreateTheMenuFromScratchWithoutAnyPriorMenuRow() {
        Restaurant r = proRestaurant();
        assertThat(menuRepository.findByRestaurantId(r.getId())).isEmpty(); // aucun menu en base

        MenuResponse menu = menuService.saveStructure(r.getId(), List.of(
                category("Entrées", item("Velouté de potiron", 890))));

        assertThat(menuRepository.findByRestaurantId(r.getId())).isPresent();
        assertThat(menu.type()).isEqualTo(MenuType.STRUCTURED);
        assertThat(menu.structure().categories()).hasSize(1);
        assertThat(menu.structure().categories().get(0).items().get(0).name())
                .isEqualTo("Velouté de potiron");
    }

    /** Même garantie pour PREMIUM : l'accès au menu structuré ne distingue pas PRO/PREMIUM. */
    @Test
    void manualEditorCanCreateTheMenuFromScratchForPremiumToo() {
        Restaurant premium = restaurantService.create("Resto PREMIUM " + System.nanoTime(), RestaurantOffer.PREMIUM);
        assertThat(menuRepository.findByRestaurantId(premium.getId())).isEmpty();

        MenuResponse menu = menuService.saveStructure(premium.getId(), List.of(
                category("Entrées", item("Velouté de potiron", 890))));

        assertThat(menu.structure().categories()).hasSize(1);
    }

    @Test
    void aClientCanOnlyHaveOneMenu() {
        Restaurant r = proRestaurant();
        menuService.createMenu(r.getId());

        assertThatThrownBy(() -> menuService.createMenu(r.getId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void basicClientGetsAPdfMenuNotAStructuredOne() {
        Restaurant basic = restaurantService.create("Resto BASIC " + System.nanoTime(), RestaurantOffer.BASIC);

        MenuResponse menu = menuService.createMenu(basic.getId());

        assertThat(menu.type()).isEqualTo(MenuType.PDF);
        assertThat(menu.structure()).isNull();
    }

    @Test
    void rejectsStructuredMenuForBasicOffer() {
        Restaurant basic = restaurantService.create("Resto BASIC " + System.nanoTime(), RestaurantOffer.BASIC);

        assertThatThrownBy(() -> menuService.saveStructure(basic.getId(), List.of(category("Burgers"))))
                .isInstanceOf(ConflictException.class);
    }

    // ------------------------------------------------------------ écriture du contenu

    @Test
    void savesCategoriesAndItemsWithPriceInCents() {
        Restaurant r = proRestaurant();

        MenuResponse menu = menuService.saveStructure(r.getId(), List.of(
                new SaveCategoryRequest(null, "Burgers", "Nos burgers", 1, true, List.of(
                        new SaveItemRequest(null, "Cheeseburger", "Steak, cheddar, salade",
                                1290, "EUR", null, 1, true)))));

        assertThat(menu.type()).isEqualTo(MenuType.STRUCTURED);
        assertThat(menu.status()).isEqualTo(MenuStatus.READY);
        assertThat(menu.structure().categories()).hasSize(1);

        CategoryResponse burgers = menu.structure().categories().get(0);
        assertThat(burgers.id()).isNotNull();
        assertThat(burgers.name()).isEqualTo("Burgers");
        assertThat(burgers.description()).isEqualTo("Nos burgers");
        assertThat(burgers.sortOrder()).isEqualTo(1);
        assertThat(burgers.visible()).isTrue();
        assertThat(burgers.items()).hasSize(1);

        ItemResponse cheeseburger = burgers.items().get(0);
        assertThat(cheeseburger.id()).isNotNull();
        assertThat(cheeseburger.name()).isEqualTo("Cheeseburger");
        assertThat(cheeseburger.description()).isEqualTo("Steak, cheddar, salade");
        assertThat(cheeseburger.price()).isEqualTo(1290); // centimes entiers, jamais 12.90
        assertThat(cheeseburger.currency()).isEqualTo("EUR");
        assertThat(cheeseburger.available()).isTrue();
        assertThat(cheeseburger.imageAssetId()).isNull();
        assertThat(cheeseburger.imageUrl()).isNull();
    }

    @Test
    void appliesDefaultsForCurrencySortOrderAndAvailability() {
        Restaurant r = proRestaurant();

        MenuResponse menu = menuService.saveStructure(r.getId(), List.of(
                category("Boissons", item("Coca", 350), item("Eau", 200))));

        CategoryResponse boissons = menu.structure().categories().get(0);
        assertThat(boissons.sortOrder()).isZero();
        assertThat(boissons.visible()).isTrue();
        assertThat(boissons.items()).extracting(ItemResponse::currency).containsOnly("EUR");
        assertThat(boissons.items()).extracting(ItemResponse::sortOrder).containsExactly(0, 1);
        assertThat(boissons.items()).extracting(ItemResponse::available).containsOnly(true);
    }

    @Test
    void structureIsReturnedOrderedBySortOrder() {
        Restaurant r = proRestaurant();

        MenuResponse menu = menuService.saveStructure(r.getId(), List.of(
                new SaveCategoryRequest(null, "Desserts", null, 5, true, List.of()),
                new SaveCategoryRequest(null, "Entrées", null, 1, true, List.of())));

        assertThat(menu.structure().categories())
                .extracting(CategoryResponse::name)
                .containsExactly("Entrées", "Desserts");
    }

    @Test
    void keepsIdentifiersOnUpdateAndRemovesMissingEntries() {
        Restaurant r = proRestaurant();
        MenuResponse initial = menuService.saveStructure(r.getId(), List.of(
                category("Burgers", item("Cheeseburger", 1290), item("Veggie", 1190)),
                category("Desserts", item("Tiramisu", 600))));

        CategoryResponse burgers = initial.structure().categories().stream()
                .filter(c -> c.name().equals("Burgers")).findFirst().orElseThrow();
        ItemResponse cheeseburger = burgers.items().stream()
                .filter(i -> i.name().equals("Cheeseburger")).findFirst().orElseThrow();

        // On renvoie uniquement Burgers/Cheeseburger, renommé et repricé.
        MenuResponse updated = menuService.saveStructure(r.getId(), List.of(
                new SaveCategoryRequest(burgers.id(), "Burgers maison", null, 0, true, List.of(
                        new SaveItemRequest(cheeseburger.id(), "Cheeseburger XL", null,
                                1490, "EUR", null, 0, false)))));

        assertThat(updated.version()).isEqualTo(initial.version() + 1);
        assertThat(updated.structure().categories()).hasSize(1);

        CategoryResponse only = updated.structure().categories().get(0);
        assertThat(only.id()).isEqualTo(burgers.id()); // identité conservée
        assertThat(only.name()).isEqualTo("Burgers maison");
        assertThat(only.items()).hasSize(1);
        assertThat(only.items().get(0).id()).isEqualTo(cheeseburger.id());
        assertThat(only.items().get(0).price()).isEqualTo(1490);
        assertThat(only.items().get(0).available()).isFalse();
    }

    @Test
    void emptyPayloadClearsContentAndReturnsToDraft() {
        Restaurant r = proRestaurant();
        menuService.saveStructure(r.getId(), List.of(category("Burgers", item("Cheeseburger", 1290))));

        MenuResponse cleared = menuService.saveStructure(r.getId(), List.of());

        assertThat(cleared.structure().categories()).isEmpty();
        assertThat(cleared.status()).isEqualTo(MenuStatus.DRAFT);
    }

    // ------------------------------------------------------------ validations

    @Test
    void rejectsNegativePrice() {
        Restaurant r = proRestaurant();

        assertThatThrownBy(() -> menuService.saveStructure(r.getId(),
                List.of(category("Burgers", item("Cheeseburger", -1)))))
                .isInstanceOf(InvalidMenuException.class);
    }

    @Test
    void rejectsMissingPrice() {
        Restaurant r = proRestaurant();

        assertThatThrownBy(() -> menuService.saveStructure(r.getId(),
                List.of(category("Burgers", item("Cheeseburger", null)))))
                .isInstanceOf(InvalidMenuException.class);
    }

    /**
     * Atomicité de l'enregistrement : un payload avec un item invalide au milieu d'items
     * par ailleurs valides ne doit rien écrire — ni le nouveau contenu, ni une partie de
     * l'ancien remplacée. Représentatif du studio d'édition, qui envoie tout le document
     * en un seul {@code PUT} (voir {@code MenuEditorComponent}, admin-frontend).
     */
    @Test
    void rejectsWholePayloadAtomicallyWhenOneItemIsInvalid() {
        Restaurant r = proRestaurant();
        menuService.saveStructure(r.getId(), List.of(
                category("Burgers", item("Cheeseburger", 1290))));

        assertThatThrownBy(() -> menuService.saveStructure(r.getId(), List.of(
                category("Burgers", item("Cheeseburger", 1490)),
                category("Desserts", item("Tiramisu", -1))))) // prix négatif : invalide
                .isInstanceOf(InvalidMenuException.class);

        // Rien de la tentative ratée n'a été écrit : le menu précédent est intact.
        MenuResponse unchanged = menuService.getMenu(r.getId());
        assertThat(unchanged.structure().categories()).hasSize(1);
        assertThat(unchanged.structure().categories().get(0).name()).isEqualTo("Burgers");
        assertThat(unchanged.structure().categories().get(0).items().get(0).price())
                .isEqualTo(1290); // pas 1490 : la mise à jour partielle n'a pas eu lieu
    }

    @Test
    void rejectsBlankNames() {
        Restaurant r = proRestaurant();

        assertThatThrownBy(() -> menuService.saveStructure(r.getId(),
                List.of(category("   ", item("Cheeseburger", 1290)))))
                .isInstanceOf(InvalidMenuException.class);

        assertThatThrownBy(() -> menuService.saveStructure(r.getId(),
                List.of(category("Burgers", item("  ", 1290)))))
                .isInstanceOf(InvalidMenuException.class);
    }

    @Test
    void rejectsUnknownCurrency() {
        Restaurant r = proRestaurant();

        assertThatThrownBy(() -> menuService.saveStructure(r.getId(), List.of(
                new SaveCategoryRequest(null, "Burgers", null, 0, true, List.of(
                        new SaveItemRequest(null, "Cheeseburger", null, 1290, "XXX", null, 0, true))))))
                .isInstanceOf(InvalidMenuException.class);
    }

    @Test
    void rejectsUnknownImageAsset() {
        Restaurant r = proRestaurant();

        assertThatThrownBy(() -> menuService.saveStructure(r.getId(), List.of(
                new SaveCategoryRequest(null, "Burgers", null, 0, true, List.of(
                        new SaveItemRequest(null, "Cheeseburger", null, 1290, "EUR",
                                UUID.randomUUID(), 0, true))))))
                .isInstanceOf(InvalidMenuException.class);
    }

    @Test
    void rejectsCategoryBelongingToAnotherMenu() {
        Restaurant a = proRestaurant();
        Restaurant b = proRestaurant();
        MenuResponse menuA = menuService.saveStructure(a.getId(), List.of(category("Burgers")));
        UUID categoryOfA = menuA.structure().categories().get(0).id();

        assertThatThrownBy(() -> menuService.saveStructure(b.getId(), List.of(
                new SaveCategoryRequest(categoryOfA, "Vol de catégorie", null, 0, true, List.of()))))
                .isInstanceOf(InvalidMenuException.class);
    }

    // ------------------------------------------------------------ lecture / suppression

    @Test
    void menuIsScopedToItsOwnClient() {
        Restaurant a = proRestaurant();
        Restaurant b = proRestaurant();
        menuService.saveStructure(a.getId(), List.of(category("Burgers", item("Cheeseburger", 1290))));

        assertThat(menuService.getMenu(a.getId()).structure().categories()).hasSize(1);
        assertThat(menuService.getMenu(b.getId()).structure().categories()).isEmpty();
        assertThat(menuService.getMenu(b.getId()).version()).isZero(); // aucun menu en base
    }

    @Test
    void deleteMenuRemovesMenuAndItsStructure() {
        Restaurant r = proRestaurant();
        menuService.saveStructure(r.getId(), List.of(category("Burgers", item("Cheeseburger", 1290))));
        UUID menuId = menuRepository.findByRestaurantId(r.getId()).orElseThrow().getId();
        assertThat(categoryRepository.countByMenuId(menuId)).isEqualTo(1);

        menuService.deleteMenu(r.getId());

        assertThat(menuRepository.findByRestaurantId(r.getId())).isEmpty();
        assertThat(categoryRepository.countByMenuId(menuId)).isZero();
        assertThat(menuService.getMenu(r.getId()).structure().categories()).isEmpty();
    }

    @Test
    void deleteMenuFailsWhenThereIsNoMenu() {
        Restaurant r = proRestaurant();

        assertThatThrownBy(() -> menuService.deleteMenu(r.getId()))
                .isInstanceOf(NotFoundException.class);
    }
}
