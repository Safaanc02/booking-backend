#!/usr/bin/env bash
#
# Met l'application debout sur une seule adresse, pour la faire essayer.
#
#   ./scripts/partager.sh                          # essai local, sur 8090
#   ./scripts/partager.sh https://essai.exemple.ma # adresse publique
#
# Construit les images, démarre la pile, attend qu'elle réponde, puis peuple
# le jeu de démonstration. À la fin, il affiche l'adresse à transmettre et les
# comptes pour se connecter.
#
# ⚠️ Ce n'est pas une mise en production. Les comptes de démonstration ont
#    pour mot de passe leur identifiant, et la boîte aux lettres de test est
#    lisible par quiconque connaît l'adresse. Traitez l'adresse comme un
#    secret, et n'y saisissez aucune donnée réelle de client.
set -euo pipefail
cd "$(dirname "$0")/.."

bleu()  { printf '\033[36m%s\033[0m\n' "$1"; }
vert()  { printf '  \033[32m✓\033[0m %s\n' "$1"; }
jaune() { printf '  \033[33m!\033[0m %s\n' "$1"; }
rouge() { printf '  \033[31m✗\033[0m %s\n' "$1" >&2; }

URL_DEMANDEE="${1:-}"
FICHIER=partage.env

# --------------------------------------------------------------------
# Préalables
# --------------------------------------------------------------------
docker info >/dev/null 2>&1 || {
  rouge "Docker ne répond pas. Démarrez Docker Desktop ou OrbStack."
  exit 1
}

[ -d ../booking-frontend ] || {
  rouge "../booking-frontend est introuvable."
  rouge "Les deux dépôts doivent être clonés côte à côte dans le même dossier."
  exit 1
}

# --------------------------------------------------------------------
# Secrets
#
# Tirés au sort à la première exécution, puis conservés : les régénérer à
# chaque fois invaliderait les liens d'annulation déjà envoyés, et ferait
# perdre l'accès à la console Keycloak.
# --------------------------------------------------------------------
if [ ! -f "$FICHIER" ]; then
  bleu '→ Premiers secrets'
  # 40 caractères : la signature des liens d'annulation en exige au moins 32,
  # et l'application refuse de démarrer en dessous. Un premier jet en
  # produisait 24, et la pile ne montait pas.
  secret() { openssl rand -base64 48 | tr -d '/+=' | cut -c1-40; }
  cat > "$FICHIER" <<EOF
# Écrit par scripts/partager.sh. Non suivi par Git.
URL_PUBLIQUE=http://localhost:8090
PORT_PUBLIC=8090
DB_PASSWORD=$(secret)
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=$(secret)
ANNULATION_SECRET=$(secret)
# Fixé par l'import du realm : à changer dans les deux fichiers à la fois.
KEYCLOAK_ADMIN_CLIENT_SECRET=secret-de-developpement-a-remplacer
EOF
  chmod 600 "$FICHIER"
  vert "$FICHIER créé, avec des secrets tirés au sort"
fi

# --------------------------------------------------------------------
# Adresse publique
#
# Elle est inscrite dans les jetons émis par Keycloak et dans les liens des
# e-mails. En changer impose de recréer les conteneurs, ce que fait « up ».
# --------------------------------------------------------------------
if [ -n "$URL_DEMANDEE" ]; then
  URL_DEMANDEE="${URL_DEMANDEE%/}"
  # sed -i diffère entre BSD et GNU : on réécrit le fichier en Python, qui
  # se comporte pareil partout.
  python3 - "$FICHIER" "$URL_DEMANDEE" <<'PY'
import re, sys
chemin, url = sys.argv[1], sys.argv[2]
with open(chemin) as f:
    contenu = f.read()
contenu = re.sub(r'^URL_PUBLIQUE=.*$', f'URL_PUBLIQUE={url}', contenu, flags=re.M)
with open(chemin, 'w') as f:
    f.write(contenu)
PY
  vert "adresse publique : $URL_DEMANDEE"
fi

set -a
# shellcheck disable=SC1090
. "./$FICHIER"
set +a

COMPOSE="docker compose -f docker-compose.partage.yml"

bleu '→ Construction des images'
echo '  La première fois prend quelques minutes : Maven et npm téléchargent tout.'
$COMPOSE build

bleu '→ Démarrage'
$COMPOSE up -d

# --------------------------------------------------------------------
# Attente
#
# On interroge le site à travers Caddy, et non les conteneurs un par un : ce
# qui compte est ce que verra la personne à qui l'on donne l'adresse.
# --------------------------------------------------------------------
bleu '→ Attente'
LOCALE="http://localhost:${PORT_PUBLIC:-8090}"
pret=non
for _ in $(seq 1 90); do
  site=$(curl -s -o /dev/null -w '%{http_code}' -m 3 "$LOCALE/" || echo 000)
  api=$(curl -s -o /dev/null -w '%{http_code}' -m 3 "$LOCALE/api/public/villes" || echo 000)
  kc=$(curl -s -o /dev/null -w '%{http_code}' -m 3 \
    "$LOCALE/auth/realms/booking-realm/.well-known/openid-configuration" || echo 000)
  if [ "$site" = 200 ] && [ "$api" = 200 ] && [ "$kc" = 200 ]; then pret=oui; break; fi
  sleep 3
done

if [ "$pret" != oui ]; then
  rouge "la pile ne répond pas (site=$site api=$api keycloak=$kc)"
  rouge "voir : $COMPOSE logs --tail=60"
  exit 1
fi
vert 'site, API et Keycloak répondent'

# --------------------------------------------------------------------
# Jeu de démonstration
#
# Sans lui, la personne qui reçoit l'adresse tombe sur un site vide et n'a
# rien à essayer. Le script est idempotent sur les comptes, mais recréerait
# les salons : on ne le rejoue donc que sur une base neuve.
# --------------------------------------------------------------------
salons=$(curl -s -m 5 "$LOCALE/api/public/salons?size=1" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin).get("totalElements",0))' 2>/dev/null || echo 0)

if [ "$salons" = 0 ]; then
  bleu '→ Jeu de démonstration'
  KC_URL="$LOCALE/auth" API_URL="$LOCALE" \
  BASE_URL="$URL_PUBLIQUE" MAILPIT_URL="$URL_PUBLIQUE/courrier" \
  KEYCLOAK_ADMIN="${KEYCLOAK_ADMIN:-admin}" \
  KEYCLOAK_ADMIN_PASSWORD="$KEYCLOAK_ADMIN_PASSWORD" \
    node scripts/donnees-demo.mjs >/dev/null 2>&1 \
      && vert 'salons, prestations, équipes et rendez-vous en place' \
      || jaune 'jeu de démonstration non peuplé — relancer : node scripts/donnees-demo.mjs'
else
  vert "base déjà peuplée ($salons salons) — rien à recréer"
fi

# --------------------------------------------------------------------
printf '\n'
bleu '── À transmettre ──'
printf '  Application   %s\n' "$URL_PUBLIQUE"
printf '  Boîte mail    %s/courrier\n' "$URL_PUBLIQUE"
printf '\n'
bleu '── Comptes de démonstration (mot de passe = identifiant) ──'
printf '  client1       réserver, noter, annuler\n'
printf '  pro1          gérer un salon\n'
printf '  admin         référencer les salons, traiter les demandes\n'
printf '\n'
bleu '── Console Keycloak ──'
printf '  %s/auth/admin  —  %s / voir %s\n' "$URL_PUBLIQUE" "${KEYCLOAK_ADMIN:-admin}" "$FICHIER"
printf '\n'
jaune "Environnement d'essai : l'adresse vaut mot de passe. Aucune donnée réelle de client."
printf '\n'
printf '  Arrêter          %s down\n' "$COMPOSE"
printf '  Tout effacer     %s down -v\n' "$COMPOSE"
printf '  Journaux         %s logs -f api\n' "$COMPOSE"
