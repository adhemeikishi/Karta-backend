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

Ajoute cette ligne (backup tous les jours à 3h du matin) :

```
0 3 * * * cd /home/TON_USER/qr-menu && ./scripts/backup-postgres.sh >> /var/log/qrmenu-backup.log 2>&1
```

### Tester la restauration (obligatoire — une sauvegarde jamais testée n'est pas fiable)

```bash
./scripts/restore-postgres.sh /var/backups/qrmenu/qrmenu_2026-08-23_03-00-00.sql.gz
```

Par défaut, ça restaure vers une base de test (`qrmenu_restore_test`), jamais
vers la prod, donc sans risque. Vérifie que le compte de restaurants affiché
à la fin correspond à ce que tu attends.

## 9. Créer ton premier vrai restaurant

Depuis ton PC (ou le VPS) :

```bash
curl -u admin:TON_MOT_DE_PASSE -X POST https://qr.mondomaine.fr/api/admin/restaurants \
  -H "Content-Type: application/json" \
  -d '{"name":"Nom du restaurant"}'
```

Récupère l'`id` renvoyé, puis crée son QR :

```bash
curl -u admin:TON_MOT_DE_PASSE -X POST https://qr.mondomaine.fr/api/admin/restaurants/ID_RESTAURANT/qr-codes \
  -H "Content-Type: application/json" \
  -d '{"name":"QR principal","destinationUrl":"https://url-du-vrai-menu"}'
```

Télécharge le PNG :

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

---

## Ce qui n'est PAS couvert ici (P1/P2 — plus tard)

- Rate limiting sur l'API admin
- Monitoring actif / alerting (UptimeRobot, Sentry)
- CI/CD automatique (GitHub Actions)
- Restauration automatisée testée en pipeline
- Cloudflare (proxy/cache/protection DDoS)
