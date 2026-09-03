package com.qrmenu.admin;

import com.qrmenu.common.InvalidUploadException;
import com.qrmenu.common.PublicUrlBuilder;
import com.qrmenu.media.MediaAsset;
import com.qrmenu.media.MediaService;
import com.qrmenu.restaurant.RestaurantService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * Upload des images d'un client : {@code POST /api/admin/restaurants/{id}/images}.
 *
 * Volontairement rattaché au <strong>client</strong> et non au menu : la même image peut
 * servir un produit aujourd'hui, un logo ou une bannière plus tard, sans nouvelle route.
 * Ce n'est pas un gestionnaire de médias — pas de liste, pas de renommage : uniquement
 * ce qu'il faut pour qu'un produit puisse référencer une image réelle.
 *
 * La réponse porte l'{@code assetId} (à placer dans {@code imageAssetId} d'un produit)
 * et l'{@code url} publique servie par {@code GET /media/{assetId}}.
 */
@RestController
public class MediaAdminController {

    private final MediaService mediaService;
    private final RestaurantService restaurantService;
    private final PublicUrlBuilder urlBuilder;

    public MediaAdminController(
            MediaService mediaService,
            RestaurantService restaurantService,
            PublicUrlBuilder urlBuilder
    ) {
        this.mediaService = mediaService;
        this.restaurantService = restaurantService;
        this.urlBuilder = urlBuilder;
    }

    @PostMapping("/api/admin/restaurants/{restaurantId}/images")
    @ResponseStatus(HttpStatus.CREATED)
    public ImageResponse uploadImage(
            @PathVariable UUID restaurantId,
            @RequestParam("file") MultipartFile file
    ) {
        restaurantService.getOrThrow(restaurantId); // 404 explicite si le client n'existe pas
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("Aucun fichier fourni.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du fichier impossible.", e);
        }

        // Le Content-Type annoncé par le navigateur n'est pas transmis : le format est
        // déduit de la signature du fichier par MediaService.
        MediaAsset asset = mediaService.storeImage(restaurantId, content, file.getOriginalFilename());

        return new ImageResponse(
                asset.getId(),
                urlBuilder.forAsset(asset.getId()),
                asset.getContentType(),
                asset.getSizeBytes(),
                asset.getOriginalFilename());
    }

    public record ImageResponse(
            UUID assetId,
            String url,
            String contentType,
            long sizeBytes,
            String originalFilename
    ) {
    }
}
