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

    /**
     * Réservations méritant un rappel : confirmées, démarrant dans la fenêtre,
     * et pour lesquelles aucun rappel n'a encore été journalisé.
     *
     * Le NOT EXISTS sur le journal est ce qui rend la tâche rejouable sans
     * risque : la relancer n'envoie rien de plus.
     */
    @Query("""
            SELECT r FROM Reservation r
            WHERE r.statut = :statut
              AND r.debut >= :debut
              AND r.debut <  :fin
              AND NOT EXISTS (
                  SELECT 1 FROM Notification n
                  WHERE n.reservation = r AND CAST(n.type AS String) = :type
              )
            ORDER BY r.debut
            """)
    List<Reservation> aRappeler(@Param("statut") StatutReservation statut,
                                @Param("type") String type,
                                @Param("debut") Instant debut,
                                @Param("fin") Instant fin);

    /**
     * Planning de plusieurs praticiens sur une fenêtre, tous statuts confondus.
     *
     * Sert au planning personnel : un praticien doit voir ses annulations et
     * ses absences constatées, pas seulement les rendez-vous encore actifs.
     */
    @Query("""
            SELECT r FROM Reservation r
            WHERE r.employe.id IN :employeIds
              AND r.debut < :fin
              AND r.fin   > :debut
            ORDER BY r.debut
            """)
    List<Reservation> planningDesPraticiens(@Param("employeIds") Collection<Long> employeIds,
                                            @Param("debut") Instant debut,
                                            @Param("fin") Instant fin);

    boolean existsByEmployeIdAndStatutInAndDebutLessThanAndFinGreaterThan(
            Long employeId, Collection<StatutReservation> statuts, Instant fin, Instant debut);
}
