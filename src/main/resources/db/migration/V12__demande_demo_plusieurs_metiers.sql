-- Une demande de démonstration déclare plusieurs métiers.
--
-- Le formulaire public n'en acceptait qu'un, par boutons radio : le même
-- défaut que salon.categorie, un cran plus tôt dans le parcours. Un institut
-- qui fait la coiffure, l'onglerie et l'esthétique devait en choisir un, et
-- le conseiller ne savait donc pas ce qu'il allait trouver sur place.
--
-- La conséquence n'est pas seulement documentaire : le formulaire de
-- référencement se préremplit depuis la demande, et il ne pouvait proposer
-- qu'un métier sur trois.
--
-- type_etablissement reste le métier principal, comme salon.categorie.

CREATE TABLE demande_demo_metier (
    demande_id BIGINT      NOT NULL REFERENCES demande_demo (id) ON DELETE CASCADE,
    metier     VARCHAR(30) NOT NULL,

    PRIMARY KEY (demande_id, metier),

    -- Mêmes valeurs que demande_demo.type_etablissement, AUTRE compris : le
    -- prospect doit pouvoir ne pas se ranger dans une case, et c'est au
    -- conseiller de trancher au téléphone.
    CONSTRAINT chk_demande_metier CHECK (
        metier IN ('COIFFURE', 'BARBIER', 'ONGLERIE', 'ESTHETIQUE',
                   'SPA_HAMMAM', 'AUTRE'))
);

-- Reprise de l'existant : chaque demande garde ce qu'elle déclarait.
INSERT INTO demande_demo_metier (demande_id, metier)
SELECT id, type_etablissement FROM demande_demo;
