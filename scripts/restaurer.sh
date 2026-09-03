#!/usr/bin/env bash
#
# Restaure les deux bases depuis une sauvegarde.
#
#   ./scripts/restaurer.sh ./sauvegardes/2026-09-03_21h12
#   ./scripts/restaurer.sh ./sauvegardes/2026-09-03_21h12 --sans-confirmation
#
# Ce script existe pour être essayé, pas seulement pour le jour où tout brûle.
# Une sauvegarde jamais restaurée n'est pas une sauvegarde : c'est un fichier
# dont on suppose le contenu.
#
# ⚠️ ÉCRASE les données actuelles des deux bases.
set -euo pipefail
cd "$(dirname "$0")/.."

vert()  { printf '  \033[32m✓\033[0m %s\n' "$1"; }
jaune() { printf '  \033[33m!\033[0m %s\n' "$1"; }
rouge() { printf '  \033[31m✗\033[0m %s\n' "$1" >&2; }

SOURCE="${1:-}"
CONFIRME=non
[ "${2:-}" = "--sans-confirmation" ] && CONFIRME=oui

[ -n "$SOURCE" ] || {
  rouge 'Indiquez le dossier de sauvegarde.'
  printf '\n  Disponibles :\n' >&2
  ls -1d sauvegardes/20* 2>/dev/null | sed 's/^/    /' >&2 || printf '    aucune\n' >&2
  exit 1
}
[ -d "$SOURCE" ] || { rouge "$SOURCE n'existe pas."; exit 1; }

set -a; . ./partage.env; set +a
COMPOSE="docker compose -f docker-compose.partage.yml"
CONTENEUR=$($COMPOSE ps -q postgres 2>/dev/null || true)
[ -n "$CONTENEUR" ] || { rouge 'le conteneur postgres ne tourne pas.'; exit 1; }

# ------------------------------------------------------------------
# Vérifier avant de détruire
#
# La somme de contrôle est lue d'abord : découvrir qu'une archive est
# tronquée après avoir effacé la base en production est le pire moment
# possible.
# ------------------------------------------------------------------
printf '\033[36m→ Vérification de l’archive\033[0m\n'
if [ -f "$SOURCE/sommes.sha256" ]; then
  ( cd "$SOURCE" && shasum -a 256 -c sommes.sha256 >/dev/null 2>&1 ) \
    && vert 'sommes de contrôle conformes' \
    || { rouge 'sommes de contrôle invalides — archive corrompue, on s’arrête.'; exit 1; }
else
  jaune 'aucune somme de contrôle dans cette sauvegarde'
fi

for base in beauty_booking keycloak; do
  [ -f "$SOURCE/$base.dump" ] || { rouge "$base.dump manque."; exit 1; }
done
vert 'les deux archives sont présentes'

if [ "$CONFIRME" != oui ]; then
  printf '\n'
  jaune "Ceci ÉCRASE les données actuelles de beauty_booking et keycloak."
  printf '  Taper « restaurer » pour continuer : '
  read -r reponse
  [ "$reponse" = restaurer ] || { printf '  annulé\n'; exit 1; }
fi

# ------------------------------------------------------------------
# Restauration
#
# L'API et Keycloak sont arrêtés d'abord : restaurer sous une application qui
# écrit encore laisse un mélange des deux états, et Keycloak garde en mémoire
# des données que la restauration vient de remplacer.
# ------------------------------------------------------------------
printf '\n\033[36m→ Arrêt des services applicatifs\033[0m\n'
$COMPOSE stop api keycloak >/dev/null 2>&1
vert 'api et keycloak arrêtés'

printf '\033[36m→ Restauration\033[0m\n'
for base in beauty_booking keycloak; do
  # --clean --if-exists remplace le contenu au lieu de s'ajouter par-dessus ;
  # sans lui, chaque restauration échouerait sur des clés déjà présentes.
  # Les erreurs de propriétaire sont attendues et sans conséquence : le rôle
  # booking-user existe déjà.
  if docker exec -i -e PGPASSWORD="$DB_PASSWORD" "$CONTENEUR" \
       pg_restore -U booking_user -d "$base" --clean --if-exists --no-owner \
       < "$SOURCE/$base.dump" 2>/dev/null; then
    vert "$base restaurée"
  else
    # pg_restore sort en erreur sur des avertissements bénins : on vérifie le
    # résultat plutôt que le code de sortie.
    lignes=$(docker exec -e PGPASSWORD="$DB_PASSWORD" "$CONTENEUR" \
      psql -U booking_user -d "$base" -t -A -c \
      "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'" 2>/dev/null || echo 0)
    if [ "${lignes:-0}" -gt 0 ]; then
      vert "$base restaurée ($lignes tables, avertissements ignorés)"
    else
      rouge "échec sur $base"
      exit 1
    fi
  fi
done

printf '\033[36m→ Redémarrage\033[0m\n'
$COMPOSE up -d >/dev/null 2>&1
vert 'services relancés'

# ------------------------------------------------------------------
# Contrôle
#
# On compte ce qui a été restauré : un « restauré » sans chiffre derrière ne
# prouve rien.
# ------------------------------------------------------------------
printf '\n\033[36m→ Contrôle\033[0m\n'
docker exec -e PGPASSWORD="$DB_PASSWORD" "$CONTENEUR" \
  psql -U booking_user -d beauty_booking -t -A -F' ' -c "
  SELECT 'salons', count(*) FROM salon
  UNION ALL SELECT 'réservations', count(*) FROM reservation
  UNION ALL SELECT 'comptes locaux', count(*) FROM users
  UNION ALL SELECT 'avis', count(*) FROM avis;" 2>/dev/null | sed 's/^/    /'

docker exec -e PGPASSWORD="$DB_PASSWORD" "$CONTENEUR" \
  psql -U booking_user -d keycloak -t -A -F' ' -c \
  "SELECT 'comptes Keycloak', count(*) FROM user_entity;" 2>/dev/null | sed 's/^/    /'

printf '\n'
vert 'restauration terminée'
jaune 'Vérifiez une connexion réelle : les comptes viennent de la base restaurée.'

# Le secret du client de service est stocké dans la base de Keycloak : une
# restauration le ramène donc à sa valeur d'alors, tandis que partage.env
# garde la valeur courante. L'API ne peut plus créer de comptes, et le
# message d'erreur ne dit pas pourquoi.
jaune 'Le secret du client de service a repris la valeur qu’il avait dans la sauvegarde.'
jaune 'Réalignez-le : node scripts/durcir-production.mjs --appliquer'
