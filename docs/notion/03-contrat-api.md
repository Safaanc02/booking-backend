# 🔌 Contrat API

Base : `http://localhost:8080`
Auth : `Authorization: Bearer <JWT Keycloak>` sauf mention « public ».

## Convention de nommage

Aujourd'hui les routes mélangent français et anglais, et `/api/public/**` est déclaré ouvert dans `SecurityConfig` mais aucune route métier ne l'utilise. On fixe la règle :

- **`/api/public/**`** → tout ce qui est accessible sans token (recherche, fiches, disponibilités)
- **`/api/**`** → nécessite un token
- **`/api/pro/**`** → back-office professionnel
- **`/api/admin/**`** → administration plateforme
- Ressources au **pluriel**, en **français** (cohérent avec le domaine métier déjà écrit)

## Public — parcours client

| Méthode | Route | Description |
|---|---|---|
| `GET` | `/api/public/salons?ville=&metier=&q=&lat=&lng=&rayon=&page=&size=` | Recherche paginée. Renvoie note moyenne, prix minimum et **tous les métiers** exercés. `metier` teste l'ensemble, non la seule catégorie principale. `lat`+`lng` classent du plus proche au plus lointain et ajoutent `distanceKm` à chaque salon — les deux ensemble, sinon `400`. `rayon` en km, défaut 25, plafonné à 100. Une recherche située répond `Cache-Control: no-store` et porte l'en-tête `X-Salons-Non-Situes` |
| `GET` | `/api/public/salons/prochaines-dispos?ids=&jours=` | Premier créneau libre de plusieurs salons, en un appel. Calculé sur la prestation la plus courte de chacun — celle qui a le plus de créneaux à montrer. Horizon 7 jours par défaut, 50 salons au plus. Un salon absent de la réponse n'a rien de libre dans l'horizon, ou n'a pas encore de catalogue |
| `GET` | `/api/public/salons/{id}` | Fiche complète : prestations groupées par catégorie, équipe, horaires, photos |
| `GET` | `/api/public/salons/{id}/prestations` | Catalogue seul |
| `GET` | `/api/public/salons/{id}/employes?prestationId=` | Praticiens sachant faire cette prestation |
| `GET` | `/api/public/salons/{id}/disponibilites?prestationId=&date=&employeId=` | **La route la plus appelée du produit** |
| `GET` | `/api/public/salons/{id}/prochaines-dispos?prestationId=&jours=7` | Les 7 prochains jours ayant au moins un créneau — évite au client de cliquer jour par jour |
| `GET` | `/api/public/salons/{id}/avis?page=` | Avis publiés |
| `GET` | `/api/public/villes?q=` | Autocomplétion de la barre de recherche |
| `POST` | `/api/public/demandes-demo` | **Prise de contact d'un professionnel.** `metiers` accepte plusieurs valeurs ; `typeEtablissement` seul reste accepté, pour ne pas casser un appel existant. Ne crée aucun compte. Répond toujours `202`, y compris sur un doublon récent — distinguer les deux cas donnerait le moyen de savoir qui s'est manifesté |

**Exemple — salons autour d'un point**

```http
GET /api/public/salons?lat=33.573&lng=-7.590&rayon=25
```
```json
{
  "content": [
    { "id": 3, "nom": "Nails & Co", "quartier": "Gauthier", "distanceKm": 3.4446 },
    { "id": 2, "nom": "Atlas Barber", "quartier": "Maarif", "distanceKm": 4.1538 }
  ],
  "totalElements": 2
}
```

La position arrive arrondie au millième de degré — cent mètres. C'est l'interface qui arrondit, avant l'envoi : ce qui ne quitte pas le navigateur ne peut être ni journalisé ni mis en cache. Le `no-store` protège le reste : une URL portant la position de quelqu'un n'a rien à faire dans un cache partagé, et c'est de toute façon la réponse la moins réutilisable du service.

Le tri est fait par la base, en deux temps : un cadre latitude/longitude servi par `idx_salon_coordonnees`, puis la distance exacte (haversine) sur les seules lignes retenues, qui filtre le rayon et donne l'ordre. Le cadre seul ne suffirait pas — c'est un carré circonscrit au cercle, ses coins dépassent le rayon de 41 %.

`X-Salons-Non-Situes` compte les salons qui répondent aux critères mais qu'aucun repère ne situe, donc absents du classement. Il vaut 0 dès que la ville figure dans `repere_geo`. L'interface l'affiche : faire disparaître des salons sans le dire est le défaut que cet en-tête existe pour éviter.

**Exemple — disponibilités**

```http
GET /api/public/salons/12/disponibilites?prestationId=45&date=2026-09-15
```
```json
{
  "date": "2026-09-15",
  "prestation": { "id": 45, "nom": "Coupe femme", "dureeMinutes": 45, "prix": 38.00 },
  "creneaux": [
    { "debut": "09:00", "employesDisponibles": [3, 7] },
    { "debut": "09:15", "employesDisponibles": [3] },
    { "debut": "14:30", "employesDisponibles": [7] }
  ]
}
```

## Client authentifié

| Méthode | Route | Rôle | Description |
|---|---|---|---|
| `POST` | `/api/reservations` | client | Créer. `409` si le créneau vient d'être pris |
| `GET` | `/api/reservations/me?statut=&page=` | client | Mes réservations, à venir puis passées |
| `GET` | `/api/reservations/{id}` | client | La sienne uniquement |
| `PATCH` | `/api/reservations/{id}/annuler` | client | `409` si hors délai d'annulation |
| `PATCH` | `/api/reservations/{id}/deplacer` | client | Nouveau créneau |
| `POST` | `/api/avis` | client | Uniquement si la résa est `HONOREE` et sans avis existant |
| `GET` | `/api/moi` | tous | Profil courant, issu du token |
| `PUT` | `/api/moi` | tous | Téléphone, préférences de notification |

**Exemple — création**

```http
POST /api/reservations
{
  "salonId": 12,
  "prestationId": 45,
  "employeId": null,          // null = sans préférence
  "debut": "2026-09-15T14:30:00",
  "noteClient": "Première visite"
}
```
```json
201 Created
{
  "id": 981,
  "statut": "CONFIRMEE",
  "debut": "2026-09-15T14:30:00",
  "fin": "2026-09-15T15:15:00",
  "employe": { "id": 7, "prenom": "Sofia" },
  "prixFige": 38.00
}
```

## Back-office pro

Toutes ces routes vérifient que l'appelant est propriétaire du salon visé.

| Méthode | Route | Description |
|---|---|---|
| `GET` | `/api/pro/salons` | Mes salons |
| `POST` | `/api/pro/salons` | Créer (statut `EN_ATTENTE`) |
| `PUT` | `/api/pro/salons/{id}` | Modifier |
| `GET/POST/PUT/DELETE` | `/api/pro/salons/{id}/prestations[/{pid}]` | Catalogue |
| `GET/POST/PUT/DELETE` | `/api/pro/salons/{id}/employes[/{eid}]` | Équipe |
| `PUT` | `/api/pro/employes/{eid}/prestations` | Qui fait quoi (liste d'ids) |
| `GET/PUT` | `/api/pro/salons/{id}/horaires` | Horaires hebdomadaires |
| `GET/POST/DELETE` | `/api/pro/absences[/{aid}]` | Congés, fermetures |
| `GET` | `/api/pro/salons/{id}/agenda?debut=&fin=&employeId=` | **Vue agenda** |
| `POST` | `/api/pro/salons/{id}/reservations` | Saisir une résa prise par téléphone |
| `PATCH` | `/api/pro/reservations/{id}/statut` | `HONOREE`, `ABSENT`, `ANNULEE_SALON` |
| `POST` | `/api/pro/avis/{id}/reponse` | Droit de réponse |

## Admin

| Méthode | Route | Description |
|---|---|---|
| `POST` | `/api/admin/salons` | **Référencer un salon** : compte du gérant, rôle, fiche et invitation en un envoi |
| `GET` | `/api/admin/demandes-demo?statut=NOUVELLE` | File commerciale |
| `GET` | `/api/admin/demandes-demo/nouvelles` | Décompte, pour la pastille de l'onglet |
| `PATCH` | `/api/admin/demandes-demo/{id}?statut=&note=` | Faire avancer une demande |
| `GET` | `/api/admin/salons?statut=EN_ATTENTE` | File de validation |
| `PATCH` | `/api/admin/salons/{id}/statut` | Valider / suspendre |
| `GET/PUT/DELETE` | `/api/admin/users[/{id}]` | Utilisateurs |
| `PATCH` | `/api/admin/avis/{id}/statut` | Modération |
| `GET` | `/api/admin/stats` | Tableau de bord plateforme |

## Format d'erreur unique

Aujourd'hui **deux `@ControllerAdvice` coexistent** et renvoient des formats et des statuts différents pour la même exception (409 chez l'un, 422 chez l'autre pour `IllegalStateException`). Il faut n'en garder qu'un. Format retenu :

```json
{
  "timestamp": "2026-09-01T14:23:11Z",
  "status": 409,
  "code": "CRENEAU_INDISPONIBLE",
  "message": "Ce créneau vient d'être réservé",
  "details": { "prochainCreneau": "2026-09-15T15:00:00" }
}
```

Le champ `code` est **stable et exploitable par le frontend** ; `message` est destiné à l'humain et peut changer.

| Code | Statut | Quand |
|---|---|---|
| `VALIDATION` | 400 | Champs invalides, `details` liste les champs |
| `NON_AUTHENTIFIE` | 401 | Token absent ou expiré |
| `ACCES_REFUSE` | 403 | Pas propriétaire de la ressource |
| `INTROUVABLE` | 404 | |
| `CRENEAU_INDISPONIBLE` | 409 | Double réservation |
| `HORS_DELAI_ANNULATION` | 409 | Annulation trop tardive |
| `AVIS_DEJA_DEPOSE` | 409 | |
| `ERREUR_INTERNE` | 500 | |

## À ajouter côté technique

- **OpenAPI / Swagger** (`springdoc-openapi-starter-webmvc-ui`) — aucune doc d'API n'existe aujourd'hui.
- **Pagination systématique** sur toute liste pouvant dépasser 50 éléments.
- **Rate limiting** sur `/api/public/**` : la route disponibilités est trivialement scrapable.
- **`Cache-Control: max-age=60`** sur les disponibilités.
