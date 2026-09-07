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
 * Vérifie le <strong>contrat HTTP</strong> de l'appel à l'API Messages d'Anthropic :
 * la forme exacte de la requête sortante, l'interprétation de la réponse et le
 * comportement en cas d'échec.
 *
 * Ce test n'utilise <strong>aucune</strong> API réelle et ne prouve pas que KartaAI
 * « fonctionne » de bout en bout — il prouve que, si une clé valide est fournie et que
 * le fournisseur répond, le client sait construire la requête et lire la réponse. Le
 * parcours métier complet (PDF → brouillon → Review → menu) est couvert par
 * {@link KartaAiFlowTest} avec un extracteur déterministe.
 *
 * Serveur bouchon : {@code com.sun.net.httpserver.HttpServer} du JDK — aucune
 * dépendance ajoutée, aucun changement de code de production.
 */
class AnthropicMenuExtractorTest {

    private static final byte[] PDF = "%PDF-1.4\nmenu\n%%EOF".getBytes(StandardCharsets.UTF_8);

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<RecordedRequest> lastRequest = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "";

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body;
            try (InputStream in = exchange.getRequestBody()) {
                body = in.readAllBytes();
            }
            lastRequest.set(new RecordedRequest(
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("x-api-key"),
                    exchange.getRequestHeaders().getFirst("anthropic-version"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(body, StandardCharsets.UTF_8)));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            // Comme l'API Messages d'Anthropic : JSON en UTF-8.
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

    private AnthropicMenuExtractor extractor(String apiKey) {
        return new AnthropicMenuExtractor(new ObjectMapper(), apiKey, "claude-opus-5", baseUrl, 10, 16000);
    }

    private static String anthropicEnvelope(String textBlock) {
        return """
                {"id":"msg_1","type":"message","role":"assistant","stop_reason":"end_turn",
                 "content":[{"type":"text","text":%s}]}
                """.formatted(new ObjectMapper().valueToTree(textBlock).toString());
    }

    // ---------------------------------------------------------------- disponibilité

    @Test
    void isUnavailableWithoutKey() {
        assertThat(extractor("").isAvailable()).isFalse();
        assertThat(extractor("   ").isAvailable()).isFalse();
    }

    @Test
    void isAvailableWithKey() {
        assertThat(extractor("sk-ant-xxx").isAvailable()).isTrue();
    }

    @Test
    void extractRefusesWithoutKeyWithoutCallingTheApi() {
        assertThatThrownBy(() -> extractor("").extract(PDF, "carte.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("n'est pas configuré");
        assertThat(lastRequest.get()).isNull();
    }

    // ---------------------------------------------------------------- contrat de requête

    @Test
    void sendsAWellFormedAnthropicMessagesRequest() throws Exception {
        responseBody = anthropicEnvelope("{\"categories\":[]}");

        try {
            extractor("sk-ant-secret").extract(PDF, "carte.pdf");
        } catch (ExtractionException ignored) {
            // {"categories":[]} passe le parsing ; l'important ici est la requête envoyée.
        }

        RecordedRequest req = lastRequest.get();
        assertThat(req).isNotNull();
        assertThat(req.path()).isEqualTo("/v1/messages");
        assertThat(req.apiKey()).isEqualTo("sk-ant-secret");
        assertThat(req.anthropicVersion()).isEqualTo("2023-06-01");
        assertThat(req.contentType()).contains("application/json");

        JsonNode body = new ObjectMapper().readTree(req.body());
        assertThat(body.path("model").asText()).isEqualTo("claude-opus-5");
        assertThat(body.path("max_tokens").asInt()).isEqualTo(16000);

        JsonNode content = body.path("messages").path(0).path("content");
        assertThat(content.get(0).path("type").asText()).isEqualTo("document");
        assertThat(content.get(1).path("type").asText()).isEqualTo("text");

        JsonNode source = content.get(0).path("source");
        assertThat(source.path("type").asText()).isEqualTo("base64");
        assertThat(source.path("media_type").asText()).isEqualTo("application/pdf");
        String data = source.path("data").asText();
        assertThat(data).doesNotContain("\n");
        assertThat(Base64.getDecoder().decode(data)).isEqualTo(PDF);
    }

    // ---------------------------------------------------------------- lecture de la réponse

    @Test
    void parsesAValidMenuFromTheTextBlock() {
        responseBody = anthropicEnvelope("""
                Voici le menu :
                {"categories":[
                  {"name":"Entrées","items":[
                    {"name":"Salade","description":"chèvre chaud","price":950,"currency":"EUR","needsReview":false,"note":null}]},
                  {"name":"Plats","items":[
                    {"name":"Steak","description":null,"price":1990,"currency":"EUR","needsReview":false,"note":null}]}
                ]}""");

        ExtractedMenu menu = extractor("sk-ant-xxx").extract(PDF, "carte.pdf");

        assertThat(menu.categories()).hasSize(2);
        assertThat(menu.categories().get(0).name()).isEqualTo("Entrées");
        assertThat(menu.categories().get(0).items().get(0).name()).isEqualTo("Salade");
        assertThat(menu.categories().get(0).items().get(0).price()).isEqualTo(950);
        assertThat(menu.itemCount()).isEqualTo(2);
    }

    @Test
    void policyRefusalIsSurfacedAsAnExtractionError() {
        responseBody = """
                {"id":"msg_1","type":"message","stop_reason":"refusal","content":[]}
                """;

        assertThatThrownBy(() -> extractor("sk-ant-xxx").extract(PDF, "carte.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("refusé");
    }

    @Test
    void aResponseWithoutAnyJsonObjectFailsCleanly() {
        responseBody = anthropicEnvelope("Je ne vois pas de carte de restaurant dans ce document.");

        assertThatThrownBy(() -> extractor("sk-ant-xxx").extract(PDF, "carte.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("Aucun plat");
    }

    @Test
    void aProviderErrorIsReportedWithoutLeakingItsBody() {
        responseStatus = 401;
        responseBody = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";

        assertThatThrownBy(() -> extractor("sk-ant-bad").extract(PDF, "carte.pdf"))
                .isInstanceOf(ExtractionException.class)
                .hasMessageContaining("momentanément indisponible")
                // le détail du fournisseur ne doit jamais atteindre le message client
                .hasMessageNotContaining("x-api-key")
                .hasMessageNotContaining("authentication_error");
    }

    // ---------------------------------------------------------------- helper

    private record RecordedRequest(
            String path, String apiKey, String anthropicVersion, String contentType, String body) {
    }
}
