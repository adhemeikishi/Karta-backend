package com.qrmenu.menu;

/**
 * Cycle de vie d'un menu Karta.
 *
 * <ul>
 *   <li>{@code DRAFT}     : menu créé mais sans contenu exploitable (aucun PDF / aucune catégorie).</li>
 *   <li>{@code READY}     : contenu présent, non diffusé au public.</li>
 *   <li>{@code PUBLISHED} : contenu diffusé — c'est ce que le QR sert.</li>
 * </ul>
 *
 * Remplace l'ancien booléen {@code published} : une seule source de vérité.
 * Le JSON expose toujours {@code published} (dérivé) pour ne rien casser côté client.
 */
public enum MenuStatus {
    DRAFT,
    READY,
    PUBLISHED
}
