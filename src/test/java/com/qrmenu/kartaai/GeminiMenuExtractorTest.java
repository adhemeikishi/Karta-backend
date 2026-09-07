package com.qrmenu.kartaai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrmenu.kartaai.ExtractionDtos.ExtractedMenu;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vérifie le <strong>contrat HTTP</strong> de l'appel à l'API generateContent de Gemini :
 * forme de la requête sortante, interprétation de la réponse, comportement en cas d'échec.
 *
 * Ce test n'appelle <strong>aucune</strong> API réelle de Google. Il prouve que, si une
 * clé valide est fournie et que Gemini répond, le client sait construire la requête et
 * lire la réponse — pas que KartaAI « fonctionne » de bout en bout. Le parcours métier
 * complet (PDF → brouillon → Review → menu) reste couvert par {@link KartaAiFlowTest}
 * avec un extracteur déterministe.
 *
 * Serveur bouchon : {@code com.sun.net.httpserver.HttpServer} du JDK — aucune dépendance
 * ajoutée, aucun changement de code de production.
 */
class GeminiMenuExtractorTest {

    private static final byte[] PDF = "%PDF-1.4\nmenu\n%%EOF".getBytes(StandardCharsets.UTF_8);

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<RecordedRequest> lastRequest = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "";
    private volatile long responseDelayMs = 0;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body;
            try (InputStream in = exchange.getRequestBody()) {
                body = in.readAllBytes();
            }
            lastRequest.set(new RecordedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders().getFirst("x-goog-api-key"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(body, StandardCharsets.UTF_8)));
            if (responseDelayMs > 0) {
                try {
                    Thread.sleep(responseDelayMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(responseStatus, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private GeminiMenuExtractor extractor(String apiKey) {
        return extractor(apiKey, "gemini-3.6-flash", 10);
    }

    private GeminiMenuExtractor extractor(String apiKey, String model, int timeoutSeconds) {
        return new GeminiMenuExtractor(
                new ObjectMapper(), apiKey, model, baseUrl, timeoutSeconds, 16000);
    }

    /** Enveloppe generateContent minimale, avec le texte donné dans la 1re part du 1er candidat. */
    private static String candidateEnvelope(String finishReason, String textPart) {
        ObjectMapper m = new ObjectMapper();
        return """
                {"candidates":[{"content":{"role":"model","parts":[{"text":%s}]},"finishReason":"%s"}],
                 "usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":20,"totalTokenCount":30},
                 "modelVersion":"gemini-3.6-flash"}
                """.formatted(m.valueToTree(textPart).toString(), finishReason);
    }

    // ==================================================================== Configuration

    @Test
    void isUnavailableWithoutKey() {
        assertThat(extractor("").isAvailable()).isFalse();
        assertThat(extractor("   ").isAvailable()).isFalse();
    }

    @Test
    void isAvailableWithKey() {
        assertThat(extractor("AIza-xxx").isAvailable()).isTrue();
    }

    @Test
    void defaultModelIsGemini36Flash() throws Exception {
        responseBody = candidateEnvelope("STOP", "{\"categories\":[]}");
        // model null/blank -> défaut ; le modèle apparaît dans l'URL
        try {
            new GeminiMenuExtractor(new ObjectMapper(), "AIza-xxx", "  ", baseUrl, 10, 16000)
                    .extract(PDF, "carte.pdf");
        } catch (ExtractionException ignored) {
            // {"categories":[]} -> ExtractionException plus loin ; on ne teste ici que l'URL.
        }
        assertThat(lastRequest.get().uri()).contains("/v1beta/models/gemini-3.6-flash:generateContent");
    }

    @Test
    void extractRefusesWithoutKeyWithoutCallingTheApi() {
        assertThatThrownBy(() -> extractor("").extract(PDF, "carte.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("n'est pas configuré");
        assertThat(lastRequest.get()).isNull();
    }

    // ==================================================================== Requête HTTP

    @Test
    void sendsAWellFormedGenerateContentRequest() throws Exception {
        responseBody = candidateEnvelope("STOP", "{\"categories\":[]}");

        try {
            extractor("AIza-secret", "gemini-3.6-flash", 10).extract(PDF, "carte.pdf");
        } catch (ExtractionException ignored) {
            // sans importance ici : on inspecte la requête envoyée.
        }

        RecordedRequest req = lastRequest.get();
        assertThat(req).isNotNull();
        assertThat(req.method()).isEqualTo("POST");
        assertThat(req.uri()).isEqualTo("/v1beta/models/gemini-3.6-flash:generateContent");
        // clé dans l'en-tête, jamais dans l'URL
        assertThat(req.apiKey()).isEqualTo("AIza-secret");
        assertThat(req.uri()).doesNotContain("AIza-secret");
        assertThat(req.contentType()).contains("application/json");

        JsonNode body = new ObjectMapper().readTree(req.body());
        JsonNode parts = body.path("contents").path(0).path("parts");
        assertThat(body.path("contents").path(0).path("role").asText()).isEqualTo("user");

        // partie document en premier, puis la consigne
        JsonNode inline = parts.get(0).path("inline_data");
        assertThat(inline.path("mime_type").asText()).isEqualTo("application/pdf");
        String data = inline.path("data").asText();
        assertThat(data).doesNotContain("\n");
        assertThat(Base64.getDecoder().decode(data)).isEqualTo(PDF);

        String prompt = parts.get(1).path("text").asText();
        assertThat(prompt).isEqualTo(ExtractionSupport.PROMPT);

        JsonNode gen = body.path("generationConfig");
        assertThat(gen.path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(gen.path("maxOutputTokens").asInt()).isEqualTo(16000);
        assertThat(gen.path("temperature").asInt()).isEqualTo(0);
        // modèle de raisonnement : réflexion minimale pour l'extraction structurée
        // (Gemini 3 : thinkingLevel ; thinkingBudget appartenait aux modèles 2.x → INVALID_ARGUMENT)
        assertThat(gen.path("thinkingConfig").path("thinkingLevel").asText()).isEqualTo("LOW");
    }

    // ==================================================================== Réponse

    @Test
    void parsesAValidMenuFromTheCandidateText() {
        responseBody = candidateEnvelope("STOP", """
                {"categories":[
                  {"name":"Entrées","items":[
                    {"name":"Salade","description":"chèvre chaud","price":950,"currency":"EUR","needsReview":false,"note":null}]},
                  {"name":"Plats","items":[
                    {"name":"Steak","description":null,"price":1990,"currency":"EUR","needsReview":false,"note":null}]}
                ]}""");

        ExtractedMenu menu = extractor("AIza-xxx").extract(PDF, "carte.pdf");

        assertThat(menu.categories()).hasSize(2);
        assertThat(menu.categories().get(0).name()).isEqualTo("Entrées");
        assertThat(menu.categories().get(0).items().get(0).name()).isEqualTo("Salade");
        assertThat(menu.categories().get(0).items().get(0).price()).isEqualTo(950);
        assertThat(menu.itemCount()).isEqualTo(2);
    }

    @Test
    void toleratesJsonWrappedInProse() {
        responseBody = candidateEnvelope("STOP",
                "Voici le menu :\n```json\n{\"categories\":[{\"name\":\"Plats\",\"items\":["
                        + "{\"name\":\"Poulet\",\"description\":null,\"price\":1400,\"currency\":\"EUR\","
                        + "\"needsReview\":false,\"note\":null}]}]}\n```");

        ExtractedMenu menu = extractor("AIza-xxx").extract(PDF, "carte.pdf");
        assertThat(menu.categories()).hasSize(1);
        assertThat(menu.categories().get(0).items().get(0).price()).isEqualTo(1400);
    }

    @Test
    void invalidJsonFailsCleanly() {
        // des accolades présentes (donc un "objet" est isolé) mais du JSON syntaxiquement cassé
        responseBody = candidateEnvelope("STOP", "{\"categories\":[{\"name\":\"Plats\",\"items\":[ }] }");

        assertThatThrownBy(() -> extractor("AIza-xxx").extract(PDF, "carte.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("n'a pas pu être interprété");
    }

    @Test
    void noJsonAtAllFailsCleanly() {
        responseBody = candidateEnvelope("STOP", "Je ne vois pas de carte de restaurant.");

        assertThatThrownBy(() -> extractor("AIza-xxx").extract(PDF, "carte.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("Aucun plat");
    }

    @Test
    void emptyResponseBodyFailsCleanly() {
        responseBody = "";

        assertThatThrownBy(() -> extractor("AIza-xxx").extract(PDF, "carte.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("illisible");
    }

    @Test
    void noCandidatesFailsCleanly() {
        responseBody = "{\"candidates\":[],\"usageMetadata\":{}}";

        assertThatThrownBy(() -> extractor("AIza-xxx").extract(PDF, "carte.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("aucun résultat");
    }

    @Test
    void promptBlockedByModerationIsSurfacedAsARefusal() {
        responseBody = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"},\"candidates\":[]}";

        assertThatThrownBy(() -> extractor("AIza-xxx").extract(PDF, "carte.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("refusé");
    }

    @Test
    void candidateBlockedBySafetyIsSurfacedAsARefusal() {
        responseBody = candidateEnvelope("SAFETY", "");

        assertThatThrownBy(() -> extractor("AIza-xxx").extract(PDF, "carte.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("refusé");
    }

    @Test
    void truncatedResponseIsReportedNotSilentlyParsed() {
        responseBody = candidateEnvelope("MAX_TOKENS", "{\"categories\":[");

        assertThatThrownBy(() -> extractor("AIza-xxx").extract(PDF, "carte.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("trop longue");
    }

    // ==================================================================== Erreurs HTTP

    @Test
    void http400IsReportedWithoutLeakingTheProviderBody() {
        responseStatus = 400;
        responseBody = "{\"error\":{\"code\":400,\"message\":\"Invalid JSON payload\",\"status\":\"INVALID_ARGUMENT\"}}";

        assertThatThrownBy(() -> extractor("AIza-xxx").extract(PDF, "carte.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("momentanément indisponible")
                .hasMessageNotContaining("INVALID_ARGUMENT")
                .hasMessageNotContaining("Invalid JSON payload");
    }

    @Test
    void http401IsReportedWithoutLeakingTheKeyOrBody() {
        responseStatus = 401;
        responseBody = "{\"error\":{\"code\":401,\"message\":\"API key not valid\",\"status\":\"UNAUTHENTICATED\"}}";

        assertThatThrownBy(() -> extractor("AIza-bad").extract(PDF, "carte.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("momentanément indisponible")
                .hasMessageNotContaining("API key")
                .hasMessageNotContaining("AIza-bad");
    }

    @Test
    void http403IsReported() {
        responseStatus = 403;
        responseBody = "{\"error\":{\"code\":403,\"status\":\"PERMISSION_DENIED\"}}";

        assertThatThrownBy(() -> extractor("AIza-xxx").extract(PDF, "carte.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("momentanément indisponible");
    }

    @Test
    void http429IsReportedAsRetryable() {
        responseStatus = 429;
        responseBody = "{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}";

        assertThatThrownBy(() -> extractor("AIza-xxx").extract(PDF, "carte.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("très sollicité");
    }

    @Test
    void http500IsReported() {
        responseStatus = 500;
        responseBody = "{\"error\":{\"code\":500,\"status\":\"INTERNAL\"}}";

        assertThatThrownBy(() -> extractor("AIza-xxx").extract(PDF, "carte.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("momentanément indisponible");
    }

    @Test
    void readTimeoutIsReported() {
        responseDelayMs = 1500;
        responseBody = candidateEnvelope("STOP", "{\"categories\":[]}");

        assertThatThrownBy(() -> extractor("AIza-xxx", "gemini-3.6-flash", 1).extract(PDF, "carte.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("momentanément indisponible");
    }

    // ---------------------------------------------------------------- helper

    private record RecordedRequest(
            String method, String uri, String apiKey, String contentType, String body) {
    }
}
