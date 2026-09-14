package com.qrmenu.menu;

/**
 * Traduction d'une catégorie ou d'un plat dans une langue. Champ vide = pas traduit :
 * le rendu retombe sur le français, jamais sur une chaîne vide.
 */
public record Translation(String name, String description) {
}
