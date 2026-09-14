package com.qrmenu.qrcode;

import java.util.Locale;

/**
 * Apparence <strong>résolue</strong> d'un QR, prête à dessiner : tout ce que
 * {@link QrImageGenerator} a besoin de savoir, rien de plus — aucune notion d'offre,
 * d'asset ou de restaurant.
 *
 * @param fgColor     couleur des modules ({@code #RRGGBB})
 * @param bgColor     fond ({@code #RRGGBB})
 * @param moduleStyle forme des modules
 * @param eyeStyle    forme des yeux
 * @param logo        octets de l'image posée au centre, ou {@code null}
 * @param logoType    type MIME du logo ({@code image/png}…), ou {@code null}
 * @param caption     vrai pour peindre la mention Karta sous le QR
 */
public record QrStyle(
        String fgColor,
        String bgColor,
        QrModuleStyle moduleStyle,
        QrEyeStyle eyeStyle,
        byte[] logo,
        String logoType,
        boolean caption
) {

    /** Noir sur blanc, modules carrés, mention Karta : le QR des offres BASIC et PRO. */
    public static final QrStyle DEFAULT =
            new QrStyle("#000000", "#FFFFFF", QrModuleStyle.SQUARE, QrEyeStyle.SQUARE, null, null, true);

    /** Contraste minimal (WCAG) entre modules et fond. En dessous, les lecteurs décrochent. */
    static final double MIN_CONTRAST = 4.0;

    public QrStyle {
        fgColor = fgColor == null ? DEFAULT.fgColor : fgColor.toUpperCase(Locale.ROOT);
        bgColor = bgColor == null ? DEFAULT.bgColor : bgColor.toUpperCase(Locale.ROOT);
        moduleStyle = moduleStyle == null ? QrModuleStyle.SQUARE : moduleStyle;
        eyeStyle = eyeStyle == null ? QrEyeStyle.SQUARE : eyeStyle;
    }

    public boolean hasLogo() {
        return logo != null && logo.length > 0;
    }

    /**
     * Un QR se lit sombre sur clair : modules nettement plus foncés que le fond, avec un
     * contraste suffisant. C'est la seule règle qui borne la personnalisation — le reste
     * (formes, logo) est couvert par la correction d'erreur.
     */
    public static boolean isScannable(String fgColor, String bgColor) {
        double fg = luminance(fgColor);
        double bg = luminance(bgColor);
        return fg < bg && (bg + 0.05) / (fg + 0.05) >= MIN_CONTRAST;
    }

    // ponytail: même formule que render.HexColor.luminance — recopiée pour que qrcode ne
    // dépende pas de render (qui dépend déjà de qrcode).
    private static double luminance(String hex) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
