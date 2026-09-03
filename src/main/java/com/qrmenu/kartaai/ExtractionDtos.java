package com.qrmenu.kartaai;

import com.qrmenu.menu.MenuDtos.SaveCategoryRequest;
import com.qrmenu.menu.MenuDtos.SaveItemRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Document produit par KartaAI, entre le PDF et le menu.
 *
 * C'est le contrat existant du menu ({@code MenuDtos.SaveCategoryRequest}) plus le strict
 * minimum de métadonnées nécessaires à la Review. Ces métadonnées s'arrêtent ici :
 * {@link #toSaveRequest} les laisse tomber, donc rien de propre à l'extraction ne peut
 * atteindre {@code menu_items}.
 *
 * KartaAI ne produit jamais de HTML — uniquement cette structure de données.
 */
public class ExtractionDtos {

    private ExtractionDtos() {
    }

    /**
     * Résultat d'une extraction.
     *
     * @param categories catégories extraites, dans l'ordre du PDF
     */
    public record ExtractedMenu(List<ExtractedCategory> categories) {

        public int itemCount() {
            return categories.stream().mapToInt(c -> c.items().size()).sum();
        }

        /** Convertit vers le contrat d'écriture du menu. Les métadonnées disparaissent ici. */
        public List<SaveCategoryRequest> toSaveRequest() {
            List<SaveCategoryRequest> result = new java.util.ArrayList<>();
            for (int i = 0; i < categories.size(); i++) {
                ExtractedCategory category = categories.get(i);
                List<SaveItemRequest> items = new java.util.ArrayList<>();
                for (int j = 0; j < category.items().size(); j++) {
                    ExtractedItem item = category.items().get(j);
                    items.add(new SaveItemRequest(
                            null,
                            item.name(),
                            item.description(),
                            item.price(),
                            item.currency(),
                            null,
                            j,
                            true));
                }
                result.add(new SaveCategoryRequest(null, category.name(), null, i, true, items));
            }
            return result;
        }
    }

    public record ExtractedCategory(String name, List<ExtractedItem> items) {
    }

    /**
     * Produit extrait.
     *
     * Trois états de fiabilité, sans système de score : un booléen et un prix nullable
     * suffisent à distinguer « fiable », « incertain » et « manquant ». Un score flottant
     * imposerait un seuil arbitraire à expliquer dans l'interface.
     *
     * @param price       <strong>centimes entiers</strong> (950 = 9,50 €), ou {@code null}
     *                    si le PDF ne permet pas de le lire. Jamais un flottant : Karta
     *                    n'exprime aucun montant en virgule flottante.
     * @param needsReview vrai si l'extraction est incertaine — la Review le signale
     * @param note        courte raison de l'incertitude (« deux prix », « prix barré »)
     */
    public record ExtractedItem(
            String name,
            String description,
            Integer price,
            String currency,
            boolean needsReview,
            String note
    ) {
    }

    // ---------------------------------------------------------------- API

    /**
     * Réponse de l'API de brouillon.
     *
     * Porte de quoi ouvrir l'écran de Review sans second appel : le contenu, sa provenance
     * et le décompte de ce qui reste à vérifier.
     */
    public record DraftResponse(
            UUID sourceAssetId,
            String sourceFilename,
            OffsetDateTime extractedAt,
            int categoryCount,
            int itemCount,
            /* Produits marqués incertains par l'extraction. */
            int needsReviewCount,
            /* Produits sans prix : la validation reste bloquée tant qu'ils existent. */
            int missingPriceCount,
            List<ExtractedCategory> categories
    ) {

        public static DraftResponse of(MenuDraft draft, ExtractedMenu menu) {
            int needsReview = 0;
            int missingPrice = 0;
            for (ExtractedCategory category : menu.categories()) {
                for (ExtractedItem item : category.items()) {
                    if (item.needsReview()) {
                        needsReview++;
                    }
                    if (item.price() == null) {
                        missingPrice++;
                    }
                }
            }
            return new DraftResponse(
                    draft.getSourceAssetId(),
                    draft.getSourceFilename(),
                    draft.getCreatedAt(),
                    menu.categories().size(),
                    menu.itemCount(),
                    needsReview,
                    missingPrice,
                    menu.categories());
        }
    }
}
