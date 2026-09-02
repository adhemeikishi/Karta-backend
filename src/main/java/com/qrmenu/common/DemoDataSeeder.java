package com.qrmenu.common;

import com.qrmenu.qrcode.QrCode;
import com.qrmenu.qrcode.QrCodeService;
import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantOffer;
import com.qrmenu.restaurant.RestaurantRepository;
import com.qrmenu.restaurant.RestaurantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Crée un restaurant + QR de démonstration pour tester le parcours complet
 * sans dépendre d'un vrai restaurant. Actif uniquement sur le profil "demo" :
 *   .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=demo
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEMO_RESTAURANT_NAME = "Restaurant Demo";
    private static final String DEMO_DESTINATION_URL = "https://example.com/menu-demo.pdf";

    private final RestaurantRepository restaurantRepository;
    private final RestaurantService restaurantService;
    private final QrCodeService qrCodeService;

    public DemoDataSeeder(
            RestaurantRepository restaurantRepository,
            RestaurantService restaurantService,
            QrCodeService qrCodeService
    ) {
        this.restaurantRepository = restaurantRepository;
        this.restaurantService = restaurantService;
        this.qrCodeService = qrCodeService;
    }

    @Override
    public void run(String... args) {
        boolean alreadySeeded = restaurantRepository.findAll().stream()
                .anyMatch(r -> DEMO_RESTAURANT_NAME.equals(r.getName()));
        if (alreadySeeded) {
            log.info("Données de démo déjà présentes, seeding ignoré.");
            return;
        }

        Restaurant restaurant = restaurantService.create(DEMO_RESTAURANT_NAME, RestaurantOffer.BASIC);
        QrCode qrCode = qrCodeService.create(restaurant.getId(), "QR principal", DEMO_DESTINATION_URL);

        log.info("=== Données de démo créées ===");
        log.info("Restaurant: {} ({})", restaurant.getName(), restaurant.getId());
        log.info("QR code:    {}", qrCode.getCode());
        log.info("Test:       GET /q/{}", qrCode.getCode());
    }
}
