-- Le client peut déplacer son rendez-vous, et il en est averti.

-- 1. Deux types de notification de plus.
ALTER TABLE notification DROP CONSTRAINT chk_notification_type;

ALTER TABLE notification
    ADD CONSTRAINT chk_notification_type CHECK (type IN (
        'CONFIRMATION_CLIENT',
        'CONFIRMATION_SALON',
        'RAPPEL_CLIENT',
        'ANNULATION_SALON',
        'DEMANDE_AVIS',
        -- Au client, quand il déplace lui-même son rendez-vous : sans nouvel
        -- écrit, il garde en tête l'ancienne heure — et le déplacement produit
        -- l'absence qu'il devait éviter.
        'DEPLACEMENT_CLIENT',
        -- Au salon, pour la même raison vue de l'autre côté : un créneau qui
        -- bouge sans que personne ne le dise, c'est une cliente qu'on attend
        -- pendant qu'elle est ailleurs.
        'DEPLACEMENT_SALON'
    ));

-- 2. L'unicité cesse de s'appliquer aux déplacements.
--
-- La contrainte (reservation, type, canal) sert l'idempotence : un rappel de
-- la veille ne doit pas partir deux fois parce qu'un planificateur repasse ou
-- qu'un redéploiement tombe mal. C'est juste pour tout ce que la machine
-- déclenche seule.
--
-- Un déplacement, non : c'est un geste du client, et rien ne l'empêche de le
-- refaire. Sous l'ancienne contrainte, le deuxième déplacement s'insérait mal,
-- le journal considérait le message déjà envoyé, et la cliente se présentait à
-- l'heure d'avant — avec, en base, la preuve qu'on l'avait « prévenue ».
--
-- Un index unique partiel dit exactement cela : unique, sauf pour ce qui est
-- répétable par nature.
ALTER TABLE notification DROP CONSTRAINT uk_notification_unique;

CREATE UNIQUE INDEX uk_notification_unique
    ON notification (reservation_id, type, canal)
    WHERE type NOT IN ('DEPLACEMENT_CLIENT', 'DEPLACEMENT_SALON');
