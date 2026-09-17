package com.qrmenu.kartapay;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Contrat JSON des groupes d'options (admin), rattachés à un produit du menu.
 *
 * Comme {@code MenuDtos.SaveMenuRequest}, l'écriture se fait par <strong>document
 * complet par produit</strong> : {@code PUT .../modifier-groups} remplace tous les
 * groupes de ce produit en un appel.
 */
public class ModifierDtos {

    private ModifierDtos() {
    }

    // ---------------------------------------------------------------- réponses

    public record ModifierOptionResponse(
            UUID id,
            String name,
            int priceDeltaCents,
            boolean available,
            int sortOrder
    ) {
    }

    public record ModifierGroupResponse(
            UUID id,
            String name,
            SelectionType selectionType,
            int minSelect,
            Integer maxSelect,
            int sortOrder,
            List<ModifierOptionResponse> options
    ) {
    }

    // ---------------------------------------------------------------- requêtes

    public record SaveModifierGroupsRequest(
            @NotNull(message = "groups est obligatoire")
            @Valid
            List<SaveModifierGroupRequest> groups
    ) {
    }

    public record SaveModifierGroupRequest(
            UUID id,
            @NotBlank(message = "le nom du groupe est obligatoire")
            @Size(max = 120, message = "le nom du groupe est trop long")
            String name,
            @NotNull(message = "selectionType est obligatoire")
            SelectionType selectionType,
            @Min(value = 0, message = "minSelect doit être >= 0")
            Integer minSelect,
            Integer maxSelect,
            @Min(value = 0, message = "sortOrder doit être >= 0")
            Integer sortOrder,
            @Valid
            List<SaveModifierOptionRequest> options
    ) {
    }

    public record SaveModifierOptionRequest(
            UUID id,
            @NotBlank(message = "le nom de l'option est obligatoire")
            @Size(max = 120, message = "le nom de l'option est trop long")
            String name,
            Integer priceDeltaCents,
            Boolean available,
            @Min(value = 0, message = "sortOrder doit être >= 0")
            Integer sortOrder
    ) {
    }
}
