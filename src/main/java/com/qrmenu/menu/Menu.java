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

    /**
     * Apparence du menu. Cinq colonnes plates plutôt qu'un document JSON : le preset est
     * contraint en base (CHECK), les couleurs sont validées à l'écriture, et une requête
     * peut compter les menus par style sans parser quoi que ce soit.
     *
     * Aucun de ces champs n'appartient au contenu : les modifier ne touche ni aux
     * catégories, ni aux prix, ni au statut de publication.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MenuPreset preset;

    @Column(name = "brand_name", length = 120)
    private String brandName;

    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    @Column(name = "secondary_color", length = 7)
    private String secondaryColor;

    @Column(name = "logo_asset_id")
    private UUID logoAssetId;

    @Column(name = "hero_asset_id")
    private UUID heroAssetId;

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
        this.preset = MenuPreset.DEFAULT;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Rattache le PDF au menu.
     *
     * Menu {@code PDF} (offre BASIC) : le PDF <em>est</em> le menu diffusé. Le remplacer
     * dépublie — une re-publication explicite est exigée.
     *
     * Menu {@code STRUCTURED} (offres PRO / PREMIUM) : le PDF n'est qu'un document
     * <strong>source</strong>, en attente de transformation. Il ne remplace pas le menu
     * structuré et ne doit donc jamais toucher au statut de diffusion : importer une
     * carte PDF ne peut pas dépublier le menu que les clients sont en train de lire.
     */
    public void attachPdf(UUID assetId) {
        this.pdfAssetId = assetId;
        if (this.type == MenuType.PDF) {
            this.status = MenuStatus.READY;
            this.publishedAt = null;
        }
        touch();
    }

    /** Symétrique d'{@link #attachPdf} : ne dépublie que si le PDF était le menu diffusé. */
    public void detachPdf() {
        this.pdfAssetId = null;
        if (this.type == MenuType.PDF) {
            this.status = MenuStatus.DRAFT;
            this.publishedAt = null;
        }
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

    /**
     * Applique une nouvelle apparence.
     *
     * Ne touche jamais au statut : un menu publié reste publié pendant que le
     * restaurateur essaie des styles. Seul « Publier » met en ligne.
     */
    public void applyDesign(MenuDesign design) {
        this.preset = design.preset() == null ? MenuPreset.DEFAULT : design.preset();
        this.brandName = design.brandName();
        this.primaryColor = design.primaryColor();
        this.secondaryColor = design.secondaryColor();
        this.logoAssetId = design.logoAssetId();
        this.heroAssetId = design.heroAssetId();
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

    /** Apparence enregistrée, telle qu'elle sera publiée. */
    public MenuDesign getDesign() {
        return new MenuDesign(
                preset == null ? MenuPreset.DEFAULT : preset,
                brandName,
                primaryColor,
                secondaryColor,
                logoAssetId,
                heroAssetId);
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
