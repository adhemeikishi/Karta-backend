package com.qrmenu.menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Langues de la carte. Le français est la langue de base — celle des champs
 * {@code name} / {@code description} — et n'est jamais « activée » : elle est toujours là.
 * Les autres se stockent en codes ISO 639-1 séparés par des virgules ({@code en,es}).
 */
public enum MenuLanguage {

    FR("fr", "FR", "Français"),
    EN("en", "EN", "English"),
    ES("es", "ES", "Español"),
    ZH("zh", "中文", "中文");

    public static final MenuLanguage BASE = FR;

    private final String code;
    private final String shortLabel;
    private final String label;

    MenuLanguage(String code, String shortLabel, String label) {
        this.code = code;
        this.shortLabel = shortLabel;
        this.label = label;
    }

    /** Code ISO, aussi valeur de {@code <html lang>} et du paramètre {@code ?lang=}. */
    public String code() {
        return code;
    }

    /** Libellé du sélecteur public (« FR », « 中文 »). */
    public String shortLabel() {
        return shortLabel;
    }

    public String label() {
        return label;
    }

    /** {@code null} si le code est inconnu — un {@code ?lang=xx} fantaisiste retombe sur FR. */
    public static MenuLanguage fromCode(String raw) {
        if (raw == null) {
            return null;
        }
        String code = raw.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(l -> l.code.equals(code)).findFirst().orElse(null);
    }

    /** « en,es » → [EN, ES]. Codes inconnus et FR ignorés, doublons écartés, ordre du catalogue. */
    public static List<MenuLanguage> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return normalize(Arrays.stream(csv.split(",")).map(MenuLanguage::fromCode).toList());
    }

    /** Même règle que {@link #parse}, depuis une liste déjà typée (requête JSON). */
    public static List<MenuLanguage> normalize(List<MenuLanguage> requested) {
        List<MenuLanguage> result = new ArrayList<>();
        if (requested == null) {
            return result;
        }
        for (MenuLanguage language : values()) {
            if (language != BASE && requested.contains(language)) {
                result.add(language);
            }
        }
        return result;
    }

    /** Inverse de {@link #parse} ; {@code null} pour une liste vide (la colonne reste NULL). */
    public static String format(List<MenuLanguage> languages) {
        List<MenuLanguage> normalized = normalize(languages);
        if (normalized.isEmpty()) {
            return null;
        }
        return String.join(",", normalized.stream().map(MenuLanguage::code).toList());
    }
}
