package com.example.booking.notification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Déclenche les notifications APRÈS le commit de la réservation.
 *
 * Envoyer depuis la transaction exposerait à confirmer par email un rendez-vous
 * qu'un rollback ultérieur ferait disparaître. AFTER_COMMIT garantit que le
 * message ne part que si la réservation existe vraiment.
 */
@Component
public class NotificationListener {

    private final NotificationService notifications;

    public NotificationListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void surReservationCreee(ReservationCreee evenement) {
        notifications.confirmerPourReservation(evenement.reservationId());
    }
}
