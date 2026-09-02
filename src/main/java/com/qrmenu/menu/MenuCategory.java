package com.qrmenu.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Catégorie d'un menu structuré (Burgers, Desserts, Boissons...).
 *
 * Comme partout dans le projet, la relation est portée par une colonne UUID brute
 * (pas de {@code @OneToMany}) : chargement explicite, aucun lazy-loading surprise.
 */
@Entity
@Table(name = "menu_categories")
public class MenuCategory {

    @Id
    private UUID id;

    @Column(name = "menu_id", nullable = false)
    private UUID menuId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Position d'affichage. Le menu doit rester réordonnable sans recréer les lignes. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean visible;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MenuCategory() {
        // JPA
    }

    public MenuCategory(UUID menuId) {
        this.id = UUID.randomUUID();
        this.menuId = menuId;
        this.visible = true;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String description, int sortOrder, boolean visible) {
        this.name = name;
        this.description = description;
        this.sortOrder = sortOrder;
        this.visible = visible;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMenuId() {
        return menuId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isVisible() {
        return visible;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
