package com.qrmenu.qrcode;

import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantOffer;
import com.qrmenu.restaurant.RestaurantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le runner lui-même s'exécute déjà une fois au démarrage du contexte Spring (avant
 * chaque test) — sur une base de test vide, c'est un no-op. Ces tests l'invoquent donc
 * une seconde fois, explicitement, après avoir recréé la situation qu'il doit rattraper
 * (un restaurant sans QR — impossible à obtenir autrement que par accès direct au
 * service, puisque le contrôleur en crée toujours un désormais).
 */
@SpringBootTest
@ActiveProfiles("test")
class QrCodeBackfillRunnerTest {

    @Autowired
    private QrCodeBackfillRunner runner;

    @Autowired
    private RestaurantService restaurantService;

    @Autowired
    private QrCodeService qrCodeService;

    @Test
    void createsTheMissingQrForARestaurantWithoutOne() {
        // Reproduit l'état d'un restaurant hérité : créé avant la règle, jamais de QR.
        Restaurant withoutQr = restaurantService.create("Hérité sans QR " + System.nanoTime(), RestaurantOffer.PRO);
        assertThat(qrCodeService.findByRestaurant(withoutQr.getId())).isEmpty();

        runner.run(null);

        assertThat(qrCodeService.findByRestaurant(withoutQr.getId())).hasSize(1);
    }

    @Test
    void neverDuplicatesAnExistingQr() {
        Restaurant withQr = restaurantService.create("Déjà équipé " + System.nanoTime(), RestaurantOffer.PRO);
        QrCode existing = qrCodeService.ensureQrCode(withQr);

        runner.run(null);
        runner.run(null); // deux passages : toujours aucun doublon

        var qrCodes = qrCodeService.findByRestaurant(withQr.getId());
        assertThat(qrCodes).hasSize(1);
        assertThat(qrCodes.get(0).getId()).isEqualTo(existing.getId());
    }
}
