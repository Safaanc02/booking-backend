package com.example.booking.repository;

import com.example.booking.model.Reservation;
import com.example.booking.model.enums.StatutReservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByClientIdOrderByDebutDesc(Long clientId);

    Page<Reservation> findBySalonIdOrderByDebutAsc(Long salonId, Pageable pageable);

    /**
     * Réservations d'un praticien qui occupent la fenêtre donnée.
     * Sert au moteur de disponibilité : une requête par jour et par praticien,
     * jamais une par créneau candidat.
     */
    @Query("""
            SELECT r FROM Reservation r
            WHERE r.employe.id = :employeId
              AND r.statut IN :statuts
              AND r.debut < :fin
              AND r.fin   > :debut
            ORDER BY r.debut
            """)
    List<Reservation> occupationEmploye(@Param("employeId") Long employeId,
                                        @Param("statuts") Collection<StatutReservation> statuts,
                                        @Param("debut") Instant debut,
                                        @Param("fin") Instant fin);

    /** Agenda du salon sur une période, tous praticiens confondus. */
    @Query("""
            SELECT r FROM Reservation r
            WHERE r.salon.id = :salonId
              AND r.debut < :fin
              AND r.fin   > :debut
            ORDER BY r.debut
            """)
    List<Reservation> agenda(@Param("salonId") Long salonId,
                             @Param("debut") Instant debut,
                             @Param("fin") Instant fin);

    /**
     * Réservations méritant un rappel : confirmées, démarrant dans la fenêtre,
     * et pour lesquelles aucun rappel n'a encore été journalisé.
     *
     * Le NOT EXISTS sur le journal est ce qui rend la tâche rejouable sans
     * risque : la relancer n'envoie rien de plus.
     */
    @Query("""
            SELECT r FROM Reservation r
            WHERE r.statut = :statut
              AND r.debut >= :debut
              AND r.debut <  :fin
              AND NOT EXISTS (
                  SELECT 1 FROM Notification n
                  WHERE n.reservation = r AND CAST(n.type AS String) = :type
              )
            ORDER BY r.debut
            """)
    List<Reservation> aRappeler(@Param("statut") StatutReservation statut,
                                @Param("type") String type,
                                @Param("debut") Instant debut,
                                @Param("fin") Instant fin);

    /**
     * Rendez-vous méritant une demande d'avis : honorés, terminés dans la
     * fenêtre, jamais sollicités, et pas encore commentés.
     *
     * Trois conditions, trois raisons distinctes.
     *
     * `HONOREE` parce que seul quelqu'un qui est venu peut juger. C'est le
     * salon qui le déclare : un rendez-vous jamais marqué ne déclenche donc
     * rien, et c'est voulu — on préfère un avis manquant à un avis sollicité
     * auprès de quelqu'un qui n'est pas venu.
     *
     * Le NOT EXISTS sur le journal rend la tâche rejouable : la relancer
     * n'envoie rien de plus.
     *
     * Le NOT EXISTS sur les avis évite de réclamer ce qu'on a déjà. Un client
     * qui a laissé son avis dans l'heure recevrait sinon, le lendemain, un
     * courriel lui demandant de faire ce qu'il vient de faire.
     */
    @Query("""
            SELECT r FROM Reservation r
            WHERE r.statut = :statut
              AND r.fin >= :debut
              AND r.fin <  :fin
              AND NOT EXISTS (
                  SELECT 1 FROM Notification n
                  WHERE n.reservation = r AND CAST(n.type AS String) = :type
              )
              AND NOT EXISTS (
                  SELECT 1 FROM Avis a WHERE a.reservation = r
              )
            ORDER BY r.fin
            """)
    List<Reservation> aSolliciterPourAvis(@Param("statut") StatutReservation statut,
                                          @Param("type") String type,
                                          @Param("debut") Instant debut,
                                          @Param("fin") Instant fin);

    /** Réservations par statut depuis une date, pour le tableau de bord. */
    @Query("SELECT r.statut, count(r) FROM Reservation r WHERE r.debut >= :depuis GROUP BY r.statut")
    List<Object[]> compterParStatutDepuis(@Param("depuis") Instant depuis);

    /**
     * Volume passé par la plateforme sur la période, au tarif figé.
     *
     * Les honorées seules. Une réservation annulée n'a rien fait entrer dans
     * la caisse du salon, et la compter gonflerait un chiffre dont toute
     * l'utilité est d'être comparable d'un mois sur l'autre.
     */
    @Query("""
            SELECT COALESCE(sum(r.prixFige), 0) FROM Reservation r
            WHERE r.debut >= :depuis AND r.statut = com.example.booking.model.enums.StatutReservation.HONOREE
            """)
    java.math.BigDecimal volumeHonoreDepuis(@Param("depuis") Instant depuis);

    /**
     * Les clients d'un salon, agrégés depuis ses réservations.
     *
     * Requête native : la clé passe par normaliser_telephone, une fonction SQL,
     * et l'agrégation conditionnelle par statut se dit mal en JPQL.
     *
     * La clé unifie les deux origines de réservation. En ligne le client a un
     * compte, au téléphone le salon saisit un nom et un numéro sans compte :
     * se fier au compte couperait en deux la même personne selon la façon dont
     * elle a réservé. Le numéro libre prime sur celui du compte — c'est celui
     * que le salon vient de noter, donc le plus à jour.
     *
     * Le nom retenu est celui de la réservation la plus récente : une personne
     * mariée, un prénom mal orthographié au téléphone, et c'est la dernière
     * graphie qui vaut.
     *
     * Le filtre LIKE sur le nom et le numéro sert la recherche de l'écran. Il
     * s'applique après l'agrégation — chercher « Bennani » doit trouver la
     * fiche, pas seulement les réservations qui portent ce nom.
     *
     * `cle` sert l'ouverture d'une fiche. La première version agrégeait tous
     * les clients du salon puis n'en gardait qu'un en Java : ouvrir une fiche
     * coûtait alors autant que la liste entière, et ce coût grandissait avec
     * la clientèle — exactement le salon pour qui l'écran est utile.
     */
    @Query(value = """
            SELECT cle, nom, telephone, visites, annulations, absences,
                   premiere_visite, derniere_visite, total_depense
            FROM (
                SELECT
                    COALESCE(
                        normaliser_telephone(r.client_telephone_libre),
                        normaliser_telephone(u.telephone),
                        'compte:' || r.client_id
                    ) AS cle,
                    (array_agg(COALESCE(r.client_nom_libre, u.full_name, u.username)
                               ORDER BY r.debut DESC))[1] AS nom,
                    (array_agg(COALESCE(r.client_telephone_libre, u.telephone)
                               ORDER BY r.debut DESC))[1] AS telephone,
                    count(*) FILTER (WHERE r.statut = 'HONOREE')                     AS visites,
                    count(*) FILTER (WHERE r.statut IN ('ANNULEE_CLIENT','ANNULEE_SALON')) AS annulations,
                    count(*) FILTER (WHERE r.statut = 'ABSENT')                      AS absences,
                    min(r.debut) FILTER (WHERE r.statut = 'HONOREE')                 AS premiere_visite,
                    max(r.debut) FILTER (WHERE r.statut = 'HONOREE')                 AS derniere_visite,
                    COALESCE(sum(r.prix_fige) FILTER (WHERE r.statut = 'HONOREE'), 0) AS total_depense
                FROM reservation r
                LEFT JOIN users u ON u.id = r.client_id
                WHERE r.salon_id = :salonId
                GROUP BY 1
            ) fiches
            WHERE cle IS NOT NULL
              AND (CAST(:cle AS varchar) IS NULL OR cle = CAST(:cle AS varchar))
              AND (CAST(:q AS varchar) IS NULL
                   OR nom ILIKE CONCAT('%', CAST(:q AS varchar), '%')
                   OR telephone ILIKE CONCAT('%', CAST(:q AS varchar), '%'))
            ORDER BY derniere_visite DESC NULLS LAST, cle
            """, nativeQuery = true)
    List<Object[]> clientsDuSalon(@Param("salonId") Long salonId,
                                  @Param("q") String q,
                                  @Param("cle") String cle);

    /**
     * Réservations d'un client précis dans ce salon, du plus récent au plus ancien.
     *
     * La même expression de clé que ci-dessus, à la lettre. Les deux doivent
     * bouger ensemble : une fiche dont l'historique ne correspond pas à ses
     * compteurs serait pire que pas de fiche du tout.
     */
    @Query(value = """
            SELECT r.* FROM reservation r
            LEFT JOIN users u ON u.id = r.client_id
            WHERE r.salon_id = :salonId
              AND COALESCE(
                      normaliser_telephone(r.client_telephone_libre),
                      normaliser_telephone(u.telephone),
                      'compte:' || r.client_id
                  ) = CAST(:cle AS varchar)
            ORDER BY r.debut DESC
            """, nativeQuery = true)
    List<Reservation> reservationsDuClient(@Param("salonId") Long salonId, @Param("cle") String cle);

    /**
     * Planning de plusieurs praticiens sur une fenêtre, tous statuts confondus.
     *
     * Sert au planning personnel : un praticien doit voir ses annulations et
     * ses absences constatées, pas seulement les rendez-vous encore actifs.
     */
    @Query("""
            SELECT r FROM Reservation r
            WHERE r.employe.id IN :employeIds
              AND r.debut < :fin
              AND r.fin   > :debut
            ORDER BY r.debut
            """)
    List<Reservation> planningDesPraticiens(@Param("employeIds") Collection<Long> employeIds,
                                            @Param("debut") Instant debut,
                                            @Param("fin") Instant fin);

    boolean existsByEmployeIdAndStatutInAndDebutLessThanAndFinGreaterThan(
            Long employeId, Collection<StatutReservation> statuts, Instant fin, Instant debut);
}
