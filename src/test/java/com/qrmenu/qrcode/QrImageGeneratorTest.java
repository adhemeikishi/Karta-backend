package com.qrmenu.qrcode;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static com.google.zxing.DecodeHintType.TRY_HARDER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que la mention {@code kartaqr.fr} est bien intégrée aux exports PNG et SVG, sans
 * casser la lisibilité du QR ni empiéter sur les modules — et que la personnalisation
 * PREMIUM (couleurs, formes, logo) laisse le QR décodable.
 */
class QrImageGeneratorTest {

    private final QrImageGenerator generator =
            new QrImageGenerator("https://kartaqr.fr", "/q", 1000);

    @Test
    void pngContainsCaptionBandAndStaysScannable() throws Exception {
        byte[] png = generator.generatePng("ABC1234567");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));

        // Bande ajoutée sous le QR : image plus haute que large, jamais l'inverse.
        assertThat(image.getHeight()).isGreaterThan(image.getWidth());

        // Le QR reste décodable, et pointe toujours sur l'URL de redirection (URL inchangée).
        assertThat(decode(image)).isEqualTo("https://kartaqr.fr/q/ABC1234567");
    }

    @Test
    void pngCaptionBandIsProportionalToExportSize() throws Exception {
        BufferedImage small = ImageIO.read(new ByteArrayInputStream(generator.generatePng("ABC1234567", 300)));
        BufferedImage large = ImageIO.read(new ByteArrayInputStream(generator.generatePng("ABC1234567", 1200)));

        double smallBandRatio = (small.getHeight() - small.getWidth()) / (double) small.getWidth();
        double largeBandRatio = (large.getHeight() - large.getWidth()) / (double) large.getWidth();
        assertThat(smallBandRatio).isCloseTo(largeBandRatio, org.assertj.core.data.Offset.offset(0.02));
    }

    @Test
    void svgContainsCaptionOutsideTheModuleArea() {
        String svg = generator.generateSvg("ABC1234567");

        assertThat(svg).contains(">kartaqr.fr</text>");
        assertThat(svg).contains("text-anchor=\"middle\"");

        // La zone de dessin est plus haute que la matrice : le texte est sous les modules.
        int viewBoxHeight = Integer.parseInt(
                svg.replaceAll("(?s).*viewBox=\"0 0 (\\d+) ([\\d.]+)\".*", "$2").split("\\.")[0]);
        int viewBoxWidth = Integer.parseInt(
                svg.replaceAll("(?s).*viewBox=\"0 0 (\\d+) .*", "$1"));
        assertThat(viewBoxHeight).isGreaterThan(viewBoxWidth);
    }

    // ---------------------------------------------------------------- style PREMIUM

    /** Logo de test : un disque persimmon sur fond blanc, encodé en PNG. */
    private static byte[] logoPng() throws Exception {
        BufferedImage logo = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = logo.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 120, 80);
        g.setColor(new Color(0xF05A00));
        g.fillOval(30, 10, 60, 60);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(logo, "PNG", out);
        return out.toByteArray();
    }

    @Test
    void styledPngWithColorsRoundedModulesEyesAndLogoStaysScannable() throws Exception {
        for (QrModuleStyle modules : QrModuleStyle.values()) {
            for (QrEyeStyle eyes : QrEyeStyle.values()) {
                QrStyle style = new QrStyle("#012FA4", "#F6F6F6", modules, eyes, logoPng(), "image/png", false);
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(generator.generatePng("ABC1234567", 600, style)));

                // Sans mention : image carrée, aucune bande ajoutée.
                assertThat(image.getHeight()).isEqualTo(image.getWidth());
                // Le logo est bien peint (persimmon au centre), et le QR reste lisible malgré lui.
                assertThat(image.getRGB(image.getWidth() / 2, image.getHeight() / 2) & 0xFFFFFF).isEqualTo(0xF05A00);
                assertThat(decode(image))
                        .as("%s / %s", modules, eyes)
                        .isEqualTo("https://kartaqr.fr/q/ABC1234567");
            }
        }
    }

    @Test
    void styledSvgUsesTheStyleAndOmitsTheCaptionWhenBrandingIsHidden() throws Exception {
        QrStyle style = new QrStyle("#131312", "#FFFFFF", QrModuleStyle.DOTS, QrEyeStyle.CIRCLE, logoPng(), "image/png", false);
        String svg = generator.generateSvg("ABC1234567", 500, style);

        assertThat(svg).contains("<circle").contains("fill=\"#131312\"").contains("<image").contains("data:image/png;base64,");
        assertThat(svg).doesNotContain("kartaqr.fr");
        assertThat(svg).contains("width=\"500\" height=\"500\"");
    }

    @Test
    void scannabilityRuleRejectsLightOrLowContrastModules() {
        assertThat(QrStyle.isScannable("#000000", "#FFFFFF")).isTrue();
        assertThat(QrStyle.isScannable("#012FA4", "#F6F6F6")).isTrue();
        assertThat(QrStyle.isScannable("#FFFFFF", "#000000")).as("inversé").isFalse();
        assertThat(QrStyle.isScannable("#FFFF00", "#FFFFFF")).as("jaune sur blanc").isFalse();
        assertThat(QrStyle.isScannable("#888888", "#AAAAAA")).as("contraste faible").isFalse();
    }

    private static String decode(BufferedImage image) throws Exception {
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        Map<DecodeHintType, Object> hints = Map.of(TRY_HARDER, Boolean.TRUE);
        Result result = new MultiFormatReader().decode(bitmap, hints);
        return result.getText();
    }
}
