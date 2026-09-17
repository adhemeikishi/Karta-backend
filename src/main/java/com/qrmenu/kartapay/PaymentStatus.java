package com.qrmenu.kartapay;

/** Statut du paiement d'une commande. MVP : reste {@code PENDING} tant qu'aucun fournisseur réel n'existe. */
public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED,
    REFUNDED
}
