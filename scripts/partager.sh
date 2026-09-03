#!/usr/bin/env bash
#
# Met l'application debout sur une seule adresse, pour la faire essayer.
#
#   ./scripts/partager.sh --tunnel     # adresse publique, tout compris
#   ./scripts/partager.sh              # essai local, sur le port 8090
#   ./scripts/partager.sh https://…    # adresse que vous fournissez
#   ./scripts/partager.sh --arreter    # tout arrêter
#   ./scripts/partager.sh --production  # sans la boîte aux lettres de test
#
# Construit les images, démarre la pile, attend qu'elle réponde, peuple le jeu
# de démonstration, puis affiche l'adresse à transmettre et les comptes.
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

FICHIER=partage.env
# La démonstration est superposée par défaut : ce script sert à faire essayer
# l'application, et sans boîte aux lettres lisible on ne peut pas suivre une
# invitation de gérant. --production s'en passe, et exige alors un vrai
# serveur d'envoi dans partage.env.
DEMO=oui
for a in "$@"; do [ "$a" = --production ] && DEMO=non; done
COMPOSE="docker compose -f docker-compose.partage.yml"
[ "$DEMO" = oui ] && COMPOSE="$COMPOSE -f docker-compose.demo.yml"
PID_TUNNEL=.tunnel.pid
LOG_TUNNEL=.tunnel.log

# --------------------------------------------------------------------
# Arrêt
# --------------------------------------------------------------------
if [ "${1:-}" = "--arreter" ]; then
  if [ -f "$PID_TUNNEL" ]; then
    kill "$(cat "$PID_TUNNEL")" 2>/dev/null && vert 'tunnel fermé' || true
    rm -f "$PID_TUNNEL"
  fi
  set -a; [ -f "./$FICHIER" ] && . "./$FICHIER"; set +a
  $COMPOSE down && vert 'pile arrêtée'
  exit 0
fi

TUNNEL=non
if [ "${1:-}" = "--tunnel" ]; then TUNNEL=oui; shift; fi
[ "${1:-}" = "--production" ] && shift
URL_DEMANDEE="${1:-}"

# --------------------------------------------------------------------
# Préalables
# --------------------------------------------------------------------
docker info >/dev/null 2>&1 || {
  rouge 'Docker ne répond pas. Démarrez Docker Desktop ou OrbStack.'
  exit 1
}

[ -d ../booking-frontend ] || {
  rouge '../booking-frontend est introuvable.'
  rouge 'Les deux dépôts doivent être clonés côte à côte dans le même dossier.'
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
  # et l'application refuse de démarrer en dessous.
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

lire_env() { set -a; . "./$FICHIER"; set +a; }
lire_env
PORT="${PORT_PUBLIC:-8090}"

# --------------------------------------------------------------------
# Tunnel
#
# Ouvert ici, et non par une commande à côté : son adresse n'est connue
# qu'après son démarrage. La donner à la main revient à copier une valeur
# depuis une sortie encore en train de défiler — et à coller, tôt ou tard,
# l'exemple de la documentation.
# --------------------------------------------------------------------
if [ "$TUNNEL" = oui ]; then
  command -v cloudflared >/dev/null 2>&1 || {
    rouge 'cloudflared est introuvable.'
    rouge 'Installer : HOMEBREW_NO_REQUIRE_TAP_TRUST=1 brew install cloudflared'
    exit 1
  }

  # Un tunnel déjà ouvert pointe sur l'ancienne adresse : on le remplace.
  if [ -f "$PID_TUNNEL" ]; then
    kill "$(cat "$PID_TUNNEL")" 2>/dev/null || true
    rm -f "$PID_TUNNEL"
  fi

  bleu '→ Ouverture du tunnel'
  : > "$LOG_TUNNEL"
  # nohup et non « & » seul : le tunnel doit survivre à la fin du script,
  # sinon l'adresse se ferme au moment où on la transmet.
  nohup cloudflared tunnel --url "http://localhost:$PORT" \
    >> "$LOG_TUNNEL" 2>&1 &
  echo $! > "$PID_TUNNEL"

  URL_DEMANDEE=""
  for _ in $(seq 1 40); do
    URL_DEMANDEE=$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "$LOG_TUNNEL" \
      | head -1 || true)
    [ -n "$URL_DEMANDEE" ] && break
    sleep 1
  done

  [ -n "$URL_DEMANDEE" ] || {
    rouge "cloudflared n'a pas annoncé d'adresse — voir $LOG_TUNNEL"
    exit 1
  }
  vert "tunnel ouvert sur $URL_DEMANDEE"
fi

# --------------------------------------------------------------------
# Adresse publique
#
# Elle est inscrite dans les jetons émis par Keycloak et dans les liens des
# e-mails : une valeur fantaisiste ne se voit qu'au moment où quelqu'un essaie
# de se connecter, et rien dans les journaux ne l'explique. On refuse donc une
# adresse dont le nom ne résout pas — le cas s'est produit avec l'exemple de
# la documentation, collé tel quel.
# --------------------------------------------------------------------
if [ -n "$URL_DEMANDEE" ]; then
  URL_DEMANDEE="${URL_DEMANDEE%/}"

  HOTE=$(python3 -c 'import sys,urllib.parse as u; print(u.urlparse(sys.argv[1]).hostname or "")' \
    "$URL_DEMANDEE")
  [ -n "$HOTE" ] || { rouge "adresse illisible : $URL_DEMANDEE"; exit 1; }

  # Le résolveur du réseau local n'est pas une autorité : certaines box ne
  # répondent pas sur les sous-domaines de tunnel, alors que le reste du monde
  # les résout parfaitement. Un premier jet refusait pour cette raison un
  # tunnel qui fonctionnait. On interroge donc aussi un résolveur public.
  resout() {
    python3 -c 'import socket,sys; socket.getaddrinfo(sys.argv[1], None)' "$1" 2>/dev/null && return 0
    command -v dig >/dev/null 2>&1 || return 1
    [ -n "$(dig +short @1.1.1.1 "$1" 2>/dev/null)" ]
  }

  if ! resout "$HOTE"; then
    if [ "$TUNNEL" = oui ]; then
      # cloudflared vient d'annoncer cette adresse et sa connexion est
      # établie : on lui fait davantage confiance qu'au DNS d'ici.
      jaune "« $HOTE » ne résout pas depuis ce réseau."
      jaune 'Vos essayeurs y accéderont ; vous, servez-vous de l’adresse locale.'
    else
      rouge "le nom « $HOTE » ne résout pas."
      rouge 'Donnez une adresse réelle, ou lancez : ./scripts/partager.sh --tunnel'
      exit 1
    fi
  fi

  # sed -i diffère entre BSD et GNU : on réécrit le fichier en Python, qui se
  # comporte pareil partout.
  python3 - "$FICHIER" "$URL_DEMANDEE" <<'PY'
import re, sys
chemin, url = sys.argv[1], sys.argv[2]
with open(chemin) as f:
    contenu = f.read()
contenu = re.sub(r'^URL_PUBLIQUE=.*$', f'URL_PUBLIQUE={url}', contenu, flags=re.M)
with open(chemin, 'w') as f:
    f.write(contenu)
PY
  lire_env
  vert "adresse publique : $URL_PUBLIQUE"
fi

# --------------------------------------------------------------------
bleu '→ Construction des images'
echo '  La première fois prend quelques minutes : Maven et npm téléchargent tout.'
$COMPOSE build

bleu '→ Démarrage'
$COMPOSE up -d

# --------------------------------------------------------------------
# Attente
#
# On interroge le site à travers le proxy, et non les conteneurs un par un :
# ce qui compte est ce que verra la personne à qui l'on donne l'adresse.
# --------------------------------------------------------------------
bleu '→ Attente'
LOCALE="http://localhost:$PORT"
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

# L'émetteur inscrit dans les jetons doit être celui que l'API vérifie. C'est
# la seule incohérence qui ne se manifeste qu'à la première connexion.
emetteur=$(curl -s -m 5 "$LOCALE/auth/realms/booking-realm/.well-known/openid-configuration" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["issuer"])' 2>/dev/null || echo '')
if [ "$emetteur" = "$URL_PUBLIQUE/auth/realms/booking-realm" ]; then
  vert 'Keycloak annonce la bonne adresse'
else
  jaune "Keycloak annonce $emetteur au lieu de $URL_PUBLIQUE/auth/realms/booking-realm"
fi

# --------------------------------------------------------------------
# Jeu de démonstration
#
# Sans lui, la personne qui reçoit l'adresse tombe sur un site vide et n'a
# rien à essayer. On ne le rejoue que sur une base neuve : il recréerait les
# salons.
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
[ "$DEMO" = oui ] && printf '  Boîte mail    %s/courrier\n' "$URL_PUBLIQUE"
if [ "$URL_PUBLIQUE" != "$LOCALE" ]; then
  # L'adresse locale ne sert qu'à vérifier que la pile répond.
  #
  # Elle ne permet pas de se servir de l'application : Keycloak émet des URL
  # absolues vers l'adresse publique — c'est une protection contre
  # l'injection d'en-tête Host, pas un réglage — et la vérification de
  # session ne revient jamais. Le site reste sur « Chargement… ». Une pile
  # est configurée pour une seule adresse à la fois.
  printf '  Vérification  %s  (répond, mais ne permet pas de se connecter)\n' "$LOCALE"
fi
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
if [ "$TUNNEL" = oui ]; then
  jaune "L'adresse se ferme si vous éteignez ce Mac, et change au prochain tunnel."
fi
printf '\n'
printf '  Tout arrêter     ./scripts/partager.sh --arreter\n'
printf '  Journaux         %s logs -f api\n' "$COMPOSE"
