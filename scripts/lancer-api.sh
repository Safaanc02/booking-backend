#!/usr/bin/env bash
#
# Lance l'API en chargeant .env.
#
# Spring Boot ne lit pas les fichiers .env : sans ce script il faut passer les
# variables à la main sur la ligne de commande, ce qu'on finit toujours par
# faire de travers.
#
#   ./scripts/lancer-api.sh
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  printf '\033[31m✗\033[0m Aucun fichier .env.\n\n' >&2
  printf '    cp .env.example .env   puis adaptez le mot de passe\n' >&2
  exit 1
fi

# set -a exporte automatiquement tout ce qui est affecté ensuite.
set -a
# shellcheck disable=SC1091
. ./.env
set +a

: "${DB_PASSWORD:?DB_PASSWORD doit être renseigné dans .env}"

printf '\033[36m→ API sur le port %s, base %s\033[0m\n' "${SERVER_PORT:-8080}" "${DB_URL:-défaut}"
exec ./mvnw spring-boot:run
