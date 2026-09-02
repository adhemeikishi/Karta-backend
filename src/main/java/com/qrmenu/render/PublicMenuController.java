package com.qrmenu.render;

import com.qrmenu.render.PublicMenuDtos.PublicMenu;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Optional;

/**
 * Page HTML publique du menu structuré : {@code GET /m/{code}}.
 *
 * Route publique (voir SecurityConfig : seul {@code /api/admin/**} est protégé).
 * Le QR continue de pointer vers {@code /q/{code}} : c'est la redirection existante
 * qui amène ici, via {@code destination_url}. Le hot-path du scan n'est pas modifié.
 */
@Controller
public class PublicMenuController {

    private final PublicMenuService publicMenuService;
    private final MenuRenderer renderer;

    public PublicMenuController(PublicMenuService publicMenuService, MenuRenderer renderer) {
        this.publicMenuService = publicMenuService;
        this.renderer = renderer;
    }

    @GetMapping("/m/{code}")
    public String menu(@PathVariable String code, Model model, HttpServletResponse response) {
        Optional<PublicMenu> menu = publicMenuService.findPublic(code);

        if (menu.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return renderer.renderUnavailable(model);
        }

        // Le menu peut changer à tout moment depuis le back-office : pas de cache
        // partagé, sinon un plat retiré resterait affiché.
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, must-revalidate");
        return renderer.render(menu.get(), model);
    }
}
