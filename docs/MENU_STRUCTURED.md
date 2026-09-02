# Menu structuré Karta — fondation

Ce document décrit le modèle de données du menu structuré, le contrat JSON qui en
découle, et **comment les briques suivantes viendront s'y greffer sans rien casser**.

Il ne décrit pas KartaAI, la Review, les presets ni le renderer : rien de tout cela
n'existe encore.

---

## 1. Modèle

```
Client (restaurants)
  │  1 — 0..1     UNIQUE (restaurant_id)
  └── Menu (menus)
        │  type = PDF | STRUCTURED     (déduit de l'offre, jamais choisi par le client)
        │  status = DRAFT | READY | PUBLISHED
        │  version = révision du contenu
        │
        ├── PDF        → pdf_asset_id ──► media_assets      (offre BASIC)
        │
        └── STRUCTURED → menu_categories (1—N)              (offres PRO / PREMIUM)
                              └── menu_items (1—N)
                                      └── image_asset_id ──► media_assets
```

Règles invariantes :

- **1 client = 1 QR = 1 menu.** La contrainte `uq_menus_restaurant` la garantit en base,
  pas seulement en Java.
- Le type du menu **découle de l'offre** : `BASIC → PDF`, `PRO / PREMIUM → STRUCTURED`.
  Aucune API ne permet de le choisir librement.
- Un fichier n'est jamais stocké en base : `media_assets` porte une référence,
  le contenu vit sur disque (`STORAGE_DIR`, cf. `DEPLOYMENT.md`).

### Statut plutôt que booléen

`status` a remplacé l'ancien booléen `published` (migration `V4`). Une seule source de
vérité, et deux états distincts là où il n'y en avait qu'un :

| Statut      | Signification                                          |
| ----------- | ------------------------------------------------------ |
| `DRAFT`     | menu créé, contenu vide ou incomplet                   |
| `READY`     | contenu présent, **pas** diffusé                       |
| `PUBLISHED` | contenu diffusé — c'est ce que le QR sert              |

Le JSON expose toujours `published` (dérivé de `status`) pour rester rétro-compatible.

### Prix

`menu_items.price_cents` est un **entier de centimes** : `1290` = 12,90 €.
Jamais de flottant sur de la monnaie. Le champ JSON s'appelle `price` et porte la même
valeur ; la devise est un code ISO-4217 par produit (`EUR` par défaut). Les
pseudo-devises ISO (`XXX`, `XAU`…) sont refusées : sans décimales définies, elles ne
peuvent pas exprimer un prix.

---

## 2. Contrat JSON

`GET /api/admin/restaurants/{id}/menu` renvoie le document canonique. C'est **ce document**
que consommeront KartaAI, la Review, l'aperçu et le renderer.

```json
{
  "offer": "PRO",
  "type": "STRUCTURED",
  "status": "READY",
  "version": 2,
  "published": false,
  "publishedAt": null,
  "pdf": null,
  "structure": {
    "restaurantName": "Le Bistrot",
    "currency": "EUR",
    "categories": [
      {
        "id": "8f1c…",
        "name": "Burgers",
        "description": "Nos burgers",
        "sortOrder": 1,
        "visible": true,
        "items": [
          {
            "id": "b32a…",
            "name": "Cheeseburger",
            "description": "Steak, cheddar, salade",
            "price": 1290,
            "currency": "EUR",
            "imageAssetId": null,
            "imageUrl": null,
            "sortOrder": 1,
            "available": true
          }
        ]
      }
    ]
  }
}
```

- `structure` est `null` pour un menu `PDF` ; `pdf` est `null` pour un menu `STRUCTURED`.
- `version: 0` signale qu'**aucune ligne `menus` n'existe** encore pour ce client.
- Les catégories et les produits sont toujours triés par `sortOrder`, puis par nom.

---

## 3. API admin

Le menu est traité comme une **ressource unique**, pas en CRUD granulaire.

| Méthode  | Route                                        | Effet                                                    |
| -------- | -------------------------------------------- | -------------------------------------------------------- |
| `GET`    | `/api/admin/restaurants/{id}/menu`           | document canonique                                        |
| `POST`   | `/api/admin/restaurants/{id}/menu`           | crée le menu (type déduit de l'offre) — `409` s'il existe |
| `PUT`    | `/api/admin/restaurants/{id}/menu`           | remplace **toute** la structure                           |
| `DELETE` | `/api/admin/restaurants/{id}/menu`           | supprime le menu et son contenu                           |
| `POST`   | `/api/admin/restaurants/{id}/menu/pdf`       | envoie le PDF (BASIC)                                     |
| `DELETE` | `/api/admin/restaurants/{id}/menu/pdf`       | retire le PDF (BASIC)                                     |
| `PUT`    | `/api/admin/restaurants/{id}/menu/publish`   | publie (BASIC)                                            |
| `PUT`    | `/api/admin/restaurants/{id}/menu/unpublish` | dépublie (BASIC)                                          |

### Pourquoi un `PUT` de document complet

La Review éditera un menu entier, pas une catégorie isolée : réordonner trois sections
et déplacer deux plats deviendrait une dizaine d'appels non atomiques. Un seul `PUT` est
plus simple, transactionnel, et correspond exactement à ce que produira KartaAI.

Sémantique :

- ce qui **n'est pas envoyé est supprimé** ;
- un `id` fourni **met à jour en place** — l'identité survit à un renommage, un
  réordonnancement ou un déplacement d'une catégorie à l'autre ;
- un `id` inconnu **du menu courant** est rejeté (`400`) : impossible d'attraper la
  catégorie d'un autre client.

Cette conservation des identifiants n'est pas cosmétique : les commentaires « plat par
plat » de la Review, les photos et, plus tard, les lignes de commande y seront rattachés.

---

## 4. Draft vs menu validé

La chaîne visée :

```
PDF → Extraction → KartaAI → MenuDraft JSON → Review → Menu validé → Renderer
```

Aujourd'hui, la séparation est portée par `status`, **sans table supplémentaire** :

- KartaAI écrira son résultat via le `PUT` existant, sur un menu en `DRAFT` ;
- la Review corrigera ce même document, toujours en `DRAFT` ;
- la validation le passera en `READY`, puis la publication en `PUBLISHED`.

C'est suffisant tant qu'aucun menu structuré n'est publié — et il ne peut pas l'être :
`publish` reste réservé au flux BASIC tant que le renderer HTML n'existe pas. **Le QR ne
doit jamais pointer vers une page qui n'existe pas.**

Le jour où l'on voudra re-passer KartaAI sur un menu **déjà publié**, il faudra une copie
de travail, faute de quoi l'import écraserait le menu vu par les clients. Ajout prévu à ce
moment-là, et pas avant :

```sql
CREATE TABLE menu_drafts (
    id          UUID PRIMARY KEY,
    menu_id     UUID NOT NULL REFERENCES menus (id) ON DELETE CASCADE,
    source      VARCHAR(20) NOT NULL,   -- KARTA_AI | MANUAL
    payload     TEXT NOT NULL,          -- le JSON `structure` tel quel
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
```

Le draft n'est qu'un **document en attente** ; la structure relationnelle reste la seule
source de vérité une fois validée. Aucune migration des tables existantes n'est nécessaire.

---

## 5. Options / suppléments — ajout futur

Volontairement **non implémenté** : aucune table sans consommateur. La structure ci-dessous
est additive, elle n'impose aucune modification de `menu_items`.

```
MenuItem
   └── ItemOptionGroup   (Taille, Cuisson, Suppléments…)   1—N
          └── ItemOption (Petite / Grande, +Bacon…)        1—N
```

```sql
CREATE TABLE item_option_groups (
    id           UUID PRIMARY KEY,
    item_id      UUID NOT NULL REFERENCES menu_items (id) ON DELETE CASCADE,
    name         VARCHAR(120) NOT NULL,
    required     BOOLEAN NOT NULL DEFAULT FALSE,
    min_choices  INTEGER NOT NULL DEFAULT 0,
    max_choices  INTEGER NOT NULL DEFAULT 1,   -- 1 = variante, >1 = suppléments
    sort_order   INTEGER NOT NULL DEFAULT 0,
    ...
);

CREATE TABLE item_options (
    id                UUID PRIMARY KEY,
    group_id          UUID NOT NULL REFERENCES item_option_groups (id) ON DELETE CASCADE,
    name              VARCHAR(120) NOT NULL,
    price_delta_cents INTEGER NOT NULL DEFAULT 0,  -- delta signé, même unité que price_cents
    available         BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order        INTEGER NOT NULL DEFAULT 0,
    ...
);
```

Impact sur le contrat JSON : un tableau `optionGroups` s'ajoute dans chaque `item`.
Champ absent aujourd'hui = aucune option — les clients existants ne cassent pas.

Impact sur le `PUT` : `SaveItemRequest` gagne une liste `optionGroups`, traitée par la
même logique d'upsert par `id` que les catégories et les produits.

`max_choices` couvre d'un seul modèle les tailles (une variante obligatoire), les cuissons
et les suppléments multiples — inutile d'inventer trois entités.

---

## 6. Compatibilité avec la suite

| Brique                      | Ce qui est déjà prêt                                                                  |
| --------------------------- | ------------------------------------------------------------------------------------- |
| **Renderer HTML**           | `structure` est déjà le document final ; `version` sert de cache-busting               |
| **Presets / thème**         | colonnes `preset` + `theme_json` à ajouter sur `menus` (additif, aucune table touchée)  |
| **KartaAI**                 | écrit via le `PUT` existant ; aucun endpoint spécifique à prévoir                       |
| **Review plat par plat**    | les `id` de catégories et de produits sont stables entre deux enregistrements           |
| **Photos**                  | `image_asset_id` existe ; il ne manque que l'endpoint d'upload d'image                  |
| **Commandes (V2)**          | les lignes de commande référenceront `menu_items.id` — stable, jamais recréé            |

Ce qui reste volontairement absent : presets, thème, renderer, upload d'images,
options, panier, commandes, paiement.
