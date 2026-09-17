package com.qrmenu.kartapay;

import com.qrmenu.common.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Commande passée par un client via Karta Pay.
 *
 * Comme partout dans le projet, la relation vers le restaurant est portée par une colonne
 * UUID brute (pas de {@code @ManyToOne}) : chargement explicite, aucun lazy-loading surprise.
 *
 * Le montant et le sous-total sont figés à la création (voir {@link OrderService#createOrder}) :
 * une commande ne se recalcule jamais après coup, seul son statut évolue via
 * {@link #transitionTo(OrderStatus)}.
 */
@Entity
@Table(name = "orders")
public class Order {

    /** PENDING -> CONFIRMED -> PREPARING -> READY -> COMPLETED, CANCELLED depuis tout état non terminal. */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.PREPARING, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PREPARING, EnumSet.of(OrderStatus.READY, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.READY, EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    @Id
    private UUID id;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "order_number", nullable = false, length = 16)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false, length = 16)
    private FulfillmentType fulfillmentType;

    @Column(name = "table_number", length = 20)
    private String tableNumber;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Column(name = "subtotal_cents", nullable = false)
    private int subtotalCents;

    @Column(name = "total_cents", nullable = false)
    private int totalCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Order() {
        // JPA
    }

    public Order(
            UUID restaurantId,
            String orderNumber,
            FulfillmentType fulfillmentType,
            String tableNumber,
            String customerName,
            int subtotalCents,
            int totalCents,
            String currency
    ) {
        this.id = UUID.randomUUID();
        this.restaurantId = restaurantId;
        this.orderNumber = orderNumber;
        this.status = OrderStatus.PENDING;
        this.fulfillmentType = fulfillmentType;
        this.tableNumber = tableNumber;
        this.customerName = customerName;
        this.subtotalCents = subtotalCents;
        this.totalCents = totalCents;
        this.currency = currency;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Fait avancer la commande, ou lève {@link ConflictException} si le saut d'état est invalide. */
    public void transitionTo(OrderStatus newStatus) {
        if (!ALLOWED_TRANSITIONS.get(this.status).contains(newStatus)) {
            throw new ConflictException(
                    "Transition invalide: " + this.status + " -> " + newStatus);
        }
        this.status = newStatus;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRestaurantId() {
        return restaurantId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public FulfillmentType getFulfillmentType() {
        return fulfillmentType;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public int getSubtotalCents() {
        return subtotalCents;
    }

    public int getTotalCents() {
        return totalCents;
    }

    public String getCurrency() {
        return currency;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
