package com.qrmenu.admin;

import com.qrmenu.common.InvalidUploadException;
import com.qrmenu.menu.MenuDtos.MenuResponse;
import com.qrmenu.menu.MenuDtos.SaveMenuRequest;
import com.qrmenu.menu.MenuService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * Gestion du menu d'un client. Sous {@code /api/admin/**} : Basic Auth exigé
 * (voir SecurityConfig).
 *
 * Le menu est traité comme une <strong>ressource unique</strong> ({@code GET / POST / PUT /
 * DELETE} sur {@code .../menu}) plutôt qu'en CRUD granulaire catégorie par catégorie :
 * la future Review manipule le document entier, et on évite une dizaine d'endpoints.
 * Les sous-routes {@code /pdf}, {@code /publish} et {@code /unpublish} restent propres
 * au flux BASIC.
 */
@RestController
@RequestMapping("/api/admin/restaurants/{restaurantId}/menu")
public class MenuAdminController {

    private final MenuService menuService;

    public MenuAdminController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public MenuResponse getMenu(@PathVariable UUID restaurantId) {
        return menuService.getMenu(restaurantId);
    }

    /** Crée le menu du client (type déduit de l'offre). 409 si un menu existe déjà. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MenuResponse createMenu(@PathVariable UUID restaurantId) {
        return menuService.createMenu(restaurantId);
    }

    /**
     * Remplace l'intégralité de la structure du menu (offres PRO / PREMIUM).
     * Document complet : ce qui n'est pas envoyé est supprimé.
     */
    @PutMapping
    public MenuResponse saveStructure(
            @PathVariable UUID restaurantId,
            @Valid @RequestBody SaveMenuRequest request
    ) {
        return menuService.saveStructure(restaurantId, request.categories());
    }

    /** Supprime le menu et tout son contenu. Le QR retrouve sa destination d'origine. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMenu(@PathVariable UUID restaurantId) {
        menuService.deleteMenu(restaurantId);
    }

    @PostMapping("/pdf")
    public MenuResponse uploadPdf(@PathVariable UUID restaurantId, @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("Aucun fichier fourni.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du fichier impossible.", e);
        }
        return menuService.uploadPdf(restaurantId, content, file.getContentType(), file.getOriginalFilename());
    }

    @DeleteMapping("/pdf")
    public MenuResponse deletePdf(@PathVariable UUID restaurantId) {
        return menuService.deletePdf(restaurantId);
    }

    @PutMapping("/publish")
    public MenuResponse publish(@PathVariable UUID restaurantId) {
        return menuService.publish(restaurantId);
    }

    @PutMapping("/unpublish")
    public MenuResponse unpublish(@PathVariable UUID restaurantId) {
        return menuService.unpublish(restaurantId);
    }
}
