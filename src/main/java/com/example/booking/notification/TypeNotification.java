package com.example.booking.notification;

public enum TypeNotification {
    /** Au client, juste après la réservation. */
    CONFIRMATION_CLIENT,
    /** Au salon, pour qu'il voie arriver le rendez-vous sans consulter l'agenda. */
    CONFIRMATION_SALON,
    /** Au client, la veille. Le levier numéro un contre le no-show. */
    RAPPEL_CLIENT,
    /** Au client, quand le salon annule de son côté. */
    ANNULATION_SALON
}
