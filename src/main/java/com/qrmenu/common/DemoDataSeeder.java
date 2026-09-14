package com.qrmenu.common;

import com.qrmenu.qrcode.QrCode;
import com.qrmenu.qrcode.QrCodeService;
import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantOffer;
import com.qrmenu.restaurant.RestaurantRepository;
import com.qrmenu.restaurant.RestaurantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Crée les restaurants de test permettant de parcourir le produit sans dépendre d'un
 * vrai client. Actif uniquement sur le profil "demo" :
 *   .\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev,demo
 *
 * <p>Deux restaurants, deux usages :
 * <ul>
 *   <li><strong>Restaurant Demo</strong> (BASIC) — parcours QR / menu PDF historique ;</li>
 *   <li><strong>Restaurant Test</strong> (PRO) — Espace Restaurateur : import PDF,
 *       KartaIA, Review, puis l'écran Apparence (les cinq presets ne sont ouverts qu'aux
 *       offres PRO et PREMIUM, voir MenuDesignService).</li>
 * </ul>
 *
 * <p>Aucun menu n'est pré-créé : l'état de départ attendu est « une carte vierge », celui
 * qui permet de dérouler le parcours complet (PDF → KartaIA → Review → READY → Apparence).
 *
 * <p>Le seeding est idempotent restaurant par restaurant, et ne supprime jamais rien :
 * relancer l'application ne touche ni aux données existantes, ni à celles des autres
 * restaurants.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String DEMO_RESTAURANT_NAME = "Restaurant Demo";
    private static final String DEMO_DESTINATION_URL = "https://example.com/menu-demo.pdf";

    /** Restaurant de test de l'Espace Restaurateur. PRO : carte numérique + presets. */
    private static final String TEST_RESTAURANT_NAME = "Restaurant Test";

    private final RestaurantRepository restaurantRepository;
    private final RestaurantService restaurantService;
    private final QrCodeService qrCodeService;

    /**
     * Identifiant du restaurant du compte restaurateur de test. C'est la même valeur que
     * celle lue par {@code SecurityConfig} : le restaurant est créé AVEC cet UUID, ce qui
     * évite toute table de liaison — la configuration et la donnée désignent le même
     * objet. Vide = aucun compte restaurateur configuré, donc rien à créer.
     */
    private final String restaurateurRestaurantId;

    public DemoDataSeeder(
            RestaurantRepository restaurantRepository,
            RestaurantService restaurantService,
            QrCodeService qrCodeService,
            @Value("${restaurateur.restaurant-id:}") String restaurateurRestaurantId
    ) {
        this.restaurantRepository = restaurantRepository;
        this.restaurantService = restaurantService;
        this.qrCodeService = qrCodeService;
        this.restaurateurRestaurantId = restaurateurRestaurantId;
    }

    @Override
    public void run(String... args) {
        seedDemoRestaurant();
        seedUserSpaceRestaurant();
    }

    /** Restaurant historique de démonstration (BASIC, QR créé à la main). */
    private void seedDemoRestaurant() {
        if (findByName(DEMO_RESTAURANT_NAME).isPresent()) {
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

    /**
     * Restaurant de test de l'Espace Restaurateur.
     *
     * <p>Passe par {@link QrCodeService#ensureQrCode} — le même chemin que la création
     * d'un vrai client depuis le back-office — pour que le QR pointe dès le départ vers
     * la page publique du menu, comme en production.
     *
     * <p>L'identifiant est journalisé : c'est lui qui compose l'URL de l'espace,
     * {@code /app/{restaurantId}/carte}. Le restaurant est aussi accessible sans le
     * connaître, en ouvrant {@code /app} (sélecteur de restaurant).
     */
    private void seedUserSpaceRestaurant() {
        if (restaurateurRestaurantId.isBlank()) {
            log.info("Aucun compte restaurateur configuré : restaurant de test non créé.");
            return;
        }

        UUID id = UUID.fromString(restaurateurRestaurantId);
        Restaurant restaurant = restaurantRepository.findById(id).orElseGet(
                () -> restaurantRepository.save(
                        new Restaurant(id, TEST_RESTAURANT_NAME, RestaurantOffer.PRO)));
        QrCode qrCode = qrCodeService.ensureQrCode(restaurant);

        log.info("=== Espace Restaurateur — restaurant de test ===");
        log.info("Restaurant: {} ({}) — offre {}",
                restaurant.getName(), restaurant.getId(), restaurant.getOffer());
        log.info("Ma carte:   /app/{}/carte", restaurant.getId());
        log.info("Apparence:  /app/{}/apparence", restaurant.getId());
        log.info("QR code:    {}", qrCode.getCode());
    }

    private Optional<Restaurant> findByName(String name) {
        return restaurantRepository.findAll().stream()
                .filter(r -> name.equals(r.getName()))
                .findFirst();
    }
}
