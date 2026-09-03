# Booking.ma

Marketplace de réservation beauté au Maroc — coiffure, barbier, onglerie, hammam.
Le client réserve en ligne 24h/24 ; le salon remplit son agenda sans décrocher le téléphone.

Deux dépôts Git distincts, côte à côte :

- `booking-backend` — API Spring Boot 3.5 / Java 17 / PostgreSQL / Keycloak.
  Porte aussi l'infrastructure (`docker-compose.yml`, `keycloak/`) et la
  documentation produit (`docs/notion/`).
- `booking-frontend` — React 19 / Vite / Tailwind 4.

> Les deux dépôts dérivent déjà l'un de l'autre. Un monorepo simplifierait
> les évolutions qui touchent les deux côtés — voir QA-07 au backlog.

## Démarrage

Prérequis : Docker, JDK 17+, Node 20+.

Trois terminaux.

```bash
# ── Terminal 1 — infrastructure et API, depuis booking-backend/
cp .env.example .env          # puis adapter les mots de passe
docker compose up -d          # PostgreSQL (5433), Keycloak (8081), Mailpit (8025)
./scripts/lancer-api.sh       # API sur 8080, charge .env

# ── Terminal 2 — interface, depuis booking-frontend/
npm install
npm run dev                   # http://localhost:5173

# ── Terminal 3 — jeu de données, depuis booking-backend/
node scripts/donnees-demo.mjs
```

Sans la troisième étape, l'application est vide : aucun salon à réserver.

> Le port PostgreSQL est **5433** et non 5432, pour cohabiter avec d'autres projets
> susceptibles d'occuper le port standard. Il se change via `DB_PORT` dans `.env`.

## Jeu de données de démonstration

Une base vide ne permet pas d'essayer grand-chose : il n'y a aucun salon à
réserver. Le script installe quatre salons validés dans trois villes, un
cinquième en attente de validation, leurs équipes, catalogues et horaires,
puis des rendez-vous passés, des réservations à venir et quelques avis.

```bash
node scripts/donnees-demo.mjs
```

Tout passe par l'API, jamais par des `INSERT` directs : le jeu de données ne
peut donc ni contredire les règles métier, ni dériver du schéma.

> ⚠️ **Recréer le conteneur Keycloak efface tous les comptes créés après
> l'import.** En mode `start-dev`, Keycloak garde ses données en mémoire :
> seuls `admin`, `pro1` et `client1` — présents dans le fichier de realm —
> reviennent. Les comptes `pro.*` et `equipe.*` disparaissent, et les salons
> en base se retrouvent sans propriétaire joignable. Après tout
> `docker compose up -d keycloak` qui recrée le conteneur, relancer
> `donnees-demo.mjs` — ou faire une remise à zéro complète.
>
> L'application sait en revanche récupérer un changement d'identifiant
> Keycloak pour un compte qui existe toujours : elle adopte le miroir local
> au lieu d'échouer.

### Remise à zéro

```bash
./scripts/reinitialiser.sh          # détruit le volume, relance les conteneurs
./mvnw spring-boot:run              # Flyway reconstruit le schéma
node scripts/donnees-demo.mjs       # dans un autre terminal
```

## Comptes de test

Mots de passe identiques aux identifiants — **développement local uniquement**.

`admin`, `pro1` et `client1` viennent de l'import du realm
(`keycloak/booking-realm-realm.json`). Les comptes `pro.*` sont créés par le
script de démonstration, qui les déclare dans Keycloak et leur attribue le
rôle `pro`.

| Identifiant | Rôle | Possède |
|---|---|---|
| `admin` | admin | Rien — valide les salons, traverse tous les contrôles de propriété |
| `client1` | client | Rien — réserve, note, annule |
| `pro1` | pro | Atlas Barber (Casablanca) |
| `pro.darzine` | pro | Dar Zine (Marrakech) |
| `pro.nails` | pro | Nails & Co (Casablanca) |
| `pro.firdaws` | pro | Hammam Al Firdaws (Rabat) |
| `pro.anfa` | pro | Salon Anfa — en attente de validation |

### Membres d'équipe de Dar Zine

| Identifiant | Rôle | Peut |
|---|---|---|
| `equipe.sofia` | PRATICIEN | Consulter **son** planning, rien d'autre |
| `equipe.youssef` | GESTIONNAIRE | Administrer Dar Zine comme Leila — sauf le supprimer |

Une fiche d'équipe rattachée à un compte donne des droits. Deux niveaux :

- **PRATICIEN** — `/mon-planning`. Aucun accès à l'agenda du salon ni aux
  réglages.
- **GESTIONNAIRE** — délégation complète : catalogue, équipe, horaires,
  absences, agenda, avis. Ni suppression du salon, ni changement de
  propriétaire. C'est ce qui permet à un gérant d'enseigne de confier une
  boutique **sans prêter son mot de passe**.

Une fiche sans compte rattaché reste une simple ligne d'agenda, sans aucun
droit — l'écran d'équipe l'indique explicitement.

Le compte doit exister avant le rattachement : il est créé dans Keycloak, et
l'application n'en garde un miroir qu'après une première connexion. Le
rattachement se fait ensuite par email.

⚠️ Un membre d'équipe a besoin du rôle Keycloak `pro` pour atteindre
`/api/pro/**`. Le rôle applicatif (PRATICIEN ou GESTIONNAIRE) se joue ensuite
ressource par ressource.

**Un propriétaire par salon**, volontairement : connecté avec l'un de ces
comptes on ne voit que son propre salon, et toute tentative sur un autre
renvoie 403. C'est le cloisonnement le plus important à vérifier — un jeu de
données où un seul compte possède tout ne l'exerce pas.

### Inscription d'un nouveau professionnel

Il n'y a **aucun parcours d'inscription professionnelle** : un compte créé par
le formulaire public n'obtient aucun rôle métier et retombe sur `client`, donc
ne peut pas référencer de salon. Aujourd'hui il faut passer par la console
Keycloak (Users → Add user → Credentials → Role mapping → `pro`), ou suivre ce
que fait `donnees-demo.mjs` avec l'API d'administration.

Console Keycloak : http://localhost:8081 (identifiants dans `.env`).

## Services

| Service | URL |
|---|---|
| Interface | http://localhost:5173 |
| API | http://localhost:8080 |
| Keycloak | http://localhost:8081 |
| PostgreSQL | `localhost:5433` |
| Mailpit (emails de test) | http://localhost:8025 |

> **Mailpit** capture tous les emails sans jamais les livrer. C'est là qu'on
> vérifie une confirmation ou un rappel, plutôt que de supposer qu'ils partent.

## Vérifier que tout fonctionne

```bash
# API publique, sans authentification
curl 'http://localhost:8080/api/public/salons?ville=Casablanca'

# Obtenir un token
curl -s -X POST http://localhost:8081/realms/booking-realm/protocol/openid-connect/token \
  -d client_id=booking-app -d username=pro1 -d password=pro1 -d grant_type=password
```

## Vérifications automatisées

```bash
cd booking-backend  && ./mvnw test              # moteur de disponibilité
cd booking-frontend && npm run verifier:tunnel  # parcours client, vrai navigateur
cd booking-frontend && npm run verifier:pro     # installation d'un salon
cd booking-frontend && npm run verifier:avis    # cycle d'un avis client
cd booking-frontend && npm run verifier:referencement  # arrivée d'un salon, de l'admin au gérant
cd booking-frontend && npm run verifier:demande-demo   # prise de contact, de la demande au salon
```

Les scripts de navigateur supposent la stack démarrée et au moins un salon
ACTIF paramétré — le jeu de démonstration suffit.

⚠️ Ils **écrivent** : réservations, avis, et un salon supplémentaire pour
`verifier:pro` comme pour `verifier:referencement` (ce dernier crée aussi un
compte de gérant, avec une adresse différente à chaque exécution). Relancer `donnees-demo.mjs` après une remise à zéro rend un
état propre. `verifier:tunnel` a besoin de Mailpit pour la section
« annulation depuis l'email » ; sans lui, elle s'annonce non exécutée.

> **Si `./mvnw test` semble bloqué**, c'est presque toujours la sonde
> Testcontainers : elle interroge le socket Docker, et attend longtemps si le
> démon est présent mais ne répond plus. Vérifier `docker info`, relancer
> OrbStack au besoin, ou cibler les classes sans Docker :
> `./mvnw test -Dtest='JetonAnnulationTest,SeauJetonsTest,DisponibiliteServiceTest'`

## État d'avancement

**Jalon J0 — Assainissement : terminé.** L'application démarre, s'authentifie et
les contrôles de propriété fonctionnent.

**Jalon J1 — Moteur de disponibilité : à faire.** C'est le cœur du produit et il
n'existe pas encore. Deux refontes structurantes l'attendent, détaillées dans
`docs/notion/02-modele-de-donnees.md` :

1. La table `Creneau` doit disparaître au profit d'un calcul à la volée.
2. L'entité `Employe` (praticien) doit être introduite.

## Sécurité — deux points à traiter avant toute mise en ligne

**1. Un mot de passe traîne dans l'historique Git.**
`commandes.docx` contenait des identifiants en clair. Le fichier n'est plus
suivi, mais **il reste dans l'historique** : n'importe qui ayant accès au dépôt
peut le récupérer. Deux actions, dans cet ordre :

```bash
# a. Changer le mot de passe concerné — c'est le seul geste qui protège vraiment.
# b. Purger l'historique, puis forcer la poussée (à coordonner avec l'équipe :
#    tous les clones existants devront être refaits).
git filter-repo --invert-paths --path commandes.docx
git push --force --all
```

**2. La documentation d'API est ouverte.** `/swagger-ui.html` et `/v3/api-docs`
décrivent toute la surface d'attaque. En production : `OPENAPI_ACTIF=false`, ou
une restriction au réseau interne.

## Conventions

- Domaine métier nommé en **français** (salon, prestation, créneau, réservation).
- Montants en **MAD**, `BigDecimal` uniquement — jamais de `double`.
- Téléphones marocains : `0[5-7]XXXXXXXX` ou `+212[5-7]XXXXXXXX`.
- Instants stockés en **UTC**, présentés en `Africa/Casablanca`.
  Attention : le Maroc bascule de UTC+1 à UTC+0 pendant le Ramadan, deux fois par an.
- Le schéma appartient à **Flyway**. `ddl-auto=validate` : une entité qui
  diverge du schéma fait échouer le démarrage, elle ne le modifie pas.
- Format d'erreur unique `{ timestamp, status, code, message, details? }`.
  `code` est stable et destiné au client, `message` peut évoluer.

## Limitation de débit

`/api/public/**` est plafonné par adresse : 120 requêtes en rafale, 240 par
minute en régime établi. La route des disponibilités est autrement trivialement
aspirable.

⚠️ Les compteurs sont **en mémoire, donc par instance**. Avec deux instances
derrière un répartiteur, la limite effective double. Passer à un compteur
partagé (Redis) avant toute mise à l'échelle horizontale.
