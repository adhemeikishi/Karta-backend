package com.qrmenu.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Identité du compte connecté.
 *
 * <p>Sert deux besoins, et c'est le seul endpoint que tout compte authentifié peut
 * appeler quel que soit son rôle :
 * <ol>
 *   <li><strong>Valider des identifiants</strong> à la connexion. Le frontend visait
 *       auparavant {@code /api/admin/dashboard}, réservé à l'administration : un
 *       restaurateur n'aurait jamais pu se connecter.</li>
 *   <li><strong>Savoir où aller ensuite.</strong> Un administrateur va au back-office,
 *       un restaurateur va dans son espace — et l'identifiant de son restaurant est
 *       renvoyé ici, donc le frontend n'a pas à le deviner ni à lister les restaurants
 *       (ce que le filtre lui interdit, à raison).</li>
 * </ol>
 *
 * <p>Ne renvoie que ce qui est nécessaire à ces deux décisions : ni mot de passe, ni
 * détail de configuration.
 */
@RestController
public class IdentityController {

    /** Restaurant du compte restaurateur. Vide tant qu'aucun compte n'est configuré. */
    private final String restaurateurRestaurantId;

    public IdentityController(
            @Value("${restaurateur.restaurant-id:}") String restaurateurRestaurantId
    ) {
        this.restaurateurRestaurantId = restaurateurRestaurantId;
    }

    @GetMapping("/api/admin/me")
    public IdentityResponse me(Authentication authentication) {
        boolean admin = hasRole(authentication, "ROLE_ADMIN");
        return new IdentityResponse(
                authentication.getName(),
                admin ? "ADMIN" : "RESTAURATEUR",
                admin || restaurateurRestaurantId.isBlank() ? null : restaurateurRestaurantId);
    }

    private boolean hasRole(Authentication authentication, String role) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (role.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param username identifiant de connexion
     * @param role     {@code ADMIN} ou {@code RESTAURATEUR}
     * @param restaurantId restaurant du compte restaurateur ; {@code null} pour un
     *                     administrateur, qui les gère tous
     */
    public record IdentityResponse(String username, String role, String restaurantId) {
    }
}
