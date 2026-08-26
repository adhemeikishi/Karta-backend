package com.qrmenu.qrcode;

import com.qrmenu.common.InvalidUrlException;
import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class QrCodeServiceTest {

    @Autowired
    private QrCodeService qrCodeService;

    @Autowired
    private RestaurantService restaurantService;

    @Test
    void createsQrCodeWithGeneratedOpaqueCode() {
        Restaurant restaurant = restaurantService.create("Snack Test");

        QrCode qrCode = qrCodeService.create(restaurant.getId(), "QR entrée", "https://example.com/menu.pdf");

        assertThat(qrCode.getId()).isNotNull();
        assertThat(qrCode.getCode()).isNotBlank();
        assertThat(qrCode.getCode()).hasSize(10); // longueur par défaut du générateur
        assertThat(qrCode.getCode()).isNotEqualTo("1"); // jamais un simple compteur
        assertThat(qrCode.isActive()).isTrue();
        assertThat(qrCode.getRestaurantId()).isEqualTo(restaurant.getId());
    }

    @Test
    void generatesUniqueCodesAcrossManyQrCodes() {
        Restaurant restaurant = restaurantService.create("Snack Unicité");
        Set<String> codes = new HashSet<>();

        for (int i = 0; i < 50; i++) {
            QrCode qrCode = qrCodeService.create(restaurant.getId(), "QR " + i, "https://example.com/menu-" + i);
            assertThat(codes.add(qrCode.getCode())).isTrue();
        }
    }

    @Test
    void rejectsDangerousDestinationUrl() {
        Restaurant restaurant = restaurantService.create("Snack Sécurité");

        assertThatThrownBy(() ->
                qrCodeService.create(restaurant.getId(), "QR", "javascript:alert(1)")
        ).isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void updatesDestinationWithoutChangingTheCode() {
        Restaurant restaurant = restaurantService.create("Snack Update");
        QrCode qrCode = qrCodeService.create(restaurant.getId(), "QR", "https://example.com/menu-v1.pdf");
        String originalCode = qrCode.getCode();

        QrCode updated = qrCodeService.updateDestination(qrCode.getId(), "https://example.com/menu-v2.pdf");

        assertThat(updated.getCode()).isEqualTo(originalCode);
        assertThat(updated.getDestinationUrl()).isEqualTo("https://example.com/menu-v2.pdf");
    }

    @Test
    void activatesAndDeactivatesQrCode() {
        Restaurant restaurant = restaurantService.create("Snack Activation");
        QrCode qrCode = qrCodeService.create(restaurant.getId(), "QR", "https://example.com/menu.pdf");

        QrCode deactivated = qrCodeService.deactivate(qrCode.getId());
        assertThat(deactivated.isActive()).isFalse();

        QrCode reactivated = qrCodeService.activate(qrCode.getId());
        assertThat(reactivated.isActive()).isTrue();
    }
}
