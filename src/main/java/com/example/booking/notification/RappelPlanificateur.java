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
 * Rappel de la veille — le levier numéro un contre le no-show.
 *
 * Le planificateur balaie une fenêtre plutôt qu'un instant précis : il tolère
 * ainsi un arrêt de l'application ou un décalage d'exécution sans laisser
 * passer des rendez-vous. C'est le journal des notifications, et non l'horloge,
 * qui garantit qu'un rappel ne part qu'une fois.
 */
@Component
public class RappelPlanificateur {

    private static final Logger log = LoggerFactory.getLogger(RappelPlanificateur.class);

    private final ReservationRepository reservationRepository;
    private final NotificationService notifications;
    private final long fenetreMinHeures;
    private final long fenetreMaxHeures;

    public RappelPlanificateur(ReservationRepository reservationRepository,
                               NotificationService notifications,
                               @Value("${app.rappel.fenetre-min-heures:20}") long fenetreMinHeures,
                               @Value("${app.rappel.fenetre-max-heures:28}") long fenetreMaxHeures) {
        this.reservationRepository = reservationRepository;
        this.notifications = notifications;
        this.fenetreMinHeures = fenetreMinHeures;
        this.fenetreMaxHeures = fenetreMaxHeures;
    }

    @Scheduled(cron = "${app.rappel.cron:0 17 * * * *}")
    @Transactional(readOnly = true)
    public void envoyerLesRappels() {
        int envoyes = declencher();
        if (envoyes > 0) {
            log.info("{} rappel(s) déclenché(s)", envoyes);
        }
    }

    /** Séparé de la tâche planifiée pour rester appelable à la demande, en test comme en exploitation. */
    @Transactional(readOnly = true)
    public int declencher() {
        Instant maintenant = Instant.now();
        List<Reservation> aRappeler = reservationRepository.aRappeler(
                StatutReservation.CONFIRMEE,
                TypeNotification.RAPPEL_CLIENT.name(),
                maintenant.plus(Duration.ofHours(fenetreMinHeures)),
                maintenant.plus(Duration.ofHours(fenetreMaxHeures)));

        aRappeler.forEach(notifications::rappeler);
        return aRappeler.size();
    }
}
