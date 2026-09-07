package com.qrmenu.kartaai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrmenu.kartaai.ExtractionDtos.ExtractedCategory;
import com.qrmenu.kartaai.ExtractionDtos.ExtractedItem;
import com.qrmenu.kartaai.ExtractionDtos.ExtractedMenu;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test d'intégration <strong>réel</strong> : un vrai PDF de menu envoyé à Gemini Flash
 * (modèle par défaut {@code gemini-3.6-flash}), jusqu'à l'{@link ExtractedMenu} validé.
 *
 * <p><strong>Ne s'exécute que si {@code KARTA_AI_API_KEY} est présent dans l'environnement.</strong>
 * Sans clé, il est <em>SKIPPED</em> (jamais FAILED) — aucun test automatisé n'appelle Google
 * en CI. Aucune clé n'est écrite ici : elle vient uniquement de l'environnement local.
 *
 * <pre>
 *   KARTA_AI_API_KEY=... mvn -o test -Dtest=GeminiRealExtractionIT
 * </pre>
 *
 * Optionnels : {@code KARTA_AI_MODEL} (défaut {@code gemini-3.6-flash}),
 * {@code KARTA_AI_BASE_URL}.
 */
@EnabledIfEnvironmentVariable(named = "KARTA_AI_API_KEY", matches = ".+")
class GeminiRealExtractionIT {

    private static byte[] sampleMenuPdf() throws Exception {
        try (InputStream in = GeminiRealExtractionIT.class.getResourceAsStream("/sample-menu.pdf")) {
            assertThat(in).as("fixture src/test/resources/sample-menu.pdf").isNotNull();
            return in.readAllBytes();
        }
    }

    @Test
    void extractsARealMenuFromARealPdfThroughGemini() throws Exception {
        String apiKey = System.getenv("KARTA_AI_API_KEY");
        String model = System.getenv().getOrDefault("KARTA_AI_MODEL", "gemini-3.6-flash");
        String baseUrl = System.getenv().getOrDefault("KARTA_AI_BASE_URL", "");

        GeminiMenuExtractor extractor =
                new GeminiMenuExtractor(new ObjectMapper(), apiKey, model, baseUrl, 120, 16000);
        assertThat(extractor.isAvailable()).isTrue();

        ExtractedMenu raw = extractor.extract(sampleMenuPdf(), "sample-menu.pdf");
        ExtractedMenu validated = new ExtractionValidator().validate(raw);

        // On ne fige pas le libellé exact du modèle : on vérifie qu'il a lu une carte
        // plausible (plusieurs catégories, plusieurs plats avec des prix en centimes).
        assertThat(validated.categories().size()).isGreaterThanOrEqualTo(3);
        assertThat(validated.itemCount()).isGreaterThanOrEqualTo(8);

        boolean hasARealPrice = validated.categories().stream()
                .flatMap(c -> c.items().stream())
                .map(ExtractedItem::price)
                .anyMatch(p -> p != null && p >= 100 && p <= 100_000);
        assertThat(hasARealPrice).as("au moins un prix en centimes lu").isTrue();

        // La consigne demande des centimes entiers : aucun prix aberrant.
        validated.categories().stream()
                .flatMap(c -> c.items().stream())
                .forEach(item -> {
                    if (item.price() != null) {
                        assertThat(item.price()).isBetween(0, 10_000_000);
                    }
                });

        // Trace lisible en local (les tests réels ne tournent pas en CI).
        System.out.println("[GeminiRealExtractionIT] modèle=" + model
                + " catégories=" + validated.categories().size()
                + " plats=" + validated.itemCount());
        for (ExtractedCategory c : validated.categories()) {
            System.out.println("  - " + c.name() + " (" + c.items().size() + ")");
        }
    }
}
