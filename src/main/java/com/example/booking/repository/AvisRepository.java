package com.example.booking.repository;

import com.example.booking.model.Avis;
import com.example.booking.model.enums.StatutAvis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AvisRepository extends JpaRepository<Avis, Long> {

    boolean existsByReservationId(Long reservationId);

    /** Identifiants des réservations déjà commentées, en une requête plutôt qu'une par ligne. */
    @Query("SELECT a.reservation.id FROM Avis a WHERE a.reservation.id IN :ids")
    java.util.Set<Long> reservationsDejaCommentees(@Param("ids") java.util.Collection<Long> ids);

    Optional<Avis> findByReservationId(Long reservationId);

    Page<Avis> findBySalonIdAndStatutOrderByCreeLeDesc(Long salonId, StatutAvis statut, Pageable pageable);

    Page<Avis> findByStatutOrderByCreeLeDesc(StatutAvis statut, Pageable pageable);

    /** Moyenne et volume des avis publiés d'un salon, pour rafraîchir la valeur dénormalisée. */
    @Query("""
            SELECT AVG(CAST(a.note AS double)), COUNT(a)
            FROM Avis a
            WHERE a.salon.id = :salonId AND a.statut = com.example.booking.model.enums.StatutAvis.PUBLIE
            """)
    Object[] statistiques(@Param("salonId") Long salonId);
}
