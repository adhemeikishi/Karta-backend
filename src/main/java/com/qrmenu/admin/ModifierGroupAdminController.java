package com.qrmenu.admin;

import com.qrmenu.kartapay.ModifierDtos.ModifierGroupResponse;
import com.qrmenu.kartapay.ModifierDtos.SaveModifierGroupsRequest;
import com.qrmenu.kartapay.ModifierGroupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Groupes d'options d'un produit Karta Pay.
 *
 * Document complet par produit, comme {@code MenuAdminController.saveStructure} :
 * {@code PUT} remplace tous les groupes (et leurs options) de ce produit en un appel.
 */
@RestController
@RequestMapping("/api/admin/restaurants/{restaurantId}/menu/items/{itemId}/modifier-groups")
public class ModifierGroupAdminController {

    private final ModifierGroupService modifierGroupService;

    public ModifierGroupAdminController(ModifierGroupService modifierGroupService) {
        this.modifierGroupService = modifierGroupService;
    }

    @GetMapping
    public List<ModifierGroupResponse> load(@PathVariable UUID restaurantId, @PathVariable UUID itemId) {
        return modifierGroupService.load(restaurantId, itemId);
    }

    @PutMapping
    public List<ModifierGroupResponse> replace(
            @PathVariable UUID restaurantId,
            @PathVariable UUID itemId,
            @Valid @RequestBody SaveModifierGroupsRequest request
    ) {
        return modifierGroupService.replace(restaurantId, itemId, request.groups());
    }
}
