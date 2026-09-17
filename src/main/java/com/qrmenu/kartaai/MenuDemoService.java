package com.qrmenu.kartaai;

import com.qrmenu.kartaai.ExtractionDtos.ExtractedMenu;
import com.qrmenu.media.MediaService;
import org.springframework.stereotype.Service;

/**
 * Extraction KartaAI pour la démo publique {@code /create/design}, sans restaurant ni compte.
 *
 * Même pipeline que {@link MenuDraftService} — {@link MenuExtractor} puis
 * {@link ExtractionValidator} — mais sans passer par lui : pas de {@code restaurantId}, pas
 * de vérification d'offre, et surtout <strong>aucune écriture</strong> en base
 * ({@code menu_drafts} ou ailleurs). Le résultat est retourné directement à l'appelant et
 * n'existe nulle part côté serveur une fois la réponse envoyée.
 */
@Service
public class MenuDemoService {

    private final MenuExtractor extractor;
    private final ExtractionValidator validator;
    private final MediaService mediaService;
    private final DemoRateLimiter rateLimiter;

    public MenuDemoService(
            MenuExtractor extractor,
            ExtractionValidator validator,
            MediaService mediaService,
            DemoRateLimiter rateLimiter
    ) {
        this.extractor = extractor;
        this.validator = validator;
        this.mediaService = mediaService;
        this.rateLimiter = rateLimiter;
    }

    public ExtractedMenu extractForDemo(
            String clientKey, byte[] pdf, String declaredContentType, String filename) {
        if (!extractor.isAvailable()) {
            throw new ExtractionException("KartaAI n'est pas configuré sur ce serveur.");
        }
        rateLimiter.checkAllowed(clientKey);
        mediaService.validatePdf(pdf, declaredContentType);
        return validator.validate(extractor.extract(pdf, filename));
    }
}
