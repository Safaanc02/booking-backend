package com.example.booking.notification;

import com.example.booking.model.Notification;
import com.example.booking.model.Reservation;
import com.example.booking.repository.NotificationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Écritures du journal des notifications, dans des transactions indépendantes.
 *
 * Ce bean existe séparément de NotificationService pour une raison précise :
 * Spring applique @Transactional par proxy, et un appel d'une méthode de la
 * même classe ne passe pas par ce proxy. Placées dans NotificationService, ces
 * deux méthodes auraient partagé la transaction appelante — la ligne de
 * réservation d'envoi aurait été annulée en même temps qu'un échec, et
 * l'idempotence n'aurait jamais fonctionné.
 */
@Component
public class JournalNotification {

    private final NotificationRepository repository;

    public JournalNotification(NotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * Pose la ligne AVANT l'envoi. La contrainte d'unicité
     * (reservation, type, canal) arbitre les doublons : si l'insertion échoue,
     * un autre envoi est déjà en cours ou a déjà eu lieu.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long reserver(Reservation reservation, TypeNotification type,
                         CanalNotification.Canal canal, String destinataire) {
        Notification n = repository.saveAndFlush(Notification.builder()
                .reservation(reservation)
                .type(type)
                .canal(canal)
                .destinataire(destinataire)
                .build());
        return n.getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marquerEnvoyee(Long id) {
        repository.findById(id).ifPresent(n -> {
            n.setEnvoyeeLe(Instant.now());
            repository.save(n);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marquerEchouee(Long id, String erreur) {
        repository.findById(id).ifPresent(n -> {
            n.setErreur(erreur == null ? "erreur inconnue"
                    : erreur.length() > 2000 ? erreur.substring(0, 2000) : erreur);
            repository.save(n);
        });
    }
}
