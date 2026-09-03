package com.qrmenu.render;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Manipulation des couleurs du thème de menu, en {@code #RRGGBB} uniquement.
 *
 * Sert à <strong>dériver</strong> les couleurs secondaires (surface, filet, texte
 * atténué) à partir des trois couleurs qu'un restaurateur choisit réellement : fond,
 * accent, texte. Sans cette dérivation, chaque preset devrait déclarer huit couleurs
 * et une personnalisation PREMIUM pourrait produire un menu illisible.
 */
public final class HexColor {

    /** Une couleur de thème est toujours un hexadécimal complet, jamais un mot-clé CSS. */
    public static final Pattern PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private HexColor() {
    }

    public static boolean isValid(String hex) {
        return hex != null && PATTERN.matcher(hex).matches();
    }

    /** Normalise en majuscules, ou {@code null} si la valeur n'est pas exploitable. */
    public static String normalize(String hex) {
        return isValid(hex) ? hex.toUpperCase(Locale.ROOT) : null;
    }

    /**
     * Mélange {@code from} vers {@code to}. {@code ratio} = 0 rend {@code from},
     * 1 rend {@code to}. Interpolation linéaire simple : suffisante et prévisible
     * pour des filets et des aplats discrets.
     */
    public static String mix(String from, String to, double ratio) {
        int[] a = rgb(from);
        int[] b = rgb(to);
        double t = Math.min(1, Math.max(0, ratio));
        return format(
                (int) Math.round(a[0] + (b[0] - a[0]) * t),
                (int) Math.round(a[1] + (b[1] - a[1]) * t),
                (int) Math.round(a[2] + (b[2] - a[2]) * t));
    }

    /**
     * Couleur de texte lisible sur {@code background} : blanc sur un fond sombre,
     * charcoal sur un fond clair.
     *
     * C'est le garde-fou de la personnalisation PREMIUM : quelle que soit la couleur
     * choisie, le texte reste lisible — on ne peut pas publier un menu blanc sur blanc.
     */
    public static String readableOn(String background) {
        return luminance(background) > 0.5 ? "#131312" : "#FFFFFF";
    }

    /**
     * Luminance relative perçue (WCAG), entre 0 (noir) et 1 (blanc).
     * Utilisée uniquement pour choisir entre deux textes, jamais pour un calcul de
     * conformité affiché.
     */
    public static double luminance(String hex) {
        int[] rgb = rgb(hex);
        return 0.2126 * channel(rgb[0]) + 0.7152 * channel(rgb[1]) + 0.0722 * channel(rgb[2]);
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int[] rgb(String hex) {
        if (!isValid(hex)) {
            throw new IllegalArgumentException("Couleur hexadécimale invalide: " + hex);
        }
        return new int[]{
                Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16)
        };
    }

    private static String format(int r, int g, int b) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", clamp(r), clamp(g), clamp(b));
    }

    private static int clamp(int value) {
        return Math.min(255, Math.max(0, value));
    }
}
