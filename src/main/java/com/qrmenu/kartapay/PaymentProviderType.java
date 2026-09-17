package com.qrmenu.kartapay;

/**
 * Fournisseur de paiement. {@code NONE} est le seul existant pour le MVP : aucune
 * dépendance Stripe, aucune clé API, aucun appel réseau de paiement. STRIPE viendra
 * plus tard, sans qu'{@link OrderService} n'ait à être réécrit (voir {@link PaymentService}).
 */
public enum PaymentProviderType {
    NONE
}
