# Karta — Backend (V1)

## Périmètre

La V1 doit rester volontairement minimale :

- QR dynamique
- redirection vers une destination
- gestion des restaurants
- gestion des QR
- statistiques de scans
- back-office interne

Ne pas ajouter sans demande explicite :

- commandes
- panier
- paiement
- Stripe
- comptes restaurants
- abonnements
- KDS
- imprimantes
- fidélité
- IA
- système d'avis
- fonctionnalités V2

## Commandes

Backend (depuis la racine de ce repository) :

- `mvn test`
- `mvn spring-boot:run -Dspring-boot.run.profiles=dev`

Infrastructure :

- `docker compose up -d`

Le frontend Angular vit dans le repository séparé `Karta-frontend`.

## Architecture

Package-by-feature :

- restaurant
- qrcode
- qrscan
- redirect
- render (menu public Thymeleaf + API publique)
- menu (menu structuré, presets, design)
- kartaai (extraction PDF → menu structuré)
- media
- admin
- common

Le menu public (`/m/{code}`) et la redirection QR (`/q/{code}`) sont rendus par
ce backend, pas par le frontend Angular.

Base de données :

- PostgreSQL
- Flyway
- migrations dans `src/main/resources/db/migration`
- ne jamais modifier une migration déjà livrée ; créer une nouvelle migration

## Conventions

- conserver l'architecture actuelle ;
- privilégier les modifications ciblées ;
- ne pas ajouter de dépendance inutile ;
- messages destinés à l'utilisateur en français ;
- ne jamais mettre de secret dans le code ou Git ;
- `.env` reste ignoré ;
- conserver la configuration par variables d'environnement ;
- ne pas introduire de valeurs par défaut dangereuses en production.

## Sécurité

- `DestinationUrlValidator` accepte uniquement HTTP/HTTPS ;
- valider les URL au moment de leur écriture ;
- ne jamais construire dynamiquement la destination dans `RedirectController` ;
- ne pas affaiblir `SecurityConfig` ;
- `/api/admin/**` reste protégé ;
- `cors.allowed-origins` doit lister explicitement l'origine du frontend
  (le frontend est déployé sur un domaine distinct) ;
- ne jamais exposer de stack trace ;
- ne jamais désactiver une protection uniquement pour faire passer un test.

## Méthode de travail

Avant toute modification importante :

1. lire les fichiers concernés ;
2. comprendre l'architecture existante ;
3. identifier les impacts ;
4. modifier uniquement ce qui est nécessaire ;
5. lancer les tests/build concernés ;
6. signaler les fichiers modifiés ;
7. ne jamais supprimer une fonctionnalité existante sans raison.

Avant chaque déploiement :

- lancer la revue `/security-review` lorsqu'elle est disponible.

IMPORTANT :
Le projet est volontairement petit et V1.
Ne pas sur-ingénieriser.
Ne pas anticiper inutilement la V2.
Ne pas transformer le projet en architecture complexe.
