package com.qrmenu.admin;

import com.qrmenu.render.MenuRenderer;
import com.qrmenu.render.PublicMenuDtos.PublicMenu;
import com.qrmenu.render.PublicMenuService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;
import java.util.UUID;

/**
 * Aperçu du menu depuis le back-office :
 * {@code GET /api/admin/restaurants/{restaurantId}/menu/preview}.
 *
 * Sous {@code /api/admin/**}, donc protégé par Basic Auth : c'est ce qui permet
 * d'afficher un menu DRAFT ou READY sans jamais le rendre public. Aperçu ≠ public.
 *
 * Classe séparée de {@link MenuAdminController} parce que celui-ci est un
 * {@code @RestController} : un nom de vue y serait sérialisé en JSON.
 */
@Controller
public class MenuPreviewController {

    private final PublicMenuService publicMenuService;
    private final MenuRenderer renderer;

    public MenuPreviewController(PublicMenuService publicMenuService, MenuRenderer renderer) {
        this.publicMenuService = publicMenuService;
        this.renderer = renderer;
    }

    @GetMapping("/api/admin/restaurants/{restaurantId}/menu/preview")
    public String preview(@PathVariable UUID restaurantId, Model model, HttpServletResponse response) {
        Optional<PublicMenu> menu = publicMenuService.buildPreview(restaurantId);

        if (menu.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return renderer.renderUnavailable(model);
        }
        return renderer.renderPreview(menu.get(), model);
    }
}
