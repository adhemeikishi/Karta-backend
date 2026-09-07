package com.qrmenu.kartaai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrmenu.kartaai.ExtractionDtos.ExtractedMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extraction via l'API Messages d'Anthropic, en HTTP direct.
 *
 * Pas de SDK ajouté : un seul appel {@code POST /v1/messages} avec {@code RestClient},
 * déjà fourni par {@code spring-boot-starter-web}. Le projet est volontairement petit
 * (voir CLAUDE.md) — une dépendance entière pour une requête ne se justifie pas.
 *
 * Le PDF est envoyé <strong>tel quel</strong> au modèle, en bloc {@code document}, plutôt
 * que d'en extraire le texte côté serveur : beaucoup de cartes de restaurant sont des
 * mises en page vectorisées ou des scans, sans couche de texte exploitable. Une extraction
 * texte y renverrait une page vide sans le signaler.
 *
 * <h2>Secrets</h2>
 * La clé vient uniquement de l'environnement, n'est jamais journalisée, jamais renvoyée
 * au client et n'atteint jamais Angular. Sans clé, le composant se déclare simplement
 * indisponible : le démarrage de l'application n'échoue pas pour autant, KartaAI étant
 * une fonctionnalité optionnelle.
 *
 * <h2>Sélection</h2>
 * Provider historique, désormais optionnel : chargé uniquement si {@code kartaai.provider=anthropic}.
 * Le provider par défaut est {@link GeminiMenuExtractor}. En le sélectionnant, penser à
 * fournir {@code KARTA_AI_MODEL} (un modèle Anthropic) et, au besoin, {@code KARTA_AI_BASE_URL}.
 */
@Component
@ConditionalOnProperty(name = "kartaai.provider", havingValue = "anthropic")
public class AnthropicMenuExtractor implements MenuExtractor {

    private static final Logger log = LoggerFactory.getLogger(AnthropicMenuExtractor.class);

    /** Version de l'API Messages, exigée à chaque requête. */
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public AnthropicMenuExtractor(
            ObjectMapper objectMapper,
            @Value("${kartaai.api-key:}") String apiKey,
            @Value("${kartaai.model}") String model,
            @Value("${kartaai.base-url}") String baseUrl,
            @Value("${kartaai.timeout-seconds}") int timeoutSeconds,
            @Value("${kartaai.max-tokens}") int maxTokens
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.maxTokens = maxTokens;

        // Un PDF de carte prend plusieurs dizaines de secondes à analyser : le timeout
        // par défaut couperait la requête en plein travail. Il reste borné pour ne pas
        // immobiliser un thread indéfiniment.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public boolean isAvailable() {
        return !apiKey.isEmpty();
    }

    @Override
    public ExtractedMenu extract(byte[] pdf, String filename) {
        if (!isAvailable()) {
            throw new ExtractionException(
                    "KartaAI n'est pas configuré sur ce serveur. Contactez l'administrateur.");
        }

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(pdf))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            // Le message du fournisseur peut contenir des détails d'infrastructure :
            // journalisé côté serveur, jamais renvoyé au restaurateur.
            log.warn("Extraction KartaAI en échec pour {}", ExtractionSupport.safeFilename(filename), e);
            throw new ExtractionException(
                    "Le service d'analyse est momentanément indisponible. Réessayez dans un instant.", e);
        }

        return parse(responseBody, filename);
    }

    // ---------------------------------------------------------------- requête

    private Map<String, Object> requestBody(byte[] pdf) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("type", "document");
        document.put("source", Map.of(
                "type", "base64",
                "media_type", "application/pdf",
                // Sans saut de ligne : l'API rejette un base64 formaté.
                "data", Base64.getEncoder().encodeToString(pdf)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("messages", List.of(Map.of(
                "role", "user",
                // Le document précède la consigne : l'ordre recommandé par l'API.
                "content", List.of(document, Map.of("type", "text", "text", ExtractionSupport.PROMPT)))));
        return body;
    }

    // ---------------------------------------------------------------- réponse

    private ExtractedMenu parse(String responseBody, String filename) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            log.warn("Réponse KartaAI illisible pour {}", ExtractionSupport.safeFilename(filename), e);
            throw new ExtractionException("La réponse du service d'analyse est illisible.", e);
        }

        // Un refus de politique renvoie un 200 : il faut lire stop_reason avant content,
        // sinon on interpréterait une réponse vide comme une carte vide.
        if ("refusal".equals(root.path("stop_reason").asText(null))) {
            throw new ExtractionException(
                    "Le service d'analyse a refusé de traiter ce document. "
                            + "Vérifiez qu'il s'agit bien d'une carte de restaurant.");
        }

        String text = concatText(root);
        String json = ExtractionSupport.extractJsonObject(text);
        if (json == null) {
            log.warn("Aucun JSON exploitable dans la réponse KartaAI pour {}", ExtractionSupport.safeFilename(filename));
            throw new ExtractionException(
                    "Aucun plat n'a pu être lu dans ce PDF. "
                            + "Vérifiez qu'il s'agit bien d'une carte, puis réessayez.");
        }

        try {
            return objectMapper.readValue(json, ExtractedMenu.class);
        } catch (Exception e) {
            log.warn("JSON KartaAI non conforme au contrat pour {}", ExtractionSupport.safeFilename(filename), e);
            throw new ExtractionException(
                    "Le contenu extrait n'a pas pu être interprété. Réessayez.", e);
        }
    }

    /** Le contenu est un tableau de blocs ; seuls les blocs texte nous intéressent. */
    private static String concatText(JsonNode root) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : root.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                sb.append(block.path("text").asText());
            }
        }
        return sb.toString();
    }
}
