package com.qrmenu.render;

import java.util.List;

/**
 * Vue <strong>publique</strong> d'un menu — volontairement distincte du DTO admin
 * ({@code MenuDtos.MenuResponse}).
 *
 * Ce qui n'y figure pas est aussi important que ce qui y figure : ni identifiants
 * internes, ni statut, ni version, ni offre commerciale, ni clé de stockage. Le
 * renderer et l'API publique ne peuvent donc pas divulguer par accident une donnée
 * d'administration, même si un champ est ajouté un jour au modèle admin.
 *
 * Les catégories masquées et les menus non publiés sont écartés <em>avant</em> la
 * construction de ces objets : le contenu non diffusable n'atteint jamais le HTML.
 */
public class PublicMenuDtos {

    private PublicMenuDtos() {
    }

    public record PublicMenu(
            /* Nom affiché : le nom de marque PREMIUM s'il est défini, sinon celui du client. */
            String restaurantName,
            String currency,
            /* Apparence déjà résolue — le rendu n'a plus aucune couleur à calculer. */
            MenuTheme theme,
            List<PublicCategory> categories
    ) {
        public boolean isEmpty() {
            return categories.isEmpty();
        }
    }

    public record PublicCategory(
            String name,
            String description,
            List<PublicItem> items
    ) {
    }

    public record PublicItem(
            String name,
            String description,
            /* Centimes entiers, comme partout dans Karta. */
            int price,
            String currency,
            /* Prix déjà formaté pour l'affichage (« 12,90 € ») — calculé sans flottant. */
            String priceLabel,
            /* URL publique de l'image, ou null. Jamais d'identifiant d'asset ici. */
            String imageUrl,
            boolean available
    ) {
    }
}
