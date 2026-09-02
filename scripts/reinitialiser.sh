#!/usr/bin/env bash
#
# Remise à zéro de l'environnement local.
#
# Supprime le volume PostgreSQL, relance les conteneurs et attend qu'ils soient
# sains. Flyway reconstruira le schéma au prochain démarrage de l'API, et
# Keycloak réimportera son realm — il tourne en mémoire en mode développement.
#
#   ./scripts/reinitialiser.sh
#
# ⚠️ Détruit toutes les données locales. Ne touche qu'au projet compose
# « booking » : les conteneurs d'autres projets ne sont pas concernés.
set -euo pipefail

cd "$(dirname "$0")/.."

printf '\033[36m→ Suppression du volume PostgreSQL\033[0m\n'
docker compose down -v

printf '\033[36m→ Redémarrage des conteneurs\033[0m\n'
docker compose up -d

printf '\033[36m→ Attente\033[0m\n'
for _ in $(seq 1 60); do
  pg=$(docker inspect --format '{{.State.Health.Status}}' booking-postgres 2>/dev/null || echo absent)
  kc=$(docker inspect --format '{{.State.Health.Status}}' booking-keycloak 2>/dev/null || echo absent)
  if [ "$pg" = healthy ] && [ "$kc" = healthy ]; then
    printf '  \033[32m✓\033[0m postgres, keycloak et mailpit sont prêts\n\n'
    printf '  Il reste deux étapes :\n'
    printf '    1. lancer l'"'"'API      ./mvnw spring-boot:run\n'
    printf '    2. peupler la base  node scripts/donnees-demo.mjs\n'
    exit 0
  fi
  sleep 3
done

printf '  \033[31m✗\033[0m postgres=%s keycloak=%s — voir « docker compose logs »\n' "$pg" "$kc" >&2
exit 1
