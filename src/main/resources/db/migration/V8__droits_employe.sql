-- Droits des membres d'équipe.
--
-- Jusqu'ici un Employe n'était qu'une ligne d'agenda : il pouvait être
-- rattaché à un compte, mais ce compte n'obtenait aucun droit — pas même la
-- consultation de son propre planning. Conséquence pratique : un propriétaire
-- qui confiait une boutique devait prêter son mot de passe.
--
-- Deux besoins distincts, donc deux rôles :
--
--   PRATICIEN     — consulte son propre planning, rien d'autre.
--   GESTIONNAIRE  — gère le salon comme le propriétaire : catalogue, équipe,
--                   horaires, absences, agenda, avis. Ne peut ni supprimer le
--                   salon ni en changer le propriétaire.

ALTER TABLE employe
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'PRATICIEN';

ALTER TABLE employe
    ADD CONSTRAINT chk_employe_role CHECK (role IN ('PRATICIEN', 'GESTIONNAIRE'));

-- Un même compte ne peut être rattaché qu'une fois au même salon : sans cela
-- deux fiches employé pourraient porter des rôles contradictoires pour la même
-- personne, et le droit appliqué dépendrait de l'ordre de lecture.
CREATE UNIQUE INDEX uk_employe_compte_salon
    ON employe (salon_id, user_id)
    WHERE user_id IS NOT NULL;

-- Requête ajoutée par l'évaluateur de permissions : « de quels salons ce
-- compte est-il membre, et à quel titre ? »
CREATE INDEX idx_employe_user ON employe (user_id) WHERE user_id IS NOT NULL;
