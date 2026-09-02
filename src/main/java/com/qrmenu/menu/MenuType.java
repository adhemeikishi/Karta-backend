package com.qrmenu.menu;

/**
 * Type de menu Karta.
 *
 * <ul>
 *   <li>{@code PDF}        : destination = un PDF hébergé par Karta (offre BASIC).</li>
 *   <li>{@code STRUCTURED} : destination = menu HTML construit à partir de
 *       catégories/produits (offres PRO/PREMIUM).</li>
 * </ul>
 *
 * Phase 1 : seul {@code PDF} est exploité. {@code STRUCTURED} existe dans le modèle
 * mais aucune fonctionnalité associée n'est développée.
 */
public enum MenuType {
    PDF,
    STRUCTURED
}
