package com.qrmenu.kartaai;

import com.qrmenu.kartaai.ExtractionDtos.ExtractedCategory;
import com.qrmenu.kartaai.ExtractionDtos.ExtractedItem;
import com.qrmenu.kartaai.ExtractionDtos.ExtractedMenu;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le document produit par le modèle est une entrée <strong>non fiable</strong>.
 *
 * Ces tests décrivent ce qui arrive quand il est structurellement valide mais
 * sémantiquement absurde — le cas réaliste, bien plus fréquent qu'un JSON malformé.
 */
class ExtractionValidatorTest {

    private final ExtractionValidator validator = new ExtractionValidator();

    private static ExtractedMenu menuOf(ExtractedItem... items) {
        return new ExtractedMenu(List.of(new ExtractedCategory("Plats", List.of(items))));
    }

    private static ExtractedItem item(String name, Integer price) {
        return new ExtractedItem(name, null, price, "EUR", false, null);
    }

    @Test
    void keepsAWellFormedMenuUnchanged() {
        ExtractedMenu result = validator.validate(menuOf(item("Burger", 950)));

        assertThat(result.categories()).hasSize(1);
        assertThat(result.categories().get(0).items().get(0).price()).isEqualTo(950);
        assertThat(result.categories().get(0).items().get(0).needsReview()).isFalse();
    }

    @Test
    void rejectsAnEmptyExtractionInsteadOfCreatingAnEmptyMenu() {
        assertThatThrownBy(() -> validator.validate(new ExtractedMenu(List.of())))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("Aucun plat n'a pu être lu");

        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(ExtractionException.class);
    }

    @Test
    void anAberrantPriceBecomesMissingRatherThanZero() {
        // Afficher 0,00 € à la place d'un prix illisible ferait publier une erreur
        // que personne ne remarquerait. Absent, il bloque la validation.
        ExtractedMenu result = validator.validate(menuOf(
                item("Prix négatif", -500),
                item("Prix aberrant", 99_999_999)));

        assertThat(result.categories().get(0).items())
                .allSatisfy(i -> {
                    assertThat(i.price()).isNull();
                    assertThat(i.needsReview()).isTrue();
                });
    }

    @Test
    void truncatesOverlongTextToTheColumnLimits() {
        String huge = "x".repeat(5000);
        ExtractedMenu result = validator.validate(new ExtractedMenu(List.of(
                new ExtractedCategory(huge, List.of(
                        new ExtractedItem(huge, huge, 100, "EUR", false, huge))))));

        ExtractedCategory category = result.categories().get(0);
        assertThat(category.name()).hasSize(ExtractionValidator.MAX_CATEGORY_NAME);
        assertThat(category.items().get(0).name()).hasSize(ExtractionValidator.MAX_ITEM_NAME);
        assertThat(category.items().get(0).description()).hasSize(ExtractionValidator.MAX_DESCRIPTION);
        assertThat(category.items().get(0).note()).hasSize(ExtractionValidator.MAX_NOTE);
    }

    @Test
    void dropsEntriesWithoutAName() {
        // Sans nom, un plat n'est même pas corrigeable en Review.
        ExtractedMenu result = validator.validate(menuOf(
                item(null, 100), item("  ", 200), item("Valide", 300)));

        assertThat(result.categories().get(0).items()).hasSize(1);
        assertThat(result.categories().get(0).items().get(0).name()).isEqualTo("Valide");
    }

    @Test
    void fallsBackToEurosOnAnUnusableCurrency() {
        ExtractedMenu result = validator.validate(menuOf(
                new ExtractedItem("Inventée", null, 100, "ZZZ", false, null),
                new ExtractedItem("Pseudo-devise", null, 100, "XAU", false, null),
                new ExtractedItem("Minuscules", null, 100, "usd", false, null)));

        List<ExtractedItem> items = result.categories().get(0).items();
        assertThat(items.get(0).currency()).isEqualTo("EUR");
        assertThat(items.get(1).currency()).isEqualTo("EUR"); // pas de décimales définies
        assertThat(items.get(2).currency()).isEqualTo("USD");
    }

    @Test
    void capsAberrantVolumes() {
        List<ExtractedCategory> categories = new ArrayList<>();
        for (int c = 0; c < 100; c++) {
            List<ExtractedItem> items = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                items.add(item("Plat " + c + "-" + i, 100));
            }
            categories.add(new ExtractedCategory("Categorie " + c, items));
        }

        ExtractedMenu result = validator.validate(new ExtractedMenu(categories));

        assertThat(result.categories().size()).isLessThanOrEqualTo(ExtractionValidator.MAX_CATEGORIES);
        assertThat(result.itemCount()).isLessThanOrEqualTo(ExtractionValidator.MAX_ITEMS_TOTAL);
    }

    @Test
    void survivesNullsAnywhereInTheDocument() {
        List<ExtractedItem> items = new ArrayList<>();
        items.add(null);
        items.add(item("Rescapé", 100));
        List<ExtractedCategory> categories = new ArrayList<>();
        categories.add(null);
        categories.add(new ExtractedCategory("Plats", items));
        categories.add(new ExtractedCategory("Sans items", null));

        ExtractedMenu result = validator.validate(new ExtractedMenu(categories));

        assertThat(result.categories()).hasSize(1);
        assertThat(result.categories().get(0).items()).hasSize(1);
    }

    @Test
    void isolatesTheJsonObjectFromSurroundingProse() {
        // Le modèle est prié de ne rendre que du JSON ; on ne fait pas dépendre le
        // parcours d'une politesse de formatage.
        assertThat(AnthropicMenuExtractor.extractJsonObject("Voici :\n```json\n{\"a\":{\"b\":1}}\n```"))
                .isEqualTo("{\"a\":{\"b\":1}}");
        assertThat(AnthropicMenuExtractor.extractJsonObject("aucun json ici")).isNull();
        assertThat(AnthropicMenuExtractor.extractJsonObject(null)).isNull();
    }
}
