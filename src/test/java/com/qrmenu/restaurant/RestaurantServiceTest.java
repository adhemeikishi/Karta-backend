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
        Restaurant created = restaurantService.create("Le Bon Kebab");

        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Le Bon Kebab");

        Restaurant fetched = restaurantService.getOrThrow(created.getId());
        assertThat(fetched.getId()).isEqualTo(created.getId());
    }

    @Test
    void throwsNotFoundForUnknownRestaurant() {
        assertThatThrownBy(() -> restaurantService.getOrThrow(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }
}
