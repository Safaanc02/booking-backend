package com.example.booking.repository;

import com.example.booking.model.Employe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.booking.model.enums.RoleEmploye;

import java.util.List;
import java.util.Optional;

public interface EmployeRepository extends JpaRepository<Employe, Long> {

    List<Employe> findBySalonIdAndActifTrueOrderByOrdreAscPrenomAsc(Long salonId);

    /**
     * Rôle du compte dans ce salon, s'il en est membre actif.
     *
     * C'est la requête qui rend la délégation possible : un GESTIONNAIRE
     * obtient les mêmes droits d'administration que le propriétaire.
     */
    @Query("""
            SELECT e.role FROM Employe e
            JOIN e.user compte
            WHERE e.salon.id = :salonId
              AND compte.keycloakId = :keycloakId
              AND e.actif = true
            """)
    Optional<RoleEmploye> roleDansSalon(@Param("salonId") Long salonId,
                                        @Param("keycloakId") String keycloakId);

    /** Fiches actives rattachées à ce compte, tous salons confondus. */
    @Query("""
            SELECT e FROM Employe e
            JOIN e.user compte
            WHERE compte.keycloakId = :keycloakId AND e.actif = true
            ORDER BY e.salon.nom
            """)
    List<Employe> mesFiches(@Param("keycloakId") String keycloakId);

    @Query("SELECT e.salon.id FROM Employe e WHERE e.id = :id")
    Optional<Long> salonDeLEmploye(@Param("id") Long id);

    /** Praticiens actifs du salon sachant réaliser cette prestation. */
    @Query("""
            SELECT e FROM Employe e
            JOIN EmployePrestation ep ON ep.employe = e
            WHERE e.salon.id = :salonId
              AND e.actif = true
              AND ep.prestation.id = :prestationId
            ORDER BY e.ordre ASC, e.prenom ASC
            """)
    List<Employe> findCapables(@Param("salonId") Long salonId,
                               @Param("prestationId") Long prestationId);
}
