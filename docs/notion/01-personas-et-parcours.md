# 👥 Personas & parcours

## Les quatre personas

### Clara — la cliente
28 ans, urbaine, salariée. Réserve depuis son téléphone, souvent le soir dans son canapé ou dans les transports.

- **Objectif** : trouver un créneau samedi matin près de chez elle, sans appeler.
- **Frustrations** : les salons ne répondent pas pendant leurs heures d'ouverture ; elle ne sait pas combien ça va coûter avant d'y être.
- **Ce qui la fait partir** : devoir créer un compte avant de voir les créneaux, un tunnel de plus de 4 étapes, pas de prix affiché.
- **Contrainte produit** : le compte se crée **à la dernière étape**, jamais avant.

### Karim — le gérant
42 ans, propriétaire d'un salon de 3 fauteuils. Pas geek, gère tout depuis un iPad à l'accueil.

- **Objectif** : remplir les creux du mardi et du jeudi après-midi ; arrêter de perdre 40 min/jour au téléphone.
- **Frustrations** : les no-shows du samedi lui coûtent cher ; il ne sait pas d'où viennent ses nouveaux clients.
- **Ce qui le fait partir** : un paramétrage initial trop long. Si créer ses prestations et ses horaires prend plus de 20 min, il abandonne.
- **Contrainte produit** : onboarding pro guidé, avec des prestations pré-remplies par métier.

### Sofia — la praticienne
26 ans, coiffeuse salariée chez Karim. Ne gère rien, consulte.

- **Objectif** : voir son planning du jour, savoir qui arrive et pour quoi.
- **Contrainte produit** : un rôle en **lecture seule sur son propre agenda**, sans accès aux réglages du salon ni aux autres praticiens.

### Admin plateforme
Nous. Valide les salons à l'inscription, arbitre les litiges, modère les avis.

- **Contrainte produit** : un salon n'est **pas visible publiquement tant qu'il n'est pas validé**. Le champ `statut` sur `Salon` sert à ça.

## Parcours client — de la recherche à l'avis

| # | Étape | Écran | Ce qui se passe côté API | Point de friction à surveiller |
|---|---|---|---|---|
| 1 | Cherche « coiffeur Lyon » | `Home` | `GET /api/public/salons?ville=` | La recherche doit répondre en < 300 ms |
| 2 | Parcourt les résultats | `Results` | idem, paginé | Afficher **prix à partir de** et **prochaine dispo** dès la liste |
| 3 | Ouvre une fiche | `SalonDetails` | `GET /api/public/salons/{id}` | Photos + prestations + avis sur un seul écran |
| 4 | Choisit une prestation | `SalonDetails` | — | Prix et durée visibles avant de cliquer |
| 5 | Choisit un praticien | `Reservation` (étape 1) | `GET .../employes?prestationId=` | **« Sans préférence » présélectionné** |
| 6 | Choisit un créneau | `Reservation` (étape 2) | `GET .../disponibilites?date=` | Si rien de libre : proposer les 3 prochains jours ouverts |
| 7 | Se connecte / s'inscrit | Keycloak | redirection OIDC | **C'est ici qu'on perd le plus de monde.** Jamais plus tôt. |
| 8 | Confirme | `Reservation` (étape 3) | `POST /api/reservations` | Récapitulatif : quoi, qui, quand, combien |
| 9 | Reçoit la confirmation | email | job async | Avec un lien d'annulation en un clic |
| 10 | Reçoit un rappel J-1 | email / SMS | job planifié | Le levier n°1 contre le no-show |
| 11 | Annule ou déplace | `Account` | `PATCH /api/reservations/{id}` | Politique d'annulation : libre jusqu'à H-24 |
| 12 | Laisse un avis | email J+1 | `POST /api/avis` | Uniquement si la résa est passée en `HONOREE` |

**Règle d'or du tunnel** : de l'étape 3 à l'étape 8, il ne doit jamais y avoir plus de **4 écrans**. Chaque écran supplémentaire coûte environ 20 % de conversion.

## Parcours pro — de l'inscription à la première réservation

| # | Étape | Écran | Objectif |
|---|---|---|---|
| 1 | Crée son compte | Keycloak (rôle `pro`) | — |
| 2 | Renseigne son salon | `pro/Onboarding` | Nom, adresse, téléphone, photos |
| 3 | Ajoute ses prestations | `pro/Prestations` | **Pré-remplir par métier** : « Coupe femme 45 min », « Barbe 20 min »… |
| 4 | Ajoute son équipe | `pro/Equipe` | Au moins lui-même ; coche qui fait quoi |
| 5 | Déclare ses horaires | `pro/Horaires` | Par jour, avec pause déjeuner |
| 6 | Attend la validation admin | — | Statut `EN_ATTENTE` → `ACTIF` |
| 7 | Reçoit sa première résa | notification | Le moment « aha » |
| 8 | Consulte son agenda | `pro/Agenda` | Vue jour par défaut sur mobile, semaine sur desktop |
| 9 | Bloque un créneau | `pro/Agenda` | Pause, rendez-vous perso, congé |

**Objectif d'onboarding** : de l'étape 1 à 5 en **moins de 20 minutes**. C'est le principal facteur d'abandon côté pro.

## Matrice des droits

| Action | Visiteur | Client | Praticien | Gérant | Admin |
|---|:-:|:-:|:-:|:-:|:-:|
| Chercher, voir une fiche salon | ✅ | ✅ | ✅ | ✅ | ✅ |
| Voir les créneaux libres | ✅ | ✅ | ✅ | ✅ | ✅ |
| Réserver | ❌ | ✅ | ✅ | ✅ | ✅ |
| Voir / annuler **ses** réservations | ❌ | ✅ | ✅ | ✅ | ✅ |
| Voir **son** agenda | ❌ | ❌ | ✅ | ✅ | ✅ |
| Voir l'agenda de **tout le salon** | ❌ | ❌ | ❌ | ✅ | ✅ |
| Gérer prestations / équipe / horaires | ❌ | ❌ | ❌ | ✅ *(son salon)* | ✅ |
| Créer un salon | ❌ | ❌ | ❌ | ✅ | ✅ |
| Valider un salon | ❌ | ❌ | ❌ | ❌ | ✅ |
| Gérer les utilisateurs | ❌ | ❌ | ❌ | ❌ | ✅ |

⚠️ Le « **son salon** » de la colonne Gérant est exactement ce que `CustomPermissionEvaluator` est censé vérifier — et ne fait pas aujourd'hui. Voir *Dette technique*, point DT-01.
