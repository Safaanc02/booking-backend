package com.example.booking.repository;

import com.example.booking.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByClientUsername(String username);
    // utile pour empêcher double réservation d’un créneau
    boolean existsByCreneauId(Long creneauId);
}
