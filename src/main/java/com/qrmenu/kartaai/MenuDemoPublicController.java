package com.qrmenu.kartaai;

import com.qrmenu.common.InvalidUploadException;
import com.qrmenu.kartaai.ExtractionDtos.ExtractedMenu;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Extraction KartaAI pour la démo commerciale publique {@code /create/design}.
 *
 * Public, sans Basic Auth (voir {@code SecurityConfig} : seul {@code /api/admin/**} exige un
 * compte). Un prospect peut donc déposer son propre PDF sans créer de restaurant ni de
 * compte — voir {@link MenuDemoService} : le résultat n'est jamais persisté.
 *
 * Limité par IP ({@link DemoRateLimiter}) : ce endpoint appelle réellement Gemini, avec un
 * coût réel, sur une route ouverte à tout visiteur.
 */
@RestController
@RequestMapping("/api/public/menu-demo")
public class MenuDemoPublicController {

    private final MenuDemoService demoService;

    public MenuDemoPublicController(MenuDemoService demoService) {
        this.demoService = demoService;
    }

    @PostMapping("/extract")
    public ExtractedMenu extract(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("Aucun fichier fourni.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du fichier impossible.", e);
        }
        return demoService.extractForDemo(clientKey(request), content, file.getContentType(), file.getOriginalFilename());
    }

    /**
     * Clé de quota : première adresse de {@code X-Forwarded-For} si le backend est derrière
     * un reverse proxy, sinon l'adresse de connexion directe.
     */
    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
