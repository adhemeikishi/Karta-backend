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

> **Mise à jour — la copie de travail existe (migration V6).**
> Ce paragraphe prévoyait de s'en passer « tant qu'aucun menu structuré n'est publié — et
> il ne peut pas l'être ». Cette condition est tombée avec l'arrivée du renderer HTML :
> un menu structuré se publie désormais. Sans copie de travail, relancer KartaAI sur une
> carte en ligne l'écraserait sous les yeux des clients. La table a donc été créée.

`menu_drafts` (V6), volontairement **hors de l'agrégat `Menu`** :

```sql
CREATE TABLE menu_drafts (
    id              UUID PRIMARY KEY,
    restaurant_id   UUID NOT NULL UNIQUE REFERENCES restaurants (id) ON DELETE CASCADE,
    source_asset_id UUID REFERENCES media_assets (id) ON DELETE SET NULL,
    source_filename VARCHAR(255),
    payload         TEXT NOT NULL,   -- le document d'extraction tel quel
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL
);
```

Rattachée au **client** et non au menu : le brouillon doit pouvoir exister avant qu'une
ligne `menus` n'existe. Un seul brouillon par client — une nouvelle extraction remplace la
précédente au lieu de s'empiler.

Le cycle est donc :

- l'extraction écrit **uniquement** dans `menu_drafts` — la carte publiée ne bouge pas ;
- la Review corrige ce document en mémoire, côté navigateur ;
- la validation écrit le menu par le `PUT` existant, puis le brouillon est consommé
  **dans la même transaction** : si l'écriture échoue, le rollback rend son brouillon au
  restaurateur au lieu de lui faire tout ressaisir.

Le draft n'est qu'un **document en attente** ; la structure relationnelle reste la seule
source de vérité une fois validée. Aucune table existante n'a été modifiée.

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
| **Renderer HTML**           | ✅ livré : `menu/menu.html`, un template pour les cinq presets                          |
| **Presets / thème**         | ✅ livré (V5) : colonnes `preset` + identité PREMIUM sur `menus`, pas de `theme_json`   |
| **KartaAI**                 | ✅ livré (V6) : `POST/GET/DELETE .../menu/ai/*` pour le brouillon, écriture par le `PUT` |
| **Review plat par plat**    | les `id` de catégories et de produits sont stables entre deux enregistrements           |
| **Photos**                  | ✅ livré : `POST .../images` + `image_asset_id`                                          |
| **Commandes (V2)**          | les lignes de commande référenceront `menu_items.id` — stable, jamais recréé            |

Ce qui reste volontairement absent : options / suppléments, panier, commandes, paiement.
