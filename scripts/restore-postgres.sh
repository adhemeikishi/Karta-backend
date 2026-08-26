#!/usr/bin/env bash
# Restaure une sauvegarde PostgreSQL. À utiliser :
#   1. En cas d'incident réel (restauration sur la base de prod).
#   2. Régulièrement en TEST sur une base temporaire, pour vérifier que les
#      sauvegardes sont réellement exploitables (une sauvegarde jamais restaurée
#      ne doit jamais être considérée comme fiable - voir docs/DEPLOYMENT.md).
#
# Usage: ./restore-postgres.sh <chemin-vers-fichier.sql.gz> [nom-base-cible]
#
# Par défaut, restaure vers une base de test "qrmenu_restore_test" pour ne
# JAMAIS écraser accidentellement la prod. Pour restaurer en prod, il faut
# explicitement passer le nom de la vraie base en second argument ET confirmer.

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_FILE="${1:?Usage: ./restore-postgres.sh <fichier.sql.gz> [nom-base-cible]}"
TARGET_DB="${2:-qrmenu_restore_test}"

# shellcheck disable=SC1090
source "$PROJECT_DIR/.env"

if [ "$TARGET_DB" = "$DB_NAME" ]; then
    echo "!!! ATTENTION !!!"
    echo "Tu es sur le point d'écraser la base de PRODUCTION '${DB_NAME}'."
    read -rp "Tape exactement le nom de la base pour confirmer: " confirmation
    if [ "$confirmation" != "$DB_NAME" ]; then
        echo "Confirmation invalide, abandon."
        exit 1
    fi
fi

echo "Création/reset de la base cible '${TARGET_DB}'..."
docker exec qrmenu-postgres-prod psql -U "$DB_USER" -d postgres -c "DROP DATABASE IF EXISTS ${TARGET_DB};"
docker exec qrmenu-postgres-prod psql -U "$DB_USER" -d postgres -c "CREATE DATABASE ${TARGET_DB} OWNER ${DB_USER};"

echo "Restauration de ${BACKUP_FILE} vers '${TARGET_DB}'..."
gunzip -c "$BACKUP_FILE" | docker exec -i qrmenu-postgres-prod psql -U "$DB_USER" -d "$TARGET_DB"

echo "Restauration terminée. Vérification rapide (nombre de restaurants):"
docker exec qrmenu-postgres-prod psql -U "$DB_USER" -d "$TARGET_DB" -c "SELECT count(*) FROM restaurants;"
