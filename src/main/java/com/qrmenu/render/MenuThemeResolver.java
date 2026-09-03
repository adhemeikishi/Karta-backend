package com.qrmenu.render;

import com.qrmenu.common.PublicUrlBuilder;
import com.qrmenu.menu.MenuDesign;
import com.qrmenu.menu.MenuPreset;
import com.qrmenu.restaurant.RestaurantOffer;
import org.springframework.stereotype.Component;

/**
 * Transforme un {@link MenuDesign} enregistré en {@link MenuTheme} prêt à rendre.
 *
 * Point unique de résolution : la page publique et l'aperçu du back-office passent tous
 * les deux par ici, donc ce que le restaurateur voit dans le studio est exactement ce que
 * ses clients verront. Une divergence d'apparence entre aperçu et public serait un bug.
 *
 * Deux garanties portées ici, et nulle part ailleurs :
 * <ol>
 *   <li>la personnalisation est réservée à PREMIUM — elle est ignorée pour les autres
 *       offres, y compris en aperçu, plutôt que masquée seulement côté interface ;</li>
 *   <li>le texte est toujours lisible : sa couleur est dérivée du fond réellement
 *       appliqué, donc aucune combinaison PREMIUM ne peut produire un menu illisible.</li>
 * </ol>
 */
@Component
public class MenuThemeResolver {

    private final PublicUrlBuilder urlBuilder;

    public MenuThemeResolver(PublicUrlBuilder urlBuilder) {
        this.urlBuilder = urlBuilder;
    }

    /**
     * Design réellement appliqué pour une offre donnée. La personnalisation reste
     * stockée en base pour une offre non PREMIUM (une rétrogradation ne détruit rien),
     * mais elle n'est ni rendue ni publiée.
     */
    public MenuDesign effectiveDesign(MenuDesign design, RestaurantOffer offer) {
        MenuDesign value = design == null ? MenuDesign.defaults() : design;
        return offer == RestaurantOffer.PREMIUM ? value : value.presetOnly();
    }

    public MenuTheme resolve(MenuDesign design, RestaurantOffer offer) {
        MenuDesign effective = effectiveDesign(design, offer);
        MenuPreset preset = effective.preset() == null ? MenuPreset.DEFAULT : effective.preset();

        String background = firstValid(effective.secondaryColor(), preset.background());
        String accent = firstValid(effective.primaryColor(), preset.accent());

        // Le texte du preset n'est conservé que si le fond n'a pas été remplacé : dès que
        // PREMIUM impose son propre fond, on redérive un texte lisible dessus.
        String text = effective.secondaryColor() == null
                ? preset.text()
                : HexColor.readableOn(background);

        return new MenuTheme(
                preset.id(),
                preset.label(),
                preset.density().id(),
                preset.typeface().stack(),
                background,
                HexColor.mix(background, text, 0.05),
                HexColor.mix(background, text, 0.16),
                text,
                HexColor.mix(text, background, 0.42),
                accent,
                HexColor.readableOn(accent),
                HexColor.luminance(background) <= 0.5,
                effective.logoAssetId() == null ? null : urlBuilder.forAsset(effective.logoAssetId()),
                effective.heroAssetId() == null ? null : urlBuilder.forAsset(effective.heroAssetId()));
    }

    private static String firstValid(String candidate, String fallback) {
        String normalized = HexColor.normalize(candidate);
        return normalized == null ? fallback : normalized;
    }
}
