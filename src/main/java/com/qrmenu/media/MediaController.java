package com.qrmenu.media;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

/**
 * Accès public aux médias (PDF de menu BASIC). Sert le fichier depuis le disque,
 * jamais depuis la base. L'identifiant d'asset est immuable — un remplacement de
 * PDF crée un nouvel asset, donc une nouvelle URL — le cache peut donc être long.
 */
@RestController
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping("/media/{assetId}")
    public ResponseEntity<byte[]> get(@PathVariable UUID assetId) {
        MediaAsset asset = mediaService.getOrThrow(assetId);
        byte[] content = mediaService.readContent(asset);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + downloadName(asset) + "\"")
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .eTag(asset.getId().toString())
                .body(content);
    }

    private static String downloadName(MediaAsset asset) {
        String name = asset.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "menu.pdf";
        }
        return name.replaceAll("[\\r\\n\"]", "");
    }
}
