package com.qrmenu.kartaai;

import com.jayway.jsonpath.JsonPath;
import com.qrmenu.kartaai.ExtractionDtos.ExtractedCategory;
import com.qrmenu.kartaai.ExtractionDtos.ExtractedMenu;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parcours KartaAI de bout en bout :
 * PDF → extraction → validation → brouillon → Review → {@code PUT /menu} → menu.
 *
 * Les invariants protégés ici sont ceux qui coûteraient cher en production : ne jamais
 * publier tout seul, ne jamais écraser une carte en ligne avant validation, ne jamais
 * créer un menu vide en silence, ne jamais perdre un brouillon sur un échec.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakeMenuExtractor.Config.class)
class KartaAiFlowTest {

    private static final byte[] PDF = "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private FakeMenuExtractor extractor;

    @BeforeEach
    void resetExtractor() {
        extractor.reset();
    }

    // ---------------------------------------------------------------- fixtures

    private String createClient(String offer) throws Exception {
        String body = mockMvc.perform(post("/api/admin/restaurants")
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Resto AI " + System.nanoTime() + "\",\"offer\":\"" + offer + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private void uploadPdf(String restaurantId) throws Exception {
        mockMvc.perform(multipart("/api/admin/restaurants/" + restaurantId + "/menu/pdf")
                        .file(new MockMultipartFile("file", "carte.pdf", "application/pdf", PDF))
                        .with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk());
    }

    /** Client PRO avec sa carte PDF déjà importée : le point de départ de KartaAI. */
    private String clientWithPdf(String offer) throws Exception {
        String id = createClient(offer);
        uploadPdf(id);
        return id;
    }

    private String aiUrl(String id) {
        return "/api/admin/restaurants/" + id + "/menu/ai";
    }

    private String menuUrl(String id) {
        return "/api/admin/restaurants/" + id + "/menu";
    }

    private String importDraft(String id) throws Exception {
        return mockMvc.perform(post(aiUrl(id) + "/import").with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** Ce que la Review renverrait après relecture humaine, sans correction. */
    private String reviewedPayload(String draftJson) {
        List<java.util.Map<String, Object>> categories = JsonPath.read(draftJson, "$.categories");
        StringBuilder sb = new StringBuilder("{\"categories\":[");
        for (int c = 0; c < categories.size(); c++) {
            if (c > 0) {
                sb.append(',');
            }
            sb.append("{\"name\":\"").append(categories.get(c).get("name")).append("\",\"items\":[");
            @SuppressWarnings("unchecked")
            List<java.util.Map<String, Object>> items =
                    (List<java.util.Map<String, Object>>) categories.get(c).get("items");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                Object price = items.get(i).get("price");
                sb.append("{\"name\":\"").append(items.get(i).get("name"))
                        .append("\",\"price\":").append(price == null ? 0 : price)
                        .append(",\"currency\":\"EUR\"}");
            }
            sb.append("]}");
        }
        return sb.append("]}").toString();
    }

    // ============================================================ cas 1 — PDF valide

    @Test
    void validPdfProducesADraftThenAMenuAfterReview() throws Exception {
        String id = clientWithPdf("PRO");

        String draft = importDraft(id);
        assertThat((int) JsonPath.read(draft, "$.categoryCount")).isEqualTo(2);
        assertThat((int) JsonPath.read(draft, "$.itemCount")).isEqualTo(3);
        assertThat((String) JsonPath.read(draft, "$.sourceFilename")).isEqualTo("carte.pdf");
        assertThat((String) JsonPath.read(draft, "$.categories[0].items[0].name"))
                .isEqualTo("Classic Burger");
        // Centimes entiers de bout en bout : jamais de flottant sur de la monnaie.
        assertThat((int) JsonPath.read(draft, "$.categories[0].items[0].price")).isEqualTo(950);

        // L'extraction seule n'a rien écrit dans le menu.
        mockMvc.perform(get(menuUrl(id)).with(httpBasic("admin", "test-password")))
                .andExpect(jsonPath("$.structure.categories.length()").value(0));

        // Review validée -> écriture par le PUT existant.
        mockMvc.perform(put(menuUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewedPayload(draft)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.structure.categories.length()").value(2))
                .andExpect(jsonPath("$.structure.categories[0].items[0].price").value(950));

        // Brouillon consommé : la Review est terminée.
        mockMvc.perform(get(aiUrl(id) + "/draft").with(httpBasic("admin", "test-password")))
                .andExpect(status().isNotFound());
    }

    // ============================================================ cas 2 — prix ambigu

    @Test
    void ambiguousPriceIsFlaggedForReviewAndCorrectable() throws Exception {
        String id = clientWithPdf("PRO");
        extractor.willReturn(new ExtractedMenu(List.of(
                new ExtractedCategory("Plats", List.of(
                        FakeMenuExtractor.item("Steak frites", null, 1800),
                        FakeMenuExtractor.ambiguous("Menu du jour", 1500, "Deux prix possibles"))))));

        String draft = importDraft(id);
        assertThat((int) JsonPath.read(draft, "$.needsReviewCount")).isEqualTo(1);
        assertThat((int) JsonPath.read(draft, "$.missingPriceCount")).isZero();
        assertThat((boolean) JsonPath.read(draft, "$.categories[0].items[1].needsReview")).isTrue();
        assertThat((String) JsonPath.read(draft, "$.categories[0].items[1].note"))
                .isEqualTo("Deux prix possibles");

        // L'utilisateur corrige le prix douteux, puis valide.
        mockMvc.perform(put(menuUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categories":[{"name":"Plats","items":[
                                  {"name":"Steak frites","price":1800,"currency":"EUR"},
                                  {"name":"Menu du jour","price":1650,"currency":"EUR"}]}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.structure.categories[0].items[1].price").value(1650));
    }

    // ============================================================ cas 3 — prix absent

    @Test
    void missingPriceIsNeverInventedAndIsReportedToTheReview() throws Exception {
        String id = clientWithPdf("PRO");
        extractor.willReturn(new ExtractedMenu(List.of(
                new ExtractedCategory("Entrées", List.of(
                        FakeMenuExtractor.withoutPrice("Soupe du jour"),
                        FakeMenuExtractor.item("Salade César", null, 1200))))));

        String draft = importDraft(id);
        assertThat((int) JsonPath.read(draft, "$.missingPriceCount")).isEqualTo(1);
        // Un prix absent reste absent : jamais remplacé par 0, qui serait publié en silence.
        assertThat((Object) JsonPath.read(draft, "$.categories[0].items[0].price")).isNull();
        assertThat((boolean) JsonPath.read(draft, "$.categories[0].items[0].needsReview")).isTrue();

        mockMvc.perform(put(menuUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categories":[{"name":"Entrées","items":[
                                  {"name":"Soupe du jour","price":700,"currency":"EUR"},
                                  {"name":"Salade César","price":1200,"currency":"EUR"}]}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.structure.categories[0].items[0].price").value(700));
    }

    // ============================================================ cas 4 — inexploitable

    @Test
    void unreadablePdfFailsLoudlyAndCreatesNothing() throws Exception {
        String id = clientWithPdf("PRO");
        extractor.willReturn(new ExtractedMenu(List.of()));

        mockMvc.perform(post(aiUrl(id) + "/import").with(httpBasic("admin", "test-password")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Aucun plat n'a pu être lu")));

        mockMvc.perform(get(aiUrl(id) + "/draft").with(httpBasic("admin", "test-password")))
                .andExpect(status().isNotFound());
        // Surtout : aucun menu vide créé en silence.
        mockMvc.perform(get(menuUrl(id)).with(httpBasic("admin", "test-password")))
                .andExpect(jsonPath("$.structure.categories.length()").value(0))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void categoriesWithoutAnyUsableItemAreDropped() throws Exception {
        String id = clientWithPdf("PRO");
        extractor.willReturn(new ExtractedMenu(List.of(
                new ExtractedCategory("Vide", List.of()),
                new ExtractedCategory("Plats", List.of(FakeMenuExtractor.item("Poulet", null, 1400))))));

        String draft = importDraft(id);
        assertThat((int) JsonPath.read(draft, "$.categoryCount")).isEqualTo(1);
        assertThat((String) JsonPath.read(draft, "$.categories[0].name")).isEqualTo("Plats");
    }

    // ============================================================ cas 5 — ownership

    @Test
    void aDraftIsNeverVisibleFromAnotherClient() throws Exception {
        String owner = clientWithPdf("PRO");
        String other = clientWithPdf("PRO");
        importDraft(owner);

        // Le brouillon du premier client n'existe pas pour le second.
        mockMvc.perform(get(aiUrl(other) + "/draft").with(httpBasic("admin", "test-password")))
                .andExpect(status().isNotFound());

        // Et supprimer chez l'un ne touche pas l'autre.
        mockMvc.perform(delete(aiUrl(other) + "/draft").with(httpBasic("admin", "test-password")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(aiUrl(owner) + "/draft").with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk());
    }

    @Test
    void extractionOnlyEverReadsTheClientsOwnPdf() throws Exception {
        String withoutPdf = createClient("PRO");

        // Aucun PDF pour ce client : refus explicite, et surtout aucun moyen de désigner
        // le document d'un autre restaurant — l'asset est relu depuis le menu du client.
        mockMvc.perform(post(aiUrl(withoutPdf) + "/import").with(httpBasic("admin", "test-password")))
                .andExpect(status().isConflict());
        assertThat(extractor.calls()).isZero();
    }

    @Test
    void kartaAiRequiresAuthentication() throws Exception {
        String id = clientWithPdf("PRO");

        mockMvc.perform(post(aiUrl(id) + "/import")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(aiUrl(id) + "/draft")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(aiUrl(id) + "/draft")).andExpect(status().isUnauthorized());
    }

    // ============================================================ cas 6 — BASIC

    @Test
    void basicClientCannotUseKartaAi() throws Exception {
        String id = clientWithPdf("BASIC");

        mockMvc.perform(post(aiUrl(id) + "/import").with(httpBasic("admin", "test-password")))
                .andExpect(status().isConflict());
        // Pas même un appel au service d'analyse : le refus est en amont.
        assertThat(extractor.calls()).isZero();

        // Et le PDF de l'offre BASIC continue de fonctionner normalement.
        mockMvc.perform(get(menuUrl(id)).with(httpBasic("admin", "test-password")))
                .andExpect(jsonPath("$.type").value("PDF"))
                .andExpect(jsonPath("$.pdf.originalFilename").value("carte.pdf"));
    }

    // ============================================================ cas 7 — aucune publication

    @Test
    void extractionNeverPublishesAnything() throws Exception {
        String id = clientWithPdf("PRO");
        importDraft(id);

        mockMvc.perform(get(menuUrl(id)).with(httpBasic("admin", "test-password")))
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.publishedAt").doesNotExist());
    }

    @Test
    void validatingTheReviewDoesNotPublishEither() throws Exception {
        String id = clientWithPdf("PRO");
        String draft = importDraft(id);

        mockMvc.perform(put(menuUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewedPayload(draft)))
                .andExpect(status().isOk())
                // READY : prêt, mais pas diffusé. Publier reste une action distincte.
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.published").value(false));
    }

    // ============================================================ brouillon et menu publié

    @Test
    void importingDoesNotTouchAnAlreadyPublishedMenu() throws Exception {
        String id = clientWithPdf("PRO");
        mockMvc.perform(put(menuUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categories":[{"name":"Carte en ligne","items":[
                                  {"name":"Plat servi","price":2000,"currency":"EUR"}]}]}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put(menuUrl(id) + "/publish").with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk());

        importDraft(id);

        // C'est toute la raison d'être de la table séparée : la carte que lisent les
        // clients n'a pas bougé d'un octet.
        mockMvc.perform(get(menuUrl(id)).with(httpBasic("admin", "test-password")))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.structure.categories.length()").value(1))
                .andExpect(jsonPath("$.structure.categories[0].name").value("Carte en ligne"));
    }

    @Test
    void aFailedSaveKeepsTheDraft() throws Exception {
        String id = clientWithPdf("PRO");
        importDraft(id);

        // Payload refusé par la validation du menu (nom vide).
        mockMvc.perform(put(menuUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categories\":[{\"name\":\"\",\"items\":[]}]}"))
                .andExpect(status().isBadRequest());

        // Le menu n'a pas bougé et le brouillon est toujours là : la Review est reprenable.
        mockMvc.perform(get(aiUrl(id) + "/draft").with(httpBasic("admin", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(3));
    }

    // ============================================================ édition manuelle après KartaAI

    /**
     * Une fois la Review validée, le menu est un menu structuré comme un autre : le
     * restaurateur doit pouvoir le corriger à la main (comme le ferait le studio d'édition)
     * sans jamais redéclencher KartaAI. Vérifié ici en comptant les appels à l'extracteur :
     * une modification manuelle après validation ne doit pas en ajouter un seul.
     */
    @Test
    void manualEditAfterKartaAiValidationDoesNotCallKartaAiAgain() throws Exception {
        String id = clientWithPdf("PRO");
        String draft = importDraft(id);
        assertThat(extractor.calls()).isEqualTo(1);

        mockMvc.perform(put(menuUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewedPayload(draft)))
                .andExpect(status().isOk());
        assertThat(extractor.calls()).isEqualTo(1); // la validation elle-même n'appelle pas KartaAI

        // Édition manuelle : même route PUT /menu que l'éditeur du studio enverrait,
        // avec les id du menu tel qu'il vient d'être créé.
        String currentMenu = mockMvc.perform(get(menuUrl(id)).with(httpBasic("admin", "test-password")))
                .andReturn().getResponse().getContentAsString();
        UUID categoryId = UUID.fromString(JsonPath.read(currentMenu, "$.structure.categories[0].id"));
        UUID itemId = UUID.fromString(JsonPath.read(currentMenu, "$.structure.categories[0].items[0].id"));

        mockMvc.perform(put(menuUrl(id))
                        .with(httpBasic("admin", "test-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categories":[{"id":"%s","name":"Renommée à la main","items":[
                                  {"id":"%s","name":"Prix corrigé à la main","price":1111,"currency":"EUR"}]}]}
                                """.formatted(categoryId, itemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.structure.categories[0].name").value("Renommée à la main"))
                .andExpect(jsonPath("$.structure.categories[0].items[0].price").value(1111));

        // Toujours un seul appel à KartaAI dans tout le scénario : celui de l'import initial.
        assertThat(extractor.calls()).isEqualTo(1);
    }

    @Test
    void aNewImportReplacesThePendingDraftInsteadOfStacking() throws Exception {
        String id = clientWithPdf("PRO");
        importDraft(id);

        extractor.willReturn(new ExtractedMenu(List.of(
                new ExtractedCategory("Nouvelle carte", List.of(
                        FakeMenuExtractor.item("Plat unique", null, 1000))))));
        importDraft(id);

        mockMvc.perform(get(aiUrl(id) + "/draft").with(httpBasic("admin", "test-password")))
                .andExpect(jsonPath("$.categoryCount").value(1))
                .andExpect(jsonPath("$.categories[0].name").value("Nouvelle carte"));
    }

    @Test
    void serviceFailureIsReportedWithoutLeakingProviderDetails() throws Exception {
        String id = clientWithPdf("PRO");
        extractor.willFail(new ExtractionException(
                "Le service d'analyse est momentanément indisponible. Réessayez dans un instant."));

        mockMvc.perform(post(aiUrl(id) + "/import").with(httpBasic("admin", "test-password")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(
                        "Le service d'analyse est momentanément indisponible. Réessayez dans un instant."));
    }
}
