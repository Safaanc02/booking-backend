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
Nous. Référence les salons pour le compte des gérants, valide leur mise en ligne, arbitre les litiges, modère les avis.

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

## Parcours pro — du démarchage à la première réservation

Un professionnel ne s'inscrit pas. **L'équipe installe son salon pour lui**, puis lui remet les clés. Ce n'est pas une limitation technique : le paramétrage d'un catalogue — trente prestations, leurs durées, qui fait quoi — est l'étape où l'on perd la quasi-totalité des gérants laissés seuls. La faire à leur place est le produit autant que le logiciel.

| # | Étape | Qui | Écran / route | Objectif |
|---|---|---|---|---|
| 0 | Se manifeste | gérant | `/professionnels` · `POST /api/public/demandes-demo` | Formulaire en trois écrans, aucun compte créé |
| 1 | Prise de contact | conseiller | `admin` → *Demandes* | Qualifier, noter, ordonner les visites |
| 2 | Référencement du salon | conseiller | `admin` → *Référencer* · `POST /api/admin/salons` | Fiche + compte du gérant, en un envoi |
| 3 | Le gérant choisit son mot de passe | gérant | lien reçu par e-mail | Aucun mot de passe ne circule en clair |
| 4 | Paramétrage assisté | conseiller **avec** le gérant | `pro/Prestations`, `Equipe`, `Horaires` | **Pré-remplir par métier** : « Coupe femme 45 min », « Barbe 20 min »… |
| 5 | Mise en ligne | conseiller | `admin` → *À valider* | `EN_ATTENTE` → `ACTIF` |
| 6 | Reçoit sa première résa | gérant | notification | Le moment « aha » |
| 7 | Consulte son agenda | gérant | `pro/Agenda` | Vue jour par défaut sur mobile, semaine sur desktop |
| 8 | Bloque un créneau | gérant | `pro/Agenda` | Pause, rendez-vous perso, congé |

L'étape 0 n'est pas obligatoire : un salon démarché sur le terrain n'a déposé aucune demande, et le conseiller référence directement. Quand la demande existe, le formulaire de référencement s'ouvre prérempli avec ce qu'elle a recueilli, et la demande passe en `CONVERTIE` avec un lien vers le salon créé — c'est ce lien qui rend le rendement du formulaire mesurable.

Les étapes 2 et 5 se confondent quand l'installation se fait sur place, avec le gérant : le conseiller cochant « mettre en ligne tout de suite ».

### Ce que le formulaire demande, et dans quel ordre

Trois écrans plutôt qu'un bloc de quatorze champs. L'ordre n'est pas cosmétique : d'abord ce dont le gérant est fier, ensuite ce qui nous sert à le classer, ses coordonnées en dernier.

| Écran | Champs | Pourquoi là |
|---|---|---|
| L'établissement | **métiers** (plusieurs), nom, ville, quartier, spécialité | Facile à répondre, engage la suite |
| L'activité | ancienneté, taille de l'équipe, local en propriété, outil actuel | Décide de l'ordre des visites |
| Le contact | prénom, nom, téléphone, e-mail, **ICE**, message | Réclamé une fois le reste investi |

Les métiers se cochent, ils ne se choisissent pas. Des boutons radio n'en
acceptaient qu'un : un institut qui fait la coiffure, l'onglerie et
l'esthétique devait en désigner un seul, et le conseiller ne savait donc pas
ce qu'il allait trouver sur place — ni le formulaire de référencement quoi
préremplir. Le premier coché fait office de métier principal.

L'**ICE** (Identifiant Commun de l'Entreprise, quinze chiffres) est facultatif et ferme la marche : c'est l'équivalent marocain du SIRET, il ne sert qu'au contrat, et personne ne le connaît de mémoire. Le demander tôt transforme une prise de contact en formalité administrative.

Deux gardes, pas de captcha : le débit est limité par IP sur `/api/public/**`, et une même adresse ne peut pas redéposer dans les vingt-quatre heures — au-delà, un gérant qui rappelle parce que personne ne l'a contacté est un signal, pas un doublon.

### L'adresse comme identifiant : oui pour les pros, non pour le realm

Les comptes professionnels sont créés par le référencement avec l'adresse pour
identifiant — un seul élément à retenir, six mois plus tard. C'est fait dans le
code, explicitement.

L'option Keycloak `registrationEmailAsUsername` semblait généraliser cette
idée aux inscriptions clients, et retirait un champ du formulaire. Essayée,
puis écartée : elle ne gouverne pas seulement le formulaire, elle réécrit
l'identifiant de **tout** compte, y compris ceux créés par l'API
d'administration et ceux de l'import du realm. `admin`, `pro1` et `client1`
sont devenus `admin@booking.ma` et compagnie, ce qui casse la convention des
comptes de démonstration — mot de passe égal à l'identifiant — sur laquelle
reposent le jeu de données et les six suites de vérification.

Un champ de moins sur le formulaire d'inscription client ne valait pas de
transformer chaque identifiant de test en adresse longue à taper.

**Ce que le gérant ne fait jamais** : créer un établissement. `POST /api/salons` est réservé à l'administration, et l'espace professionnel ne propose aucun formulaire de création. Un compte tombé sans salon — un membre d'équipe, un rattachement manqué — est orienté, pas invité à recommencer.

**Objectif** : de l'étape 2 à l'étape 5 dans **une seule visite**. C'est ce délai, et non celui d'un formulaire d'inscription, qui décide de l'adoption.

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
| Créer un salon | ❌ | ❌ | ❌ | ❌ | ✅ |
| Valider un salon | ❌ | ❌ | ❌ | ❌ | ✅ |
| Gérer les utilisateurs | ❌ | ❌ | ❌ | ❌ | ✅ |

⚠️ Le « **son salon** » de la colonne Gérant est exactement ce que `CustomPermissionEvaluator` est censé vérifier — et ne fait pas aujourd'hui. Voir *Dette technique*, point DT-01.
