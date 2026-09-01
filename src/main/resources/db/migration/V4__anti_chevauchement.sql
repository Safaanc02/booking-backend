-- Empêche deux réservations de se chevaucher pour un même praticien.
--
-- Le contrôle applicatif ne suffit pas : deux clients qui valident le même
-- créneau à la même seconde passent tous les deux la vérification avant que
-- l'un ou l'autre n'ait écrit. Seule une contrainte en base est une garantie.
--
-- btree_gist permet de mêler une égalité (employe_id) et un recouvrement
-- d'intervalle (&&) dans une même contrainte d'exclusion.

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE reservation
    ADD CONSTRAINT pas_de_chevauchement
    EXCLUDE USING gist (
        employe_id WITH =,
        tstzrange(debut, fin) WITH &&
    )
    -- Une réservation annulée libère le créneau ; elle ne doit plus bloquer.
    WHERE (statut IN ('EN_ATTENTE', 'CONFIRMEE'));
