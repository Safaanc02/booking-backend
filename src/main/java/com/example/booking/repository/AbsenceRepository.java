package com.example.booking.repository;

import com.example.booking.model.Absence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AbsenceRepository extends JpaRepository<Absence, Long> {

    /**
     * Absences chevauchant la fenêtre demandée, qu'elles visent le praticien ou
     * le salon entier (fermeture exceptionnelle).
     *
     * Test de chevauchement : debut < finFenetre AND fin > debutFenetre.
     * Strict des deux côtés — deux intervalles qui se touchent sans se
     * recouvrir ne sont pas en conflit.
     */
    @Query("""
            SELECT a FROM Absence a
            WHERE (a.employe.id = :employeId OR a.salon.id = :salonId)
              AND a.debut < :fin
              AND a.fin   > :debut
            """)
    List<Absence> chevauchant(@Param("employeId") Long employeId,
                              @Param("salonId") Long salonId,
                              @Param("debut") Instant debut,
                              @Param("fin") Instant fin);

    List<Absence> findBySalonId(Long salonId);

    List<Absence> findByEmployeId(Long employeId);
}
