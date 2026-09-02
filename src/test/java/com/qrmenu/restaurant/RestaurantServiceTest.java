package com.qrmenu.restaurant;

import com.qrmenu.common.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class RestaurantServiceTest {

    @org.springframework.beans.factory.annotation.Autowired
    private RestaurantService restaurantService;

    @Test
    void createsAndRetrievesRestaurant() {
        Restaurant created = restaurantService.create("Le Bon Kebab", RestaurantOffer.PRO);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Le Bon Kebab");
        assertThat(created.getOffer()).isEqualTo(RestaurantOffer.PRO);

        Restaurant fetched = restaurantService.getOrThrow(created.getId());
        assertThat(fetched.getId()).isEqualTo(created.getId());
        assertThat(fetched.getOffer()).isEqualTo(RestaurantOffer.PRO);
    }

    @Test
    void throwsNotFoundForUnknownRestaurant() {
        assertThatThrownBy(() -> restaurantService.getOrThrow(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void changeOfferPersists() {
        Restaurant created = restaurantService.create("Offre " + System.nanoTime(), RestaurantOffer.BASIC);

        restaurantService.changeOffer(created.getId(), RestaurantOffer.PREMIUM);

        assertThat(restaurantService.getOrThrow(created.getId()).getOffer())
                .isEqualTo(RestaurantOffer.PREMIUM);
    }

    @Test
    void deleteRemovesRestaurant() {
        Restaurant created = restaurantService.create("Suppr " + System.nanoTime(), RestaurantOffer.BASIC);

        restaurantService.delete(created.getId());

        assertThatThrownBy(() -> restaurantService.getOrThrow(created.getId()))
                .isInstanceOf(NotFoundException.class);
    }
}
