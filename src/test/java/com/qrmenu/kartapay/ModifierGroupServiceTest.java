package com.qrmenu.kartapay;

import com.qrmenu.common.InvalidMenuException;
import com.qrmenu.kartapay.ModifierDtos.ModifierGroupResponse;
import com.qrmenu.kartapay.ModifierDtos.SaveModifierGroupRequest;
import com.qrmenu.kartapay.ModifierDtos.SaveModifierOptionRequest;
import com.qrmenu.menu.MenuDtos.SaveCategoryRequest;
import com.qrmenu.menu.MenuDtos.SaveItemRequest;
import com.qrmenu.menu.MenuService;
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
 * Groupes d'options d'un produit : écriture par document complet, comme
 * {@code MenuStructureServiceTest}, et règles de sélection (min/max, SINGLE).
 */
@SpringBootTest
@ActiveProfiles("test")
class ModifierGroupServiceTest {

    @Autowired
    private ModifierGroupService modifierGroupService;
    @Autowired
    private MenuService menuService;
    @Autowired
    private RestaurantService restaurantService;

    private record ItemRef(UUID restaurantId, UUID itemId) {
    }

    private ItemRef newItem() {
        var restaurant = restaurantService.create("Resto Options " + System.nanoTime(), RestaurantOffer.PRO);
        var menu = menuService.saveStructure(restaurant.getId(), List.of(
                new SaveCategoryRequest(null, "Burgers", null, 0, true, List.of(
                        new SaveItemRequest(null, "Cheeseburger", null, 1290, "EUR", null, 0, true)))));
        UUID itemId = menu.structure().categories().get(0).items().get(0).id();
        return new ItemRef(restaurant.getId(), itemId);
    }

    // ------------------------------------------------------------------ document complet

    @Test
    void replacesWholeDocumentKeepingIdentifiersAndDeletingWhatsMissing() {
        ItemRef item = newItem();

        List<ModifierGroupResponse> created = modifierGroupService.replace(item.restaurantId(), item.itemId(), List.of(
                new SaveModifierGroupRequest(null, "Cuisson", SelectionType.SINGLE, 1, null, 0, List.of(
                        new SaveModifierOptionRequest(null, "Saignant", 0, true, 0),
                        new SaveModifierOptionRequest(null, "Bien cuit", 0, true, 1))),
                new SaveModifierGroupRequest(null, "Suppléments", SelectionType.MULTIPLE, 0, 3, 1, List.of(
                        new SaveModifierOptionRequest(null, "Bacon", 150, true, 0)))));

        assertThat(created).hasSize(2);
        assertThat(modifierGroupService.load(item.restaurantId(), item.itemId())).hasSize(2);

        UUID cuissonId = created.get(0).id();
        UUID saignantId = created.get(0).options().get(0).id();

        // On ne renvoie plus que "Cuisson" (renommé), une option conservée + une nouvelle.
        List<ModifierGroupResponse> updated = modifierGroupService.replace(item.restaurantId(), item.itemId(), List.of(
                new SaveModifierGroupRequest(cuissonId, "Cuisson de la viande", SelectionType.SINGLE, 1, null, 0, List.of(
                        new SaveModifierOptionRequest(saignantId, "Saignant", 0, true, 0),
                        new SaveModifierOptionRequest(null, "À point", 0, true, 1)))));

        assertThat(updated).hasSize(1); // "Suppléments" supprimé
        ModifierGroupResponse cuisson = updated.get(0);
        assertThat(cuisson.id()).isEqualTo(cuissonId); // identité conservée
        assertThat(cuisson.name()).isEqualTo("Cuisson de la viande");
        assertThat(cuisson.options()).hasSize(2); // "Bien cuit" supprimé, "À point" ajouté
        assertThat(cuisson.options().get(0).id()).isEqualTo(saignantId);

        assertThat(modifierGroupService.load(item.restaurantId(), item.itemId())).hasSize(1);
    }

    // ------------------------------------------------------------------ validations

    @Test
    void rejectsMinSelectGreaterThanMaxSelect() {
        ItemRef item = newItem();

        assertThatThrownBy(() -> modifierGroupService.replace(item.restaurantId(), item.itemId(), List.of(
                new SaveModifierGroupRequest(null, "Suppléments", SelectionType.MULTIPLE, 3, 1, 0, List.of()))))
                .isInstanceOf(InvalidMenuException.class);
    }

    @Test
    void singleSelectionAlwaysForcesMaxSelectToOne() {
        ItemRef item = newItem();

        List<ModifierGroupResponse> result = modifierGroupService.replace(item.restaurantId(), item.itemId(), List.of(
                new SaveModifierGroupRequest(null, "Cuisson", SelectionType.SINGLE, 1, 5, 0, List.of())));

        assertThat(result.get(0).maxSelect()).isEqualTo(1); // maxSelect demandé (5) ignoré pour SINGLE
    }
}
