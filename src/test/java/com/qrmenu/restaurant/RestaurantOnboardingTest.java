package com.qrmenu.restaurant;

import com.qrmenu.media.MediaService;
import com.qrmenu.menu.MenuService;
import com.qrmenu.qrcode.QrCodeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fin d'onboarding du restaurateur.
 *
 * <p>C'est le backend qui en décide, à la première publication : le frontend ne peut ni
 * l'oublier, ni la simuler, et le restaurateur retrouve son espace configuré depuis
 * n'importe quel appareil — ce qu'un drapeau posé dans le navigateur ne permettrait pas.
 */
@SpringBootTest
@ActiveProfiles("test")
class RestaurantOnboardingTest {

    private static final byte[] VALID_PDF =
            "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private RestaurantService restaurantService;
    @Autowired
    private MenuService menuService;
    @Autowired
    private QrCodeService qrCodeService;
    @Autowired
    private MediaService mediaService;
    @Autowired
    private RestaurantRepository restaurantRepository;

    private Restaurant newRestaurant() {
        Restaurant restaurant =
                restaurantService.create("Resto Onboarding " + System.nanoTime(), RestaurantOffer.BASIC);
        qrCodeService.ensureQrCode(restaurant);
        return restaurant;
    }

    private Restaurant reload(Restaurant restaurant) {
        return restaurantRepository.findById(restaurant.getId()).orElseThrow();
    }

    @Test
    void aNewRestaurantHasNotCompletedItsOnboarding() {
        Restaurant restaurant = newRestaurant();

        assertThat(reload(restaurant).isOnboardingCompleted()).isFalse();
        assertThat(reload(restaurant).getOnboardingCompletedAt()).isNull();
    }

    @Test
    void importingAMenuDoesNotCompleteTheOnboarding() {
        // Déposer un PDF n'est pas être en ligne : tant que rien n'est publié, le
        // parcours de configuration reste à terminer.
        Restaurant restaurant = newRestaurant();

        menuService.uploadPdf(restaurant.getId(), VALID_PDF, "application/pdf", "carte.pdf");

        assertThat(reload(restaurant).isOnboardingCompleted()).isFalse();
    }

    @Test
    void publishingCompletesTheOnboarding() {
        Restaurant restaurant = newRestaurant();
        menuService.uploadPdf(restaurant.getId(), VALID_PDF, "application/pdf", "carte.pdf");

        menuService.publish(restaurant.getId());

        Restaurant reloaded = reload(restaurant);
        assertThat(reloaded.isOnboardingCompleted()).isTrue();
        assertThat(reloaded.getOnboardingCompletedAt()).isNotNull();
    }

    @Test
    void unpublishingNeverReopensTheOnboarding() {
        // Masquer sa carte une journée ne doit pas renvoyer le restaurateur sur l'écran
        // « Bienvenue sur Karta » : le marqueur est à sens unique.
        Restaurant restaurant = newRestaurant();
        menuService.uploadPdf(restaurant.getId(), VALID_PDF, "application/pdf", "carte.pdf");
        menuService.publish(restaurant.getId());

        menuService.unpublish(restaurant.getId());

        assertThat(reload(restaurant).isOnboardingCompleted()).isTrue();
    }

    @Test
    void republishingKeepsTheFirstCompletionDate() {
        Restaurant restaurant = newRestaurant();
        menuService.uploadPdf(restaurant.getId(), VALID_PDF, "application/pdf", "carte.pdf");
        menuService.publish(restaurant.getId());
        OffsetDateTime first = reload(restaurant).getOnboardingCompletedAt();

        menuService.unpublish(restaurant.getId());
        menuService.publish(restaurant.getId());

        assertThat(reload(restaurant).getOnboardingCompletedAt()).isEqualTo(first);
    }

    @Test
    void theDateIsExposedToTheFrontend() {
        // Le frontend décide où envoyer le restaurateur à partir de ce seul champ.
        Restaurant restaurant = newRestaurant();
        assertThat(RestaurantDtos.RestaurantResponse.from(reload(restaurant)).onboardingCompletedAt())
                .isNull();

        menuService.uploadPdf(restaurant.getId(), VALID_PDF, "application/pdf", "carte.pdf");
        menuService.publish(restaurant.getId());

        assertThat(RestaurantDtos.RestaurantResponse.from(reload(restaurant)).onboardingCompletedAt())
                .isNotNull();
    }
}
