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

```bash
# Depuis booking-backend/

# 1. Infrastructure — PostgreSQL (5433) et Keycloak (8081)
cp .env.example .env        # puis adapter les mots de passe
docker compose up -d

# 2. API — port 8080
DB_URL="jdbc:postgresql://localhost:5433/beauty_booking" \
DB_USER=booking_user DB_PASSWORD=beauty123 \
./mvnw spring-boot:run

# 3. Interface — port 5173
cd ../booking-frontend
npm install
npm run dev
```

> Le port PostgreSQL est **5433** et non 5432, pour cohabiter avec d'autres projets
> susceptibles d'occuper le port standard. Il se change via `DB_PORT` dans `.env`.

## Comptes de test

Créés automatiquement par l'import du realm Keycloak (`keycloak/booking-realm-realm.json`).
Mots de passe identiques aux identifiants — **développement local uniquement**.

| Identifiant | Rôle | Peut |
|---|---|---|
| `admin` | admin | Tout |
| `pro1` | pro | Créer et gérer ses salons |
| `client1` | client | Réserver |

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
```

Les deux scripts de navigateur supposent la stack démarrée et au moins un salon
ACTIF paramétré.

## État d'avancement

**Jalon J0 — Assainissement : terminé.** L'application démarre, s'authentifie et
les contrôles de propriété fonctionnent.

**Jalon J1 — Moteur de disponibilité : à faire.** C'est le cœur du produit et il
n'existe pas encore. Deux refontes structurantes l'attendent, détaillées dans
`docs/notion/02-modele-de-donnees.md` :

1. La table `Creneau` doit disparaître au profit d'un calcul à la volée.
2. L'entité `Employe` (praticien) doit être introduite.

## Conventions

- Domaine métier nommé en **français** (salon, prestation, créneau, réservation).
- Montants en **MAD**, `BigDecimal` uniquement — jamais de `double`.
- Téléphones marocains : `0[5-7]XXXXXXXX` ou `+212[5-7]XXXXXXXX`.
- Instants stockés en **UTC**, présentés en `Africa/Casablanca`.
  Attention : le Maroc bascule de UTC+1 à UTC+0 pendant le Ramadan, deux fois par an.
