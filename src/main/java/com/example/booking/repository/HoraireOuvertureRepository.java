package com.example.booking.repository;

import com.example.booking.model.HoraireOuverture;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;

public interface HoraireOuvertureRepository extends JpaRepository<HoraireOuverture, Long> {

    List<HoraireOuverture> findByEmployeIdAndJourSemaineOrderByHeureDebut(Long employeId, DayOfWeek jour);

    List<HoraireOuverture> findBySalonIdAndJourSemaineOrderByHeureDebut(Long salonId, DayOfWeek jour);

    List<HoraireOuverture> findBySalonIdOrderByJourSemaineAscHeureDebutAsc(Long salonId);

    List<HoraireOuverture> findByEmployeIdOrderByJourSemaineAscHeureDebutAsc(Long employeId);

    void deleteBySalonId(Long salonId);

    void deleteByEmployeId(Long employeId);
}
