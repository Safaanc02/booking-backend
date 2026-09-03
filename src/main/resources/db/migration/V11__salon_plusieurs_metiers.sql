-- Un salon exerce plusieurs métiers.
--
-- Le modèle n'en autorisait qu'un : salon.categorie, valeur unique contrainte
-- par un CHECK. Or l'institut de quartier fait couramment la coiffure,
-- l'onglerie et l'esthétique, et un spa vend du hammam autant que du massage.
-- Forcer un choix revenait à rendre le salon introuvable pour deux de ses
-- trois activités.
--
-- salon.categorie est conservée comme métier PRINCIPAL, et ce n'est pas une
-- facilité de migration : l'identité visuelle du salon — la teinte de sa
-- couverture, dans la liste comme sur sa fiche — a besoin d'une seule
-- couleur. Un établissement qui en afficherait trois n'en aurait aucune.

CREATE TABLE salon_metier (
    salon_id BIGINT      NOT NULL REFERENCES salon (id) ON DELETE CASCADE,
    metier   VARCHAR(20) NOT NULL,

    -- La paire est la clé : un métier ne se déclare pas deux fois, et la
    -- contrainte évite d'y penser dans le code.
    PRIMARY KEY (salon_id, metier),

    -- Mêmes valeurs que salon.categorie. Dupliquer la liste n'est pas
    -- élégant, mais une contrainte partagée entre deux tables demanderait un
    -- type énuméré PostgreSQL — dont les migrations sont bien plus rigides
    -- qu'un CHECK, notamment pour retirer une valeur.
    CONSTRAINT chk_salon_metier CHECK (
        metier IN ('COIFFURE', 'BARBIER', 'ONGLERIE', 'ESTHETIQUE', 'SPA'))
);

-- Requête dominante : les salons d'un métier donné, croisée avec le statut et
-- la ville par la recherche publique.
CREATE INDEX idx_salon_metier_metier ON salon_metier (metier, salon_id);

-- Reprise de l'existant : chaque salon garde ce qu'il déclarait, ni plus ni
-- moins. Sans cela, tous les salons disparaîtraient du filtre par métier au
-- premier déploiement.
INSERT INTO salon_metier (salon_id, metier)
SELECT id, categorie FROM salon;
