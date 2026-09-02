package com.qrmenu.render;

import com.qrmenu.common.NotFoundException;
import com.qrmenu.render.PublicMenuDtos.PublicMenu;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API publique du menu : {@code GET /api/public/menus/{code}}.
 *
 * Renvoie exactement ce que le renderer affiche — même DTO, mêmes filtrages. Sert de
 * contrat pour un futur front public ou un aperçu tiers, sans jamais exposer les
 * données d'administration (voir {@link PublicMenuDtos}).
 */
@RestController
@RequestMapping("/api/public/menus")
public class PublicMenuApiController {

    private final PublicMenuService publicMenuService;

    public PublicMenuApiController(PublicMenuService publicMenuService) {
        this.publicMenuService = publicMenuService;
    }

    @GetMapping("/{code}")
    public PublicMenu menu(@PathVariable String code) {
        return publicMenuService.findPublic(code)
                .orElseThrow(() -> new NotFoundException("Menu indisponible."));
    }
}
