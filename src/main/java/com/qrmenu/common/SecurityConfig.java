package com.qrmenu.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Sécurité volontairement minimale pour la V1 (voir §13 / §19 du contexte projet) :
 * - Basic Auth sur /api/admin/**
 * - Tout le reste (redirection publique /q/**, actuator/health) reste ouvert
 * <p>
 * CORS : le back-office Angular (admin-frontend) tourne sur une origine différente
 * en développement (localhost:4200 vs localhost:8080). En production, il est prévu
 * d'être servi depuis le même domaine (voir docs/DEPLOYMENT.md), donc aucune origine
 * autorisée par défaut - à configurer explicitement si jamais il est servi ailleurs.
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
    private final List<String> allowedOrigins;

    public SecurityConfig(
            @Value("${admin.username}") String adminUsername,
            @Value("${admin.password}") String adminPassword,
            @Value("${cors.allowed-origins:}") String allowedOriginsCsv
    ) {
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.allowedOrigins = allowedOriginsCsv.isBlank()
                ? List.of()
                : Arrays.stream(allowedOriginsCsv.split(",")).map(String::trim).toList();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails admin = User.withUsername(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/admin/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API stateless, pas de formulaire HTML côté admin en V1
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        // Les requêtes preflight CORS (OPTIONS) n'envoient jamais de credentials -
                        // les exiger bloquerait toute requête cross-origin (ex: back-office Angular en dev).
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll()
                )
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
