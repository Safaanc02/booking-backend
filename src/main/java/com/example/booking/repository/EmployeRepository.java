package com.example.booking.repository;

import com.example.booking.model.Employe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EmployeRepository extends JpaRepository<Employe, Long> {

    List<Employe> findBySalonIdAndActifTrueOrderByOrdreAscPrenomAsc(Long salonId);

    /** Praticiens actifs du salon sachant réaliser cette prestation. */
    @Query("""
            SELECT e FROM Employe e
            JOIN EmployePrestation ep ON ep.employe = e
            WHERE e.salon.id = :salonId
              AND e.actif = true
              AND ep.prestation.id = :prestationId
            ORDER BY e.ordre ASC, e.prenom ASC
            """)
    List<Employe> findCapables(@Param("salonId") Long salonId,
                               @Param("prestationId") Long prestationId);
}
