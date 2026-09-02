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

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Restaurant() {
        // JPA
    }

    public Restaurant(String name, RestaurantOffer offer) {
        this.id = UUID.randomUUID();
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
