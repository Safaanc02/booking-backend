-- Keycloak reçoit sa propre base, dans la même instance PostgreSQL.
--
-- Séparée de celle de l'application, et non un simple schéma : les deux
-- produits gèrent leurs migrations indépendamment, et Flyway ne doit jamais
-- voir les tables de Keycloak.
--
-- Ce script n'est exécuté qu'à la toute première initialisation du volume.
CREATE DATABASE keycloak OWNER booking_user;
