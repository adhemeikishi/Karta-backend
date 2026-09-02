#!/usr/bin/env bash
# Restaure les fichiers de menu (PDF) dans le volume Docker "qrmenu-menu-storage"
# à partir d'une archive produite par backup-storage.sh.
#
# ATTENTION : écrase le contenu actuel du volume. Arrêter le backend pendant
# l'opération est recommandé :
#   docker compose -f docker-compose.prod.yml stop backend
#   ./restore-storage.sh /var/backups/qrmenu/qrmenu-menu-storage_....tar.gz
#   docker compose -f docker-compose.prod.yml start backend
#
# Usage: ./restore-storage.sh <chemin-vers-archive.tar.gz>

set -euo pipefail

ARCHIVE="${1:?Usage: ./restore-storage.sh <archive.tar.gz>}"
VOLUME="${STORAGE_VOLUME:-qrmenu-menu-storage}"

if [ ! -f "$ARCHIVE" ]; then
    echo "Archive introuvable: $ARCHIVE"
    exit 1
fi

ARCHIVE_DIR="$(cd "$(dirname "$ARCHIVE")" && pwd)"
ARCHIVE_FILE="$(basename "$ARCHIVE")"

echo "!!! Cette opération écrase le contenu du volume '${VOLUME}'."
read -rp "Confirmer la restauration ? (tape: oui) " confirmation
if [ "$confirmation" != "oui" ]; then
    echo "Abandon."
    exit 1
fi

echo "Restauration de ${ARCHIVE_DIR}/${ARCHIVE_FILE} dans le volume '${VOLUME}'..."

docker run --rm \
    -v "${VOLUME}:/data" \
    -v "${ARCHIVE_DIR}:/backup:ro" \
    alpine sh -c "find /data -mindepth 1 -delete && tar xzpf /backup/${ARCHIVE_FILE} -C /data"

echo "Restauration terminée. Aperçu du contenu du volume:"
docker run --rm -v "${VOLUME}:/data:ro" alpine sh -c "find /data -type f | head -20; echo '...'; find /data -type f | wc -l | xargs echo 'fichiers au total:'"
