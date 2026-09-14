package com.qrmenu.menu;

import java.util.List;
import java.util.UUID;

/**
 * Apparence choisie pour un menu : un preset, et — offre PREMIUM uniquement — l'identité
 * du restaurant.
 *
 * Volontairement plat : des champs nullables, pas de {@code theme_json}, pas de moteur
 * de thème. PREMIUM veut poser <em>son</em> identité (nom, logo, deux couleurs, une image,
 * une typographie, son QR, ses langues, sans la marque Karta), pas régler des espacements.
 *
 * Le design ne fait jamais partie du contenu : changer de preset ne touche à aucune
 * catégorie, à aucun prix, à aucun produit.
 *
 * @param preset         style de base, jamais {@code null}
 * @param brandName      nom affiché en tête du menu, ou {@code null} pour le nom du client
 * @param primaryColor   remplace l'accent du preset ({@code #RRGGBB}), ou {@code null}
 * @param secondaryColor remplace le fond du preset ({@code #RRGGBB}), ou {@code null}
 * @param logoAssetId    image de logo dans {@code media_assets}, ou {@code null}
 * @param heroAssetId    image d'en-tête dans {@code media_assets}, ou {@code null}
 * @param hideBranding   retire la marque Karta de la carte et du QR ; {@code null} = non
 *                       (nullable pour que {@link #mergedWith} sache « pas de surcharge »)
 * @param font           typographie choisie, ou {@code null} pour celle du preset
 * @param languages      langues activées en plus du français ; {@code null} = aucune
 * @param qr             apparence du QR, jamais {@code null}
 */
public record MenuDesign(
        MenuPreset preset,
        String brandName,
        String primaryColor,
        String secondaryColor,
        UUID logoAssetId,
        UUID heroAssetId,
        Boolean hideBranding,
        MenuFont font,
        List<MenuLanguage> languages,
        QrDesign qr
) {

    public MenuDesign {
        qr = qr == null ? QrDesign.DEFAULT : qr;
    }

    /** Design d'un client qui n'a encore rien choisi (ou qui n'a pas encore de menu). */
    public static MenuDesign defaults() {
        return new MenuDesign(MenuPreset.DEFAULT, null, null, null, null, null, null, null, null, null);
    }

    /**
     * Design réduit à son preset. Appliqué aux offres non PREMIUM : la personnalisation
     * reste stockée (une rétrogradation ne détruit rien) mais n'est ni rendue, ni publiée.
     */
    public MenuDesign presetOnly() {
        return new MenuDesign(preset, null, null, null, null, null, null, null, null, null);
    }

    public boolean isBrandingHidden() {
        return Boolean.TRUE.equals(hideBranding);
    }

    public List<MenuLanguage> languagesOrEmpty() {
        return languages == null ? List.of() : languages;
    }

    /** Fusionne des valeurs d'aperçu non enregistrées par-dessus le design courant. */
    public MenuDesign mergedWith(MenuDesign overrides) {
        if (overrides == null) {
            return this;
        }
        return new MenuDesign(
                overrides.preset() != null ? overrides.preset() : preset,
                overrides.brandName() != null ? overrides.brandName() : brandName,
                overrides.primaryColor() != null ? overrides.primaryColor() : primaryColor,
                overrides.secondaryColor() != null ? overrides.secondaryColor() : secondaryColor,
                overrides.logoAssetId() != null ? overrides.logoAssetId() : logoAssetId,
                overrides.heroAssetId() != null ? overrides.heroAssetId() : heroAssetId,
                overrides.hideBranding() != null ? overrides.hideBranding() : hideBranding,
                overrides.font() != null ? overrides.font() : font,
                overrides.languages() != null ? overrides.languages() : languages,
                qr.mergedWith(overrides.qr()));
    }
}
