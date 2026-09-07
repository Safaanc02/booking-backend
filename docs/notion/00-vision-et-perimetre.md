# 🎯 Vision & périmètre

## En une phrase

> Une marketplace de réservation beauté : le client trouve un salon près de chez lui et réserve un créneau en ligne 24/7 ; le professionnel remplit son agenda sans décrocher son téléphone.

## Le modèle à deux faces

| | Côté client (B2C) | Côté pro (B2B) |
|---|---|---|
| **Qui** | Particulier qui cherche un salon | Gérant de salon, praticien indépendant |
| **Problème** | Réserver hors des heures d'ouverture, comparer prix et dispos | Téléphone qui sonne pendant une coupe, créneaux vides, no-shows |
| **Promesse** | Réserver en 3 clics, à toute heure | Agenda rempli, moins d'appels, moins de lapins |
| **Ce qu'on lui demande** | Rien (gratuit) | Abonnement mensuel par établissement |
| **Sans l'autre face** | Aucun salon à réserver | Aucun client à recevoir |

### Pas de commission sur les rendez-vous

Un abonnement fixe, et rien sur les réservations. Ce n'est pas une facilité de facturation, c'est la position commerciale : un salon qui verse un pourcentage sur un client qu'il avait déjà finit par comparer la facture au bénéfice, et cherche à sortir de la plateforme. Planity en a fait un argument affiché ; le prendre au sérieux nous prive d'un revenu variable, mais rend la relation défendable devant un gérant qui compte.

Corollaire : le revenu ne dépend que du nombre d'établissements abonnés. La priorité n'est donc pas le volume de rendez-vous, mais la **rétention** — ce qui explique le modèle d'installation décrit ci-dessous.

### Le côté pro d'abord

Le piège classique du two-sided market : on ne peut pas lancer les deux faces en même temps. **On amorce par le côté pro** (démarchage manuel de 10–20 salons sur une seule ville) avant d'ouvrir le côté client.

Et on n'attend pas du professionnel qu'il s'inscrive. **C'est l'équipe qui installe le salon** : compte, fiche, catalogue, équipe, horaires. Le paramétrage d'un catalogue est précisément ce qui fait abandonner un gérant — le lui épargner est la meilleure garantie qu'il reste. Il reçoit ensuite un lien pour choisir son mot de passe, et trouve son salon déjà prêt. Voir *Personas & parcours* pour le détail.

## Les briques de Planity, décomposées

| Brique | Description | MVP ? |
|---|---|---|
| Recherche géolocalisée | Par ville, prestation, note, dispo, et **« autour de moi »** — classement par distance depuis la position du navigateur | ✅ |
| Fiche salon | Prestations, prix, horaires, équipe, avis | ✅ |
| Photos de salon | Envoi d'images, galerie | 🟡 V1 — une identité visuelle est générée en attendant |
| Moteur de disponibilité | Calcul des créneaux libres en temps réel | ✅ **cœur du produit** |
| Tunnel de réservation | Prestation → praticien → créneau → confirmation | ✅ |
| Compte client | Historique, réservations à venir, annulation | ✅ |
| Agenda pro | Vue jour/semaine, drag & drop, blocage manuel | ✅ |
| Back-office pro | Prestations, équipe, horaires, congés | ✅ |
| Notifications | Confirmation + rappel J-1 (email, puis SMS) | 🟡 V1 |
| Avis clients | Note + commentaire après passage | 🟡 V1 |
| Fiche client (CRM) | Historique, notes praticien, no-shows | 🟡 V1 |
| Acompte / empreinte CB | Anti no-show via Stripe | 🔴 V2 |
| Statistiques pro | CA, taux de remplissage, top prestations | 🔴 V2 |
| Caisse & encaissement | TPE, tickets, TVA | ❌ hors périmètre |
| Stock produits | Réassort, inventaire | ❌ hors périmètre |
| Fidélité / cartes cadeaux | | ❌ hors périmètre |
| App mobile native | Le web responsive suffit d'abord | ❌ hors périmètre |

## Deux décisions structurantes

Ces deux points changent le modèle de données actuel en profondeur. Tout le reste en découle.

### 1. Les créneaux se **calculent**, ils ne se stockent pas

Le code actuel a une table `Creneau` : une ligne par plage horaire réservable, créée à la main, liée en `OneToOne` à une `Reservation`.

Ça ne tient pas à l'échelle d'un Planity :

- Un salon ouvert 6j/7, 10h/j, avec une granularité de 15 min et 3 praticiens = **~2 900 lignes par semaine et par salon** à pré-générer, maintenir et purger.
- Une prestation de 45 min doit bloquer 3 créneaux consécutifs. Le `OneToOne` `Creneau ↔ Reservation` le rend **structurellement impossible**.
- Changer un horaire d'ouverture obligerait à régénérer des milliers de lignes.

**À la place** : on stocke les *règles* de disponibilité (horaires d'ouverture, absences, congés) et les *réservations confirmées*. Les créneaux libres sont calculés à la volée pour un couple (date, prestation, praticien).

**Conséquence** : la table `Creneau` disparaît. `Reservation` porte directement `debut` et `fin`. Voir *Modèle de données* pour l'algorithme.

### 2. On réserve **un praticien**, pas un salon

Sur Planity, l'étape « avec qui ? » est centrale. Le modèle actuel n'a aucune notion d'employé.

Sans praticien, on ne peut pas :

- accepter deux réservations simultanées dans un salon à 3 fauteuils (on plafonnerait à 1 client à la fois) ;
- gérer un planning par personne (Sofia travaille le samedi, Karim non) ;
- proposer « sans préférence », qui est le choix par défaut de la majorité des clients ;
- restreindre une prestation à qui sait la faire (le balayage, pas l'apprenti).

**Conséquence** : nouvelle entité `Employe` + table de liaison `EmployePrestation`.

### 3. Un salon exerce **plusieurs métiers**

Ajoutée après coup, pour la même raison que les deux précédentes : une valeur
unique ne tenait pas. L'institut de quartier fait la coiffure, l'onglerie et
l'esthétique ; un spa vend du hammam autant que des soins. N'en retenir qu'un
rendait le salon **introuvable pour deux de ses trois activités**.

**Conséquence** : table `salon_metier`, et `salon.categorie` conservée comme
métier *principal* — l'identité visuelle du salon a besoin d'une seule couleur.

## Hypothèses posées

Elles sont à confirmer, mais tout le reste des documents part de là :

- **Marketplace multi-salons**, pas un SaaS mono-salon.
- Verticales de départ : coiffure, barbier, onglerie, esthétique, **hammam & spa**.
  Un salon en exerce **plusieurs** — l'institut de quartier fait couramment les
  trois premières.
- **Marché marocain** : interface en français, fuseau `Africa/Casablanca`, devise
  **MAD**, numéros au format `+212`, quartier plutôt que code postal.
- **Mobile-first**, et davantage qu'ailleurs : le trafic marocain est
  massivement mobile, souvent en 4G. Le poids des pages est un critère, pas un
  détail.
- Keycloak reste le fournisseur d'identité (déjà en place, realm `booking-realm`).
- Lancement sur **une seule ville** pour amorcer la densité.

> Ces trois premières lignes disaient « marché français, fuseau Europe/Paris,
> devise EUR » — l'énoncé d'origine, avant que le produit ne soit décidé
> marocain. Le code, lui, était en MAD et en `Africa/Casablanca` depuis le
> début : c'est le document qui avait pris du retard.

## Comment on saura que ça marche

| Indicateur | Cible à 6 mois |
|---|---|
| Salons actifs (≥ 1 résa/semaine) | 20 |
| Réservations / mois | 500 |
| Taux de complétion du tunnel | > 60 % |
| Taux de no-show | < 8 % |
| Part de réservations hors heures d'ouverture | > 40 % *(c'est là qu'est la valeur)* |
