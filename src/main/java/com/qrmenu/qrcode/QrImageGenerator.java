package com.qrmenu.qrcode;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Génère l'image du QR à la demande (jamais stockée en base - voir §11 du contexte projet).
 * Le QR encode toujours l'URL de redirection de notre système, jamais la destination finale :
 *   https://{qr.base-url}/{qr.redirect-path}/{code}
 *
 * <p><strong>Point unique de rendu.</strong> PNG, SVG et impression (le frontend imprime le
 * PNG produit ici) passent tous par {@link #toBufferedImage} / {@link #toSvg}, pilotés par
 * un {@link QrStyle} déjà résolu. Le style par défaut ({@link QrStyle#DEFAULT}) reproduit le
 * QR historique : noir sur blanc, modules carrés, mention {@value #CAPTION} dessous.
 *
 * <p>Le rendu se fait <em>module par module</em> (et non pixel par pixel) : c'est ce qui
 * permet des modules arrondis, des yeux ronds et un logo, sans toucher à ce qui rend le QR
 * lisible — la quiet zone de {@value #QUIET_ZONE} modules, les trois yeux au bon ratio, et
 * une correction d'erreur relevée à H dès qu'un logo recouvre le centre.
 *
 * <p>La mention est peinte dans une bande <em>ajoutée sous</em> la matrice : elle ne
 * recouvre jamais un module. Bande et police sont dimensionnées en fraction de la largeur,
 * donc le rendu s'adapte à toute taille d'export. Le texte reprend la couleur des modules :
 * il contraste toujours avec le fond.
 */
@Service
public class QrImageGenerator {

    /** Mention de marque ajoutée sous chaque QR exporté (sauf option PREMIUM « sans branding »). */
    static final String CAPTION = "kartaqr.fr";
    /** Hauteur de la bande de texte, en fraction de la largeur du QR. */
    private static final double CAPTION_BAND_RATIO = 0.09;
    /** Taille de police, en fraction de la largeur du QR. */
    private static final double CAPTION_FONT_RATIO = 0.05;
    /** Marge blanche autour de la matrice, en modules — la norme en exige 4. */
    private static final int QUIET_ZONE = 4;
    /** Côté du logo, en fraction de la largeur : ~5 % de la surface, couvert par la correction H. */
    private static final double LOGO_RATIO = 0.22;
    /** Taille des yeux, en modules (norme QR). */
    private static final int EYE = 7;

    private final String baseUrl;
    private final String redirectPath;
    private final int defaultPngSize;

    public QrImageGenerator(
            @Value("${qr.base-url}") String baseUrl,
            @Value("${qr.redirect-path:/q}") String redirectPath,
            @Value("${qr.image.default-png-size:1000}") int defaultPngSize
    ) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.redirectPath = ensureLeadingSlash(redirectPath);
        this.defaultPngSize = defaultPngSize;
    }

    public String buildRedirectUrl(String code) {
        return baseUrl + redirectPath + "/" + code;
    }

    public byte[] generatePng(String code) {
        return generatePng(code, defaultPngSize, QrStyle.DEFAULT);
    }

    public byte[] generatePng(String code, int size) {
        return generatePng(code, size, QrStyle.DEFAULT);
    }

    public byte[] generatePng(String code, QrStyle style) {
        return generatePng(code, defaultPngSize, style);
    }

    public byte[] generatePng(String code, int size, QrStyle style) {
        try {
            BufferedImage image = toBufferedImage(encode(buildRedirectUrl(code), style), size, style);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new QrImageGenerationException("Impossible de générer le PNG du QR pour le code " + code, e);
        }
    }

    public String generateSvg(String code) {
        return generateSvg(code, defaultPngSize, QrStyle.DEFAULT);
    }

    public String generateSvg(String code, int size) {
        return generateSvg(code, size, QrStyle.DEFAULT);
    }

    public String generateSvg(String code, QrStyle style) {
        return generateSvg(code, defaultPngSize, style);
    }

    public String generateSvg(String code, int size, QrStyle style) {
        try {
            return toSvg(encode(buildRedirectUrl(code), style), size, style);
        } catch (WriterException e) {
            throw new QrImageGenerationException("Impossible de générer le SVG du QR pour le code " + code, e);
        }
    }

    // ---------------------------------------------------------------- encodage

    /** Matrice de modules brute (sans quiet zone) : la marge est dessinée par le rendu. */
    private static ByteMatrix encode(String content, QrStyle style) throws WriterException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        // Un logo masque une partie des données : H (30 %) au lieu de M (15 %).
        ErrorCorrectionLevel level = style.hasLogo() ? ErrorCorrectionLevel.H : ErrorCorrectionLevel.M;
        return Encoder.encode(content, level, hints).getMatrix();
    }

    private static boolean isDark(ByteMatrix matrix, int x, int y) {
        return matrix.get(x, y) == 1;
    }

    /** Les trois yeux sont dessinés à part : ce module leur appartient-il ? */
    private static boolean inEye(int n, int x, int y) {
        return (x < EYE && y < EYE) || (x >= n - EYE && y < EYE) || (x < EYE && y >= n - EYE);
    }

    private static int[][] eyeOrigins(int n) {
        return new int[][]{{0, 0}, {n - EYE, 0}, {0, n - EYE}};
    }

    // ---------------------------------------------------------------- PNG

    private BufferedImage toBufferedImage(ByteMatrix matrix, int size, QrStyle style) {
        int n = matrix.getWidth();
        int total = n + 2 * QUIET_ZONE;
        int scale = Math.max(1, size / total);
        int width = scale * total;
        int band = style.caption() ? (int) Math.round(width * CAPTION_BAND_RATIO) : 0;

        BufferedImage image = new BufferedImage(width, width + band, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        Color fg = Color.decode(style.fgColor());
        Color bg = Color.decode(style.bgColor());
        g.setColor(bg);
        g.fillRect(0, 0, width, width + band);
        g.setColor(fg);

        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (!isDark(matrix, x, y) || inEye(n, x, y)) {
                    continue;
                }
                int px = (QUIET_ZONE + x) * scale;
                int py = (QUIET_ZONE + y) * scale;
                switch (style.moduleStyle()) {
                    case ROUNDED -> g.fillRoundRect(px, py, scale, scale, scale / 2, scale / 2);
                    case DOTS -> g.fillOval(px + scale / 10, py + scale / 10, scale * 4 / 5, scale * 4 / 5);
                    default -> g.fillRect(px, py, scale, scale);
                }
            }
        }

        for (int[] origin : eyeOrigins(n)) {
            int ex = (QUIET_ZONE + origin[0]) * scale;
            int ey = (QUIET_ZONE + origin[1]) * scale;
            g.setColor(fg);
            fillEyeShape(g, style.eyeStyle(), ex, ey, EYE * scale, scale);
            g.setColor(bg);
            fillEyeShape(g, style.eyeStyle(), ex + scale, ey + scale, 5 * scale, scale);
            g.setColor(fg);
            fillEyeShape(g, style.eyeStyle(), ex + 2 * scale, ey + 2 * scale, 3 * scale, scale);
        }

        if (style.hasLogo()) {
            drawLogo(g, style, width, scale, bg);
        }

        if (style.caption()) {
            g.setColor(fg);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(10, (int) Math.round(width * CAPTION_FONT_RATIO))));
            FontMetrics fm = g.getFontMetrics();
            int textX = (width - fm.stringWidth(CAPTION)) / 2;
            int textY = width + (band - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(CAPTION, textX, textY);
        }
        g.dispose();
        return image;
    }

    /** Un anneau d'œil : carré, carré arrondi ou cercle, à la taille demandée. */
    private static void fillEyeShape(Graphics2D g, QrEyeStyle eyeStyle, int x, int y, int side, int scale) {
        switch (eyeStyle) {
            case CIRCLE -> g.fillOval(x, y, side, side);
            case ROUNDED -> g.fillRoundRect(x, y, side, side, scale * 2, scale * 2);
            default -> g.fillRect(x, y, side, side);
        }
    }

    /**
     * Logo centré, sur une plage dégagée d'un module autour. Une image illisible par
     * ImageIO (WebP, fichier corrompu) est ignorée : le QR sort sans logo plutôt que cassé.
     */
    private static void drawLogo(Graphics2D g, QrStyle style, int width, int scale, Color bg) {
        BufferedImage logo;
        try {
            logo = ImageIO.read(new ByteArrayInputStream(style.logo()));
        } catch (IOException e) {
            logo = null;
        }
        if (logo == null) {
            return;
        }
        int side = (int) Math.round(width * LOGO_RATIO);
        int origin = (width - side) / 2;
        g.setColor(bg);
        g.fillRect(origin - scale, origin - scale, side + 2 * scale, side + 2 * scale);

        double ratio = Math.min((double) side / logo.getWidth(), (double) side / logo.getHeight());
        int w = (int) Math.round(logo.getWidth() * ratio);
        int h = (int) Math.round(logo.getHeight() * ratio);
        g.drawImage(logo, origin + (side - w) / 2, origin + (side - h) / 2, w, h, null);
    }

    // ---------------------------------------------------------------- SVG

    /** Coordonnées en modules : le SVG se met à n'importe quelle taille sans perdre en netteté. */
    private String toSvg(ByteMatrix matrix, int size, QrStyle style) {
        int n = matrix.getWidth();
        int total = n + 2 * QUIET_ZONE;
        double band = style.caption() ? total * CAPTION_BAND_RATIO : 0;
        double fontSize = Math.max(0.5, total * CAPTION_FONT_RATIO);
        String fg = style.fgColor().toLowerCase(Locale.ROOT);
        String bg = style.bgColor().toLowerCase(Locale.ROOT);

        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" viewBox=\"0 0 ")
                .append(total).append(" ").append(fmt(total + band))
                .append("\" width=\"").append(size).append("\" height=\"").append(fmt(size * (total + band) / total))
                .append("\" shape-rendering=\"").append(style.moduleStyle() == QrModuleStyle.SQUARE ? "crispEdges" : "geometricPrecision")
                .append("\">\n");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"").append(bg).append("\"/>\n");

        svg.append("<g fill=\"").append(fg).append("\">\n");
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (!isDark(matrix, x, y) || inEye(n, x, y)) {
                    continue;
                }
                int px = QUIET_ZONE + x;
                int py = QUIET_ZONE + y;
                switch (style.moduleStyle()) {
                    case ROUNDED -> svg.append("<rect x=\"").append(px).append("\" y=\"").append(py)
                            .append("\" width=\"1\" height=\"1\" rx=\"0.25\"/>\n");
                    case DOTS -> svg.append("<circle cx=\"").append(fmt(px + 0.5)).append("\" cy=\"").append(fmt(py + 0.5))
                            .append("\" r=\"0.4\"/>\n");
                    default -> svg.append("<rect x=\"").append(px).append("\" y=\"").append(py)
                            .append("\" width=\"1\" height=\"1\"/>\n");
                }
            }
        }
        svg.append("</g>\n");

        for (int[] origin : eyeOrigins(n)) {
            int ex = QUIET_ZONE + origin[0];
            int ey = QUIET_ZONE + origin[1];
            appendEyeShape(svg, style.eyeStyle(), ex, ey, EYE, fg);
            appendEyeShape(svg, style.eyeStyle(), ex + 1, ey + 1, 5, bg);
            appendEyeShape(svg, style.eyeStyle(), ex + 2, ey + 2, 3, fg);
        }

        if (style.hasLogo() && style.logoType() != null) {
            double side = total * LOGO_RATIO;
            double origin = (total - side) / 2;
            svg.append("<rect x=\"").append(fmt(origin - 1)).append("\" y=\"").append(fmt(origin - 1))
                    .append("\" width=\"").append(fmt(side + 2)).append("\" height=\"").append(fmt(side + 2))
                    .append("\" fill=\"").append(bg).append("\"/>\n");
            svg.append("<image x=\"").append(fmt(origin)).append("\" y=\"").append(fmt(origin))
                    .append("\" width=\"").append(fmt(side)).append("\" height=\"").append(fmt(side))
                    .append("\" preserveAspectRatio=\"xMidYMid meet\"");
            // `href` (SVG 2) pour les navigateurs, `xlink:href` pour les outils d'impression plus anciens.
            String dataUri = "data:" + style.logoType() + ";base64," + Base64.getEncoder().encodeToString(style.logo());
            svg.append(" href=\"").append(dataUri).append("\" xlink:href=\"").append(dataUri).append("\"/>\n");
        }

        if (style.caption()) {
            svg.append("<text x=\"").append(fmt(total / 2.0)).append("\" y=\"").append(fmt(total + band * 0.66))
                    .append("\" text-anchor=\"middle\" shape-rendering=\"auto\"")
                    .append(" font-family=\"Helvetica, Arial, sans-serif\" font-size=\"").append(fmt(fontSize))
                    .append("\" fill=\"").append(fg).append("\">").append(CAPTION).append("</text>\n");
        }
        svg.append("</svg>");
        return svg.toString();
    }

    private static void appendEyeShape(StringBuilder svg, QrEyeStyle eyeStyle, int x, int y, int side, String fill) {
        switch (eyeStyle) {
            case CIRCLE -> svg.append("<circle cx=\"").append(fmt(x + side / 2.0)).append("\" cy=\"").append(fmt(y + side / 2.0))
                    .append("\" r=\"").append(fmt(side / 2.0)).append("\" fill=\"").append(fill).append("\"/>\n");
            case ROUNDED -> svg.append("<rect x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" width=\"").append(side).append("\" height=\"").append(side)
                    .append("\" rx=\"1\" fill=\"").append(fill).append("\"/>\n");
            default -> svg.append("<rect x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" width=\"").append(side).append("\" height=\"").append(side)
                    .append("\" fill=\"").append(fill).append("\"/>\n");
        }
    }

    /** Nombre SVG compact : « 41 » plutôt que « 41.0 », « 3.69 » plutôt que « 3.6899999 ». */
    private static String fmt(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String ensureLeadingSlash(String value) {
        return value.startsWith("/") ? value : "/" + value;
    }
}
