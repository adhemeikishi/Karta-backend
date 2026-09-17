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
 * Paiement d'une commande. MVP : {@code provider = NONE}, jamais marqué {@code PAID}
 * automatiquement (voir {@link NoPaymentProvider}) — un vrai fournisseur (Stripe...)
 * viendra plus tard sans que ce modèle n'ait à être réécrit.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Column(name = "amount_cents", nullable = false)
    private int amountCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentProviderType provider;

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Payment() {
        // JPA
    }

    public Payment(UUID orderId, int amountCents, String currency, PaymentProviderType provider) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.status = PaymentStatus.PENDING;
        this.amountCents = amountCents;
        this.currency = currency;
        this.provider = provider;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** MVP : seule transition possible pour le moment, sans effet si déjà en attente. */
    public void markPending() {
        this.status = PaymentStatus.PENDING;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public int getAmountCents() {
        return amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentProviderType getProvider() {
        return provider;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
