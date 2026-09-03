package com.qrmenu.kartaai;

import com.qrmenu.kartaai.ExtractionDtos.ExtractedCategory;
import com.qrmenu.kartaai.ExtractionDtos.ExtractedItem;
import com.qrmenu.kartaai.ExtractionDtos.ExtractedMenu;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

/**
 * Extracteur déterministe pour les tests.
 *
 * Aucun test n'appelle l'API réelle : ce serait lent, non reproductible et facturé. C'est
 * la seule raison d'être de l'interface {@link MenuExtractor} — pas une abstraction
 * spéculative.
 */
public class FakeMenuExtractor implements MenuExtractor {

    private ExtractedMenu next = sampleMenu();
    private RuntimeException failure;
    private boolean available = true;
    private int calls;

    public void willReturn(ExtractedMenu menu) {
        this.next = menu;
        this.failure = null;
    }

    public void willFail(RuntimeException e) {
        this.failure = e;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public int calls() {
        return calls;
    }

    public void reset() {
        next = sampleMenu();
        failure = null;
        available = true;
        calls = 0;
    }

    @Override
    public ExtractedMenu extract(byte[] pdf, String filename) {
        calls++;
        if (failure != null) {
            throw failure;
        }
        return next;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    // ---------------------------------------------------------------- fixtures

    /** Carte lisible : deux catégories, tous les prix trouvés. */
    public static ExtractedMenu sampleMenu() {
        return new ExtractedMenu(List.of(
                new ExtractedCategory("Burgers", List.of(
                        item("Classic Burger", "Steak haché, salade, tomate", 950),
                        item("Chicken Burger", null, 890))),
                new ExtractedCategory("Desserts", List.of(
                        item("Tiramisu", null, 600)))));
    }

    public static ExtractedItem item(String name, String description, Integer priceCents) {
        return new ExtractedItem(name, description, priceCents, "EUR", false, null);
    }

    /** Prix ambigu : lu, mais signalé comme douteux. */
    public static ExtractedItem ambiguous(String name, Integer priceCents, String note) {
        return new ExtractedItem(name, null, priceCents, "EUR", true, note);
    }

    /** Prix absent : jamais inventé. */
    public static ExtractedItem withoutPrice(String name) {
        return new ExtractedItem(name, null, null, "EUR", true, "Prix introuvable");
    }

    @TestConfiguration
    public static class Config {

        /** {@code @Primary} : remplace l'implémentation Anthropic dans le contexte de test. */
        @Bean
        @Primary
        public FakeMenuExtractor fakeMenuExtractor() {
            return new FakeMenuExtractor();
        }
    }
}
