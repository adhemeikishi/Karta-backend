package com.qrmenu.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Compte RESTAURATEUR issu de l'inscription libre-service, 1 compte = 1 restaurant.
 *
 * <p>Le compte ADMIN n'a pas d'équivalent ici : il reste in-memory (voir
 * {@code SecurityConfig}), inchangé. Le compte RESTAURATEUR historique, configuré par
 * variables d'environnement (un seul, {@code restaurateur.username}), n'est pas non plus
 * migré dans cette table — il continue de fonctionner tel quel (voir
 * {@code RestaurateurAccountResolver}) ; cette table n'accueille que les comptes créés via
 * {@code POST /api/public/signup}.
 *
 * <p>Le mot de passe n'est jamais stocké en clair : {@code passwordHash} est produit par le
 * {@code PasswordEncoder} déjà utilisé pour le compte ADMIN (voir SecurityConfig).
 */
@Entity
@Table(name = "restaurateur_accounts")
public class RestaurateurAccount {

    @Id
    private UUID id;

    /** Identifiant de connexion. Toujours comparé/stocké en minuscules — voir SignupService. */
    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * Restaurant de ce compte, 1:1. Contrainte d'unicité en base (migration V11) : un
     * restaurant ne peut pas avoir deux comptes, cohérent avec "1 restaurant = 1 offre".
     */
    @Column(name = "restaurant_id", nullable = false, unique = true)
    private UUID restaurantId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected RestaurateurAccount() {
        // JPA
    }

    public RestaurateurAccount(String email, String passwordHash, UUID restaurantId) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.restaurantId = restaurantId;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UUID getRestaurantId() {
        return restaurantId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
