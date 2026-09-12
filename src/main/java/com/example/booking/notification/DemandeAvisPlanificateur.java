package com.example.booking.notification;

import com.example.booking.model.Reservation;
import com.example.booking.model.enums.StatutReservation;
import com.example.booking.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Demande d'avis au lendemain d'une visite.
 *
 * Sans sollicitation, les avis ne viennent pas. Personne ne retourne
 * spontanément sur un site pour dire que sa coupe était réussie ; on n'y
 * retourne que mécontent. Un réseau qui ne demande rien récolte donc surtout
 * des plaintes, et une note moyenne qui ne ressemble pas à ses salons.
 *
 * Le lendemain, et non le soir même : à la sortie du salon on n'a pas encore
 * vu ce que donne la coupe une fois recoiffée chez soi. Trop tard non plus, et
 * la visite s'efface.
 *
 * Comme pour le rappel, la tâche balaie une fenêtre plutôt qu'un instant
 * précis : elle tolère ainsi un arrêt de l'application sans laisser passer des
 * rendez-vous. C'est le journal des notifications, et non l'horloge, qui
 * garantit qu'une demande ne part qu'une fois.
 */
@Component
public class DemandeAvisPlanificateur {

    private static final Logger log = LoggerFactory.getLogger(DemandeAvisPlanificateur.class);

    private final ReservationRepository reservationRepository;
    private final NotificationService notifications;
    private final long fenetreMinHeures;
    private final long fenetreMaxHeures;

    public DemandeAvisPlanificateur(ReservationRepository reservationRepository,
                                    NotificationService notifications,
                                    @Value("${app.avis.fenetre-min-heures:20}") long fenetreMinHeures,
                                    @Value("${app.avis.fenetre-max-heures:44}") long fenetreMaxHeures) {
        this.reservationRepository = reservationRepository;
        this.notifications = notifications;
        this.fenetreMinHeures = fenetreMinHeures;
        this.fenetreMaxHeures = fenetreMaxHeures;
    }

    /**
     * À 18 h 30, une fois par heure décalée du rappel.
     *
     * Deux tâches à la même minute se disputeraient la même transaction et le
     * même serveur d'envoi, pour rien.
     */
    @Scheduled(cron = "${app.avis.cron:0 37 * * * *}")
    @Transactional(readOnly = true)
    public void demanderLesAvis() {
        int envoyees = declencher();
        if (envoyees > 0) {
            log.info("{} demande(s) d'avis déclenchée(s)", envoyees);
        }
    }

    /** Séparé de la tâche planifiée pour rester appelable à la demande, en test comme en exploitation. */
    @Transactional(readOnly = true)
    public int declencher() {
        Instant maintenant = Instant.now();
        /*
         * La fenêtre regarde en arrière : un rendez-vous terminé il y a entre
         * 20 et 44 heures. Large de vingt-quatre heures, elle laisse à la
         * tâche le temps de rattraper une nuit d'arrêt sans jamais renvoyer —
         * le journal s'en charge.
         */
        List<Reservation> aSolliciter = reservationRepository.aSolliciterPourAvis(
                StatutReservation.HONOREE,
                TypeNotification.DEMANDE_AVIS.name(),
                maintenant.minus(Duration.ofHours(fenetreMaxHeures)),
                maintenant.minus(Duration.ofHours(fenetreMinHeures)));

        aSolliciter.forEach(notifications::demanderAvis);
        return aSolliciter.size();
    }
}
