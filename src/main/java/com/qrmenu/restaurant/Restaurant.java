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

    /**
     * Interrupteur commercial Karta Pay (commande + paiement sur place), au même titre
     * que {@link #offer} : ADMIN uniquement (voir RestaurantAdminController et
     * RestaurateurScopeFilter), jamais togglé par le restaurateur lui-même.
     */
    @Column(name = "karta_pay_enabled", nullable = false)
    private boolean kartaPayEnabled;

    /**
     * Abonnement payé, indépendamment de {@link #offer} : {@code offer} dit QUEL niveau de
     * fonctionnalités le restaurant utilise, {@code subscriptionActive} dit s'il a payé pour
     * l'utiliser. Un compte créé par inscription libre-service a un restaurant tout de suite
     * exploitable (offre choisie, QR généré) mais {@code subscriptionActive = false} tant
     * qu'aucun paiement n'a eu lieu — voir {@code SignupService}.
     *
     * <p>Tout restaurant créé avant cette notion (back-office, seeding) reste {@code true} :
     * il n'y avait jusqu'ici aucun état "sans abonnement", donc aucune régression à leur faire
     * porter.
     */
    @Column(name = "subscription_active", nullable = false)
    private boolean subscriptionActive;

    /**
     * Compteur de numérotation des commandes Karta Pay, propre à ce restaurant. Incrémenté
     * à chaque commande (voir {@link #nextOrderNumber()}), jamais remis à zéro.
     */
    @Column(name = "order_sequence", nullable = false)
    private int orderSequence;

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
        this(id, name, offer, true);
    }

    /**
     * Constructeur complet : seule l'inscription libre-service ({@code SignupService})
     * a besoin de poser {@code subscriptionActive} à {@code false} dès la création. Tous
     * les autres chemins (back-office, seeding) passent par les constructeurs ci-dessus,
     * qui l'imposent à {@code true} — comportement strictement inchangé pour eux.
     */
    public Restaurant(UUID id, String name, RestaurantOffer offer, boolean subscriptionActive) {
        this.id = id;
        this.name = name;
        this.offer = offer;
        this.subscriptionActive = subscriptionActive;
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

    public void changeKartaPayEnabled(boolean enabled) {
        this.kartaPayEnabled = enabled;
        this.updatedAt = OffsetDateTime.now();
    }

    public boolean isKartaPayEnabled() {
        return kartaPayEnabled;
    }

    public boolean isSubscriptionActive() {
        return subscriptionActive;
    }

    /**
     * Numéro lisible de la prochaine commande ("A0001", "A0002"...), unique et
     * strictement croissant pour ce restaurant, jamais remis à zéro.
     */
    public String nextOrderNumber() {
        this.orderSequence++;
        this.updatedAt = OffsetDateTime.now();
        return String.format("A%04d", orderSequence);
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
