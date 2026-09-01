package com.example.booking.notification;

/**
 * Une réservation vient d'être enregistrée.
 *
 * L'événement ne transporte que l'identifiant, pas l'entité : le destinataire
 * s'exécute dans un autre fil et une entité détachée y serait un piège.
 */
public record ReservationCreee(Long reservationId) {}
