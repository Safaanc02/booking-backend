package com.example.booking.repository;

import com.example.booking.model.Prestation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PrestationRepository extends JpaRepository<Prestation, Long> {
    List<Prestation> findBySalonId(Long salonId);

    @Query("SELECT p.salon.id FROM Prestation p WHERE p.id = :id")
    Optional<Long> salonDeLaPrestation(@Param("id") Long id);
}
