#!/usr/bin/env bash
#
# Sauvegarde les deux bases de la pile partagée.
#
#   ./scripts/sauvegarder.sh                    # vers ./sauvegardes
#   ./scripts/sauvegarder.sh /volume/nas        # vers un autre dossier
#   DESTINATION_DISTANTE=b2:booking ./scripts/sauvegarder.sh
#
# Deux bases, et il faut les deux :
#
#   beauty_booking  les rendez-vous, les salons, les avis
#   keycloak        les comptes et leurs mots de passe
#
# Perdre la première fait perdre l'activité ; perdre la seconde prive tous
# les gérants d'accès, sans possibilité de les recréer à l'identique. Une
# sauvegarde qui n'emporte que la première donne l'illusion d'être protégé.
#
# Le format est « custom » (-Fc) et non du SQL brut : il se restaure
# sélectivement, table par table si besoin, et il est déjà compressé.
#
# ⚠️ Une sauvegarde jamais restaurée n'est pas une sauvegarde.
#    scripts/restaurer.sh existe pour cela, et doit être essayé.
set -euo pipefail
cd "$(dirname "$0")/.."

vert()  { printf '  \033[32m✓\033[0m %s\n' "$1"; }
jaune() { printf '  \033[33m!\033[0m %s\n' "$1"; }
rouge() { printf '  \033[31m✗\033[0m %s\n' "$1" >&2; }

DOSSIER="${1:-./sauvegardes}"
GARDER_JOURS="${GARDER_JOURS:-14}"
COMPOSE="docker compose -f docker-compose.partage.yml"

[ -f partage.env ] || { rouge 'partage.env est introuvable.'; exit 1; }
set -a; . ./partage.env; set +a

CONTENEUR=$($COMPOSE ps -q postgres 2>/dev/null || true)
[ -n "$CONTENEUR" ] || { rouge 'le conteneur postgres ne tourne pas.'; exit 1; }

HORODATAGE=$(date +%Y-%m-%d_%Hh%M)
CIBLE="$DOSSIER/$HORODATAGE"
mkdir -p "$CIBLE"

printf '\033[36m→ Sauvegarde vers %s\033[0m\n' "$CIBLE"

for base in beauty_booking keycloak; do
  fichier="$CIBLE/$base.dump"
  # pg_dump depuis l'intérieur du conteneur : aucun client à installer sur
  # l'hôte, et la version du client correspond forcément au serveur.
  if docker exec -e PGPASSWORD="$DB_PASSWORD" "$CONTENEUR" \
       pg_dump -U booking_user -d "$base" -Fc > "$fichier" 2>/dev/null; then
    vert "$base — $(du -h "$fichier" | cut -f1)"
  else
    rouge "échec sur $base"
    rm -f "$fichier"
    exit 1
  fi
done

# La somme de contrôle détecte une copie tronquée, ce qu'une taille de
# fichier ne fait pas toujours.
( cd "$CIBLE" && shasum -a 256 ./*.dump > sommes.sha256 )
vert 'sommes de contrôle écrites'

# Le realm est le pendant Keycloak du schéma : sans lui, restaurer la base
# des comptes dans une instance neuve laisse un realm sans clients ni rôles.
cp keycloak/booking-realm-realm.json "$CIBLE/realm.json"
vert 'définition du realm copiée'

# ------------------------------------------------------------------
# Rétention
#
# Sur place uniquement : ce qui est parti chez le fournisseur distant relève
# de sa propre politique, et supprimer à distance depuis ici serait le plus
# sûr moyen d'effacer une archive qu'on croyait garder.
# ------------------------------------------------------------------
supprimes=0
while IFS= read -r vieux; do
  rm -rf "$vieux"
  supprimes=$((supprimes + 1))
done < <(find "$DOSSIER" -maxdepth 1 -type d -name '20*' -mtime "+$GARDER_JOURS" 2>/dev/null)
[ "$supprimes" -gt 0 ] && vert "$supprimes sauvegarde(s) de plus de $GARDER_JOURS jours supprimée(s)"

# ------------------------------------------------------------------
# Copie distante
#
# Facultative, et c'est le point qui décide si vous êtes réellement
# protégé : une sauvegarde sur le même disque que la base disparaît avec
# elle. rclone parle à Backblaze B2, S3, Drive et une trentaine d'autres.
# ------------------------------------------------------------------
if [ -n "${DESTINATION_DISTANTE:-}" ]; then
  if command -v rclone >/dev/null 2>&1; then
    rclone copy "$CIBLE" "$DESTINATION_DISTANTE/$HORODATAGE" \
      && vert "copié vers $DESTINATION_DISTANTE/$HORODATAGE"
  else
    rouge 'DESTINATION_DISTANTE est définie mais rclone est absent.'
    rouge 'Installer : brew install rclone   puis : rclone config'
    exit 1
  fi
else
  jaune 'aucune copie distante — la sauvegarde est sur le même disque que la base.'
  jaune 'Définir DESTINATION_DISTANTE (voir rclone) pour être réellement protégé.'
fi

printf '\n'
vert "sauvegarde du $HORODATAGE terminée"
printf '  Restaurer      ./scripts/restaurer.sh %s\n' "$CIBLE"
printf '  Automatiser    ajouter au crontab :\n'
printf '    0 3 * * *  cd %s && ./scripts/sauvegarder.sh >> sauvegardes/journal.log 2>&1\n' "$(pwd)"
