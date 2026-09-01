package com.example.booking.repository;

import com.example.booking.model.EmployePrestation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployePrestationRepository
        extends JpaRepository<EmployePrestation, EmployePrestation.Id> {

    List<EmployePrestation> findByPrestationId(Long prestationId);

    List<EmployePrestation> findByEmployeId(Long employeId);

    Optional<EmployePrestation> findByEmployeIdAndPrestationId(Long employeId, Long prestationId);
}
