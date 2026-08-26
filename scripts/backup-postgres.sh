#!/usr/bin/env bash
# Sauvegarde quotidienne de la base PostgreSQL de production.
# À exécuter sur le VPS, via cron (voir docs/DEPLOYMENT.md pour la configuration cron).
#
# Usage: ./backup-postgres.sh
#
# Prérequis: le fichier .env (à côté de docker-compose.prod.yml) doit être lisible,
# et le conteneur qrmenu-postgres-prod doit tourner.

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/qrmenu}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
TIMESTAMP="$(date +%Y-%m-%d_%H-%M-%S)"

# shellcheck disable=SC1090
source "$PROJECT_DIR/.env"

mkdir -p "$BACKUP_DIR"

BACKUP_FILE="$BACKUP_DIR/qrmenu_${TIMESTAMP}.sql.gz"

echo "Sauvegarde de la base '${DB_NAME}' vers ${BACKUP_FILE}..."

docker exec qrmenu-postgres-prod pg_dump -U "$DB_USER" "$DB_NAME" | gzip > "$BACKUP_FILE"

echo "Sauvegarde terminée: $BACKUP_FILE ($(du -h "$BACKUP_FILE" | cut -f1))"

echo "Suppression des sauvegardes de plus de ${RETENTION_DAYS} jours..."
find "$BACKUP_DIR" -name "qrmenu_*.sql.gz" -mtime "+${RETENTION_DAYS}" -delete

echo "Sauvegardes actuellement conservées:"
ls -lh "$BACKUP_DIR"
