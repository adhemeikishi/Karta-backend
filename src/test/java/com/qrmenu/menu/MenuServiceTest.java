package com.qrmenu.menu;

import com.qrmenu.common.ConflictException;
import com.qrmenu.common.InvalidUploadException;
import com.qrmenu.common.NotFoundException;
import com.qrmenu.media.MediaService;
import com.qrmenu.menu.MenuDtos.MenuResponse;
import com.qrmenu.qrcode.QrCode;
import com.qrmenu.qrcode.QrCodeService;
import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantOffer;
import com.qrmenu.restaurant.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class MenuServiceTest {

    private static final byte[] VALID_PDF =
            "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MenuService menuService;
    @Autowired
    private RestaurantService restaurantService;
    @Autowired
    private QrCodeService qrCodeService;
    @Autowired
    private MediaService mediaService;

    private Restaurant basicRestaurant() {
        return restaurantService.create("Resto Menu " + System.nanoTime(), RestaurantOffer.BASIC);
    }

    @Test
    void getMenuReturnsSyntheticStateWhenNoMenuYet() {
        Restaurant r = basicRestaurant();

        MenuResponse menu = menuService.getMenu(r.getId());

        assertThat(menu.offer()).isEqualTo(RestaurantOffer.BASIC);
        assertThat(menu.type()).isEqualTo(MenuType.PDF);
        assertThat(menu.published()).isFalse();
        assertThat(menu.pdf()).isNull();
    }

    @Test
    void uploadPdfCreatesMenuWithPdf() {
        Restaurant r = basicRestaurant();

        MenuResponse menu = menuService.uploadPdf(r.getId(), VALID_PDF, "application/pdf", "carte.pdf");

        assertThat(menu.type()).isEqualTo(MenuType.PDF);
        assertThat(menu.published()).isFalse();
        assertThat(menu.pdf()).isNotNull();
        assertThat(menu.pdf().sizeBytes()).isEqualTo(VALID_PDF.length);
        assertThat(menu.pdf().originalFilename()).isEqualTo("carte.pdf");
        assertThat(menu.pdf().url()).endsWith("/media/" + menu.pdf().assetId());
    }

    @Test
    void rejectsNonPdfContentType() {
        Restaurant r = basicRestaurant();

        assertThatThrownBy(() -> menuService.uploadPdf(r.getId(), "hello".getBytes(), "text/plain", "x.txt"))
                .isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void rejectsFileWithoutPdfSignatureEvenIfContentTypeIsPdf() {
        Restaurant r = basicRestaurant();

        assertThatThrownBy(() ->
                menuService.uploadPdf(r.getId(), "definitely not a pdf".getBytes(), "application/pdf", "fake.pdf"))
                .isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void rejectsPdfOver10Mb() {
        Restaurant r = basicRestaurant();
        byte[] tooBig = new byte[(int) MediaService.MAX_PDF_BYTES + 1];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.UTF_8), 0, tooBig, 0, 5);

        assertThatThrownBy(() -> menuService.uploadPdf(r.getId(), tooBig, "application/pdf", "big.pdf"))
                .isInstanceOf(InvalidUploadException.class);
    }

    /**
     * Le flux PDF est ouvert aux offres PRO / PREMIUM : le PDF y est le document
     * <strong>source</strong> d'une future transformation KartaAI, pas le menu diffusé.
     * Refuser l'envoi obligerait le restaurateur à ressaisir sa carte à la main.
     * Voir {@link MenuOfferUpgradeTest} pour le parcours complet BASIC → PRO.
     */
    @Test
    void acceptsSourcePdfForNonBasicOffer() {
        Restaurant pro = restaurantService.create("Pro " + System.nanoTime(), RestaurantOffer.PRO);

        MenuResponse menu = menuService.uploadPdf(pro.getId(), VALID_PDF, "application/pdf", "x.pdf");

        assertThat(menu.pdf()).isNotNull();
        assertThat(menu.type()).isEqualTo(MenuType.STRUCTURED);
        assertThat(menu.published()).isFalse();
    }

    @Test
    void publishRequiresAPdf() {
        Restaurant r = basicRestaurant();

        assertThatThrownBy(() -> menuService.publish(r.getId())).isInstanceOf(ConflictException.class);
    }

    @Test
    void publishPointsQrToPdfAndUnpublishRestoresFallback() {
        Restaurant r = basicRestaurant();
        QrCode qr = qrCodeService.create(r.getId(), "QR", "https://example.com/old-menu.pdf");

        MenuResponse uploaded = menuService.uploadPdf(r.getId(), VALID_PDF, "application/pdf", "carte.pdf");
        MenuResponse published = menuService.publish(r.getId());

        assertThat(published.published()).isTrue();
        QrCode afterPublish = qrCodeService.getOrThrow(qr.getId());
        assertThat(afterPublish.getDestinationUrl()).endsWith("/media/" + uploaded.pdf().assetId());

        menuService.unpublish(r.getId());
        QrCode afterUnpublish = qrCodeService.getOrThrow(qr.getId());
        assertThat(afterUnpublish.getDestinationUrl()).isEqualTo("https://example.com/old-menu.pdf");
    }

    @Test
    void deletePdfRemovesAssetAndUnpublishes() {
        Restaurant r = basicRestaurant();
        qrCodeService.create(r.getId(), "QR", "https://example.com/old.pdf");
        MenuResponse uploaded = menuService.uploadPdf(r.getId(), VALID_PDF, "application/pdf", "c.pdf");
        menuService.publish(r.getId());
        UUID assetId = uploaded.pdf().assetId();

        MenuResponse afterDelete = menuService.deletePdf(r.getId());

        assertThat(afterDelete.pdf()).isNull();
        assertThat(afterDelete.published()).isFalse();
        assertThatThrownBy(() -> mediaService.getOrThrow(assetId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void replacingPdfKeepsSingleAssetAndRequiresRepublish() {
        Restaurant r = basicRestaurant();
        MenuResponse first = menuService.uploadPdf(r.getId(), VALID_PDF, "application/pdf", "v1.pdf");
        menuService.publish(r.getId());

        MenuResponse second = menuService.uploadPdf(r.getId(), VALID_PDF, "application/pdf", "v2.pdf");

        assertThat(second.pdf().assetId()).isNotEqualTo(first.pdf().assetId());
        assertThat(second.published()).isFalse();
        assertThatThrownBy(() -> mediaService.getOrThrow(first.pdf().assetId()))
                .isInstanceOf(NotFoundException.class);
    }
}
