package com.qrmenu.restaurant;

/**
 * Offre commerciale Karta V1. Règle métier : 1 restaurant = 1 offre.
 *
 * <ul>
 *   <li>{@code BASIC}   : QR dynamique -> menu PDF</li>
 *   <li>{@code PRO}     : QR dynamique -> menu HTML mobile</li>
 *   <li>{@code PREMIUM} : QR dynamique -> menu HTML premium / personnalisé</li>
 * </ul>
 *
 * Le paiement et le changement d'offre sont hors périmètre V1.
 */
public enum RestaurantOffer {
    BASIC,
    PRO,
    PREMIUM
}
