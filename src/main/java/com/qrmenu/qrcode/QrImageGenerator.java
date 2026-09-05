package com.qrmenu.qrcode;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Génère l'image du QR à la demande (jamais stockée en base - voir §11 du contexte projet).
 * Le QR encode toujours l'URL de redirection de notre système, jamais la destination finale :
 *   https://{qr.base-url}/{qr.redirect-path}/{code}
 *
 * <p><strong>Point unique de rendu.</strong> PNG, SVG et impression (le frontend imprime le
 * PNG produit ici) passent tous par {@link #toBufferedImage} / {@link #toSvg} : la mention
 * {@value #CAPTION} sous le QR est donc ajoutée automatiquement à tous les formats, il est
 * impossible de l'oublier sur un chemin d'export.
 *
 * <p>La mention est peinte dans une bande blanche <em>ajoutée sous</em> la matrice : elle ne
 * recouvre jamais un module, et la quiet zone (marge de 4 modules, {@link EncodeHintType#MARGIN})
 * reste intacte. Bande et police sont dimensionnées en fraction de la largeur, donc le rendu
 * s'adapte à toute taille d'export. Le texte reprend la couleur des modules (sombre sur fond
 * clair) : il contraste toujours avec le fond, quel que soit un futur inversement des couleurs.
 */
@Service
public class QrImageGenerator {

    /** Mention de marque ajoutée sous chaque QR exporté. */
    static final String CAPTION = "kartaqr.fr";
    /** Hauteur de la bande de texte, en fraction de la largeur du QR. */
    private static final double CAPTION_BAND_RATIO = 0.09;
    /** Taille de police, en fraction de la largeur du QR. */
    private static final double CAPTION_FONT_RATIO = 0.05;

    private static final int QR_DARK = 0x000000;
    private static final int QR_LIGHT = 0xFFFFFF;

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
        return generatePng(code, defaultPngSize);
    }

    public byte[] generatePng(String code, int size) {
        try {
            BitMatrix matrix = encode(buildRedirectUrl(code), size);
            BufferedImage image = toBufferedImage(matrix);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new QrImageGenerationException("Impossible de générer le PNG du QR pour le code " + code, e);
        }
    }

    public String generateSvg(String code) {
        return generateSvg(code, defaultPngSize);
    }

    public String generateSvg(String code, int size) {
        try {
            BitMatrix matrix = encode(buildRedirectUrl(code), size);
            return toSvg(matrix);
        } catch (WriterException e) {
            throw new QrImageGenerationException("Impossible de générer le SVG du QR pour le code " + code, e);
        }
    }

    private BitMatrix encode(String content, int size) throws WriterException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 4);
        QRCodeWriter writer = new QRCodeWriter();
        return writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);
    }

    private BufferedImage toBufferedImage(BitMatrix matrix) {
        int width = matrix.getWidth();
        int qrHeight = matrix.getHeight();
        int band = (int) Math.round(width * CAPTION_BAND_RATIO);
        BufferedImage image = new BufferedImage(width, qrHeight + band, BufferedImage.TYPE_INT_RGB);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < qrHeight + band; y++) {
                boolean dark = y < qrHeight && matrix.get(x, y);
                image.setRGB(x, y, dark ? QR_DARK : QR_LIGHT);
            }
        }

        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(QR_DARK));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(10, (int) Math.round(width * CAPTION_FONT_RATIO))));
        FontMetrics fm = g.getFontMetrics();
        int textX = (width - fm.stringWidth(CAPTION)) / 2;
        int textY = qrHeight + (band - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(CAPTION, textX, textY);
        g.dispose();
        return image;
    }

    private String toSvg(BitMatrix matrix) {
        int width = matrix.getWidth();
        int qrHeight = matrix.getHeight();
        double band = width * CAPTION_BAND_RATIO;
        double fontSize = Math.max(4.0, width * CAPTION_FONT_RATIO);
        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(width).append(" ").append(qrHeight + band)
                .append("\" shape-rendering=\"crispEdges\">\n");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n");
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < qrHeight; y++) {
                if (matrix.get(x, y)) {
                    svg.append("<rect x=\"").append(x).append("\" y=\"").append(y)
                            .append("\" width=\"1\" height=\"1\" fill=\"#000000\"/>\n");
                }
            }
        }
        svg.append("<text x=\"").append(width / 2.0).append("\" y=\"").append(qrHeight + band * 0.66)
                .append("\" text-anchor=\"middle\" shape-rendering=\"auto\"")
                .append(" font-family=\"Helvetica, Arial, sans-serif\" font-size=\"").append(fontSize)
                .append("\" fill=\"#000000\">").append(CAPTION).append("</text>\n");
        svg.append("</svg>");
        return svg.toString();
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String ensureLeadingSlash(String value) {
        return value.startsWith("/") ? value : "/" + value;
    }
}
