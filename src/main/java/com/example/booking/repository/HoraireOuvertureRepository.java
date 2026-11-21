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

    /**
     * Les horaires du salon (ceux sans praticien) pour toute une page de
     * résultats, en une requête : vingt cartes, vingt « ouvert là ? », un
     * seul aller-retour.
     */
    List<HoraireOuverture> findBySalonIdInAndEmployeIsNullOrderBySalonIdAscJourSemaineAscHeureDebutAsc(
            java.util.Collection<Long> salonIds);

    /** Les horaires d'ouverture du salon lui-même, sans ceux de son équipe. */
    List<HoraireOuverture> findBySalonIdAndEmployeIsNullOrderByJourSemaineAscHeureDebutAsc(Long salonId);

    void deleteBySalonId(Long salonId);

    void deleteByEmployeId(Long employeId);
}
