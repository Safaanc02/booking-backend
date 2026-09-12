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
     * Rendez-vous méritant une demande d'avis : honorés, terminés dans la
     * fenêtre, jamais sollicités, et pas encore commentés.
     *
     * Trois conditions, trois raisons distinctes.
     *
     * `HONOREE` parce que seul quelqu'un qui est venu peut juger. C'est le
     * salon qui le déclare : un rendez-vous jamais marqué ne déclenche donc
     * rien, et c'est voulu — on préfère un avis manquant à un avis sollicité
     * auprès de quelqu'un qui n'est pas venu.
     *
     * Le NOT EXISTS sur le journal rend la tâche rejouable : la relancer
     * n'envoie rien de plus.
     *
     * Le NOT EXISTS sur les avis évite de réclamer ce qu'on a déjà. Un client
     * qui a laissé son avis dans l'heure recevrait sinon, le lendemain, un
     * courriel lui demandant de faire ce qu'il vient de faire.
     */
    @Query("""
            SELECT r FROM Reservation r
            WHERE r.statut = :statut
              AND r.fin >= :debut
              AND r.fin <  :fin
              AND NOT EXISTS (
                  SELECT 1 FROM Notification n
                  WHERE n.reservation = r AND CAST(n.type AS String) = :type
              )
              AND NOT EXISTS (
                  SELECT 1 FROM Avis a WHERE a.reservation = r
              )
            ORDER BY r.fin
            """)
    List<Reservation> aSolliciterPourAvis(@Param("statut") StatutReservation statut,
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
