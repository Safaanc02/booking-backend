package com.example.booking.dto;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/**
 * Un créneau réservable.
 *
 * `employesDisponibles` peut contenir plusieurs identifiants : en mode « sans
 * préférence », l'heure n'est proposée qu'une fois même si trois praticiens
 * sont libres. L'attribution a lieu à la confirmation.
 *
 * Pas d'heure de fin ici : elle dépend du praticien retenu, deux d'entre eux
 * pouvant réaliser la même prestation en des temps différents.
 */
public record CreneauDisponible(
        LocalTime heure,
        Instant debut,
        List<Long> employesDisponibles
) {}
