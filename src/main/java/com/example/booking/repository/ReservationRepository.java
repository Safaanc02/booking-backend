package com.example.booking.repository;

import com.example.booking.model.Reservation;
import com.example.booking.model.enums.StatutReservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByClientIdOrderByDebutDesc(Long clientId);

    Page<Reservation> findBySalonIdOrderByDebutAsc(Long salonId, Pageable pageable);

    /**
     * Réservations d'un praticien qui occupent la fenêtre donnée.
     * Sert au moteur de disponibilité : une requête par jour et par praticien,
     * jamais une par créneau candidat.
     */
    @Query("""
            SELECT r FROM Reservation r
            WHERE r.employe.id = :employeId
              AND r.statut IN :statuts
              AND r.debut < :fin
              AND r.fin   > :debut
            ORDER BY r.debut
            """)
    List<Reservation> occupationEmploye(@Param("employeId") Long employeId,
                                        @Param("statuts") Collection<StatutReservation> statuts,
                                        @Param("debut") Instant debut,
                                        @Param("fin") Instant fin);

    /** Agenda du salon sur une période, tous praticiens confondus. */
    @Query("""
            SELECT r FROM Reservation r
            WHERE r.salon.id = :salonId
              AND r.debut < :fin
              AND r.fin   > :debut
            ORDER BY r.debut
            """)
    List<Reservation> agenda(@Param("salonId") Long salonId,
                             @Param("debut") Instant debut,
                             @Param("fin") Instant fin);

    boolean existsByEmployeIdAndStatutInAndDebutLessThanAndFinGreaterThan(
            Long employeId, Collection<StatutReservation> statuts, Instant fin, Instant debut);
}
