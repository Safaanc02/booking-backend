package com.example.booking.repository;

import com.example.booking.model.Favori;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriRepository extends JpaRepository<Favori, Long> {
    List<Favori> findByClientIdOrderByCreeLeDesc(Long clientId);
    Optional<Favori> findByClientIdAndSalonId(Long clientId, Long salonId);
    boolean existsByClientIdAndSalonId(Long clientId, Long salonId);
}
