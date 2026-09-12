# 🗄️ Modèle de données cible

## Écart entre l'existant et la cible

| Entité actuelle | Verdict | Action |
|---|---|---|
| `User` | 🟡 À enrichir | Séparer identité (Keycloak) et profil ; ajouter `telephone` |
| `Salon` | 🟡 À enrichir | **Supprimer le doublon `owner` / `proprietaire`** ; ajouter ville, CP, lat/lng, description, photos, catégorie, statut |
| `Prestation` | 🟡 À enrichir | Ajouter `categorie`, `actif`, `ordre` ; `duree` devient un `Integer` partout |
| `Creneau` | 🔴 **À supprimer** | Remplacé par le calcul de disponibilité (voir plus bas) |
| `Reservation` | 🔴 À refondre | Perd le lien `Creneau`, gagne `debut`/`fin`, `employe`, `prestation`, `prixFige`, un `statut` en enum |
| — | 🆕 | `Employe` |
| — | 🆕 | `EmployePrestation` |
| — | 🆕 | `HoraireOuverture` |
| — | 🆕 | `Absence` |
| — | 🆕 | `Avis` |

## Schéma cible

```
User ──1:N──> Salon ──1:N──> Employe
                │              │
                │              ├──N:M──> Prestation  (EmployePrestation)
                │              ├──1:N──> HoraireOuverture
                │              └──1:N──> Absence
                │
                ├──1:N──> Prestation
                ├──1:N──> HoraireOuverture   (horaires du salon)
                └──1:N──> Avis

Reservation ──> User (client)
            ──> Salon
            ──> Employe
            ──> Prestation
            └── debut / fin / statut / prixFige
```

## Entités

### `Salon`

| Champ | Type | Notes |
|---|---|---|
| `id` | Long | |
| `owner` | → User | **un seul champ propriétaire**, pas deux |
| `nom`, `description` | String | |
| `adresse`, `ville`, `codePostal` | String | `ville` et `codePostal` **indexés** — base de la recherche |
| `latitude`, `longitude` | Double | « Autour de moi ». Relevées sur une carte si le conseiller les saisit, sinon reprises du centre du quartier, à défaut de la ville (voir `repere_geo`). Les deux ou aucune : `chk_salon_point_complet` refuse la moitié d'un point, qui ne situe rien et se propagerait dans un calcul de distance |
| `telephone`, `email` | String | |
| `categorie` | Enum | `COIFFURE`, `BARBIER`, `ONGLERIE`, `ESTHETIQUE`, `SPA` |
| `photos` | List\<String\> | URLs ; la première sert de couverture |
| `statut` | Enum | `EN_ATTENTE`, `ACTIF`, `SUSPENDU` — **seuls les `ACTIF` sont publics** |
| `noteMoyenne`, `nombreAvis` | Double / Integer | Dénormalisés, recalculés à chaque avis |
| `delaiAnnulationHeures` | Integer | Défaut 24 |

### `RepereGeo` → table `repere_geo`

| Champ | Type | Notes |
|---|---|---|
| `id` | Long | |
| `ville` | String | |
| `quartier` | String | Nul = le repère vaut pour la ville entière |
| `latitude`, `longitude` | Double | Centre du lieu |

Table de référence, alimentée par la migration V13 : 26 villes et 36 quartiers marocains. Ouvrir une ville demande une ligne, pas une livraison de code.

Elle remplace le géocodage par service externe qui était prévu. Trois raisons : aucune clé d'API ni condition d'utilisation à respecter, aucune dépendance réseau au moment où l'on référence un salon, et une précision suffisante pour l'usage réel — classer les salons d'une ville du plus proche au plus lointain. Un centre de quartier situe à quelques centaines de mètres ; l'interface annonce « au quartier près » et n'affiche jamais mieux que le kilomètre entier, pour ne pas prêter à la donnée une précision qu'elle n'a pas. Le conseiller qui veut la rue près colle les coordonnées relevées sur Google Maps.

La comparaison des libellés passe par la fonction SQL `normaliser_libelle`, `IMMUTABLE` et indexée : « Guéliz », « Gueliz » et « GUÉLIZ » sont le même quartier, et « Centre-ville » rejoint « Centre ville ». Un conseiller qui saisit à la main n'a pas à deviner l'orthographe de référence.

### `PhotoSalon` → table `photo_salon`

| Champ | Type | Notes |
|---|---|---|
| `id` | Long | |
| `salon` | → Salon | `ON DELETE CASCADE` |
| `fichier` | String | Nom **engendré par le serveur**, unique. Jamais celui envoyé : un nom d'origine peut porter des séparateurs de chemin ou celui d'un fichier déjà présent |
| `typeMime` | String | `image/jpeg`, `image/png` ou `image/webp`, contraint en base |
| `taille` | Integer | 5 Mo au plus, contraint en base |
| `ordre` | Integer | Rang d'affichage. La première sert de couverture partout |

**Le binaire n'est pas en base.** La table décrit la photo, le fichier vit sur le disque : PostgreSQL n'est pas un serveur de fichiers, et une image en `bytea` alourdit chaque sauvegarde, chaque réplication et chaque requête qui oublie de l'exclure.

Le format est reconnu **aux premiers octets**, pas au type déclaré dans la requête — celui-ci se choisit librement. Un fichier annoncé `image/png` peut être n'importe quoi, et le servir ensuite sous ce type revient à laisser un tiers décider de ce que le navigateur d'un visiteur va interpréter. Pas de SVG non plus : c'est un document XML, il porte des scripts et s'exécute dans le contexte du domaine qui le sert.

⚠️ **Le répertoire des photos doit être persistant.** Sur un hébergement dont le disque est éphémère — la plupart des offres gérées —, les photos disparaîtraient à chaque redéploiement, sans erreur, ne laissant que des images cassées sur les fiches. Le jour où il faudra un stockage objet, c'est `StockagePhotos` qu'on remplacera, et elle seule : le reste du code ne connaît qu'un nom de fichier. La pile partagée monte un volume Docker, et les scripts de sauvegarde et de restauration emportent ce répertoire.

### `Employe`

| Champ | Type | Notes |
|---|---|---|
| `id` | Long | |
| `salon` | → Salon | |
| `user` | → User, *nullable* | Null tant que la personne n'a pas de compte |
| `prenom`, `nom` | String | |
| `photo`, `titre` | String | ex. « Coloriste » |
| `actif` | boolean | Sortie d'effectif sans perdre l'historique |
| `ordre` | Integer | Ordre d'affichage sur la fiche |

### `Prestation`

| Champ | Type | Notes |
|---|---|---|
| `id` | Long | |
| `salon` | → Salon | |
| `nom`, `description` | String | |
| `categorie` | String | Regroupement sur la fiche : « Coupe », « Couleur », « Soin » |
| `prix` | BigDecimal | |
| `dureeMinutes` | **Integer** | ⚠️ Aujourd'hui le DTO est un `String` (« 30min ») parsé en `Integer` → plante |
| `actif`, `ordre` | boolean / Integer | |

### `EmployePrestation`
Table de liaison. Qui sait faire quoi.

| Champ | Type | Notes |
|---|---|---|
| `employe` | → Employe | |
| `prestation` | → Prestation | |
| `dureeSpecifique` | Integer, *nullable* | Le senior fait la couleur en 40 min, l'apprenti en 60 |

### `HoraireOuverture`
Récurrence hebdomadaire.

| Champ | Type | Notes |
|---|---|---|
| `salon` | → Salon, *nullable* | Renseigné pour les horaires du salon |
| `employe` | → Employe, *nullable* | Renseigné pour les horaires d'un praticien |
| `jourSemaine` | Enum | `LUNDI`…`DIMANCHE` |
| `heureDebut`, `heureFin` | LocalTime | |

Deux lignes pour une journée coupée par la pause déjeuner (9h–12h, 14h–19h). Un employé sans horaire propre hérite de ceux du salon.

### `Absence`
Exception ponctuelle : congé, formation, fermeture exceptionnelle.

| Champ | Type |
|---|---|
| `employe` ou `salon` | → Employe / Salon |
| `debut`, `fin` | LocalDateTime |
| `motif` | String |

### `Reservation`

| Champ | Type | Notes |
|---|---|---|
| `id` | Long | |
| `client` | → User | |
| `salon`, `employe`, `prestation` | → | Dénormalisé volontairement : on veut retrouver la résa même si la prestation est supprimée |
| `debut`, `fin` | LocalDateTime | **`fin` est calculée** = `debut` + durée effective |
| `statut` | Enum | `EN_ATTENTE`, `CONFIRMEE`, `ANNULEE_CLIENT`, `ANNULEE_SALON`, `HONOREE`, `ABSENT` |
| `prixFige` | BigDecimal | Le prix **au moment de la réservation** — il ne doit pas bouger si le salon change son tarif |
| `nomPrestationFige` | String | Idem, pour l'historique |
| `noteClient` | String | « Je serai en retard de 5 min » |
| `creeLe`, `annuleeLe` | LocalDateTime | |

⚠️ `ANNULEE_CLIENT` et `ANNULEE_SALON` sont distincts : ils ne comptent pas pareil dans les statistiques ni dans le score de fiabilité du client.

### `Avis`

| Champ | Type | Notes |
|---|---|---|
| `reservation` | → Reservation, unique | **Un avis par réservation** — c'est ce qui empêche les faux avis |
| `salon`, `employe` | → | |
| `note` | Integer | 1 à 5 |
| `commentaire` | String | |
| `reponseSalon` | String | Droit de réponse du gérant |
| `statut` | Enum | `EN_ATTENTE`, `PUBLIE`, `MASQUE` |

## Le moteur de disponibilité

C'est le cœur du produit. Entrée : un salon, une prestation, une date, et un praticien (ou « sans préférence »). Sortie : la liste des heures de début réservables.

```
disponibilites(salonId, prestationId, date, employeId?) -> List<LocalTime>

  duree      = durée de la prestation pour ce praticien
  candidats  = [employeId] si précisé, sinon tous les employés actifs
               du salon capables de faire cette prestation

  creneaux = {}
  pour chaque employe dans candidats :

      # 1. Amplitude de travail ce jour-là
      plages = horaires(employe, jourSemaine(date))
               ou, à défaut, horaires(salon, jourSemaine(date))
      si plages est vide -> employé absent ce jour, on passe

      # 2. On retire les indisponibilités
      occupe = reservations(employe, date) où statut ∈ {EN_ATTENTE, CONFIRMEE}
             + absences(employe, date)
             + absences(salon, date)

      # 3. On balaie chaque plage par pas de 15 min
      pour chaque plage dans plages :
          t = plage.debut
          tant que t + duree <= plage.fin :
              si [t, t+duree] ne chevauche aucun intervalle de `occupe` :
                  creneaux.ajouter(t)
              t = t + 15min

  # 4. Règles métier
  retirer les créneaux commençant dans moins de 2h  (délai de prévenance)
  retirer les créneaux au-delà de J+60              (horizon de réservation)
  retourner creneaux triés, dédoublonnés
```

**Points d'attention**

- **Pas de 15 minutes** : granularité standard du secteur. À rendre configurable par salon plus tard.
- **Dédoublonnage** : en mode « sans préférence », si deux praticiens sont libres à 14h, on n'affiche qu'un seul créneau à 14h. L'attribution du praticien se fait à la confirmation.
- **Test de chevauchement** : `debutA < finB && debutB < finA`. Le piège classique est d'utiliser `<=` et de rejeter deux rendez-vous qui se touchent sans se chevaucher.
- **Performance** : un seul aller-retour en base par jour demandé, jamais une requête par créneau. Cacher le résultat 60 s.

## La double réservation, sérieusement

Deux clients qui cliquent sur 14h à la même seconde : les deux voient le créneau libre, les deux valident. Le contrôle applicatif ne suffit pas.

Trois niveaux, à mettre en place ensemble :

1. **Vérification applicative** au moment du `POST` (rattrape 99 % des cas).
2. **Contrainte d'exclusion PostgreSQL** — la seule garantie réelle :
   ```sql
   ALTER TABLE reservation ADD CONSTRAINT pas_de_chevauchement
     EXCLUDE USING gist (
       employe_id WITH =,
       tstzrange(debut, fin) WITH &&
     ) WHERE (statut IN ('EN_ATTENTE','CONFIRMEE'));
   ```
   *(nécessite `CREATE EXTENSION btree_gist;`)*
3. **Traduction de l'erreur** : intercepter la violation de contrainte et renvoyer un `409 CONFLICT` lisible, pas une stack trace.

Le code actuel n'a que le niveau 1, via `existsByCreneauId` — qui ne protège de rien en concurrence.

## Migrations

Ces changements sont trop lourds pour `ddl-auto=update`, qui ne sait ni renommer ni supprimer une colonne.

**Passer à Flyway dès maintenant**, avant d'écrire la moindre nouvelle entité :

- `V1__baseline.sql` — l'état actuel du schéma
- `V2__suppression_creneau.sql`
- `V3__employes_et_horaires.sql`
- `V4__refonte_reservation.sql`
- `V5__avis.sql`
- `V6__contrainte_exclusion.sql`

Puis `spring.jpa.hibernate.ddl-auto=validate` en permanence.

## Un salon exerce plusieurs métiers

`salon.categorie` n'en autorisait qu'un. Or l'institut de quartier fait
couramment la coiffure, l'onglerie et l'esthétique, et un spa vend du hammam
autant que des soins : forcer un choix rendait le salon **introuvable pour
deux de ses trois activités**.

La table `salon_metier` porte l'ensemble, et `salon.categorie` reste le métier
**principal**. Ce n'est pas une facilité de migration : l'identité visuelle du
salon — la teinte de sa couverture, dans la liste comme sur sa fiche — a
besoin d'une seule couleur. Un établissement qui en afficherait trois n'en
aurait aucune.

| | |
|---|---|
| Clé | `(salon_id, metier)` — un métier ne se déclare pas deux fois |
| Index | `(metier, salon_id)` pour le filtre de la recherche publique |
| Invariant | le métier principal fait toujours partie de l'ensemble, garanti par `Salon.normaliserMetiers()` |
| Chargement | `EAGER`, contre l'usage : l'ensemble est lu à chaque affichage de salon et jamais seul. En `LAZY`, une page de vingt résultats déclenchait vingt requêtes de plus |

Le filtre `GET /api/public/salons?metier=` teste l'appartenance à cet ensemble,
côté serveur. L'interface le faisait sur la page reçue : « Onglerie » pouvait
ne rien rendre alors que la ville comptait plusieurs ongleries, hors des vingt
premiers résultats.
