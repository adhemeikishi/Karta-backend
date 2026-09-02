package com.qrmenu.render;

/**
 * Habillage du menu public.
 *
 * Un seul preset existe aujourd'hui : {@code CLASSIC}, la fondation. Les suivants
 * (MODERN, BISTRO, STREET, MINIMAL) s'ajouteront en déposant un template
 * {@code templates/menu/presets/<nom>.html} et une valeur dans cet enum — sans
 * toucher au service, au DTO public ni aux contrôleurs.
 *
 * Le preset n'est volontairement pas encore persisté : tant qu'il n'y a qu'une
 * valeur possible, une colonne serait une anticipation inutile. Elle s'ajoutera
 * sur {@code menus} au moment où le choix devient réel.
 */
public enum MenuPreset {

    CLASSIC;

    public static final MenuPreset DEFAULT = CLASSIC;

    /** Nom du template Thymeleaf correspondant. */
    public String templateName() {
        return "menu/presets/" + name().toLowerCase();
    }
}
