package com.qrmenu.menu;

import java.util.Locale;

/**
 * Habillage du menu public : les cinq styles proposés au restaurateur.
 *
 * Un preset n'est <strong>que</strong> de l'apparence — il ne touche jamais au JSON du
 * menu. Le même contenu rendu avec {@code MODERN} ou {@code LUXE} produit exactement les
 * mêmes catégories et les mêmes prix ; seules les couleurs et la densité changent.
 *
 * Ajouter un preset = ajouter une valeur ici. Aucun template supplémentaire, aucun
 * service à modifier : {@code MenuThemeResolver} en dérive les couleurs secondaires et
 * {@code templates/menu/menu.html} rend n'importe quel thème.
 */
public enum MenuPreset {

    /** Contemporain, clair, identité Karta. */
    MODERN("Modern", "#FFFFFF", "#F05A00", "#131312", Density.EDITORIAL, Typeface.SANS),

    /** Sombre, tech, contraste fort. */
    DARK("Dark", "#131312", "#012FA4", "#FFFFFF", Density.EDITORIAL, Typeface.SANS),

    /** Énergique, compact, lecture rapide au comptoir. */
    STREET_FOOD("Street Food", "#131312", "#DC2626", "#FFFFFF", Density.COMPACT, Typeface.SANS),

    /** Très épuré : beaucoup d'air, la typographie porte tout. */
    MINIMAL("Minimal", "#FFFFFF", "#131312", "#131312", Density.AIRY, Typeface.SANS),

    /** Élégant : noir, or et crème, empattements. */
    LUXE("Luxe", "#131312", "#C9A96E", "#F5EDD8", Density.ELEGANT, Typeface.SERIF);

    /** Preset appliqué tant que le restaurateur n'a rien choisi. */
    public static final MenuPreset DEFAULT = MODERN;

    private final String label;
    private final String background;
    private final String accent;
    private final String text;
    private final Density density;
    private final Typeface typeface;

    MenuPreset(String label, String background, String accent, String text, Density density, Typeface typeface) {
        this.label = label;
        this.background = background;
        this.accent = accent;
        this.text = text;
        this.density = density;
        this.typeface = typeface;
    }

    /** Nom affiché dans le back-office. */
    public String label() {
        return label;
    }

    /** Fond de la page. */
    public String background() {
        return background;
    }

    /** Couleur des détails : filets, prix, éléments d'accentuation. */
    public String accent() {
        return accent;
    }

    /** Couleur du texte principal. */
    public String text() {
        return text;
    }

    public Density density() {
        return density;
    }

    public Typeface typeface() {
        return typeface;
    }

    /** Identifiant stable côté client et dans le HTML rendu ({@code street_food}). */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Rythme vertical du rendu. Porté par une classe sur {@code <body>} : une seule
     * feuille de style, cinq densités.
     */
    public enum Density {
        /** Colonne lisible, rythme standard. */
        EDITORIAL,
        /** Serré, pensé pour parcourir vite une carte de street food. */
        COMPACT,
        /** Très aéré, typographie dominante. */
        AIRY,
        /** Espacé et centré, registre haut de gamme. */
        ELEGANT;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Famille typographique. Aucune police distante : le menu se charge en 4G à table. */
    public enum Typeface {
        SANS("-apple-system, BlinkMacSystemFont, \"Segoe UI\", Roboto, Helvetica, Arial, "
                + "\"Noto Sans\", sans-serif"),
        SERIF("\"Iowan Old Style\", \"Palatino Linotype\", Palatino, Georgia, "
                + "\"Times New Roman\", serif");

        private final String stack;

        Typeface(String stack) {
            this.stack = stack;
        }

        public String stack() {
            return stack;
        }
    }
}
