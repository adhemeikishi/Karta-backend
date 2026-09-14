package com.qrmenu.render;

import com.qrmenu.menu.MenuLanguage;

/**
 * Les quelques libellés fixes du gabarit public, dans chaque langue de la carte.
 * Tout le reste du texte vient du restaurateur (et de ses traductions).
 */
public record MenuLabels(String eyebrow, String unavailable, String empty, String languages) {

    public static MenuLabels of(MenuLanguage language) {
        return switch (language) {
            case EN -> new MenuLabels("Menu", "Unavailable", "The menu will be available very soon.", "Languages");
            case ES -> new MenuLabels("Carta", "No disponible", "La carta estará disponible muy pronto.", "Idiomas");
            case ZH -> new MenuLabels("菜单", "暂无供应", "菜单即将上线。", "语言");
            default -> new MenuLabels("Menu", "Indisponible", "Le menu sera disponible très prochainement.", "Langues");
        };
    }
}
