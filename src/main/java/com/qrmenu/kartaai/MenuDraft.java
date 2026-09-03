package com.qrmenu.kartaai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Brouillon d'extraction KartaAI en attente de Review.
 *
 * Volontairement en dehors de l'agrégat {@code Menu} : tant que le restaurateur n'a pas
 * validé, rien n'est écrit dans le menu — la carte publiée reste celle que lisent les
 * clients. Un seul brouillon par client (contrainte UNIQUE), comme il n'y a qu'un menu.
 *
 * Le payload n'est jamais requêté : il est relu en bloc puis jeté à la validation.
 */
@Entity
@Table(name = "menu_drafts")
public class MenuDraft {

    @Id
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, unique = true)
    private UUID restaurantId;

    @Column(name = "source_asset_id")
    private UUID sourceAssetId;

    @Column(name = "source_filename", length = 255)
    private String sourceFilename;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected MenuDraft() {
        // JPA
    }

    public MenuDraft(UUID restaurantId, UUID sourceAssetId, String sourceFilename, String payload) {
        this.id = UUID.randomUUID();
        this.restaurantId = restaurantId;
        this.sourceAssetId = sourceAssetId;
        this.sourceFilename = sourceFilename;
        this.payload = payload;
        this.createdAt = OffsetDateTime.now();
    }

    /** Remplace le contenu : une nouvelle extraction écrase la précédente, sans s'empiler. */
    public void replace(UUID sourceAssetId, String sourceFilename, String payload) {
        this.sourceAssetId = sourceAssetId;
        this.sourceFilename = sourceFilename;
        this.payload = payload;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRestaurantId() {
        return restaurantId;
    }

    public UUID getSourceAssetId() {
        return sourceAssetId;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public String getPayload() {
        return payload;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
