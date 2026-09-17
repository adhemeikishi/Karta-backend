package com.qrmenu.kartapay;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Groupe d'options d'un produit (ex : "Cuisson", "Suppléments").
 *
 * Comme partout dans le projet, la relation vers le produit est portée par une colonne
 * UUID brute (pas de {@code @ManyToOne}) : chargement explicite, aucun lazy-loading surprise.
 */
@Entity
@Table(name = "modifier_groups")
public class ModifierGroup {

    @Id
    private UUID id;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_type", nullable = false, length = 16)
    private SelectionType selectionType;

    @Column(name = "min_select", nullable = false)
    private int minSelect;

    @Column(name = "max_select")
    private Integer maxSelect;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ModifierGroup() {
        // JPA
    }

    public ModifierGroup(UUID itemId) {
        this.id = UUID.randomUUID();
        this.itemId = itemId;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, SelectionType selectionType, int minSelect, Integer maxSelect, int sortOrder) {
        this.name = name;
        this.selectionType = selectionType;
        this.minSelect = minSelect;
        this.maxSelect = maxSelect;
        this.sortOrder = sortOrder;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getItemId() {
        return itemId;
    }

    public String getName() {
        return name;
    }

    public SelectionType getSelectionType() {
        return selectionType;
    }

    public int getMinSelect() {
        return minSelect;
    }

    public Integer getMaxSelect() {
        return maxSelect;
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
