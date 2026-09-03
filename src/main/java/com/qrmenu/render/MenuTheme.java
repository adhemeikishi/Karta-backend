package com.qrmenu.render;

/**
 * Thème <strong>résolu</strong> d'un menu : tout ce dont le template a besoin, déjà
 * calculé. Aucune logique de couleur ne subsiste dans le HTML.
 *
 * Produit par {@link MenuThemeResolver} à partir du preset choisi et — pour l'offre
 * PREMIUM seulement — de la personnalisation du client. C'est le même objet qui alimente
 * la page publique et l'aperçu du back-office : ce que le restaurateur voit dans le
 * studio est exactement ce que ses clients verront.
 *
 * @param preset      identifiant stable ({@code modern}, {@code street_food}, ...)
 * @param presetLabel libellé affichable
 * @param density     rythme vertical, appliqué comme classe sur {@code <body>}
 * @param fontStack   pile typographique complète (aucune police distante)
 * @param background  fond de page
 * @param surface     fond des blocs posés sur la page
 * @param border      filets et séparateurs
 * @param text        texte principal
 * @param muted       texte secondaire (descriptions, mentions)
 * @param accent      détails : filets d'accent, prix, pastilles
 * @param accentText  texte lisible posé sur {@code accent}
 * @param dark        vrai si le fond est sombre — pilote quelques ajustements du rendu
 * @param logoUrl     logo PREMIUM, ou {@code null}
 * @param heroUrl     image d'en-tête PREMIUM, ou {@code null}
 */
public record MenuTheme(
        String preset,
        String presetLabel,
        String density,
        String fontStack,
        String background,
        String surface,
        String border,
        String text,
        String muted,
        String accent,
        String accentText,
        boolean dark,
        String logoUrl,
        String heroUrl
) {
}
