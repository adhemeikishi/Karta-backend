package com.qrmenu.media;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Référence en base d'un fichier physique stocké sur disque (jamais le contenu).
 * Rattaché au restaurant : un fichier survit à la suppression de l'objet qui le
 * référence et peut être nettoyé indépendamment.
 */
@Entity
@Table(name = "media_assets")
public class MediaAsset {

    @Id
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaKind kind;

    /** Chemin relatif dans le stockage. Basé sur l'UUID de l'asset, jamais sur le nom client. */
    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected MediaAsset() {
        // JPA
    }

    public MediaAsset(
            UUID id,
            UUID restaurantId,
            MediaKind kind,
            String storageKey,
            String contentType,
            long sizeBytes,
            String originalFilename
    ) {
        this.id = id;
        this.restaurantId = restaurantId;
        this.kind = kind;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.originalFilename = originalFilename;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRestaurantId() {
        return restaurantId;
    }

    public MediaKind getKind() {
        return kind;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
