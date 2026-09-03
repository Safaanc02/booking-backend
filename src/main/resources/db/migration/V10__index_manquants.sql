-- Trois index qui manquaient, dont un qui garantit une règle sur laquelle le
-- code s'appuyait déjà sans filet.

-- ------------------------------------------------------------------
-- 1. Unicité de l'adresse e-mail
--
-- Trois endroits appellent findByEmail en attendant un Optional :
-- ReferencementService, pour décider si un compte existe déjà avant d'en
-- créer un ; EquipeService, pour rattacher un membre d'équipe ; et
-- CurrentUserService, pour adopter un miroir local quand l'identifiant
-- Keycloak a changé. Deux lignes portant la même adresse et Spring Data lève
-- IncorrectResultSizeDataAccessException — l'utilisateur voit une erreur 500
-- sans rapport avec ce qu'il faisait.
--
-- Keycloak impose déjà l'unicité de son côté, mais le miroir local n'est pas
-- Keycloak : une adresse modifiée là-bas, un import, un script, et le doublon
-- existe. La règle est donc posée là où elle est vérifiable.
--
-- lower(email) et non email : « Leila@darzine.ma » et « leila@darzine.ma »
-- désignent la même boîte, et le référencement met déjà l'adresse en
-- minuscules avant de chercher.
--
-- Index partiel : l'adresse est facultative, et PostgreSQL admet autant de
-- NULL qu'on veut dans un index unique — mais l'écrire explicitement dit
-- l'intention plutôt que de compter sur ce comportement.
CREATE UNIQUE INDEX uk_users_email_lower
    ON users (lower(email))
    WHERE email IS NOT NULL;

-- ------------------------------------------------------------------
-- 2. L'agenda d'un salon sur une période
--
-- C'est la requête que chaque salon lance en ouvrant son écran, et la seule
-- de la table reservation qui n'avait pas d'index : (employe_id, debut)
-- existait, mais l'agenda filtre par salon, pas par praticien.
--
-- Sans lui, le coût grandit avec le nombre total de réservations, toutes
-- enseignes confondues — c'est-à-dire avec le succès de la plateforme, et
-- non avec l'activité du salon qui consulte.
CREATE INDEX idx_reservation_salon_debut ON reservation (salon_id, debut);

-- ------------------------------------------------------------------
-- 3. La recherche par ville
--
-- Le filtre le plus utilisé de la recherche publique. Le statut y est joint
-- parce que seuls les salons ACTIF remontent : l'index couvre alors les deux
-- conditions d'un coup.
--
-- Reste le LOWER(...) LIKE '%...%' sur le nom, que le joker en tête empêche
-- d'indexer ainsi. À quelques milliers de salons il faudra pg_trgm ; ce n'est
-- pas ce que cet index prétend régler.
CREATE INDEX idx_salon_ville_statut ON salon (ville, statut);
