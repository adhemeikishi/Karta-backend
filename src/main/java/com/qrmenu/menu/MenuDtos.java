package com.qrmenu.menu;

import com.qrmenu.restaurant.RestaurantOffer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Contrat JSON du menu Karta.
 *
 * {@link MenuResponse} est la réponse canonique de {@code GET /api/admin/restaurants/{id}/menu}.
 * C'est ce document que consommeront ensuite KartaAI, la Review, le renderer et l'aperçu :
 * toute évolution doit y rester rétro-compatible.
 *
 * Note sur les prix : le champ JSON s'appelle {@code price} et vaut toujours des
 * <strong>centimes entiers</strong> (1290 = 12,90 €). La colonne correspondante est
 * {@code price_cents} pour lever l'ambiguïté côté base.
 */
public class MenuDtos {

    private MenuDtos() {
    }

    // ---------------------------------------------------------------- réponses

    public record MenuResponse(
            RestaurantOffer offer,
            MenuType type,
            MenuStatus status,
            int version,
            /* Dérivé de status. Conservé pour ne pas casser les clients existants. */
            boolean published,
            OffsetDateTime publishedAt,
            /* Renseigné uniquement pour un menu PDF (offre BASIC). */
            PdfInfo pdf,
            /* Renseigné uniquement pour un menu STRUCTURED (offres PRO / PREMIUM). */
            MenuStructure structure
    ) {
    }

    public record PdfInfo(
            UUID assetId,
            String url,
            String originalFilename,
            long sizeBytes,
            OffsetDateTime uploadedAt
    ) {
    }

    public record MenuStructure(
            String restaurantName,
            String currency,
            List<CategoryResponse> categories
    ) {
    }

    public record CategoryResponse(
            UUID id,
            String name,
            String description,
            int sortOrder,
            boolean visible,
            List<ItemResponse> items
    ) {
    }

    public record ItemResponse(
            UUID id,
            String name,
            String description,
            /* Centimes entiers. */
            int price,
            String currency,
            UUID imageAssetId,
            /* URL publique du média, null si aucune image. */
            String imageUrl,
            int sortOrder,
            boolean available
    ) {
    }

    // ---------------------------------------------------------------- requêtes

    /**
     * Corps de {@code PUT .../menu} : remplace l'intégralité de la structure.
     *
     * Sémantique « document complet » : ce qui n'est pas envoyé est supprimé.
     * Les {@code id} fournis sont conservés (mise à jour en place), ce qui permet à la
     * Review de réordonner, renommer ou déplacer un plat sans lui faire perdre son identité.
     */
    public record SaveMenuRequest(
            @NotNull(message = "categories est obligatoire")
            @Valid
            List<SaveCategoryRequest> categories
    ) {
    }

    public record SaveCategoryRequest(
            UUID id,
            @NotBlank(message = "le nom de la catégorie est obligatoire")
            @Size(max = 120, message = "le nom de la catégorie est trop long")
            String name,
            @Size(max = 2000, message = "la description de la catégorie est trop longue")
            String description,
            @Min(value = 0, message = "sortOrder doit être >= 0")
            Integer sortOrder,
            Boolean visible,
            @Valid
            List<SaveItemRequest> items
    ) {
    }

    public record SaveItemRequest(
            UUID id,
            @NotBlank(message = "le nom du produit est obligatoire")
            @Size(max = 160, message = "le nom du produit est trop long")
            String name,
            @Size(max = 2000, message = "la description du produit est trop longue")
            String description,
            @NotNull(message = "le prix est obligatoire")
            @Min(value = 0, message = "le prix doit être >= 0")
            Integer price,
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "la devise doit être un code ISO-4217")
            String currency,
            UUID imageAssetId,
            @Min(value = 0, message = "sortOrder doit être >= 0")
            Integer sortOrder,
            Boolean available
    ) {
    }
}
