package com.qrmenu.kartapay;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Option d'un groupe (ex : "Bien cuit", "+ Bacon").
 *
 * Le delta de prix est en <strong>centimes entiers</strong>, comme partout dans Karta :
 * peut être négatif (ex : "Sans fromage" -100) mais jamais un flottant.
 */
@Entity
@Table(name = "modifier_options")
public class ModifierOption {

    @Id
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "price_delta_cents", nullable = false)
    private int priceDeltaCents;

    @Column(nullable = false)
    private boolean available;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ModifierOption() {
        // JPA
    }

    public ModifierOption(UUID groupId) {
        this.id = UUID.randomUUID();
        this.groupId = groupId;
        this.available = true;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, int priceDeltaCents, boolean available, int sortOrder) {
        this.name = name;
        this.priceDeltaCents = priceDeltaCents;
        this.available = available;
        this.sortOrder = sortOrder;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public String getName() {
        return name;
    }

    public int getPriceDeltaCents() {
        return priceDeltaCents;
    }

    public boolean isAvailable() {
        return available;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
