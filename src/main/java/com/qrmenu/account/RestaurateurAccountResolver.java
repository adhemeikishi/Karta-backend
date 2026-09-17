package com.qrmenu.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Résout le restaurant d'UN compte RESTAURATEUR authentifié, quelle que soit son origine :
 *
 * <ul>
 *   <li>le compte historique, unique, configuré par variables d'environnement
 *       ({@code restaurateur.username} / {@code restaurateur.restaurant-id}) ;</li>
 *   <li>ou un compte créé par inscription libre-service, en base ({@link RestaurateurAccount}).</li>
 * </ul>
 *
 * <p>Point unique utilisé à la fois par {@code RestaurateurScopeFilter} (pour savoir quel
 * restaurant appartient à qui) et par {@code IdentityController} (pour répondre à
 * {@code GET /api/admin/me}) — les deux doivent voir exactement le même restaurant pour le
 * même compte, donc une seule implémentation.
 */
@Component
public class RestaurateurAccountResolver {

    private final RestaurateurAccountRepository accountRepository;
    private final String legacyUsername;
    private final UUID legacyRestaurantId;

    public RestaurateurAccountResolver(
            RestaurateurAccountRepository accountRepository,
            @Value("${restaurateur.username:}") String legacyUsername,
            @Value("${restaurateur.restaurant-id:}") String legacyRestaurantId
    ) {
        this.accountRepository = accountRepository;
        this.legacyUsername = legacyUsername;
        this.legacyRestaurantId = legacyRestaurantId.isBlank() ? null : UUID.fromString(legacyRestaurantId);
    }

    /**
     * Restaurant du compte {@code username}, ou vide si ce nom ne correspond à aucun compte
     * restaurateur connu (ne devrait pas arriver pour un principal déjà authentifié avec le
     * rôle RESTAURATEUR, mais on ne suppose rien côté sécurité).
     */
    public Optional<UUID> resolveRestaurantId(String username) {
        if (legacyRestaurantId != null && !legacyUsername.isBlank() && legacyUsername.equals(username)) {
            return Optional.of(legacyRestaurantId);
        }
        return accountRepository.findByEmail(normalize(username)).map(RestaurateurAccount::getRestaurantId);
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
