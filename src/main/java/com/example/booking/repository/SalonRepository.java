package com.example.booking.repository;

import com.example.booking.model.Salon;
import com.example.booking.model.enums.SalonCategorie;
import com.example.booking.model.enums.SalonStatut;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SalonRepository extends JpaRepository<Salon, Long> {

    /**
     * Recherche publique. Ville, texte libre et métier sont tous facultatifs ;
     * seuls les salons ACTIF remontent.
     *
     * Le métier est filtré ici, et non dans le navigateur. L'interface le
     * faisait sur la page reçue, si bien qu'un filtre ne voyait que les vingt
     * premiers résultats : « Onglerie » pouvait ne rien rendre alors que la
     * ville comptait trente salons dont plusieurs ongleries.
     *
     * Le test porte sur l'ensemble des métiers exercés, pas sur la seule
     * catégorie principale — c'est tout l'objet de la table de liaison.
     *
     * Le CAST(:param AS String) n'est pas décoratif : sans lui PostgreSQL ne peut
     * pas inférer le type d'un paramètre nul, le traite comme du bytea, et échoue
     * sur « function lower(bytea) does not exist ».
     *
     * Le LOWER(...) LIKE convient au volume actuel ; au-delà de quelques milliers
     * de salons il faudra un index trigram (pg_trgm) ou de la recherche plein texte.
     */
    @Query("""
            SELECT s FROM Salon s
            WHERE s.statut = :statut
              AND (CAST(:ville AS String) IS NULL
                   OR LOWER(s.ville) = LOWER(CAST(:ville AS String)))
              AND (:metier IS NULL OR :metier MEMBER OF s.metiers)
              AND (CAST(:q AS String) IS NULL
                   OR LOWER(s.nom)      LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))
                   OR LOWER(s.adresse)  LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))
                   OR LOWER(s.quartier) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%')))
            """)
    Page<Salon> rechercher(@Param("statut") SalonStatut statut,
                           @Param("ville") String ville,
                           @Param("q") String q,
                           @Param("metier") SalonCategorie metier,
                           Pageable pageable);

    @Query("SELECT DISTINCT s.ville FROM Salon s WHERE s.ville IS NOT NULL AND s.statut = :statut ORDER BY s.ville")
    List<String> villesDistinctes(@Param("statut") SalonStatut statut);

    /**
     * Identifiant Keycloak du propriétaire. Jointure externe : un salon sans
     * propriétaire (owner mis à NULL par suppression de compte) doit renvoyer
     * une ligne vide, pas disparaître du résultat.
     */
    @Query("SELECT proprietaire.keycloakId FROM Salon s LEFT JOIN s.owner proprietaire WHERE s.id = :id")
    java.util.Optional<String> keycloakIdDuProprietaire(@Param("id") Long id);

    /**
     * Salons que ce compte peut administrer : ceux qu'il possède, et ceux où il
     * est gestionnaire délégué.
     *
     * `findByOwnerId` ne suffisait pas : un gestionnaire ne possède rien, son
     * tableau de bord restait donc vide alors que l'API lui donnait bien accès
     * au salon. « Mes salons » signifie « ceux que je peux gérer ».
     *
     * Jointures explicitement externes des deux côtés : un salon sans
     * propriétaire, ou sans membre d'équipe rattaché à un compte, ne doit pas
     * disparaître du résultat.
     */
    @Query("""
            SELECT DISTINCT s FROM Salon s
            LEFT JOIN s.owner proprietaire
            LEFT JOIN Employe e ON e.salon = s AND e.actif = true
            LEFT JOIN e.user compte
            WHERE proprietaire.keycloakId = :keycloakId
               OR (compte.keycloakId = :keycloakId
                   AND e.role = com.example.booking.model.enums.RoleEmploye.GESTIONNAIRE)
            ORDER BY s.nom
            """)
    List<Salon> queJePeuxGerer(@Param("keycloakId") String keycloakId);


    /**
     * Recherche autour d'un point, du plus proche au plus lointain.
     *
     * Requête native, et non JPQL : le calcul de distance demande des
     * fonctions trigonométriques que JPQL ne porte pas, et surtout il doit
     * s'exécuter dans la base. Trier en Java supposerait de rapatrier tous les
     * salons du réseau à chaque recherche, et paginerait sur un ensemble déjà
     * tronqué — la deuxième page ne voudrait plus rien dire.
     *
     * Deux temps, pour que cela tienne à l'échelle :
     *
     *   1. un cadre latitude/longitude, servi par idx_salon_coordonnees, écarte
     *      d'emblée l'essentiel des lignes ;
     *   2. la distance exacte, calculée sur ce qui reste, filtre le rayon et
     *      donne l'ordre.
     *
     * Le cadre seul ne suffirait pas : c'est un carré circonscrit au cercle,
     * ses coins dépassent le rayon de 41 %. Promettre « à moins de 25 km » et
     * rendre un salon à 33 serait faux.
     *
     * Le LATERAL calcule la distance une fois et la rend disponible au filtre
     * comme au tri ; l'écrire deux fois inviterait à ne corriger qu'une des
     * deux. SELECT s.* seul : la projection reste exactement l'entité.
     *
     * Le tri secondaire sur l'identifiant n'est pas décoratif — deux salons du
     * même quartier partagent le centre de ce quartier, donc la distance au
     * mètre près. Sans second critère, leur ordre relatif varierait d'une page
     * à l'autre et l'un des deux pourrait n'apparaître sur aucune.
     *
     * Statut et métier passent en texte : Hibernate ne lie pas fidèlement un
     * enum dans une requête native. Les CAST(... AS varchar) sont, eux, ce qui
     * permet à PostgreSQL de typer un paramètre nul.
     */
    @Query(value = """
            SELECT s.* FROM salon s
            CROSS JOIN LATERAL (
                SELECT 2 * 6371.0088 * asin(least(1.0, sqrt(
                          power(sin(radians(s.latitude - :lat) / 2), 2)
                        + cos(radians(:lat)) * cos(radians(s.latitude))
                          * power(sin(radians(s.longitude - :lng) / 2), 2)
                      ))) AS km
            ) d
            WHERE s.statut = CAST(:statut AS varchar)
              AND (CAST(:ville AS varchar) IS NULL
                   OR LOWER(s.ville) = LOWER(CAST(:ville AS varchar)))
              AND s.latitude  BETWEEN :latMin AND :latMax
              AND s.longitude BETWEEN :lngMin AND :lngMax
              AND d.km <= :rayonKm
              AND (CAST(:metier AS varchar) IS NULL OR EXISTS (
                      SELECT 1 FROM salon_metier m
                       WHERE m.salon_id = s.id AND m.metier = CAST(:metier AS varchar)))
              AND (CAST(:q AS varchar) IS NULL
                   OR LOWER(s.nom)      LIKE LOWER(CONCAT('%', CAST(:q AS varchar), '%'))
                   OR LOWER(s.adresse)  LIKE LOWER(CONCAT('%', CAST(:q AS varchar), '%'))
                   OR LOWER(s.quartier) LIKE LOWER(CONCAT('%', CAST(:q AS varchar), '%')))
            ORDER BY d.km, s.id
            """,
            countQuery = """
            SELECT count(*) FROM salon s
            CROSS JOIN LATERAL (
                SELECT 2 * 6371.0088 * asin(least(1.0, sqrt(
                          power(sin(radians(s.latitude - :lat) / 2), 2)
                        + cos(radians(:lat)) * cos(radians(s.latitude))
                          * power(sin(radians(s.longitude - :lng) / 2), 2)
                      ))) AS km
            ) d
            WHERE s.statut = CAST(:statut AS varchar)
              AND (CAST(:ville AS varchar) IS NULL
                   OR LOWER(s.ville) = LOWER(CAST(:ville AS varchar)))
              AND s.latitude  BETWEEN :latMin AND :latMax
              AND s.longitude BETWEEN :lngMin AND :lngMax
              AND d.km <= :rayonKm
              AND (CAST(:metier AS varchar) IS NULL OR EXISTS (
                      SELECT 1 FROM salon_metier m
                       WHERE m.salon_id = s.id AND m.metier = CAST(:metier AS varchar)))
              AND (CAST(:q AS varchar) IS NULL
                   OR LOWER(s.nom)      LIKE LOWER(CONCAT('%', CAST(:q AS varchar), '%'))
                   OR LOWER(s.adresse)  LIKE LOWER(CONCAT('%', CAST(:q AS varchar), '%'))
                   OR LOWER(s.quartier) LIKE LOWER(CONCAT('%', CAST(:q AS varchar), '%')))
            """,
            nativeQuery = true)
    Page<Salon> rechercherAutour(@Param("statut") String statut,
                                 @Param("ville") String ville,
                                 @Param("lat") double lat,
                                 @Param("lng") double lng,
                                 @Param("latMin") double latMin,
                                 @Param("latMax") double latMax,
                                 @Param("lngMin") double lngMin,
                                 @Param("lngMax") double lngMax,
                                 @Param("rayonKm") double rayonKm,
                                 @Param("q") String q,
                                 @Param("metier") String metier,
                                 Pageable pageable);

    /**
     * Salons répondant aux mêmes critères mais dépourvus de coordonnées.
     *
     * Ils ne peuvent pas figurer dans un classement par distance. Les taire
     * serait pourtant les effacer sans un mot : l'interface annonce leur
     * nombre plutôt que de laisser croire le réseau plus pauvre qu'il n'est.
     */
    @Query("""
            SELECT count(s) FROM Salon s
            WHERE s.statut = :statut
              AND s.latitude IS NULL
              AND (CAST(:ville AS String) IS NULL
                   OR LOWER(s.ville) = LOWER(CAST(:ville AS String)))
              AND (:metier IS NULL OR :metier MEMBER OF s.metiers)
              AND (CAST(:q AS String) IS NULL
                   OR LOWER(s.nom)      LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))
                   OR LOWER(s.adresse)  LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))
                   OR LOWER(s.quartier) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%')))
            """)
    long compterNonSitues(@Param("statut") SalonStatut statut,
                          @Param("ville") String ville,
                          @Param("q") String q,
                          @Param("metier") SalonCategorie metier);

    List<Salon> findByOwnerIdOrderByNomAsc(Long ownerId);

    Page<Salon> findByStatutOrderByCreeLeAsc(SalonStatut statut, Pageable pageable);
}
