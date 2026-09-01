package com.example.booking.repository;

import com.example.booking.model.Prestation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrestationRepository extends JpaRepository<Prestation, Long> {
    List<Prestation> findBySalonId(Long salonId);
}
