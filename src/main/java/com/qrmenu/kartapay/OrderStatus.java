package com.qrmenu.kartapay;

/**
 * Cycle de vie d'une commande Karta Pay. Machine à états stricte, voir
 * {@link Order#transitionTo(OrderStatus)} : {@code COMPLETED} et {@code CANCELLED} sont
 * terminaux, aucune transition ne peut sauter d'étape (ex : {@code PENDING} -> {@code READY}).
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PREPARING,
    READY,
    COMPLETED,
    CANCELLED
}
