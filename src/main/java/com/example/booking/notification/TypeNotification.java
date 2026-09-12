package com.example.booking.notification;

public enum TypeNotification {
    /** Au client, juste après la réservation. */
    CONFIRMATION_CLIENT,
    /** Au salon, pour qu'il voie arriver le rendez-vous sans consulter l'agenda. */
    CONFIRMATION_SALON,
    /** Au client, la veille. Le levier numéro un contre le no-show. */
    RAPPEL_CLIENT,
    /** Au client, quand le salon annule de son côté. */
    ANNULATION_SALON,
    /**
     * Au client, le lendemain d'un rendez-vous honoré.
     *
     * Sans sollicitation, les avis ne viennent pas : personne ne retourne
     * spontanément sur un site pour dire que sa coupe était réussie. Or la
     * note moyenne est ce qui fait choisir un salon plutôt qu'un autre — un
     * réseau sans avis est un annuaire.
     */
    DEMANDE_AVIS
}
