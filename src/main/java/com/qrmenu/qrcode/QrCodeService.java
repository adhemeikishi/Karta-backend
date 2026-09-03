package com.qrmenu.qrcode;

import com.qrmenu.common.ConflictException;
import com.qrmenu.common.DestinationUrlValidator;
import com.qrmenu.common.NotFoundException;
import com.qrmenu.common.PublicUrlBuilder;
import com.qrmenu.restaurant.Restaurant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Règle produit Karta : <strong>1 restaurant = 1 QR, permanent</strong>. Le QR est créé
 * automatiquement à la création du restaurant ({@link #ensureQrCode}) — le restaurateur
 * n'en crée jamais un lui-même, ne choisit jamais sa destination, ne la modifie jamais.
 *
 * L'unicité est garantie à deux niveaux : ici ({@link #create}, en dernier recours pour
 * un restaurant hérité qui n'aurait pas encore de QR) et par une contrainte
 * {@code UNIQUE (restaurant_id)} en base (migration V7) — la garde applicative évite
 * qu'une violation de contrainte remonte comme une erreur 500 non maîtrisée.
 */
@Service
public class QrCodeService {

    private static final int MAX_CODE_GENERATION_ATTEMPTS = 10;

    private final QrCodeRepository qrCodeRepository;
    private final QrCodeGenerator qrCodeGenerator;
    private final DestinationUrlValidator destinationUrlValidator;
    private final PublicUrlBuilder publicUrlBuilder;

    public QrCodeService(
            QrCodeRepository qrCodeRepository,
            QrCodeGenerator qrCodeGenerator,
            DestinationUrlValidator destinationUrlValidator,
            PublicUrlBuilder publicUrlBuilder
    ) {
        this.qrCodeRepository = qrCodeRepository;
        this.qrCodeGenerator = qrCodeGenerator;
        this.destinationUrlValidator = destinationUrlValidator;
        this.publicUrlBuilder = publicUrlBuilder;
    }

    /**
     * Garantit que ce restaurant a son QR unique, en le créant s'il n'existe pas encore.
     * Idempotent : rappelable sans risque (création à la création du restaurant, et
     * rattrapage au démarrage pour les restaurants hérités — voir {@link QrCodeBackfillRunner}).
     *
     * La destination initiale pointe vers la page de menu publique du futur code
     * ({@code /m/{code}}) : cette page gère déjà proprement l'absence de contenu
     * publié (voir {@code PublicMenuController}), donc c'est une destination valide dès
     * la création, avant même qu'un menu existe. {@link com.qrmenu.menu.MenuService}
     * la réécrit ensuite automatiquement à chaque publication — jamais touchée ici après
     * coup, jamais par le restaurateur.
     */
    @Transactional
    public QrCode ensureQrCode(Restaurant restaurant) {
        return qrCodeRepository.findFirstByRestaurantId(restaurant.getId())
                .orElseGet(() -> {
                    String code = generateUniqueCode();
                    QrCode qrCode = new QrCode(
                            restaurant.getId(),
                            restaurant.getName(),
                            publicUrlBuilder.forMenu(code),
                            code);
                    return qrCodeRepository.save(qrCode);
                });
    }

    /**
     * Création manuelle historique. Refuse un second QR pour un restaurant qui en a
     * déjà un — la contrainte DB (V7) le refuserait de toute façon, mais un rejet
     * applicatif propre (409) évite de laisser fuiter une violation de contrainte SQL.
     * Le chemin normal reste {@link #ensureQrCode}, jamais appelé par le restaurateur.
     */
    public QrCode create(UUID restaurantId, String name, String destinationUrl) {
        if (qrCodeRepository.findFirstByRestaurantId(restaurantId).isPresent()) {
            throw new ConflictException("Ce restaurant possède déjà son QR unique.");
        }
        destinationUrlValidator.validate(destinationUrl);
        String uniqueCode = generateUniqueCode();
        QrCode qrCode = new QrCode(restaurantId, name, destinationUrl, uniqueCode);
        return qrCodeRepository.save(qrCode);
    }

    public QrCode getOrThrow(UUID id) {
        return qrCodeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("QR code introuvable: " + id));
    }

    public QrCode getByCodeOrThrow(String code) {
        return qrCodeRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("QR code introuvable: " + code));
    }

    public List<QrCode> findByRestaurant(UUID restaurantId) {
        return qrCodeRepository.findByRestaurantId(restaurantId);
    }

    public QrCode activate(UUID id) {
        QrCode qrCode = getOrThrow(id);
        qrCode.activate();
        return qrCodeRepository.save(qrCode);
    }

    public QrCode deactivate(UUID id) {
        QrCode qrCode = getOrThrow(id);
        qrCode.deactivate();
        return qrCodeRepository.save(qrCode);
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String candidate = qrCodeGenerator.generate();
            if (!qrCodeRepository.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Impossible de générer un code QR unique après " + MAX_CODE_GENERATION_ATTEMPTS + " tentatives.");
    }
}
