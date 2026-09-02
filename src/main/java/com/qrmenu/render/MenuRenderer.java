package com.qrmenu.render;

import com.qrmenu.render.PublicMenuDtos.PublicMenu;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/**
 * Point de rendu unique du menu Karta.
 *
 * L'aperçu back-office et la page publique passent tous les deux par ici : il ne doit
 * jamais exister deux logiques d'affichage qui divergent. Le renderer ne connaît que
 * le {@link PublicMenu} — aucune donnée métier propre à un client, aucune notion de
 * statut ou d'offre.
 *
 * Ajouter un preset = déposer {@code templates/menu/presets/<nom>.html} et une valeur
 * dans {@link MenuPreset}. Aucune modification ici.
 */
@Component
public class MenuRenderer {

    /** Rendu public. */
    public String render(PublicMenu menu, Model model) {
        return render(menu, MenuPreset.DEFAULT, false, model);
    }

    /** Rendu d'aperçu : même sortie, plus un bandeau signalant que rien n'est diffusé. */
    public String renderPreview(PublicMenu menu, Model model) {
        return render(menu, MenuPreset.DEFAULT, true, model);
    }

    /** Page « menu indisponible » — servie avec un 404, sans révéler la cause. */
    public String renderUnavailable(Model model) {
        return "menu/unavailable";
    }

    private String render(PublicMenu menu, MenuPreset preset, boolean preview, Model model) {
        model.addAttribute("menu", menu);
        model.addAttribute("preview", preview);
        return preset.templateName();
    }
}
