package com.example.booking.notification;

import com.example.booking.model.Reservation;
import com.example.booking.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Envoi et journalisation des notifications.
 *
 * Deux principes :
 *
 *  1. Une notification ne doit JAMAIS faire échouer une réservation. Tout est
 *     asynchrone et rattrapé : au pire le client ne reçoit pas son email, il a
 *     quand même son rendez-vous.
 *  2. On journalise avant d'envoyer, et la contrainte d'unicité en base
 *     arbitre les doublons. C'est ce qui évite le rappel reçu deux fois après
 *     un redéploiement au mauvais moment.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ReservationRepository reservationRepository;
    private final JournalNotification journal;
    private final CanalNotification canal;
    private final RedacteurEmail redacteur;
    private final boolean actif;

    public NotificationService(ReservationRepository reservationRepository,
                               JournalNotification journal,
                               CanalNotification canal,
                               RedacteurEmail redacteur,
                               @Value("${app.mail.actif:true}") boolean actif) {
        this.reservationRepository = reservationRepository;
        this.journal = journal;
        this.canal = canal;
        this.redacteur = redacteur;
        this.actif = actif;
    }

    /** Recharge la réservation : l'appelant s'exécute dans un autre fil, après commit. */
    @Transactional(readOnly = true)
    public void confirmerPourReservation(Long reservationId) {
        reservationRepository.findById(reservationId).ifPresent(this::confirmerReservation);
    }

    @Transactional(readOnly = true)
    public void rappelerPourReservation(Long reservationId) {
        reservationRepository.findById(reservationId).ifPresent(this::rappeler);
    }

    public void confirmerReservation(Reservation r) {
        if (r.getClient() != null && renseigne(r.getClient().getEmail())) {
            envoyer(r, TypeNotification.CONFIRMATION_CLIENT, r.getClient().getEmail(),
                    () -> redacteur.confirmationClient(r));
        }
        if (r.getSalon() != null && renseigne(r.getSalon().getEmail())) {
            envoyer(r, TypeNotification.CONFIRMATION_SALON, r.getSalon().getEmail(),
                    () -> redacteur.confirmationSalon(r));
        }
    }

    public void rappeler(Reservation r) {
        if (r.getClient() != null && renseigne(r.getClient().getEmail())) {
            envoyer(r, TypeNotification.RAPPEL_CLIENT, r.getClient().getEmail(),
                    () -> redacteur.rappelClient(r));
        }
    }

    /**
     * Demande d'avis au lendemain d'une visite.
     *
     * Seul le client est sollicité, et seulement s'il a laissé une adresse :
     * un rendez-vous pris au téléphone par le salon n'en a pas toujours.
     */
    public void demanderAvis(Reservation r) {
        if (r.getClient() != null && renseigne(r.getClient().getEmail())) {
            envoyer(r, TypeNotification.DEMANDE_AVIS, r.getClient().getEmail(),
                    () -> redacteur.demandeAvis(r));
        }
    }

    /* ---------- Mécanique ---------- */

    private void envoyer(Reservation r, TypeNotification type, String destinataire,
                         Supplier<CanalNotification.Message> redaction) {
        Long id;
        try {
            id = journal.reserver(r, type, canal.canal(), destinataire);
        } catch (DataIntegrityViolationException e) {
            log.debug("Notification {} déjà prise en charge pour la réservation {}", type, r.getId());
            return;
        }

        if (!actif) {
            log.info("Envoi désactivé (app.mail.actif=false) : {} vers {}", type, destinataire);
            return;
        }

        try {
            canal.envoyer(redaction.get());
            journal.marquerEnvoyee(id);
            log.info("Notification {} envoyée à {}", type, destinataire);
        } catch (Exception e) {
            log.warn("Échec de la notification {} vers {} : {}", type, destinataire, e.getMessage());
            journal.marquerEchouee(id, e.getMessage());
        }
    }

    private boolean renseigne(String s) {
        return s != null && !s.isBlank();
    }
}
