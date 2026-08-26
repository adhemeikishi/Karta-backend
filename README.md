# QR Menu — V1

Système de QR code dynamique permettant à un client de scanner un QR affiché
en restaurant et d'être redirigé vers le menu digital de l'établissement.

Cette V1 est volontairement minimale : pas de commande, pas de paiement, pas
de compte restaurant, pas de dashboard. Uniquement :

```
Admin crée un restaurant → associe une URL de menu → génère un QR
Client scanne le QR → backend résout le code → redirection vers le menu
```

## 1. Prérequis

- **Java 21**
- **Maven** (ou utiliser le wrapper `mvnw` / `mvnw.cmd` une fois généré)
- **PostgreSQL** (via Docker recommandé, voir plus bas)
- **Docker** (optionnel mais recommandé pour PostgreSQL en local)

## 2. Configuration

Toute la configuration passe par variables d'environnement (aucun secret en dur
dans le code ni dans Git) :

| Variable         | Description                                   | Défaut (dev)                              |
|------------------|------------------------------------------------|--------------------------------------------|
| `DB_URL`         | URL JDBC PostgreSQL                            | `jdbc:postgresql://localhost:5432/qrmenu` |
| `DB_USER`        | Utilisateur PostgreSQL                         | `qrmenu`                                   |
| `DB_PASSWORD`    | Mot de passe PostgreSQL                        | `qrmenu`                                   |
| `SERVER_PORT`    | Port HTTP de l'application                     | `8080`                                     |
| `QR_BASE_URL`    | Domaine encodé dans le QR (ex: `/q/{code}`)    | `http://localhost:8080`                    |
| `ADMIN_USERNAME` | Utilisateur pour l'API admin (Basic Auth)      | `admin`                                    |
| `ADMIN_PASSWORD` | Mot de passe pour l'API admin (Basic Auth)     | Aucun par défaut en base — voir note ci-dessous |

En développement, les valeurs par défaut de `application.yml` suffisent.
En production, définir ces variables via votre environnement de déploiement,
**jamais** en les committant dans Git.

## 3. Lancement (local)

### a. Démarrer PostgreSQL

```powershell
docker compose up -d
```

### b. Démarrer le backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Pour démarrer avec les données de démonstration (un restaurant + un QR de test) :

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,demo"
```

> **Changement important** : les identifiants admin (`ADMIN_USERNAME`/`ADMIN_PASSWORD`)
> n'ont plus de valeur par défaut au niveau racine — un démarrage sans profil actif
> échoue désormais volontairement plutôt que de tourner avec un mot de passe
> connu. Le profil `dev` fournit un identifiant de développement (`admin` /
> `dev-only-changeme`), donc il faut toujours l'activer en local. En production,
> le profil `prod` est utilisé et les vraies valeurs viennent de `.env`
> (voir `docs/DEPLOYMENT.md`).

### c. Vérifier que l'application tourne

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/actuator/health"
```

Réponse attendue : `{"status":"UP"}`.

### d. Arrêter

```powershell
# Ctrl+C dans le terminal Spring Boot, puis :
docker compose stop
```

## 4. Utilisation (parcours complet)

Toutes les routes `/api/admin/**` nécessitent une authentification Basic Auth
(`ADMIN_USERNAME` / `ADMIN_PASSWORD`).

### 1. Créer un restaurant

```powershell
$restaurant = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/admin/restaurants" `
  -Headers @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("<votre_username>:<votre_password>")) } `
  -ContentType "application/json" `
  -Body '{"name":"Le Bon Kebab"}'

$restaurant
```

### 2. Créer un QR pour ce restaurant

```powershell
$qr = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/admin/restaurants/$($restaurant.id)/qr-codes" `
  -Headers @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("<votre_username>:<votre_password>")) } `
  -ContentType "application/json" `
  -Body '{"name":"QR entrée","destinationUrl":"https://example.com/menu.pdf"}'

$qr
```

### 3. Générer le PNG du QR

```powershell
Invoke-WebRequest -Uri "http://localhost:8080/api/admin/qr-codes/$($qr.id)/image.png" `
  -Headers @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("<votre_username>:<votre_password>")) } `
  -OutFile "qr-$($qr.code).png"
```

### 4. Scanner le QR (ou ouvrir l'URL directement)

Ouvrir `http://localhost:8080/q/<code>` dans un navigateur (ou téléphone sur
le même réseau, en remplaçant `localhost` par l'IP de la machine) →
redirection HTTP 302 vers `destinationUrl`.

### 5. Modifier la destination (le QR imprimé reste identique)

```powershell
Invoke-RestMethod -Method Put `
  -Uri "http://localhost:8080/api/admin/qr-codes/$($qr.id)" `
  -Headers @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("<votre_username>:<votre_password>")) } `
  -ContentType "application/json" `
  -Body '{"destinationUrl":"https://example.com/menu-v2.pdf"}'
```

### 6. Re-scanner le même QR

Le même code redirige maintenant vers `menu-v2.pdf` — le QR physique n'a pas changé.

### 7. Désactiver / réactiver un QR

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/admin/qr-codes/$($qr.id)/deactivate" `
  -Headers @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("<votre_username>:<votre_password>")) }
```

Scanner un QR désactivé renvoie `410 Gone` avec le message
« Ce QR code n'est plus actif. » (aucune redirection).

### 8. Consulter les statistiques

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/admin/qr-codes/$($qr.id)/stats" `
  -Headers @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("<votre_username>:<votre_password>")) }
```

## 5. Tests

```powershell
cd backend
.\mvnw.cmd test
```

Les tests utilisent une base H2 en mémoire (profil `test`), aucune dépendance
à un PostgreSQL local n'est nécessaire pour les lancer.

## 6. Structure du projet

```
qr-menu/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/qrmenu/
│       │   ├── restaurant/   → Restaurant (entité, repo, service, DTOs)
│       │   ├── qrcode/       → QrCode (entité, repo, service, génération PNG/SVG)
│       │   ├── qrscan/       → QrScan (tracking des scans, stats)
│       │   ├── redirect/     → GET /q/{code} (route publique)
│       │   ├── admin/        → API admin (/api/admin/**)
│       │   └── common/       → validation URL, sécurité, exceptions
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/ → migrations Flyway
│       └── test/java/com/qrmenu/...
├── docker-compose.yml
└── README.md
```

## 7. Hors périmètre de cette V1

Volontairement absents (voir feuille de route produit) : panier, commande,
paiement/Stripe, comptes restaurants, dashboard, avis clients, emails,
impression cuisine, bornes, IA, fidélité.

## 8. Déploiement en production

Voir [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) pour la procédure complète :
VPS, Docker, HTTPS automatique (Caddy + Let's Encrypt), sauvegardes
PostgreSQL, et checklist de mise en production.
