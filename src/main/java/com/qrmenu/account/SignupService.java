package com.qrmenu.account;

import com.qrmenu.account.SignupDtos.SignupRequest;
import com.qrmenu.account.SignupDtos.SignupResponse;
import com.qrmenu.common.ConflictException;
import com.qrmenu.qrcode.QrCodeService;
import com.qrmenu.restaurant.Restaurant;
import com.qrmenu.restaurant.RestaurantOffer;
import com.qrmenu.restaurant.RestaurantService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inscription en libre-service : {@code POST /api/public/signup}.
 *
 * <p>Crée, dans la même transaction, exactement ce qu'il faut pour que le restaurateur
 * puisse commencer à préparer sa carte : son compte, son restaurant, son QR permanent.
 * Aucun abonnement n'est jamais actif à ce stade (voir {@link RestaurantService#createWithoutSubscription})
 * — Karta ne simule jamais un paiement.
 *
 * <p>L'offre posée au restaurant est {@link RestaurantOffer#PRO} : elle ouvre la carte
 * structurée et le choix d'un style (voir {@code MenuService}/{@code MenuDesignService}),
 * ce que le restaurateur doit pouvoir préparer avant même de payer. Seule la personnalisation
 * réservée à PREMIUM (identité de marque, QR personnalisé) reste fermée tant que rien n'a
 * été payé, exactement comme pour un client PRO existant — pas de traitement spécial à
 * maintenir pour un compte en cours d'inscription.
 */
@Service
public class SignupService {

    private static final RestaurantOffer SIGNUP_OFFER = RestaurantOffer.PRO;

    private final RestaurateurAccountRepository accountRepository;
    private final RestaurantService restaurantService;
    private final QrCodeService qrCodeService;
    private final PasswordEncoder passwordEncoder;

    public SignupService(
            RestaurateurAccountRepository accountRepository,
            RestaurantService restaurantService,
            QrCodeService qrCodeService,
            PasswordEncoder passwordEncoder
    ) {
        this.accountRepository = accountRepository;
        this.restaurantService = restaurantService;
        this.qrCodeService = qrCodeService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = RestaurateurAccountResolver.normalize(request.email());
        if (accountRepository.existsByEmail(email)) {
            throw new ConflictException("Un compte existe déjà avec cet email.");
        }

        Restaurant restaurant = restaurantService.createWithoutSubscription(
                request.restaurantName(), SIGNUP_OFFER);
        qrCodeService.ensureQrCode(restaurant);

        RestaurateurAccount account = new RestaurateurAccount(
                email, passwordEncoder.encode(request.password()), restaurant.getId());
        accountRepository.save(account);

        return new SignupResponse(restaurant.getId());
    }
}
