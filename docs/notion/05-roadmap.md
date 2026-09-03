# 🗺️ Roadmap

Hypothèse : **1 développeur à plein temps, ~20 points par semaine**. À deux, diviser par 1,7 — pas par 2, la coordination coûte.

## Vue d'ensemble

| Jalon | Durée | Objectif | Livrable démontrable |
|---|:-:|---|---|
| **J0 — Assainir** | 1 sem. | L'app démarre, les droits fonctionnent | Une démo qui ne plante pas |
| **J1 — Le moteur** | 3 sem. | Calcul de disponibilité fiable | `GET /disponibilites` + sa suite de tests |
| **J2 — Le tunnel** | 3 sem. | Un client réserve de bout en bout | **Première réservation réelle** |
| **J3 — Le pro** | 3 sem. | L'équipe installe un salon, le gérant tient son agenda | Référencement + back-office |
| **J4 — Le pilote** | 2 sem. | 5 salons réels, vraies réservations | Retours terrain |
| **J5 — Rétention** | 3 sem. | Notifications et avis | Baisse du no-show mesurée |

**MVP jouable en public : fin J4, soit ~12 semaines.**

## J0 — Assainir · semaine 1

Épique 0 en entier (18 pts). Semaine ingrate, non contournable : neuf de ces points cassent des fonctionnalités existantes. Construire par-dessus reviendrait à empiler sur du sable.

- [ ] `CustomPermissionEvaluator` réparé
- [ ] Casse des rôles unifiée
- [ ] Frontend qui démarre
- [ ] Un seul gestionnaire d'exceptions
- [ ] Secrets sortis du dépôt
- [ ] Flyway installé, `ddl-auto=validate`
- [ ] Docker Compose (PostgreSQL + Keycloak avec realm importé)

**Critère de sortie** : `git clone && docker compose up && npm run dev` fonctionne sur une machine vierge.

## J1 — Le moteur · semaines 2 à 4

Épiques 1 et 2 (54 pts). La partie techniquement la plus dense, et elle est **invisible à l'écran**. Ne pas céder à la tentation de commencer par l'interface : tout le reste dépend de ce calcul.

- [ ] `Employe`, `EmployePrestation`, `HoraireOuverture`, `Absence`
- [ ] `Reservation` refondue, `Creneau` supprimée
- [ ] Service de disponibilité
- [ ] Contrainte d'exclusion PostgreSQL
- [ ] Tests couvrant les cas limites

**Critère de sortie** : un jeu de données de test (1 salon, 3 praticiens, 8 prestations, horaires avec pause déjeuner, 2 congés) et une réponse vérifiée à la main.

**Risque** : sous-estimer les cas limites. Prévoir 3 à 5 jours de tampon.

## J2 — Le tunnel · semaines 5 à 7

Épiques 3 et 4 (70 pts — la tranche la plus lourde).

- [ ] Socle frontend : routeur, Tailwind, client API, auth `check-sso`
- [ ] Recherche → résultats → fiche salon
- [ ] Stepper de réservation
- [ ] Espace compte client
- [ ] Responsive mobile

**Critère de sortie** : une personne extérieure au projet réserve un créneau sur son téléphone, sans aide.

**Risque** : 54 points de frontend à écrire de zéro. Si ça glisse, sacrifier `Home` (une barre de recherche suffit) plutôt que le stepper.

## J3 — Le pro · semaines 8 à 10

Épique 5 + AD-01 (52 pts).

- [ ] Onboarding guidé
- [ ] Agenda jour / semaine
- [ ] Prestations, équipe, horaires, congés
- [ ] Saisie manuelle des résas téléphoniques
- [ ] Validation admin des salons

**Critère de sortie** : Karim met son salon en ligne seul, en moins de 20 minutes.

**Point de vigilance** : PR-07 (saisie manuelle) paraît secondaire. Sans lui, le salon tient deux agendas en parallèle — le nôtre et son carnet papier — et abandonne en deux semaines.

## J4 — Le pilote · semaines 11 et 12

Aucune nouvelle fonctionnalité. On installe **5 salons réels sur une seule ville** et on observe.

- [ ] Installation accompagnée, sur place
- [ ] Observation de vraies réservations
- [ ] Mesures : complétion du tunnel, temps d'onboarding, no-shows
- [ ] Correction de ce que le terrain révèle

**C'est le jalon le plus important du plan.** Tout ce qui a été supposé jusqu'ici sera confronté au réel, et une partie sera fausse.

## J5 — Rétention · semaines 13 à 15

Épiques 6 et 7 (38 pts).

- [ ] Confirmation + rappel J-1
- [ ] Annulation en un clic
- [ ] Avis clients

**Critère de sortie** : taux de no-show mesuré avant / après le rappel J-1.

## Au-delà

| Sujet | Déclencheur | Pourquoi attendre |
|---|---|---|
| Acompte Stripe | No-show > 10 % malgré les rappels | Très efficace, mais freine la conversion |
| Statistiques pro | Les salons demandent « combien j'ai fait ce mois-ci ? » | Argument de rétention B2B |
| SMS | Email plafonne sous 40 % d'ouverture | Coût réel par message |
| App mobile native | > 2 000 résas/mois | Le web responsive suffit largement avant |
| 2ᵉ ville | 20 salons actifs sur la 1ʳᵉ | La densité prime sur la couverture |

## L'essentiel en une phrase

**Assainir → le moteur → le client → le pro → le terrain → la rétention.**

Deux erreurs classiques à éviter :

1. **Commencer par le frontend** parce que c'est visible. Sans moteur de disponibilité, on construit une maquette.
2. **Repousser le pilote terrain** pour « finir d'abord ». Cinq vrais salons en semaine 11 valent mieux qu'un produit parfait en semaine 20.
