package com.qrmenu.menu;

import com.qrmenu.common.InvalidMenuException;
import com.qrmenu.media.MediaAsset;
import com.qrmenu.media.MediaService;
import com.qrmenu.media.TestImages;
import com.qrmenu.menu.MenuDtos.MenuResponse;
import com.qrmenu.menu.MenuDtos.SaveCategoryRequest;
import com.qrmenu.menu.MenuDtos.SaveItemRequest;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Passage BASIC → PRO / PREMIUM avec une carte PDF existante.
 *
 * Le bug produit corrigé ici : après l'upgrade, le PDF du restaurateur disparaissait de
 * l'interface (« Aucun menu structuré »), l'obligeant à ressaisir sa carte à la main.
 * Le PDF doit rester présent comme <strong>source</strong> de la future transformation.
 */
@SpringBootTest
@ActiveProfiles("test")
class MenuOfferUpgradeTest {

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

    private Restaurant basicWithPdf() {
        Restaurant r = restaurantService.create("Upgrade " + System.nanoTime(), RestaurantOffer.BASIC);
        menuService.uploadPdf(r.getId(), VALID_PDF, "application/pdf", "carte.pdf");
        return r;
    }

    // ------------------------------------------------------------- CAS 1 / CAS 4

    @Test
    void pdfSurvivesUpgradeToProAndStaysVisible() {
        Restaurant r = basicWithPdf();

        restaurantService.changeOffer(r.getId(), RestaurantOffer.PRO);
        MenuResponse menu = menuService.getMenu(r.getId());

        assertThat(menu.offer()).isEqualTo(RestaurantOffer.PRO);
        assertThat(menu.pdf()).as("le PDF source doit rester exposé après l'upgrade").isNotNull();
        assertThat(menu.pdf().originalFilename()).isEqualTo("carte.pdf");
        assertThat(menu.pdf().url()).endsWith("/media/" + menu.pdf().assetId());
    }

    @Test
    void upgradeDoesNotCreateAnEmptyStructuredMenu() {
        Restaurant r = basicWithPdf();

        restaurantService.changeOffer(r.getId(), RestaurantOffer.PREMIUM);
        MenuResponse menu = menuService.getMenu(r.getId());

        // Le menu reste la carte PDF : aucun menu structuré vide n'est fabriqué.
        assertThat(menu.type()).isEqualTo(MenuType.PDF);
        assertThat(menu.structure()).isNull();
    }

    @Test
    void upgradeDoesNotBreakAPublishedPdfQrCode() {
        Restaurant r = basicWithPdf();
        QrCode qr = qrCodeService.create(r.getId(), "QR", "https://exemple.test/avant");
        menuService.publish(r.getId());
        String destinationBefore = qrCodeService.getOrThrow(qr.getId()).getDestinationUrl();

        restaurantService.changeOffer(r.getId(), RestaurantOffer.PRO);

        assertThat(qrCodeService.getOrThrow(qr.getId()).getDestinationUrl())
                .as("le QR imprimé continue de servir le PDF")
                .isEqualTo(destinationBefore);
        assertThat(menuService.getMenu(r.getId()).published()).isTrue();
    }

    /**
     * Un QR déjà imprimé doit rester pilotable après l'upgrade : avant correction,
     * dépublier levait un conflit « le menu ne correspond pas à l'offre ».
     */
    @Test
    void upgradedPdfMenuCanStillBeUnpublishedAndRepublished() {
        Restaurant r = basicWithPdf();
        qrCodeService.create(r.getId(), "QR", "https://exemple.test/avant");
        menuService.publish(r.getId());
        restaurantService.changeOffer(r.getId(), RestaurantOffer.PRO);

        assertThat(menuService.unpublish(r.getId()).published()).isFalse();
        assertThat(menuService.publish(r.getId()).published()).isTrue();
    }

    // ------------------------------------------------------------- CAS 2

    @Test
    void proRestaurantWithoutPdfCanImportOne() {
        Restaurant r = restaurantService.create("Pro import " + System.nanoTime(), RestaurantOffer.PRO);

        MenuResponse menu = menuService.uploadPdf(r.getId(), VALID_PDF, "application/pdf", "source.pdf");

        assertThat(menu.pdf()).isNotNull();
        assertThat(menu.pdf().originalFilename()).isEqualTo("source.pdf");
        // Le menu créé suit l'offre : le PDF est une source, pas le menu diffusé.
        assertThat(menu.type()).isEqualTo(MenuType.STRUCTURED);
        assertThat(menu.published()).isFalse();
    }

    @Test
    void premiumRestaurantCanRemoveItsSourcePdf() {
        Restaurant r = restaurantService.create("Prem " + System.nanoTime(), RestaurantOffer.PREMIUM);
        menuService.uploadPdf(r.getId(), VALID_PDF, "application/pdf", "source.pdf");

        assertThat(menuService.deletePdf(r.getId()).pdf()).isNull();
    }

    // ------------------------------------------------------------- CAS 3

    @Test
    void importingASourcePdfNeverUnpublishesALiveStructuredMenu() {
        Restaurant r = restaurantService.create("Struct " + System.nanoTime(), RestaurantOffer.PRO);
        qrCodeService.create(r.getId(), "QR", "https://exemple.test/avant");
        menuService.saveStructure(r.getId(), List.of(new SaveCategoryRequest(
                null, "Entrées", null, 0, true,
                List.of(new SaveItemRequest(null, "Soupe", null, 750, "EUR", null, 0, true)))));
        menuService.publish(r.getId());

        MenuResponse afterImport =
                menuService.uploadPdf(r.getId(), VALID_PDF, "application/pdf", "nouvelle-carte.pdf");

        assertThat(afterImport.published())
                .as("importer une carte source ne doit pas couper la diffusion en cours")
                .isTrue();
        assertThat(afterImport.pdf()).isNotNull();
        assertThat(afterImport.structure().categories()).hasSize(1);
    }

    @Test
    void structuredMenuStillRequiresProOrPremiumToPublish() {
        Restaurant r = restaurantService.create("Down " + System.nanoTime(), RestaurantOffer.PRO);
        menuService.saveStructure(r.getId(), List.of(new SaveCategoryRequest(
                null, "Plats", null, 0, true,
                List.of(new SaveItemRequest(null, "Steak", null, 1900, "EUR", null, 0, true)))));
        restaurantService.changeOffer(r.getId(), RestaurantOffer.BASIC);

        assertThatThrownBy(() -> menuService.publish(r.getId()))
                .hasMessageContaining("PRO et PREMIUM");
    }

    // ------------------------------------------------------------- images

    /** Un PDF ne peut pas être référencé comme photo de plat : image cassée garantie. */
    @Test
    void pdfAssetCannotBeUsedAsAnItemImage() {
        Restaurant r = restaurantService.create("Img " + System.nanoTime(), RestaurantOffer.PRO);
        MediaAsset pdfAsset = mediaService.storePdf(r.getId(), VALID_PDF, "application/pdf", "carte.pdf");

        assertThatThrownBy(() -> menuService.saveStructure(r.getId(), List.of(new SaveCategoryRequest(
                null, "Plats", null, 0, true,
                List.of(new SaveItemRequest(
                        null, "Burger", null, 1290, "EUR", pdfAsset.getId(), 0, true))))))
                .isInstanceOf(InvalidMenuException.class)
                .hasMessageContaining("n'est pas une image");
    }

    @Test
    void realImageAssetIsAcceptedAndExposedAsAPublicUrl() {
        Restaurant r = restaurantService.create("Img ok " + System.nanoTime(), RestaurantOffer.PRO);
        MediaAsset image = mediaService.storeImage(r.getId(), TestImages.PNG, "burger.png");

        MenuResponse menu = menuService.saveStructure(r.getId(), List.of(new SaveCategoryRequest(
                null, "Plats", null, 0, true,
                List.of(new SaveItemRequest(
                        null, "Burger", null, 1290, "EUR", image.getId(), 0, true)))));

        var item = menu.structure().categories().get(0).items().get(0);
        assertThat(item.imageAssetId()).isEqualTo(image.getId());
        assertThat(item.imageUrl()).endsWith("/media/" + image.getId());
    }
}
