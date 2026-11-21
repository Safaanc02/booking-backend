-- « Mes réservations » lit par client, et rien ne l'indexait.
--
-- Seize index existaient sur le schéma ; aucun pour findByClientIdOrderByDebutDesc,
-- la requête qui ouvre la page la plus consultée par une cliente connectée.
-- Invisible à cinq salons ; à cent mille rendez-vous, c'est un balayage de
-- toute la table à chaque ouverture de l'écran.
CREATE INDEX IF NOT EXISTS idx_reservation_client ON reservation (client_id, debut DESC);
