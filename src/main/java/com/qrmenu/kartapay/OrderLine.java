package com.qrmenu.kartapay;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Ligne d'une commande : snapshot complet au moment de la commande.
 *
 * Le nom et le prix unitaire sont figés à la création ({@code *_snapshot}) : si le produit
 * source change de prix, de nom, ou disparaît ensuite ({@code item_id} -> {@code SET NULL}),
 * l'historique de la commande ne bouge jamais.
 */
@Entity
@Table(name = "order_lines")
public class OrderLine {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "item_id")
    private UUID itemId;

    @Column(name = "item_name_snapshot", nullable = false, length = 160)
    private String itemNameSnapshot;

    @Column(name = "unit_price_cents_snapshot", nullable = false)
    private int unitPriceCentsSnapshot;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "line_total_cents", nullable = false)
    private int lineTotalCents;

    /** Options choisies, sérialisées en JSON (nom + delta de prix) — lecture seule après coup. */
    @Column(name = "modifiers_snapshot", columnDefinition = "TEXT")
    private String modifiersSnapshot;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected OrderLine() {
        // JPA
    }

    public OrderLine(
            UUID orderId,
            UUID itemId,
            String itemNameSnapshot,
            int unitPriceCentsSnapshot,
            int quantity,
            int lineTotalCents,
            String modifiersSnapshot,
            int sortOrder
    ) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.itemId = itemId;
        this.itemNameSnapshot = itemNameSnapshot;
        this.unitPriceCentsSnapshot = unitPriceCentsSnapshot;
        this.quantity = quantity;
        this.lineTotalCents = lineTotalCents;
        this.modifiersSnapshot = modifiersSnapshot;
        this.sortOrder = sortOrder;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getItemId() {
        return itemId;
    }

    public String getItemNameSnapshot() {
        return itemNameSnapshot;
    }

    public int getUnitPriceCentsSnapshot() {
        return unitPriceCentsSnapshot;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getLineTotalCents() {
        return lineTotalCents;
    }

    public String getModifiersSnapshot() {
        return modifiersSnapshot;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
