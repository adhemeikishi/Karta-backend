package com.qrmenu.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Produit / plat d'une catégorie.
 *
 * Le prix est stocké en <strong>centimes entiers</strong> ({@code price_cents}) :
 * jamais de flottant sur de la monnaie. 1290 = 12,90 €.
 *
 * L'image n'est pas stockée ici : seule une référence vers {@code media_assets}
 * est conservée ({@code image_asset_id}), le fichier restant sur disque.
 */
@Entity
@Table(name = "menu_items")
public class MenuItem {

    @Id
    private UUID id;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_cents", nullable = false)
    private int priceCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "image_asset_id")
    private UUID imageAssetId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean available;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MenuItem() {
        // JPA
    }

    public MenuItem(UUID categoryId) {
        this.id = UUID.randomUUID();
        this.categoryId = categoryId;
        this.available = true;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * La catégorie fait partie des champs modifiables : la Review pourra déplacer un plat
     * d'une catégorie à l'autre sans lui faire perdre son identifiant.
     */
    public void update(
            UUID categoryId,
            String name,
            String description,
            int priceCents,
            String currency,
            UUID imageAssetId,
            int sortOrder,
            boolean available
    ) {
        this.categoryId = categoryId;
        this.name = name;
        this.description = description;
        this.priceCents = priceCents;
        this.currency = currency;
        this.imageAssetId = imageAssetId;
        this.sortOrder = sortOrder;
        this.available = available;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getPriceCents() {
        return priceCents;
    }

    public String getCurrency() {
        return currency;
    }

    public UUID getImageAssetId() {
        return imageAssetId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isAvailable() {
        return available;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
