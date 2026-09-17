package com.qrmenu.render;

import com.qrmenu.common.PublicUrlBuilder;
import com.qrmenu.media.MediaAsset;
import com.qrmenu.media.MediaService;
import com.qrmenu.menu.MenuDesign;
import com.qrmenu.menu.MenuPreset;
import com.qrmenu.menu.QrDesign;
import com.qrmenu.qrcode.QrStyle;
import com.qrmenu.restaurant.RestaurantOffer;
import org.springframework.stereotype.Component;

/**
 * Transforme un {@link MenuDesign} enregistré en {@link MenuTheme} prêt à rendre — et en
 * {@link QrStyle} prêt à dessiner.
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
    private final MediaService mediaService;

    public MenuThemeResolver(PublicUrlBuilder urlBuilder, MediaService mediaService) {
        this.urlBuilder = urlBuilder;
        this.mediaService = mediaService;
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
                effective.font() == null ? preset.typeface().stack() : effective.font().stack(),
                effective.font() == null ? null : effective.font().stylesheetUrl(),
                background,
                HexColor.mix(background, text, 0.05),
                HexColor.mix(background, text, 0.16),
                text,
                HexColor.mix(text, background, 0.42),
                accent,
                HexColor.readableOn(accent),
                HexColor.luminance(background) <= 0.5,
                effective.logoAssetId() == null ? null : urlBuilder.forAsset(effective.logoAssetId()),
                effective.heroAssetId() == null ? null : urlBuilder.forAsset(effective.heroAssetId()),
                !effective.isBrandingHidden(),
                preset.density() == MenuPreset.Density.ELEGANT);
    }

    /**
     * Apparence du QR pour une offre donnée. Même gate que le menu : hors PREMIUM, le QR
     * est celui de toujours (noir sur blanc, mention Karta). Le logo est chargé ici — le
     * générateur ne connaît ni les assets, ni les offres.
     *
     * Des couleurs enregistrées puis devenues illisibles (impossible par l'API, mais une
     * base modifiée à la main n'est pas exclue) retombent sur le noir et blanc : un QR
     * qui ne se scanne pas ne sert à rien, quelle que soit la volonté du client.
     */
    public QrStyle resolveQr(MenuDesign design, RestaurantOffer offer) {
        MenuDesign effective = effectiveDesign(design, offer);
        QrDesign qr = effective.qr();
        String fg = firstValid(qr.fgColor(), QrStyle.DEFAULT.fgColor());
        String bg = firstValid(qr.bgColor(), QrStyle.DEFAULT.bgColor());
        if (!QrStyle.isScannable(fg, bg)) {
            fg = QrStyle.DEFAULT.fgColor();
            bg = QrStyle.DEFAULT.bgColor();
        }
        MediaAsset logo = qr.logoAssetId() == null ? null : mediaService.getOrThrow(qr.logoAssetId());
        return new QrStyle(
                fg,
                bg,
                qr.moduleStyle(),
                qr.eyeStyle(),
                logo == null ? null : mediaService.readContent(logo),
                logo == null ? null : logo.getContentType(),
                !effective.isBrandingHidden());
    }

    private static String firstValid(String candidate, String fallback) {
        String normalized = HexColor.normalize(candidate);
        return normalized == null ? fallback : normalized;
    }
}
