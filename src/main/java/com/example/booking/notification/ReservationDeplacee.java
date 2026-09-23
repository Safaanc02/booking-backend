package com.example.booking.notification;

import java.time.Instant;

/**
 * Un client vient de déplacer son rendez-vous.
 *
 * L'événement transporte l'ancienne heure en plus de l'identifiant : après le
 * commit, elle n'existe plus nulle part, et c'est pourtant elle qui rend le
 * message lisible. Un courriel qui annonce une heure sans dire laquelle il
 * remplace ressemble à s'y méprendre à la confirmation d'origine.
 *
 * Comme pour {@link ReservationCreee}, rien d'autre que des valeurs : le
 * destinataire s'exécute dans un autre fil, où une entité détachée serait un
 * piège.
 */
public record ReservationDeplacee(Long reservationId, Instant ancienDebut) {}
