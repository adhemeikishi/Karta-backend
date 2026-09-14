package com.qrmenu.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Isolation d'un compte restaurateur : il ne voit que SON restaurant.
 *
 * <p>Toute l'API produit est servie sous {@code /api/admin/**}. Un restaurateur y accède
 * avec le rôle {@code RESTAURATEUR}, mais uniquement sur les chemins de son propre
 * restaurant. Changer l'UUID dans l'URL ne donne accès à rien : la vérification est faite
 * ici, côté serveur, avant d'atteindre le moindre contrôleur.
 *
 * <p><strong>Un seul point de contrôle, et il refuse par défaut.</strong> Autoriser
 * endpoint par endpoint (annotations sur les contrôleurs) laisserait chaque route ajoutée
 * ensuite ouverte par oubli ; ici, tout ce qui n'est pas explicitement reconnu comme
 * « appartenant à ce restaurant » est refusé.
 *
 * <p>Sont refusés à un restaurateur, même sur son propre restaurant :
 * <ul>
 *   <li>le tableau de bord global et la liste des restaurants — ils exposent les autres
 *       clients ;</li>
 *   <li>renommer, changer d'offre, supprimer — actions commerciales de Karta, pas du
 *       restaurateur ;</li>
 *   <li>{@code /api/admin/qr-codes/**}, dont l'URL ne porte pas le restaurant : son
 *       appartenance n'est pas vérifiable depuis le chemin, donc on refuse plutôt que de
 *       supposer. À rouvrir explicitement quand l'écran « Mon QR » arrivera.</li>
 * </ul>
 *
 * <p>Un administrateur n'est jamais concerné : le filtre le laisse passer immédiatement,
 * son comportement est strictement celui d'avant.
 */
public class RestaurateurScopeFilter extends OncePerRequestFilter {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String RESTAURATEUR_ROLE = "ROLE_RESTAURATEUR";

    /** `/api/admin/restaurants/{uuid}` + le reste du chemin, éventuellement vide. */
    private static final Pattern RESTAURANT_PATH = Pattern.compile(
            "^/api/admin/restaurants/([0-9a-fA-F-]{36})(/.*)?$");

    /** Identité : lisible par tout compte authentifié, c'est elle qui dit qui l'on est. */
    private static final String IDENTITY_PATH = "/api/admin/me";

    private final UUID ownedRestaurantId;

    public RestaurateurScopeFilter(UUID ownedRestaurantId) {
        this.ownedRestaurantId = ownedRestaurantId;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!isRestaurateur(auth)) {
            chain.doFilter(request, response);
            return;
        }

        if (isAllowed(request)) {
            chain.doFilter(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
    }

    private boolean isRestaurateur(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        boolean restaurateur = false;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (ADMIN_ROLE.equals(authority.getAuthority())) {
                return false; // l'administrateur n'est jamais restreint
            }
            if (RESTAURATEUR_ROLE.equals(authority.getAuthority())) {
                restaurateur = true;
            }
        }
        return restaurateur;
    }

    private boolean isAllowed(HttpServletRequest request) {
        String path = request.getRequestURI();

        if (IDENTITY_PATH.equals(path)) {
            return true;
        }

        Matcher matcher = RESTAURANT_PATH.matcher(path);
        if (!matcher.matches()) {
            // Dashboard, liste et création de restaurants, /qr-codes/** : hors périmètre.
            return false;
        }

        // Un UUID mal formé n'atteint pas ce point (le motif ne l'accepte pas) ; un UUID
        // valide mais autre que le sien est refusé — c'est exactement le cas « je change
        // l'identifiant dans l'URL ».
        if (!ownedRestaurantId.toString().equalsIgnoreCase(matcher.group(1))) {
            return false;
        }

        String suffix = matcher.group(2) == null ? "" : matcher.group(2);
        boolean commercial = suffix.isEmpty() || "/offer".equals(suffix);
        if (commercial) {
            // Lire sa propre fiche est légitime ; la renommer, changer son offre ou la
            // supprimer relèvent de Karta.
            return "GET".equals(request.getMethod());
        }

        return true;
    }
}
