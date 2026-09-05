package com.qrmenu.qrcode;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;

import static com.google.zxing.DecodeHintType.TRY_HARDER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que la mention {@code kartaqr.fr} est bien intégrée aux exports PNG et SVG, sans
 * casser la lisibilité du QR ni empiéter sur les modules.
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

    private static String decode(BufferedImage image) throws Exception {
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        Map<DecodeHintType, Object> hints = Map.of(TRY_HARDER, Boolean.TRUE);
        Result result = new MultiFormatReader().decode(bitmap, hints);
        return result.getText();
    }
}
