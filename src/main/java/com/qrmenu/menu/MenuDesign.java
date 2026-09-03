package com.qrmenu.menu;

import java.util.UUID;

/**
 * Apparence choisie pour un menu : un preset, et — offre PREMIUM uniquement — l'identité
 * du restaurant.
 *
 * Volontairement plat : cinq champs nullables, pas de {@code theme_json}, pas de moteur
 * de thème. PREMIUM veut poser <em>son</em> identité (nom, logo, deux couleurs, une image),
 * pas régler des espacements.
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
 */
public record MenuDesign(
        MenuPreset preset,
        String brandName,
        String primaryColor,
        String secondaryColor,
        UUID logoAssetId,
        UUID heroAssetId
) {

    /** Design d'un client qui n'a encore rien choisi (ou qui n'a pas encore de menu). */
    public static MenuDesign defaults() {
        return new MenuDesign(MenuPreset.DEFAULT, null, null, null, null, null);
    }

    /**
     * Design réduit à son preset. Appliqué aux offres non PREMIUM : la personnalisation
     * reste stockée (une rétrogradation ne détruit rien) mais n'est ni rendue, ni publiée.
     */
    public MenuDesign presetOnly() {
        return new MenuDesign(preset, null, null, null, null, null);
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
                overrides.heroAssetId() != null ? overrides.heroAssetId() : heroAssetId);
    }
}
