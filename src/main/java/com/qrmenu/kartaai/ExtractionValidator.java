package com.qrmenu.kartaai;

import com.qrmenu.kartaai.ExtractionDtos.ExtractedCategory;
import com.qrmenu.kartaai.ExtractionDtos.ExtractedItem;
import com.qrmenu.kartaai.ExtractionDtos.ExtractedMenu;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

/**
 * Assainit le document produit par KartaAI.
 *
 * <strong>Le JSON du modèle est une entrée non fiable</strong>, au même titre qu'un corps
 * de requête HTTP : il peut être structurellement valide et sémantiquement absurde
 * (prix négatif, nom de 40 000 caractères, devise inventée, 900 catégories). Rien de ce
 * qui sort d'ici ne dépasse les limites des colonnes ni les invariants du menu.
 *
 * Politique : <em>écarter</em> plutôt qu'échouer. Un plat aberrant est retiré et le reste
 * du menu part en Review — c'est ce que le restaurateur attend d'un outil d'aide à la
 * saisie. Une seule condition provoque un échec : ne rien avoir d'exploitable du tout,
 * car créer un menu vide en silence est pire que refuser.
 *
 * Ce n'est pas la dernière barrière : la validation définitive reste celle de
 * {@code MenuStructureService}, au moment du {@code PUT} qui écrit réellement le menu.
 */
@Component
public class ExtractionValidator {

    /** Alignées sur les colonnes : menu_categories.name(120), menu_items.name(160). */
    static final int MAX_CATEGORY_NAME = 120;
    static final int MAX_ITEM_NAME = 160;
    static final int MAX_DESCRIPTION = 2000;
    static final int MAX_NOTE = 200;

    /** 100 000 € en centimes. Au-delà, c'est une erreur de lecture, pas un prix. */
    static final int MAX_PRICE_CENTS = 10_000_000;

    /** Garde-fous de volume : une carte de restaurant, pas un catalogue. */
    static final int MAX_CATEGORIES = 40;
    static final int MAX_ITEMS_PER_CATEGORY = 100;
    static final int MAX_ITEMS_TOTAL = 400;

    private static final String DEFAULT_CURRENCY = "EUR";

    /**
     * @throws ExtractionException si rien d'exploitable ne subsiste — jamais de menu vide
     *                             créé en silence
     */
    public ExtractedMenu validate(ExtractedMenu raw) {
        if (raw == null || raw.categories() == null || raw.categories().isEmpty()) {
            throw new ExtractionException(unreadableMessage());
        }

        List<ExtractedCategory> categories = new ArrayList<>();
        int totalItems = 0;

        for (ExtractedCategory rawCategory : raw.categories()) {
            if (rawCategory == null || categories.size() >= MAX_CATEGORIES) {
                continue;
            }
            String name = trimTo(rawCategory.name(), MAX_CATEGORY_NAME);
            if (name == null) {
                continue; // une catégorie sans nom n'est pas rattachable
            }

            List<ExtractedItem> items = new ArrayList<>();
            for (ExtractedItem rawItem : nullSafe(rawCategory.items())) {
                if (rawItem == null
                        || items.size() >= MAX_ITEMS_PER_CATEGORY
                        || totalItems >= MAX_ITEMS_TOTAL) {
                    continue;
                }
                ExtractedItem item = sanitizeItem(rawItem);
                if (item != null) {
                    items.add(item);
                    totalItems++;
                }
            }

            // Une catégorie sans aucun produit exploitable n'apporte rien à la Review.
            if (!items.isEmpty()) {
                categories.add(new ExtractedCategory(name, items));
            }
        }

        if (categories.isEmpty()) {
            throw new ExtractionException(unreadableMessage());
        }
        return new ExtractedMenu(categories);
    }

    // ---------------------------------------------------------------- internes

    private ExtractedItem sanitizeItem(ExtractedItem raw) {
        String name = trimTo(raw.name(), MAX_ITEM_NAME);
        if (name == null) {
            return null; // un produit sans nom n'est pas corrigeable en Review
        }

        Integer price = sanitizePrice(raw.price());
        // Un prix rejeté devient « manquant » et non « zéro » : afficher 0,00 € à la
        // place d'un prix illisible ferait publier une erreur sans que personne ne la voie.
        boolean priceRejected = raw.price() != null && price == null;

        return new ExtractedItem(
                name,
                trimTo(raw.description(), MAX_DESCRIPTION),
                price,
                sanitizeCurrency(raw.currency()),
                raw.needsReview() || priceRejected || price == null,
                trimTo(raw.note(), MAX_NOTE));
    }

    private static Integer sanitizePrice(Integer price) {
        if (price == null || price < 0 || price > MAX_PRICE_CENTS) {
            return null;
        }
        return price;
    }

    /**
     * Devise réellement utilisable pour un prix en centimes. Les pseudo-devises ISO-4217
     * (XXX, XAU…) n'ont pas de nombre de décimales défini — même règle que
     * {@code MenuStructureService}.
     */
    private static String sanitizeCurrency(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_CURRENCY;
        }
        String code = raw.trim().toUpperCase(Locale.ROOT);
        try {
            if (Currency.getInstance(code).getDefaultFractionDigits() < 0) {
                return DEFAULT_CURRENCY;
            }
            return code;
        } catch (IllegalArgumentException e) {
            return DEFAULT_CURRENCY;
        }
    }

    private static String trimTo(String raw, int max) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static String unreadableMessage() {
        return "Aucun plat n'a pu être lu dans ce PDF. "
                + "Vérifiez qu'il s'agit bien d'une carte, puis réessayez.";
    }
}
