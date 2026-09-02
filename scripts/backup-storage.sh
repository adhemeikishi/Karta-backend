#!/usr/bin/env bash
# Sauvegarde des fichiers de menu (PDF de l'offre BASIC) stockés dans le volume
# Docker "qrmenu-menu-storage".
#
# IMPORTANT : la sauvegarde PostgreSQL seule NE SUFFIT PAS. La table media_assets
# ne contient que des références ; les fichiers vivent sur ce volume. Sans cette
# sauvegarde, une perte du volume laisse des QR pointant vers des PDF inexistants.
#
# À exécuter sur le VPS, via cron, en parallèle de backup-postgres.sh
# (voir docs/DEPLOYMENT.md).
#
# Usage: ./backup-storage.sh

set -euo pipefail

VOLUME="${STORAGE_VOLUME:-qrmenu-menu-storage}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/qrmenu}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
TIMESTAMP="$(date +%Y-%m-%d_%H-%M-%S)"

mkdir -p "$BACKUP_DIR"
BACKUP_NAME="qrmenu-menu-storage_${TIMESTAMP}.tar.gz"

echo "Sauvegarde du volume '${VOLUME}' vers ${BACKUP_DIR}/${BACKUP_NAME}..."

# Conteneur jetable : monte le volume en lecture seule + le dossier de sauvegarde,
# archive le contenu du volume (droits/propriétaires préservés).
docker run --rm \
    -v "${VOLUME}:/data:ro" \
    -v "${BACKUP_DIR}:/backup" \
    alpine tar czf "/backup/${BACKUP_NAME}" -C /data .

echo "Sauvegarde terminée: ${BACKUP_DIR}/${BACKUP_NAME} ($(du -h "${BACKUP_DIR}/${BACKUP_NAME}" | cut -f1))"

echo "Suppression des sauvegardes de stockage de plus de ${RETENTION_DAYS} jours..."
find "$BACKUP_DIR" -name "qrmenu-menu-storage_*.tar.gz" -mtime "+${RETENTION_DAYS}" -delete

echo "Sauvegardes de stockage actuellement conservées:"
ls -lh "$BACKUP_DIR"/qrmenu-menu-storage_*.tar.gz 2>/dev/null || echo "(aucune)"
