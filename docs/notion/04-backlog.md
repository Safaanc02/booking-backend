# 📋 Backlog

> **Dans Notion** : sélectionner un tableau → `⋮⋮` → *Convertir en base de données*. Les colonnes Priorité, Statut et Épique deviennent des propriétés filtrables.

**Estimation** en points de complexité (1 = quelques heures, 8 = presque une semaine).
**Priorité** : `P0` bloquant · `P1` MVP · `P2` V1 · `P3` plus tard.

## Épique 0 — Stabilisation

Rien ne peut être construit tant que ces points ne sont pas réglés. Détail dans *Dette technique*.

| ID | Story | Prio | Est. |
|---|---|:-:|:-:|
| ST-01 | Réparer `CustomPermissionEvaluator` : les 3 méthodes `isOwnerX` appelées par les `@PreAuthorize` n'existent pas | P0 | 3 |
| ST-02 | Unifier la casse des rôles Keycloak (`admin` vs `ADMIN`, `PRO` vs `PROPRIETAIRE`) | P0 | 2 |
| ST-03 | Réparer le montage React : `AuthProvider` n'enveloppe pas `App`, l'app plante au démarrage | P0 | 1 |
| ST-04 | Supprimer la seconde instance Keycloak (`main.jsx` vs `keycloak.js`) | P0 | 1 |
| ST-05 | Ne garder qu'un seul `@ControllerAdvice` | P0 | 2 |
| ST-06 | Sortir les secrets du dépôt (mot de passe PostgreSQL, `commandes.docx`) | P0 | 2 |
| ST-07 | Installer Flyway, figer une baseline, passer en `ddl-auto=validate` | P0 | 3 |
| ST-08 | Supprimer le doublon `owner` / `proprietaire` sur `Salon` | P0 | 2 |
| ST-09 | `Prestation.duree` en `Integer` de bout en bout (le `parseInt("30min")` actuel plante) | P0 | 2 |

**Total : 18 points**

## Épique 1 — Socle du modèle métier

| ID | Story | Prio | Est. | Dépend de |
|---|---|:-:|:-:|---|
| MD-01 | Entité `Employe` + CRUD | P1 | 5 | ST-07 |
| MD-02 | Liaison `EmployePrestation` (qui fait quoi, durée spécifique) | P1 | 3 | MD-01 |
| MD-03 | Entité `HoraireOuverture` (salon et employé) | P1 | 5 | MD-01 |
| MD-04 | Entité `Absence` | P1 | 3 | MD-01 |
| MD-05 | Refonte `Reservation` : `debut`/`fin`, `employe`, `prixFige`, statut en enum | P1 | 5 | MD-01 |
| MD-06 | Suppression de `Creneau` + migration des données | P1 | 3 | MD-05 |
| MD-07 | Enrichir `Salon` : ville, CP, lat/lng, photos, catégorie, statut | P1 | 3 | ST-08 |

**Total : 27 points**

## Épique 2 — Moteur de disponibilité

Le cœur du produit. À traiter avec le plus grand soin de test.

| ID | Story | Prio | Est. | Dépend de |
|---|---|:-:|:-:|---|
| DI-01 | Service de calcul des créneaux libres (algorithme du doc *Modèle de données*) | P1 | 8 | MD-03 |
| DI-02 | `GET /disponibilites` — un jour, une prestation, un praticien optionnel | P1 | 3 | DI-01 |
| DI-03 | `GET /prochaines-dispos` — les N prochains jours ayant du libre | P1 | 3 | DI-01 |
| DI-04 | Mode « sans préférence » : agrégation et dédoublonnage inter-praticiens | P1 | 3 | DI-01 |
| DI-05 | Contrainte d'exclusion PostgreSQL anti-double-réservation | P1 | 3 | MD-05 |
| DI-06 | Traduction de la violation de contrainte en `409 CRENEAU_INDISPONIBLE` | P1 | 2 | DI-05 |
| DI-07 | **Suite de tests du moteur** : chevauchements, pause déjeuner, absences, changement d'heure, résa qui déborde de la plage | P1 | 5 | DI-01 |

**Total : 27 points**

⚠️ DI-07 n'est pas négociable. Un bug de disponibilité se traduit par un client qui se présente pour rien.

## Épique 3 — Recherche & découverte

| ID | Story | Prio | Est. |
|---|---|:-:|:-:|
| RE-01 | `GET /api/public/salons` avec filtres ville / catégorie / texte | P1 | 5 |
| RE-02 | Index base sur `ville`, `code_postal`, `statut` | P1 | 1 |
| RE-03 | Prix minimum + prochaine dispo renvoyés dès la liste de résultats | P1 | 3 |
| RE-04 | Autocomplétion des villes | P2 | 2 |
| RE-05 | ~~Géocodage à la création du salon + tri par distance~~ — ✅ **fait** | P2 | 5 |

**Total : 16 points**

**RE-05, ce qui a été livré.** Le tri par distance existe, sans service de géocodage. Un salon est situé par ses coordonnées relevées si le conseiller les colle depuis une carte, sinon par le centre de son quartier, sinon par celui de sa ville — les repères viennent de la table `repere_geo` (26 villes, 36 quartiers). Le classement se fait en base : cadre latitude/longitude servi par index, puis haversine exacte sur les lignes retenues. Côté client, un bouton « Salons autour de moi » sous la barre de recherche, la position arrondie à cent mètres avant l'envoi, et une phrase qui dit la précision réelle plutôt que de la laisser deviner.

Renoncer à un service externe évite une clé d'API, des conditions d'utilisation et une dépendance réseau au moment où l'on référence un salon, pour une précision qui suffit à l'usage : classer les salons d'une ville. À revoir le jour où il faudra une carte, ou une distance à la rue près sur tout le réseau.

## Épique 4 — Frontend client

Tous les fichiers de `booking-frontend/src/pages` et `components` sont **actuellement vides**. Tout est à écrire.

| ID | Story | Prio | Est. |
|---|---|:-:|:-:|
| FE-01 | Socle : React Router, layout, Tailwind, client axios avec intercepteur JWT | P0 | 5 |
| FE-02 | Refonte de l'auth : Keycloak en `check-sso` (pas `login-required`) pour laisser le site public accessible | P0 | 3 |
| FE-03 | `Home` — barre de recherche, catégories, salons en avant | P1 | 5 |
| FE-04 | `Results` — liste, filtres, pagination | P1 | 5 |
| FE-05 | `SalonDetails` — photos, prestations par catégorie, équipe, avis | P1 | 8 |
| FE-06 | `Reservation` — stepper prestation → praticien → créneau → confirmation | P1 | 8 |
| FE-07 | `CalendarSlots` — sélecteur de jour + grille de créneaux | P1 | 5 |
| FE-08 | `Account` — réservations à venir / passées, annulation | P1 | 5 |
| FE-09 | États vides, chargements, erreurs (`Loader`, messages) | P1 | 3 |
| FE-10 | Responsive mobile-first sur tout le tunnel | P1 | 5 |
| FE-11 | `Legal` — CGU, confidentialité, mentions légales | P1 | 2 |

**Total : 54 points**

## Épique 5 — Back-office pro

| ID | Story | Prio | Est. |
|---|---|:-:|:-:|
| PR-01 | Onboarding guidé en 4 étapes, prestations pré-remplies par métier | P1 | 8 |
| PR-02 | Agenda jour / semaine | P1 | 8 |
| PR-03 | Gestion des prestations | P1 | 5 |
| PR-04 | Gestion de l'équipe + affectation des prestations | P1 | 5 |
| PR-05 | Édition des horaires hebdomadaires | P1 | 5 |
| PR-06 | Congés et fermetures exceptionnelles | P1 | 3 |
| PR-07 | Saisie manuelle d'une réservation prise par téléphone | P1 | 3 |
| PR-08 | Marquer une résa `HONOREE` / `ABSENT` | P1 | 2 |
| PR-09 | Déplacement d'un rendez-vous en glisser-déposer | P2 | 5 |
| PR-10 | Fiche client : historique, notes, compteur de no-shows | P2 | 5 |

**Total : 49 points**

## Épique 6 — Notifications

| ID | Story | Prio | Est. |
|---|---|:-:|:-:|
| NO-01 | Envoi d'emails transactionnels (templates + service) | P2 | 5 |
| NO-02 | Confirmation de réservation au client et au salon | P2 | 3 |
| NO-03 | Rappel J-1 planifié | P2 | 5 |
| NO-04 | Annulation en un clic depuis l'email (lien signé) | P2 | 3 |
| NO-05 | Email de demande d'avis à J+1 | P2 | 3 |
| NO-06 | SMS de rappel (Twilio / OVH) | P3 | 5 |

**Total : 24 points**

## Épique 7 — Avis

| ID | Story | Prio | Est. |
|---|---|:-:|:-:|
| AV-01 | Entité `Avis` + dépôt limité aux résas `HONOREE` | P2 | 5 |
| AV-02 | Affichage sur la fiche salon + note moyenne dénormalisée | P2 | 3 |
| AV-03 | Droit de réponse du salon | P2 | 3 |
| AV-04 | Modération admin | P2 | 3 |

**Total : 14 points**

## Épique 8 — Administration

| ID | Story | Prio | Est. |
|---|---|:-:|:-:|
| AD-01 | File de validation des salons | P1 | 3 |
| AD-02 | Gestion des utilisateurs | P2 | 3 |
| AD-03 | Tableau de bord plateforme | P3 | 5 |

**Total : 11 points**

## Épique 9 — Qualité & exploitation

| ID | Story | Prio | Est. |
|---|---|:-:|:-:|
| QA-01 | Tests d'intégration sur le tunnel de réservation (Testcontainers) | P1 | 8 |
| QA-02 | OpenAPI / Swagger | P1 | 2 |
| QA-03 | CI GitHub Actions : build + tests sur chaque PR | P1 | 3 |
| QA-04 | Docker Compose : PostgreSQL + Keycloak + realm pré-importé | P1 | 5 |
| QA-05 | Rate limiting sur `/api/public/**` | P2 | 3 |
| QA-06 | Logs structurés + traçage des erreurs | P2 | 3 |
| QA-07 | Monorepo ou submodules : les deux dépôts dérivent déjà | P2 | 2 |

**Total : 26 points**

## Récapitulatif

| Épique | Points | Priorité dominante |
|---|:-:|:-:|
| 0 — Stabilisation | 18 | P0 |
| 1 — Modèle métier | 27 | P1 |
| 2 — Disponibilité | 27 | P1 |
| 3 — Recherche | 16 | P1 |
| 4 — Frontend client | 54 | P1 |
| 5 — Back-office pro | 49 | P1 |
| 6 — Notifications | 24 | P2 |
| 7 — Avis | 14 | P2 |
| 8 — Administration | 11 | P1/P2 |
| 9 — Qualité | 26 | P1 |
| **Total** | **266** | |

**Périmètre MVP (P0 + P1) : ~200 points.** À 20 points par semaine pour une personne à plein temps, cela représente **environ 10 semaines**.
