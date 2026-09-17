package com.qrmenu.restaurant;

import com.qrmenu.common.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;

    public RestaurantService(RestaurantRepository restaurantRepository) {
        this.restaurantRepository = restaurantRepository;
    }

    public Restaurant create(String name, RestaurantOffer offer) {
        Restaurant restaurant = new Restaurant(name, offer);
        return restaurantRepository.save(restaurant);
    }

    /**
     * Création par inscription libre-service ({@code SignupService}) : le restaurant est
     * immédiatement exploitable (offre posée, QR généré par l'appelant) mais sans abonnement
     * payé. Karta ne transforme jamais un compte gratuit en payant automatiquement — seul un
     * futur parcours de paiement (Phase 3) pourra faire passer {@code subscriptionActive} à
     * {@code true}.
     */
    public Restaurant createWithoutSubscription(String name, RestaurantOffer offer) {
        Restaurant restaurant = new Restaurant(UUID.randomUUID(), name, offer, false);
        return restaurantRepository.save(restaurant);
    }

    public Restaurant rename(UUID id, String newName) {
        Restaurant restaurant = getOrThrow(id);
        restaurant.rename(newName);
        return restaurantRepository.save(restaurant);
    }

    public Restaurant changeOffer(UUID id, RestaurantOffer offer) {
        Restaurant restaurant = getOrThrow(id);
        restaurant.changeOffer(offer);
        return restaurantRepository.save(restaurant);
    }

    public Restaurant changeKartaPayEnabled(UUID id, boolean enabled) {
        Restaurant restaurant = getOrThrow(id);
        restaurant.changeKartaPayEnabled(enabled);
        return restaurantRepository.save(restaurant);
    }

    /**
     * Suppression physique. Les lignes liées (qr_codes, qr_scans, menus, media_assets)
     * sont supprimées par les contraintes FK ON DELETE CASCADE (voir migrations V1/V3).
     * Le nettoyage des fichiers sur disque est de la responsabilité de l'appelant.
     */
    public void delete(UUID id) {
        restaurantRepository.delete(getOrThrow(id));
    }

    public Restaurant getOrThrow(UUID id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Restaurant introuvable: " + id));
    }

    public List<Restaurant> findAll() {
        return restaurantRepository.findAll();
    }
}
