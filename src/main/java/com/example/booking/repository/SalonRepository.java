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

    List<Salon> findByOwnerIdOrderByNomAsc(Long ownerId);

    Page<Salon> findByStatutOrderByCreeLeAsc(SalonStatut statut, Pageable pageable);
}
