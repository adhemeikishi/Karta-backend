package com.qrmenu.menu;

/**
 * Typographies proposées à l'offre PREMIUM.
 *
 * Catalogue fermé : jamais une police libre saisie par le client. Chaque entrée est une
 * famille Google Fonts chargée avec {@code display=swap} — le texte s'affiche tout de
 * suite dans la pile de repli, puis bascule. Sans choix ({@code null} en base), le menu
 * garde la pile système du preset et ne charge <strong>aucune</strong> police distante :
 * le budget réseau du menu par défaut ne bouge pas.
 */
public enum MenuFont {

    PLUS_JAKARTA_SANS("Plus Jakarta Sans", "Plus+Jakarta+Sans:wght@400;500;600;700", MenuPreset.Typeface.SANS),
    DM_SANS("DM Sans", "DM+Sans:wght@400;500;600;700", MenuPreset.Typeface.SANS),
    SPACE_GROTESK("Space Grotesk", "Space+Grotesk:wght@400;500;600;700", MenuPreset.Typeface.SANS),
    PLAYFAIR_DISPLAY("Playfair Display", "Playfair+Display:wght@400;500;600;700", MenuPreset.Typeface.SERIF),
    LORA("Lora", "Lora:wght@400;500;600;700", MenuPreset.Typeface.SERIF),
    INSTRUMENT_SERIF("Instrument Serif", "Instrument+Serif", MenuPreset.Typeface.SERIF);

    private final String family;
    private final String query;
    private final MenuPreset.Typeface fallback;

    MenuFont(String family, String query, MenuPreset.Typeface fallback) {
        this.family = family;
        this.query = query;
        this.fallback = fallback;
    }

    /** Nom affiché dans le studio. */
    public String label() {
        return family;
    }

    /** Pile CSS complète : la famille, puis le repli système de même registre. */
    public String stack() {
        return "\"" + family + "\", " + fallback.stack();
    }

    /** Feuille de style à charger. Une seule requête, `swap` : jamais de texte invisible. */
    public String stylesheetUrl() {
        return "https://fonts.googleapis.com/css2?family=" + query + "&display=swap";
    }
}
