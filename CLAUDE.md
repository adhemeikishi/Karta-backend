# Karta — Backend

## Périmètre

Le backend gère aujourd'hui, en production de code (vérifié par lecture directe, audit du 2026-09-14) :

- QR dynamique, 1 QR permanent par restaurant (contrainte DB + logique applicative)
- redirection vers une destination, avec tracking de scans (stats 7/30j)
- gestion des restaurants, avec offre BASIC / PRO / PREMIUM (`RestaurantOffer`)
- comptes restaurateurs (Basic Auth, un seul compte configurable actuellement —
  limitation de configuration, pas un vrai multi-tenant)
- back-office interne (API `/api/admin/**`)
- KartaAI : extraction de menu depuis PDF via Gemini ou Anthropic (package `kartaai`),
  réservée aux offres PRO/PREMIUM, avec double barrière de validation avant
  toute écriture réelle du menu
- studio de design / personnalisation Premium (branding, QR personnalisé, langues)
- onboarding restaurateur (marqueur `onboarding_completed_at`)

**Toujours hors périmètre, sans demande explicite** :

- commandes, panier
- paiement / Stripe / facturation / abonnement réel (le champ `offer` existe
  mais n'est qu'un attribut modifiable par un admin, sans facturation)
- KDS, imprimantes cuisine
- fidélité
- système d'avis clients
- multi-compte restaurateur réel (au-delà du compte unique actuel)

## Commandes

(inchangé)

## Architecture

Package-by-feature :

- restaurant — entité Restaurant, offre (BASIC/PRO/PREMIUM), CRUD
- qrcode — génération et unicité du QR permanent
- qrscan — tracking et statistiques de scans
- redirect — route publique `/q/{code}`
- render — menu public Thymeleaf + API publique
- menu — menu structuré, presets, design, brouillons (menu_drafts)
- kartaai — extraction PDF → menu structuré (Gemini par défaut, Anthropic en option)
- media — fichiers (PDF, images), validation par signature binaire
- admin — API `/api/admin/**`, y compris IdentityController (`/me`)
- common — SecurityConfig (Basic Auth), RestaurateurScopeFilter, validation, exceptions

Le menu public (`/m/{code}`) et la redirection QR (`/q/{code}`) sont rendus par
ce backend, pas par le frontend Angular.

Base de données :

- PostgreSQL, Flyway, migrations dans `src/main/resources/db/migration`
- ne jamais modifier une migration déjà livrée ; créer une nouvelle migration

## Authentification

HTTP Basic Auth uniquement (pas de JWT, pas de session). Un compte ADMIN
obligatoire, un compte RESTAURATEUR optionnel (config par variables
d'environnement — un seul restaurant pilote possible aujourd'hui).
`RestaurateurScopeFilter` isole un restaurateur à son propre restaurant,
deny-by-default sur les routes non reconnues.

(reste des sections Conventions / Sécurité / Méthode de travail : inchangé)
