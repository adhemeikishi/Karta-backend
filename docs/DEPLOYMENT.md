# Déploiement en production — QR Menu V1

Procédure complète pour passer de "ça marche en local" à "un vrai restaurant
peut scanner un QR en HTTPS, 24/7". Couvre uniquement le P0 (indispensable
avant premier client). Le P1/P2 (monitoring avancé, CI/CD auto, rate
limiting) viendront ensuite.

## 0. Prérequis

- Un **VPS** (n'importe quel fournisseur : Hetzner, OVH, DigitalOcean...) avec
  Docker installé, Ubuntu 22.04/24.04 recommandé.
- Un **nom de domaine** que tu contrôles (ex: `mondomaine.fr`), avec un
  sous-domaine `qr.mondomaine.fr` prévu pour ce projet.
- Accès SSH au VPS.

## 1. Pointer le DNS vers le VPS

Chez ton registrar / fournisseur DNS, crée un enregistrement :

```
Type: A
Nom:  qr
Valeur: <IP publique du VPS>
TTL: 3600 (ou automatique)
```

Vérifie la propagation (peut prendre de quelques minutes à quelques heures) :

```bash
nslookup qr.mondomaine.fr
```

## 2. Préparer le VPS

Connecte-toi en SSH, puis installe Docker si ce n'est pas déjà fait :

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
# Se déconnecter/reconnecter pour que le groupe prenne effet
```

Vérifie :

```bash
docker --version
docker compose version
```

## 3. Copier le projet sur le VPS

Depuis ton PC Windows (PowerShell), pousse d'abord ton code sur GitHub si ce
n'est pas déjà fait :

```powershell
git remote add origin https://github.com/TON_USER/qr-menu.git
git push -u origin main
```

Puis, sur le VPS (SSH) :

```bash
git clone https://github.com/TON_USER/qr-menu.git
cd qr-menu
```

## 4. Configurer les secrets de production

**Ne jamais committer de vrais secrets.** Sur le VPS :

```bash
cp .env.example .env
nano .env
```

Remplis avec de vraies valeurs fortes, en particulier :
- `DB_PASSWORD` — génère-en un avec `openssl rand -base64 24`
- `ADMIN_PASSWORD` — idem
- `QR_BASE_URL=https://qr.mondomaine.fr` (le vrai domaine, en https)
- `STORAGE_DIR` — **laisser la valeur par défaut** (`/var/lib/qrmenu/storage`). C'est
  le chemin, dans le conteneur backend, où sont stockés les PDF de menu ; un volume
  Docker persistant y est monté (voir §12).

## 5. Adapter le Caddyfile à ton vrai domaine

```bash
nano Caddyfile
```

Remplace `qr.mondomaine.fr` par ton vrai domaine, et ajoute ton email pour
Let's Encrypt en première ligne du bloc si tu veux être notifié en cas de
souci de certificat :

```
qr.mondomaine.fr {
    tls ton-email@example.com
    reverse_proxy backend:8080
    ...
}
```

## 6. Lancer la stack de production

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Ça va : builder l'image du backend, démarrer PostgreSQL, démarrer Caddy qui
va **automatiquement obtenir un certificat Let's Encrypt** pour ton domaine
(ça prend quelques secondes, à condition que le DNS pointe déjà correctement
vers le VPS — voir étape 1).

## 7. Vérifier

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f backend
```

Puis, depuis n'importe quelle machine :

```bash
curl https://qr.mondomaine.fr/actuator/health
```

Doit renvoyer `{"status":"UP"}`, en HTTPS, avec un certificat valide.

Vérifie aussi que le volume de stockage des menus a bien été créé :

```bash
docker volume ls | grep qrmenu-menu-storage
```

## 8. Mettre en place les sauvegardes automatiques

Sur le VPS :

```bash
chmod +x scripts/backup-postgres.sh scripts/restore-postgres.sh

# Test manuel immédiat
./scripts/backup-postgres.sh
```

Puis programme-le quotidiennement via cron :

```bash
crontab -e
```

Ajoute ces lignes (backups tous les jours à 3h du matin) :

```
0 3 * * * cd /home/TON_USER/qr-menu && ./scripts/backup-postgres.sh >> /var/log/qrmenu-backup.log 2>&1
5 3 * * * cd /home/TON_USER/qr-menu && ./scripts/backup-storage.sh  >> /var/log/qrmenu-backup.log 2>&1
```

> **Les deux sont indispensables.** `backup-postgres.sh` sauvegarde la base ;
> `backup-storage.sh` sauvegarde les **fichiers PDF de menu** (volume Docker,
> hors base). Restaurer l'une sans l'autre laisse des QR qui pointent vers des
> PDF manquants. Voir §12.

### Tester la restauration (obligatoire — une sauvegarde jamais testée n'est pas fiable)

```bash
# Base : restaure vers une base de test (jamais la prod), sans risque.
./scripts/restore-postgres.sh /var/backups/qrmenu/qrmenu_2026-08-23_03-00-00.sql.gz
```

Vérifie que le compte de restaurants affiché à la fin correspond à ce que tu
attends. Pour la restauration des fichiers de menu, voir §12.

## 9. Créer ton premier vrai restaurant

Depuis ton PC (ou le VPS) :

```bash
curl -u admin:TON_MOT_DE_PASSE -X POST https://qr.mondomaine.fr/api/admin/restaurants \
  -H "Content-Type: application/json" \
  -d '{"name":"Nom du restaurant","offer":"BASIC"}'
```

Récupère l'`id` renvoyé, puis crée son QR :

```bash
curl -u admin:TON_MOT_DE_PASSE -X POST https://qr.mondomaine.fr/api/admin/restaurants/ID_RESTAURANT/qr-codes \
  -H "Content-Type: application/json" \
  -d '{"name":"QR principal","destinationUrl":"https://url-du-vrai-menu"}'
```

Pour un menu BASIC, envoie le PDF puis publie-le (le QR pointera alors vers ce PDF,
sans jamais changer) :

```bash
curl -u admin:TON_MOT_DE_PASSE -F "file=@menu.pdf;type=application/pdf" \
  https://qr.mondomaine.fr/api/admin/restaurants/ID_RESTAURANT/menu/pdf
curl -u admin:TON_MOT_DE_PASSE -X PUT \
  https://qr.mondomaine.fr/api/admin/restaurants/ID_RESTAURANT/menu/publish
```

Télécharge le PNG du QR :

```bash
curl -u admin:TON_MOT_DE_PASSE https://qr.mondomaine.fr/api/admin/qr-codes/ID_QR/image.png -o qr-final.png
```

## 10. Checklist avant de donner le QR au restaurant

- [ ] URL du menu accessible publiquement, en HTTPS
- [ ] QR actif (`GET .../stats` répond, `active: true`)
- [ ] QR scanné avec succès à 20–50 cm
- [ ] Testé sur au moins un iPhone et un Android
- [ ] Testé en Wi-Fi **et** en 4G/5G (pas seulement sur le réseau du resto)
- [ ] Destination correcte, menu bien affiché sur mobile
- [ ] Pas d'erreur dans la console navigateur
- [ ] Certificat HTTPS valide (cadenas vert, pas d'avertissement)
- [ ] Redirection rapide (perceptible < 1 seconde)
- [ ] Test du cycle désactivation/réactivation
- [ ] Test du changement de destination sans réimpression

## 11. Mettre à jour l'application plus tard

```bash
cd qr-menu
git pull
docker compose -f docker-compose.prod.yml up -d --build
```

Flyway applique automatiquement les nouvelles migrations au démarrage.
Les **fichiers PDF de menu ne sont pas affectés** : ils vivent sur le volume
`qrmenu-menu-storage`, qui n'est pas recréé par `up --build` (voir §12).

---

## 12. Stockage des fichiers de menu (PDF)

### Emplacement

- **Dans le conteneur backend** : `STORAGE_DIR` (`/var/lib/qrmenu/storage` par défaut).
- **Sur l'hôte** : un volume Docker nommé **`qrmenu-menu-storage`**, géré par Docker
  (`/var/lib/docker/volumes/qr-menu_qrmenu-menu-storage/_data` sur le VPS, mais on
  n'y touche jamais directement).
- Arborescence interne : `{restaurantId}/{assetId}.pdf`. Le nom de fichier est un
  UUID généré côté serveur — jamais le nom envoyé par le navigateur.
- La base de données ne stocke **que des références** (`media_assets`), jamais le
  contenu des fichiers.

### Persistance

Le volume est **nommé** (pas un bind-mount, pas `tmpfs`). Il survit à :

- un `docker compose -f docker-compose.prod.yml restart backend` ;
- un `docker compose -f docker-compose.prod.yml up -d --build` (rebuild de l'image) ;
- un `docker compose -f docker-compose.prod.yml down` puis `up` ;
- la suppression / recréation du conteneur backend.

Il n'est détruit **que** par une action explicite :
`docker compose ... down -v`, ou `docker volume rm qrmenu-menu-storage`.

Vérification manuelle (à faire une fois après le premier déploiement) :

```bash
# 1. Upload + publication d'un PDF de test (voir §9), noter l'URL /media/<id> renvoyée
# 2. Forcer une recréation complète du backend
docker compose -f docker-compose.prod.yml up -d --build --force-recreate backend
# 3. Le PDF doit toujours répondre
curl -I https://qr.mondomaine.fr/media/<id>   # -> 200, application/pdf
```

### Sauvegarde / restauration

Sauvegarde (en parallèle de la base, voir §8) :

```bash
./scripts/backup-storage.sh
# -> /var/backups/qrmenu/qrmenu-menu-storage_<date>.tar.gz
```

Restauration (arrêter le backend pendant l'opération) :

```bash
docker compose -f docker-compose.prod.yml stop backend
./scripts/restore-storage.sh /var/backups/qrmenu/qrmenu-menu-storage_<date>.tar.gz
docker compose -f docker-compose.prod.yml start backend
```

> Après une restauration de la base, restaure **le même jour** de fichiers, sinon
> `media_assets` peut référencer des PDF absents (→ QR qui redirige vers un 404).

### Points d'attention

- **Ne jamais** faire `docker compose ... down -v` en production : `-v` supprime
  tous les volumes, y compris les PDF de menu **et** la base.
- Le volume n'est monté **que** dans le conteneur `backend`. Ni PostgreSQL ni
  Caddy n'y ont accès.
- **Caddy ne sert aucun fichier du disque** : sa config est uniquement
  `reverse_proxy backend:8080`. Les PDF ne sont accessibles que via l'endpoint
  applicatif `GET /media/{assetId}` (lookup par UUID en base, aucun chemin fourni
  par le client, garde anti path-traversal dans `LocalFileStorage`).
- Le répertoire appartient à l'utilisateur non-root `qrmenu` du conteneur, en
  permissions `700`.
- Surveiller l'espace disque du VPS : un PDF peut peser jusqu'à 10 Mo par
  restaurant. `du -sh` sur le volume via
  `docker run --rm -v qrmenu-menu-storage:/data:ro alpine du -sh /data`.
- Changer `STORAGE_DIR` impose d'adapter `backend/Dockerfile` (le point de montage
  doit exister dans l'image, appartenir à `qrmenu`) — sinon le conteneur ne
  pourra pas écrire.

---

## Ce qui n'est PAS couvert ici (P1/P2 — plus tard)

- Rate limiting sur l'API admin
- Monitoring actif / alerting (UptimeRobot, Sentry)
- CI/CD automatique (GitHub Actions)
- Restauration automatisée testée en pipeline
- Cloudflare (proxy/cache/protection DDoS)
