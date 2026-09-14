package com.qrmenu.menu;

import com.qrmenu.qrcode.QrEyeStyle;
import com.qrmenu.qrcode.QrModuleStyle;

import java.util.UUID;

/**
 * Apparence du QR choisie par le client (offre PREMIUM). Tout est nullable :
 * {@code null} = valeur par défaut (noir sur blanc, modules carrés, pas de logo).
 *
 * Volontairement une référence d'asset et non des octets : c'est le résolveur de rendu
 * qui charge le logo, au moment de dessiner.
 */
public record QrDesign(
        String fgColor,
        String bgColor,
        QrModuleStyle moduleStyle,
        QrEyeStyle eyeStyle,
        UUID logoAssetId
) {

    public static final QrDesign DEFAULT = new QrDesign(null, null, null, null, null);

    public QrDesign mergedWith(QrDesign overrides) {
        if (overrides == null) {
            return this;
        }
        return new QrDesign(
                overrides.fgColor != null ? overrides.fgColor : fgColor,
                overrides.bgColor != null ? overrides.bgColor : bgColor,
                overrides.moduleStyle != null ? overrides.moduleStyle : moduleStyle,
                overrides.eyeStyle != null ? overrides.eyeStyle : eyeStyle,
                overrides.logoAssetId != null ? overrides.logoAssetId : logoAssetId);
    }
}
