package com.qrmenu.restaurant;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "restaurants")
public class Restaurant {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RestaurantOffer offer;

    /**
     * Date à laquelle le restaurateur a terminé son onboarding — c'est-à-dire publié sa
     * carte pour la première fois. {@code null} tant que ce n'est pas fait.
     *
     * <p>Marqueur à sens unique : dépublier ne le retire jamais. Un restaurant configuré
     * le reste, et repasser son propriétaire par l'écran « Bienvenue sur Karta » parce
     * qu'il a masqué sa carte une journée n'aurait aucun sens.
     *
     * <p>Persisté ici, et non dans le navigateur : le restaurateur doit retrouver son
     * espace configuré depuis n'importe quel appareil.
     */
    @Column(name = "onboarding_completed_at")
    private OffsetDateTime onboardingCompletedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Restaurant() {
        // JPA
    }

    public Restaurant(String name, RestaurantOffer offer) {
        this(UUID.randomUUID(), name, offer);
    }

    /**
     * Restaurant à identifiant imposé.
     *
     * L'identifiant est assigné par l'application (jamais par la base), ce qui permet de
     * créer un restaurant dont l'UUID est connu à l'avance. Utilisé uniquement par le
     * seeding de développement, pour qu'un compte restaurateur de test puisse être lié à
     * « son » restaurant par configuration, sans table de liaison ni migration.
     */
    public Restaurant(UUID id, String name, RestaurantOffer offer) {
        this.id = id;
        this.name = name;
        this.offer = offer;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void rename(String newName) {
        this.name = newName;
        this.updatedAt = OffsetDateTime.now();
    }

    public void changeOffer(RestaurantOffer newOffer) {
        this.offer = newOffer;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Marque l'onboarding comme terminé. Sans effet s'il l'était déjà : la date conservée
     * est celle de la première publication, jamais celle de la dernière.
     */
    public void completeOnboarding() {
        if (this.onboardingCompletedAt == null) {
            this.onboardingCompletedAt = OffsetDateTime.now();
            this.updatedAt = this.onboardingCompletedAt;
        }
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompletedAt != null;
    }

    public OffsetDateTime getOnboardingCompletedAt() {
        return onboardingCompletedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public RestaurantOffer getOffer() {
        return offer;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
