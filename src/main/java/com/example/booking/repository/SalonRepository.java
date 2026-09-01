package com.example.booking.repository;

import com.example.booking.model.Salon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface SalonRepository extends JpaRepository<Salon, Long> {

    /**
     * Recherche publique : ville et texte libre, tous deux facultatifs.
     *
     * Le CAST(:param AS String) n'est pas décoratif : sans lui, PostgreSQL ne peut
     * pas inférer le type d'un paramètre nul, le traite comme du bytea, et échoue
     * sur « function lower(bytea) does not exist ».
     * Le LOWER(...) LIKE reste acceptable au volume actuel ; il faudra passer
     * à un index trigram (pg_trgm) ou à de la recherche plein texte quand le
     * catalogue dépassera quelques milliers de salons.
     */
    @Query("""
            SELECT s FROM Salon s
            WHERE (CAST(:ville AS String) IS NULL
                   OR LOWER(s.ville) = LOWER(CAST(:ville AS String)))
              AND (CAST(:q AS String) IS NULL
                   OR LOWER(s.nom)     LIKE LOWER(CONCAT('%', CAST(:q AS String), '%'))
                   OR LOWER(s.adresse) LIKE LOWER(CONCAT('%', CAST(:q AS String), '%')))
            """)
    Page<Salon> rechercher(@Param("ville") String ville, @Param("q") String q, Pageable pageable);

    @Query("SELECT DISTINCT s.ville FROM Salon s WHERE s.ville IS NOT NULL ORDER BY s.ville")
    java.util.List<String> villesDistinctes();
}
