-- Réservations prises par téléphone ou au comptoir.
--
-- Sans cette possibilité, le salon tient deux agendas en parallèle : le nôtre
-- et son carnet papier. Il abandonne en quelques semaines — et pire, les
-- créneaux réservés par téléphone restent proposés en ligne, ce qui produit
-- des doubles réservations bien réelles.
--
-- Le client d'un rendez-vous téléphonique n'a pas de compte : client_id
-- devient facultatif, au profit d'un nom et d'un téléphone libres.

ALTER TABLE reservation ALTER COLUMN client_id DROP NOT NULL;

ALTER TABLE reservation
    ADD COLUMN client_nom_libre       VARCHAR(120),
    ADD COLUMN client_telephone_libre VARCHAR(20),
    -- Qui a saisi la réservation : utile pour distinguer le canal en ligne du
    -- canal comptoir dans les statistiques.
    ADD COLUMN origine VARCHAR(20) NOT NULL DEFAULT 'EN_LIGNE';

ALTER TABLE reservation
    ADD CONSTRAINT chk_reservation_client CHECK (
        client_id IS NOT NULL OR client_nom_libre IS NOT NULL
    ),
    ADD CONSTRAINT chk_reservation_origine CHECK (
        origine IN ('EN_LIGNE', 'TELEPHONE', 'COMPTOIR')
    );
