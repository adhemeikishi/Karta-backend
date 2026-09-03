package com.qrmenu.admin;

import com.qrmenu.menu.MenuDesign;
import com.qrmenu.menu.MenuPreset;
import com.qrmenu.render.MenuRenderer;
import com.qrmenu.render.PublicMenuDtos.PublicMenu;
import com.qrmenu.render.PublicMenuService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;
import java.util.UUID;

/**
 * Aperçu du menu depuis le studio :
 * {@code GET /api/admin/restaurants/{restaurantId}/menu/preview}.
 *
 * Sous {@code /api/admin/**}, donc protégé par Basic Auth : c'est ce qui permet
 * d'afficher un menu DRAFT ou READY sans jamais le rendre public. Aperçu ≠ public.
 *
 * <h2>Aperçu d'un design non enregistré</h2>
 * Les paramètres optionnels ({@code preset}, {@code brandName}, {@code primaryColor},
 * {@code secondaryColor}, {@code logoAssetId}, {@code heroAssetId}) sont appliqués
 * <strong>par-dessus</strong> le design enregistré, sans rien écrire en base. C'est ce
 * qui permet à l'aperçu de suivre un clic sur un preset avant « Enregistrer », tout en
 * gardant un seul et même renderer pour l'aperçu et la page publique.
 *
 * Ces surcharges ne contournent aucun droit : la personnalisation d'identité reste
 * ignorée hors PREMIUM, y compris ici — sinon l'aperçu montrerait un menu impossible à
 * publier.
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
    public String preview(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) MenuPreset preset,
            @RequestParam(required = false) String brandName,
            @RequestParam(required = false) String primaryColor,
            @RequestParam(required = false) String secondaryColor,
            @RequestParam(required = false) UUID logoAssetId,
            @RequestParam(required = false) UUID heroAssetId,
            Model model,
            HttpServletResponse response
    ) {
        MenuDesign overrides = new MenuDesign(
                preset, brandName, primaryColor, secondaryColor, logoAssetId, heroAssetId);
        Optional<PublicMenu> menu = publicMenuService.buildPreview(restaurantId, overrides);

        // L'aperçu suit l'état d'édition en cours : le mettre en cache afficherait un
        // style déjà remplacé au clic suivant.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        if (menu.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return renderer.renderUnavailable(model);
        }
        return renderer.renderPreview(menu.get(), publicMenuService.isPublished(restaurantId), model);
    }
}
