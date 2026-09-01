package com.example.booking.repository;

import com.example.booking.model.Salon;
import com.example.booking.model.enums.SalonStatut;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SalonRepository extends JpaRepository<Salon, Long> {

    /**
     * Recherche publique. Ville, texte libre et catégorie sont tous facultatifs ;
     * seuls les salons ACTIF remontent.
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
              AND (CAST(:q AS String) IS NULL
                   OR LOWER(s.nom)      LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))
                   OR LOWER(s.adresse)  LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))
                   OR LOWER(s.quartier) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%')))
            """)
    Page<Salon> rechercher(@Param("statut") SalonStatut statut,
                           @Param("ville") String ville,
                           @Param("q") String q,
                           Pageable pageable);

    @Query("SELECT DISTINCT s.ville FROM Salon s WHERE s.ville IS NOT NULL AND s.statut = :statut ORDER BY s.ville")
    List<String> villesDistinctes(@Param("statut") SalonStatut statut);

    List<Salon> findByOwnerIdOrderByNomAsc(Long ownerId);

    Page<Salon> findByStatutOrderByCreeLeAsc(SalonStatut statut, Pageable pageable);
}
