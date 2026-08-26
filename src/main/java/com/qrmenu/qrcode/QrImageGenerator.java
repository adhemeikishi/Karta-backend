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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Génère l'image du QR à la demande (jamais stockée en base - voir §11 du contexte projet).
 * Le QR encode toujours l'URL de redirection de notre système, jamais la destination finale :
 *   https://{qr.base-url}/{qr.redirect-path}/{code}
 */
@Service
public class QrImageGenerator {

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
            java.awt.image.BufferedImage image = toBufferedImage(matrix);
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

    private java.awt.image.BufferedImage toBufferedImage(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
            }
        }
        return image;
    }

    private String toSvg(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
                .append(width).append(" ").append(height)
                .append("\" shape-rendering=\"crispEdges\">\n");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n");
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (matrix.get(x, y)) {
                    svg.append("<rect x=\"").append(x).append("\" y=\"").append(y)
                            .append("\" width=\"1\" height=\"1\" fill=\"#000000\"/>\n");
                }
            }
        }
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
