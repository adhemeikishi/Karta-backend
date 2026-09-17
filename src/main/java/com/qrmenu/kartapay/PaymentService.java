package com.qrmenu.kartapay;

/**
 * Fournisseur de paiement d'une commande. Une seule implémentation existe pour le MVP
 * ({@link NoPaymentProvider}) : l'interface permet à {@link OrderService} de rester
 * inchangé le jour où un vrai fournisseur (Stripe...) sera branché.
 */
public interface PaymentService {

    /** Crée le paiement en attente d'une commande fraîchement créée. */
    Payment createPending(Order order);
}
