package com.qrmenu.kartaai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qrmenu.kartaai.ExtractionDtos.ExtractedMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 */
@Component
public class AnthropicMenuExtractor implements MenuExtractor {

    private static final Logger log = LoggerFactory.getLogger(AnthropicMenuExtractor.class);

    /** Version de l'API Messages, exigée à chaque requête. */
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    /**
     * Consigne d'extraction.
     *
     * Trois exigences non négociables y figurent : des centimes entiers (Karta n'exprime
     * aucun montant en flottant), aucun HTML, et {@code price: null} plutôt qu'une
     * invention quand le prix est illisible. Un prix inventé serait publié sans que
     * personne ne le remarque — un prix absent est signalé en Review.
     */
    private static final String PROMPT = """
            Tu extrais le contenu d'une carte de restaurant à partir du PDF fourni.

            Réponds UNIQUEMENT avec un objet JSON valide, sans texte autour, sans bloc de
            code, sans HTML. Format exact :

            {"categories":[{"name":"...","items":[
              {"name":"...","description":"...","price":950,"currency":"EUR",
               "needsReview":false,"note":null}
            ]}]}

            Règles :
            - "price" est un ENTIER en CENTIMES : 9,50 € s'écrit 950. Jamais de décimale.
            - Si un prix est illisible, ambigu ou absent, mets "price": null et
              "needsReview": true. N'invente JAMAIS un prix.
            - "needsReview": true dès que tu as un doute sur un plat (nom coupé, deux
              prix possibles, rattachement de catégorie incertain), avec une "note" très
              courte en français expliquant le doute. Sinon "needsReview": false et
              "note": null.
            - "description" : la description du plat si elle figure sur la carte, sinon null.
            - "currency" : code ISO du prix affiché ("EUR" par défaut).
            - Respecte l'ordre et les catégories de la carte. N'invente aucun plat, aucune
              catégorie, aucune description.
            - Ignore ce qui n'est pas la carte : horaires, adresse, mentions légales, wifi.
            - Si le document n'est pas une carte de restaurant, réponds {"categories":[]}.
            """;

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
            log.warn("Extraction KartaAI en échec pour {}", safe(filename), e);
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
                "content", List.of(document, Map.of("type", "text", "text", PROMPT)))));
        return body;
    }

    // ---------------------------------------------------------------- réponse

    private ExtractedMenu parse(String responseBody, String filename) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            log.warn("Réponse KartaAI illisible pour {}", safe(filename), e);
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
        String json = extractJsonObject(text);
        if (json == null) {
            log.warn("Aucun JSON exploitable dans la réponse KartaAI pour {}", safe(filename));
            throw new ExtractionException(
                    "Aucun plat n'a pu être lu dans ce PDF. "
                            + "Vérifiez qu'il s'agit bien d'une carte, puis réessayez.");
        }

        try {
            return objectMapper.readValue(json, ExtractedMenu.class);
        } catch (Exception e) {
            log.warn("JSON KartaAI non conforme au contrat pour {}", safe(filename), e);
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

    /**
     * Isole l'objet JSON du texte renvoyé.
     *
     * Le modèle est prié de ne rendre que du JSON, mais un préambule ou un bloc de code
     * reste possible ; on ne fait pas dépendre le parcours d'une politesse de formatage.
     * On borne sur les accolades extrêmes plutôt que d'utiliser une expression régulière :
     * un JSON imbriqué la mettrait en défaut.
     */
    static String extractJsonObject(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return (start < 0 || end <= start) ? null : text.substring(start, end + 1);
    }

    /** Un nom de fichier arrive du client : jamais journalisé brut (injection de log). */
    private static String safe(String filename) {
        if (filename == null) {
            return "(sans nom)";
        }
        String cleaned = filename.replaceAll("[\\r\\n\\t]", "_");
        return cleaned.length() <= 120 ? cleaned : cleaned.substring(0, 120);
    }
}
