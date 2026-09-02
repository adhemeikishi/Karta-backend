package com.qrmenu.menu;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Menu d'un restaurant. Règle métier Karta V1 : 1 restaurant = 1 menu
 * (contrainte UNIQUE sur {@code restaurant_id}).
 *
 * Deux formes coexistent :
 * <ul>
 *   <li>{@code PDF}        — offre BASIC : le QR pointe vers le PDF publié ;</li>
 *   <li>{@code STRUCTURED} — offres PRO / PREMIUM : catégories + produits en base,
 *       source de vérité du futur rendu HTML.</li>
 * </ul>
 */
@Entity
@Table(name = "menus")
public class Menu {

    @Id
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, unique = true)
    private UUID restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MenuType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MenuStatus status;

    /**
     * Révision du contenu, incrémentée à chaque remplacement de la structure.
     * Volontairement minimal : pas d'historique, pas de table de versions. Sert de
     * repère (cache-busting du futur renderer, traçabilité d'un import KartaAI).
     */
    @Column(nullable = false)
    private int version;

    @Column(name = "pdf_asset_id")
    private UUID pdfAssetId;

    /**
     * Destination du QR avant que le menu ne prenne le relais. Capturée à la
     * première publication, restaurée lors d'une dépublication / suppression du PDF.
     * Le modèle {@code QrCode} n'est pas modifié : c'est le menu qui mémorise le repli.
     */
    @Column(name = "fallback_url", columnDefinition = "TEXT")
    private String fallbackUrl;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Menu() {
        // JPA
    }

    public Menu(UUID restaurantId, MenuType type) {
        this.id = UUID.randomUUID();
        this.restaurantId = restaurantId;
        this.type = type;
        this.status = MenuStatus.DRAFT;
        this.version = 1;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void attachPdf(UUID assetId) {
        this.pdfAssetId = assetId;
        // Un remplacement de PDF dépublie : re-publication explicite requise.
        this.status = MenuStatus.READY;
        this.publishedAt = null;
        touch();
    }

    public void detachPdf() {
        this.pdfAssetId = null;
        this.status = MenuStatus.DRAFT;
        this.publishedAt = null;
        touch();
    }

    public void publish() {
        this.status = MenuStatus.PUBLISHED;
        this.publishedAt = OffsetDateTime.now();
        touch();
    }

    public void unpublish() {
        this.status = (type == MenuType.PDF && pdfAssetId == null) ? MenuStatus.DRAFT : MenuStatus.READY;
        this.publishedAt = null;
        touch();
    }

    /**
     * Recale le statut d'un menu structuré après édition du contenu.
     * Un menu déjà publié le reste : éditer ne doit jamais couper la diffusion.
     */
    public void applyContentState(boolean hasContent) {
        if (this.status != MenuStatus.PUBLISHED) {
            this.status = hasContent ? MenuStatus.READY : MenuStatus.DRAFT;
        }
        touch();
    }

    public void bumpVersion() {
        this.version++;
        touch();
    }

    public void rememberFallback(String url) {
        if (this.fallbackUrl == null) {
            this.fallbackUrl = url;
        }
    }

    private void touch() {
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRestaurantId() {
        return restaurantId;
    }

    public MenuType getType() {
        return type;
    }

    public MenuStatus getStatus() {
        return status;
    }

    public int getVersion() {
        return version;
    }

    public UUID getPdfAssetId() {
        return pdfAssetId;
    }

    public String getFallbackUrl() {
        return fallbackUrl;
    }

    /** Dérivé de {@link #getStatus()} : le statut est la seule source de vérité. */
    public boolean isPublished() {
        return status == MenuStatus.PUBLISHED;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
