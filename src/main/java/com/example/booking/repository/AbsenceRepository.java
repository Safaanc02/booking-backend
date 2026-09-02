package com.example.booking.repository;

import com.example.booking.model.Absence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AbsenceRepository extends JpaRepository<Absence, Long> {

    /**
     * Absences chevauchant la fenêtre demandée, qu'elles visent le praticien ou
     * le salon entier (fermeture exceptionnelle).
     *
     * Test de chevauchement : debut < finFenetre AND fin > debutFenetre.
     * Strict des deux côtés — deux intervalles qui se touchent sans se
     * recouvrir ne sont pas en conflit.
     */
    @Query("""
            SELECT a FROM Absence a
            WHERE (a.employe.id = :employeId OR a.salon.id = :salonId)
              AND a.debut < :fin
              AND a.fin   > :debut
            """)
    List<Absence> chevauchant(@Param("employeId") Long employeId,
                              @Param("salonId") Long salonId,
                              @Param("debut") Instant debut,
                              @Param("fin") Instant fin);

    /**
     * Identifiant Keycloak du propriétaire du salon concerné par l'absence,
     * qu'elle vise un praticien ou le salon entier.
     *
     * Résolu en une requête plutôt que par navigation d'objets : l'évaluateur
     * de permissions s'exécute hors transaction, et suivre absence → employé →
     * salon → propriétaire y déclenchait une LazyInitializationException.
     *
     * Toutes les jointures sont explicitement externes. Écrire
     * `a.salon.owner.keycloakId` produirait une jointure INTERNE implicite :
     * pour une absence visant un praticien, `a.salon` est nul et la ligne
     * disparaissait purement et simplement du résultat — le propriétaire se
     * voyait alors refuser l'accès à sa propre absence.
     */
    @Query("""
            SELECT COALESCE(proprietaireViaEmploye.keycloakId, proprietaireViaSalon.keycloakId)
            FROM Absence a
            LEFT JOIN a.employe employe
            LEFT JOIN employe.salon salonDeLEmploye
            LEFT JOIN salonDeLEmploye.owner proprietaireViaEmploye
            LEFT JOIN a.salon salon
            LEFT JOIN salon.owner proprietaireViaSalon
            WHERE a.id = :id
            """)
    Optional<String> keycloakIdDuProprietaire(@Param("id") Long id);

    List<Absence> findBySalonId(Long salonId);

    List<Absence> findByEmployeId(Long employeId);
}
