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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extraction via l'API <em>generateContent</em> de Google Gemini, en HTTP direct.
 *
 * Provider par défaut de KartaAI ({@code kartaai.provider=gemini}). Pas de SDK ajouté :
 * un seul appel {@code POST /v1beta/models/{model}:generateContent} avec {@code RestClient}
 * (déjà fourni par {@code spring-boot-starter-web}).
 *
 * Le PDF est envoyé <strong>tel quel</strong> au modèle, en partie {@code inline_data}
 * ({@code mime_type: application/pdf}), plutôt que d'en extraire le texte côté serveur :
 * beaucoup de cartes sont des mises en page vectorisées ou des scans, sans couche de
 * texte exploitable.
 *
 * Sortie JSON : {@code generationConfig.responseMimeType = application/json} (le mode JSON
 * natif de Gemini) et {@code thinkingConfig.thinkingLevel = "LOW"} — les modèles Gemini Flash
 * récents sont des modèles de raisonnement et leurs jetons de réflexion sont décomptés de
 * {@code maxOutputTokens} ; pour de l'extraction structurée la réflexion minimale suffit,
 * en laisser davantage pourrait tronquer la réponse. (Gemini 3 : {@code thinkingLevel} ;
 * les modèles antérieurs utilisaient {@code thinkingBudget} — champ différent.)
 *
 * <h2>Secrets</h2>
 * La clé vient uniquement de l'environnement, n'est jamais journalisée, jamais renvoyée
 * au client, n'atteint jamais Angular. Sans clé, le composant se déclare indisponible :
 * KartaAI est optionnel, le démarrage de l'application n'échoue pas.
 */
@Component
@ConditionalOnProperty(name = "kartaai.provider", havingValue = "gemini", matchIfMissing = true)
public class GeminiMenuExtractor implements MenuExtractor {

    private static final Logger log = LoggerFactory.getLogger(GeminiMenuExtractor.class);

    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public GeminiMenuExtractor(
            ObjectMapper objectMapper,
            @Value("${kartaai.api-key:}") String apiKey,
            @Value("${kartaai.model:gemini-3.6-flash}") String model,
            @Value("${kartaai.base-url:}") String baseUrl,
            @Value("${kartaai.timeout-seconds:120}") int timeoutSeconds,
            @Value("${kartaai.max-tokens:16000}") int maxTokens
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "gemini-3.6-flash" : model.trim();
        this.maxTokens = maxTokens;

        String base = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();

        // Analyser une carte prend plusieurs dizaines de secondes : le timeout par défaut
        // couperait la requête en plein travail. Il reste borné pour ne pas immobiliser un
        // thread indéfiniment.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(base)
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
                    // Le modèle est un identifiant de configuration (ops), pas une entrée client.
                    .uri("/v1beta/models/{model}:generateContent", model)
                    // En-tête plutôt que ?key= : la clé ne transite pas dans l'URL (logs, proxys).
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(pdf))
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.TooManyRequests e) {
            log.warn("Extraction KartaAI (Gemini) — quota atteint pour {}", ExtractionSupport.safeFilename(filename));
            throw new ExtractionException(
                    "Le service d'analyse est très sollicité. Réessayez dans quelques minutes.", e);
        } catch (RestClientException e) {
            // Le corps d'erreur du fournisseur peut contenir des détails d'infrastructure :
            // journalisé côté serveur, jamais renvoyé au restaurateur.
            log.warn("Extraction KartaAI (Gemini) en échec pour {}", ExtractionSupport.safeFilename(filename), e);
            throw new ExtractionException(
                    "Le service d'analyse est momentanément indisponible. Réessayez dans un instant.", e);
        }

        return parse(responseBody, filename);
    }

    // ---------------------------------------------------------------- requête

    private Map<String, Object> requestBody(byte[] pdf) {
        Map<String, Object> pdfPart = Map.of("inline_data", Map.of(
                "mime_type", "application/pdf",
                // Sans saut de ligne : base64 « pur ».
                "data", Base64.getEncoder().encodeToString(pdf)));
        Map<String, Object> textPart = Map.of("text", ExtractionSupport.PROMPT);

        Map<String, Object> content = Map.of(
                "role", "user",
                // Le document précède la consigne.
                "parts", List.of(pdfPart, textPart));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("temperature", 0);
        generationConfig.put("maxOutputTokens", maxTokens);
        // Modèle de raisonnement : réflexion minimale pour de l'extraction structurée
        // (Gemini 3 : "thinkingLevel" ; "thinkingBudget" appartenait aux modèles 2.x).
        generationConfig.put("thinkingConfig", Map.of("thinkingLevel", "LOW"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(content));
        body.put("generationConfig", generationConfig);
        return body;
    }

    // ---------------------------------------------------------------- réponse

    private ExtractedMenu parse(String responseBody, String filename) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            log.warn("Réponse KartaAI (Gemini) illisible pour {}", ExtractionSupport.safeFilename(filename), e);
            throw new ExtractionException("La réponse du service d'analyse est illisible.", e);
        }

        // Requête bloquée par la modération en amont : le prompt (donc le PDF) est refusé.
        String blockReason = root.path("promptFeedback").path("blockReason").asText(null);
        if (blockReason != null && !blockReason.isBlank()) {
            throw new ExtractionException(
                    "Le service d'analyse a refusé de traiter ce document. "
                            + "Vérifiez qu'il s'agit bien d'une carte de restaurant.");
        }

        JsonNode candidate = root.path("candidates").path(0);
        if (candidate.isMissingNode() || candidate.isEmpty()) {
            log.warn("Aucun candidat dans la réponse KartaAI (Gemini) pour {}", ExtractionSupport.safeFilename(filename));
            throw new ExtractionException("Le service d'analyse n'a renvoyé aucun résultat. Réessayez.");
        }

        switch (candidate.path("finishReason").asText("")) {
            case "SAFETY", "RECITATION", "PROHIBITED_CONTENT", "BLOCKLIST" -> throw new ExtractionException(
                    "Le service d'analyse a refusé de traiter ce document. "
                            + "Vérifiez qu'il s'agit bien d'une carte de restaurant.");
            case "MAX_TOKENS" -> throw new ExtractionException(
                    "La carte est trop longue pour être analysée en une seule fois. "
                            + "Réessayez, ou augmentez KARTA_AI_MAX_TOKENS.");
            default -> { /* STOP, OTHER, vide : on tente la lecture du contenu */ }
        }

        String text = concatText(candidate);
        String json = ExtractionSupport.extractJsonObject(text);
        if (json == null) {
            log.warn("Aucun JSON exploitable dans la réponse KartaAI (Gemini) pour {}",
                    ExtractionSupport.safeFilename(filename));
            throw new ExtractionException(
                    "Aucun plat n'a pu être lu dans ce PDF. "
                            + "Vérifiez qu'il s'agit bien d'une carte, puis réessayez.");
        }

        try {
            return objectMapper.readValue(json, ExtractedMenu.class);
        } catch (Exception e) {
            log.warn("JSON KartaAI (Gemini) non conforme au contrat pour {}",
                    ExtractionSupport.safeFilename(filename), e);
            throw new ExtractionException(
                    "Le contenu extrait n'a pas pu être interprété. Réessayez.", e);
        }
    }

    /** Le contenu d'un candidat est un tableau de parts ; seules les parts texte comptent. */
    private static String concatText(JsonNode candidate) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            JsonNode text = part.path("text");
            if (text.isTextual()) {
                sb.append(text.asText());
            }
        }
        return sb.toString();
    }
}
