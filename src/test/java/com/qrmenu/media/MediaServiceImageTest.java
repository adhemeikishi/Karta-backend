package com.qrmenu.media;

import com.qrmenu.common.InvalidUploadException;
import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantOffer;
import com.qrmenu.restaurant.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Stockage des images : validation réelle du contenu, puis écriture sur disque. */
@SpringBootTest
@ActiveProfiles("test")
class MediaServiceImageTest {

    @Autowired
    private MediaService mediaService;
    @Autowired
    private RestaurantService restaurantService;
    @Autowired
    private FileStorage storage;

    private Restaurant restaurant() {
        return restaurantService.create("Resto Image " + System.nanoTime(), RestaurantOffer.PRO);
    }

    @Test
    void storesPngAndWritesItToDisk() {
        Restaurant r = restaurant();

        MediaAsset asset = mediaService.storeImage(r.getId(), TestImages.PNG, "plat.png");

        assertThat(asset.getKind()).isEqualTo(MediaKind.IMAGE);
        assertThat(asset.getContentType()).isEqualTo("image/png");
        assertThat(asset.getSizeBytes()).isEqualTo(TestImages.PNG.length);
        assertThat(asset.getRestaurantId()).isEqualTo(r.getId());
        // La clé de stockage est basée sur l'UUID de l'asset, jamais sur le nom client.
        assertThat(asset.getStorageKey()).isEqualTo(r.getId() + "/" + asset.getId() + ".png");
        assertThat(storage.exists(asset.getStorageKey())).isTrue();
        assertThat(mediaService.readContent(asset)).isEqualTo(TestImages.PNG);
    }

    @Test
    void storesJpegAndWebp() {
        Restaurant r = restaurant();

        assertThat(mediaService.storeImage(r.getId(), TestImages.JPEG, "a.jpg").getContentType())
                .isEqualTo("image/jpeg");
        assertThat(mediaService.storeImage(r.getId(), TestImages.WEBP, "b.webp").getContentType())
                .isEqualTo("image/webp");
    }

    /**
     * Le cœur du durcissement : un PDF renommé {@code .png} ne doit pas passer.
     * L'extension et le {@code Content-Type} du navigateur ne sont jamais consultés.
     */
    @Test
    void rejectsPdfDisguisedAsPng() {
        Restaurant r = restaurant();

        assertThatThrownBy(() -> mediaService.storeImage(r.getId(), TestImages.PDF, "plat.png"))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("JPEG, PNG et WebP");
    }

    @Test
    void rejectsArbitraryFileRenamedAsImage() {
        Restaurant r = restaurant();

        assertThatThrownBy(() -> mediaService.storeImage(
                r.getId(), "<?php system($_GET[0]); ?>".getBytes(), "innocent.png"))
                .isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void rejectsEmptyFile() {
        Restaurant r = restaurant();

        assertThatThrownBy(() -> mediaService.storeImage(r.getId(), new byte[0], "vide.png"))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("vide");
    }

    @Test
    void rejectsImageOverMaxSize() {
        Restaurant r = restaurant();
        // Signature PNG valide mais contenu au-delà de la limite : la taille est
        // contrôlée avant la signature, le fichier ne doit pas être écrit.
        byte[] tooBig = Arrays.copyOf(TestImages.PNG, (int) MediaService.MAX_IMAGE_BYTES + 1);
        System.arraycopy(TestImages.PNG, 0, tooBig, 0, TestImages.PNG.length);

        assertThatThrownBy(() -> mediaService.storeImage(r.getId(), tooBig, "gros.png"))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("5 Mo");
    }
}
