package com.qrmenu.qrcode;

import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Rattrape, au démarrage, les restaurants créés avant la règle « 1 restaurant = 1 QR
 * automatique » et qui n'ont donc encore aucun QR.
 *
 * Ne supprime ni ne modifie aucune donnée existante : appelle {@link QrCodeService#ensureQrCode}
 * (idempotent) uniquement pour les restaurants qui n'ont pas encore de QR. Sur un
 * démarrage normal où tous les restaurants ont déjà leur QR, c'est un no-op — coût
 * négligeable à l'échelle V1 (une requête de lecture par restaurant).
 */
@Component
public class QrCodeBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QrCodeBackfillRunner.class);

    private final RestaurantService restaurantService;
    private final QrCodeService qrCodeService;
    private final QrCodeRepository qrCodeRepository;

    public QrCodeBackfillRunner(
            RestaurantService restaurantService,
            QrCodeService qrCodeService,
            QrCodeRepository qrCodeRepository
    ) {
        this.restaurantService = restaurantService;
        this.qrCodeService = qrCodeService;
        this.qrCodeRepository = qrCodeRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        int created = 0;
        for (Restaurant restaurant : restaurantService.findAll()) {
            if (qrCodeRepository.findFirstByRestaurantId(restaurant.getId()).isEmpty()) {
                qrCodeService.ensureQrCode(restaurant);
                created++;
            }
        }
        if (created > 0) {
            log.info("QR de rattrapage créés pour {} restaurant(s) qui n'en avaient pas encore.", created);
        }
    }
}
