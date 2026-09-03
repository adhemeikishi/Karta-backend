package com.qrmenu.render;

import com.qrmenu.render.PublicMenuDtos.PublicMenu;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/**
 * Point de rendu unique du menu Karta.
 *
 * L'aperçu du studio et la page publique passent tous les deux par ici : il ne doit
 * jamais exister deux logiques d'affichage qui divergent. Le renderer ne connaît que le
 * {@link PublicMenu} — aucune donnée métier propre à un client, aucune notion de statut
 * ou d'offre.
 *
 * Un seul template pour les cinq presets : l'apparence est portée par le
 * {@link MenuTheme} déjà résolu (variables CSS + classe de densité). Ajouter un style
 * ne demande donc pas de dupliquer le balisage — et donc pas de risque de voir un preset
 * prendre du retard sur les autres.
 */
@Component
public class MenuRenderer {

    private static final String TEMPLATE = "menu/menu";

    /** Rendu public. */
    public String render(PublicMenu menu, Model model) {
        return render(menu, false, model);
    }

    /**
     * Rendu d'aperçu : même sortie, plus un bandeau rappelant qu'on regarde un aperçu et
     * si la carte est déjà en ligne.
     */
    public String renderPreview(PublicMenu menu, boolean published, Model model) {
        model.addAttribute("previewPublished", published);
        return render(menu, true, model);
    }

    /** Page « menu indisponible » — servie avec un 404, sans révéler la cause. */
    public String renderUnavailable(Model model) {
        return "menu/unavailable";
    }

    private String render(PublicMenu menu, boolean preview, Model model) {
        model.addAttribute("menu", menu);
        model.addAttribute("theme", menu.theme());
        model.addAttribute("preview", preview);
        return TEMPLATE;
    }
}
