-- Demande d'avis au lendemain d'une visite.
--
-- Le type de notification est contraint en base, et pas seulement en Java.
-- C'est voulu — une valeur d'enum inconnue de la base est un bug qu'on préfère
-- voir au premier essai plutôt qu'à la lecture d'un rapport six mois plus
-- tard. Mais cela veut dire qu'ajouter un type demande une migration : sans
-- elle, l'application accepte l'envoi, la base le refuse, et l'appelant reçoit
-- un compte rendu qui annonce des envois qui n'ont pas eu lieu.
--
-- C'est exactement ce qui s'est produit en développement : « 1 demande
-- déclenchée », zéro courriel, et la contrainte violée dans les journaux.

ALTER TABLE notification DROP CONSTRAINT chk_notification_type;

ALTER TABLE notification
    ADD CONSTRAINT chk_notification_type CHECK (type IN (
        'CONFIRMATION_CLIENT',
        'CONFIRMATION_SALON',
        'RAPPEL_CLIENT',
        'ANNULATION_SALON',
        -- Au client, le lendemain d'un rendez-vous honoré. Sans sollicitation,
        -- les avis ne viennent pas : on ne retourne sur un site que mécontent,
        -- et un réseau qui ne demande rien récolte surtout des plaintes.
        'DEMANDE_AVIS'
    ));
