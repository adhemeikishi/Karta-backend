package com.qrmenu.common;

import com.qrmenu.account.RestaurateurAccountRepository;
import com.qrmenu.account.RestaurateurAccountResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sécurité volontairement minimale pour la V1 (voir §13 / §19 du contexte projet) :
 * - Basic Auth sur /api/admin/**
 * - Tout le reste (redirection publique /q/**, actuator/health) reste ouvert
 * <p>
 * CORS : le back-office Angular (frontend) tourne sur une origine différente,
 * en développement (localhost:4200 vs localhost:8080) comme en production
 * (Cloudflare https://kartaqr.fr vs VPS https://api.kartaqr.fr). L'origine autorisée
 * est déclarée explicitement par profil : application-dev.yml (localhost:4200) et
 * application-prod.yml (https://kartaqr.fr), surchargeable via CORS_ALLOWED_ORIGINS.
 * Aucune origine autorisée par défaut si la propriété est absente (fail safe).
 * <p>
 * À remplacer par une vraie solution d'authentification (JWT / comptes restaurants)
 * lorsque le produit évoluera au-delà de la V1. L'architecture (filtre de sécurité
 * appliqué uniquement sur /api/admin/**) est conçue pour permettre cette évolution
 * sans tout réécrire.
 */
@Configuration
public class SecurityConfig {

    private final String adminUsername;
    private final String adminPassword;
    private final String restaurateurUsername;
    private final String restaurateurPassword;
    private final String restaurateurRestaurantId;
    private final List<String> allowedOrigins;

    public SecurityConfig(
            @Value("${admin.username}") String adminUsername,
            @Value("${admin.password}") String adminPassword,
            @Value("${restaurateur.username:}") String restaurateurUsername,
            @Value("${restaurateur.password:}") String restaurateurPassword,
            @Value("${restaurateur.restaurant-id:}") String restaurateurRestaurantId,
            @Value("${cors.allowed-origins:}") String allowedOriginsCsv
    ) {
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.restaurateurUsername = restaurateurUsername;
        this.restaurateurPassword = restaurateurPassword;
        this.restaurateurRestaurantId = restaurateurRestaurantId;
        this.allowedOrigins = allowedOriginsCsv.isBlank()
                ? List.of()
                : Arrays.stream(allowedOriginsCsv.split(",")).map(String::trim).toList();
    }

    /**
     * Un compte restaurateur n'existe que si les trois propriétés sont renseignées :
     * identifiant, mot de passe ET restaurant. Un compte sans restaurant serait un compte
     * sans périmètre — donc un trou. Aucune valeur par défaut n'est livrée : sans
     * configuration explicite, il n'existe qu'un seul compte, exactement comme avant.
     */
    private boolean restaurateurAccountConfigured() {
        return !restaurateurUsername.isBlank()
                && !restaurateurPassword.isBlank()
                && !restaurateurRestaurantId.isBlank();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Comptes acceptés par le Basic Auth.
     *
     * Le compte d'administration existe toujours et garde exactement le rôle et les
     * droits qu'il avait. Un compte <strong>restaurateur</strong> historique, unique,
     * s'y ajoute lorsqu'il est configuré — inchangé, toujours in-memory.
     *
     * <p>Tout autre compte RESTAURATEUR (créé par inscription libre-service,
     * {@code POST /api/public/signup}) n'est PAS in-memory : il vit dans
     * {@code restaurateur_accounts} (voir {@link com.qrmenu.account.RestaurateurAccount}).
     * C'est l'option la moins risquée pour l'existant : le chemin ADMIN et le chemin
     * restaurateur historique restent exactement ce qu'ils étaient, tester
     * {@code inMemory.loadUserByUsername} en premier ne change rien pour eux ; seul un nom
     * qu'ils ne connaissent pas déclenche une lecture en base.
     */
    @Bean
    public UserDetailsService userDetailsService(
            PasswordEncoder passwordEncoder,
            RestaurateurAccountRepository accountRepository
    ) {
        List<UserDetails> users = new ArrayList<>();
        users.add(User.withUsername(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .roles("ADMIN")
                .build());

        if (restaurateurAccountConfigured()) {
            users.add(User.withUsername(restaurateurUsername)
                    .password(passwordEncoder.encode(restaurateurPassword))
                    .roles("RESTAURATEUR")
                    .build());
        }

        InMemoryUserDetailsManager inMemory = new InMemoryUserDetailsManager(users);

        return username -> {
            try {
                return inMemory.loadUserByUsername(username);
            } catch (UsernameNotFoundException notInMemory) {
                return accountRepository.findByEmail(RestaurateurAccountResolver.normalize(username))
                        .map(account -> User.withUsername(account.getEmail())
                                .password(account.getPasswordHash())
                                .roles("RESTAURATEUR")
                                .build())
                        .orElseThrow(() -> notInMemory);
            }
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/admin/**", configuration);
        source.registerCorsConfiguration("/api/public/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RestaurateurAccountResolver restaurateurAccountResolver
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API stateless, pas de formulaire HTML côté admin en V1
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        // Les requêtes preflight CORS (OPTIONS) n'envoient jamais de credentials -
                        // les exiger bloquerait toute requête cross-origin (ex: back-office Angular en dev).
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        // Le rôle RESTAURATEUR n'ouvre PAS /api/admin/** : il ouvre le
                        // droit d'y entrer, et RestaurateurScopeFilter décide ensuite,
                        // chemin par chemin, ce qui lui appartient réellement.
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "RESTAURATEUR")
                        .anyRequest().permitAll()
                )
                .httpBasic(Customizer.withDefaults());

        // Toujours enregistré, qu'un compte restaurateur historique soit configuré ou non :
        // un compte issu de l'inscription libre-service peut exister même en son absence.
        // Sans effet sur un ADMIN ou un anonyme (voir RestaurateurScopeFilter.isRestaurateur) —
        // après AuthorizationFilter, l'utilisateur est authentifié et son rôle validé, il
        // reste à vérifier que la ressource visée est bien la sienne.
        http.addFilterAfter(
                new RestaurateurScopeFilter(restaurateurAccountResolver),
                AuthorizationFilter.class);

        return http.build();
    }
}
