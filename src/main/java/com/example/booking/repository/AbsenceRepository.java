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
     *
     * Les deux jointures sont explicitement externes, et ce n'est pas un
     * détail de style. La version précédente écrivait `a.employe.id` et
     * `a.salon.id` : JPQL en tire deux jointures INTERNES, si bien qu'une
     * fermeture de salon — dont `employe` est nul — était écartée avant même
     * que le OR soit évalué. Le moteur ne voyait donc jamais les fermetures
     * exceptionnelles, et proposait des créneaux un jour où le salon annonçait
     * être fermé. Vérifié en base : zéro ligne avec la jointure interne, une
     * avec l'externe.
     *
     * Rien ne l'avait révélé parce qu'aucun écran ne créait de fermeture de
     * salon : seuls les congés de praticiens existaient en pratique, et
     * ceux-là ont bien un `employe`.
     */
    @Query("""
            SELECT a FROM Absence a
            LEFT JOIN a.employe employe
            LEFT JOIN a.salon salon
            WHERE (employe.id = :employeId OR salon.id = :salonId)
              AND a.debut < :fin
              AND a.fin   > :debut
            """)
    List<Absence> chevauchant(@Param("employeId") Long employeId,
                              @Param("salonId") Long salonId,
                              @Param("debut") Instant debut,
                              @Param("fin") Instant fin);

    /**
     * Salon concerné par l'absence, qu'elle vise un praticien ou le salon entier.
     *
     * Toutes les jointures sont explicitement externes. Écrire `a.salon.id`
     * produirait une jointure INTERNE implicite : pour une absence visant un
     * praticien, `a.salon` est nul et la ligne disparaîtrait du résultat.
     */
    @Query("""
            SELECT COALESCE(salonDeLEmploye.id, salon.id)
            FROM Absence a
            LEFT JOIN a.employe employe
            LEFT JOIN employe.salon salonDeLEmploye
            LEFT JOIN a.salon salon
            WHERE a.id = :id
            """)
    Optional<Long> salonConcerne(@Param("id") Long id);

    /**
     * Absences en cours et à venir concernant ce salon.
     *
     * Les deux formes réunies : les fermetures du salon entier, et les congés
     * de chacun de ses praticiens. Une seule requête, parce qu'un écran de
     * congés les présente ensemble — c'est la même question posée à l'agenda.
     *
     * Toutes les jointures sont externes. Écrire `a.salon.id` produirait une
     * jointure interne : les congés de praticiens, dont `a.salon` est nul,
     * disparaîtraient du résultat sans erreur.
     *
     * Le filtre porte sur la fin, non sur le début : une fermeture commencée
     * hier et qui court jusqu'à demain doit rester visible — c'est celle qu'on
     * risque le plus d'avoir besoin de corriger.
     */
    @Query("""
            SELECT a FROM Absence a
            LEFT JOIN a.employe employe
            LEFT JOIN employe.salon salonDeLEmploye
            LEFT JOIN a.salon salon
            WHERE (salon.id = :salonId OR salonDeLEmploye.id = :salonId)
              AND a.fin > :depuis
            ORDER BY a.debut
            """)
    List<Absence> aVenirPourSalon(@Param("salonId") Long salonId,
                                  @Param("depuis") Instant depuis);

    List<Absence> findBySalonId(Long salonId);

    List<Absence> findByEmployeId(Long employeId);
}
