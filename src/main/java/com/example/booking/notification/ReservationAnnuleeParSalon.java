package com.example.booking.notification;

/**
 * Le salon vient d'annuler un rendez-vous.
 *
 * Le type de notification ANNULATION_SALON existait depuis la première
 * version du journal, et n'était déclenché nulle part : la cliente dont le
 * rendez-vous était annulé ne recevait rien, et se présentait à l'heure dite
 * devant une porte fermée. Le pire cas pour la réputation d'un réseau, et il
 * était silencieux.
 *
 * Comme les autres événements, seul l'identifiant voyage : le destinataire
 * s'exécute dans un autre fil, après commit.
 */
public record ReservationAnnuleeParSalon(Long reservationId) {}
