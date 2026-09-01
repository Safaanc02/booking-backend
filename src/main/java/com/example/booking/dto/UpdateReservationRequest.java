package com.example.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Déplacement d'un rendez-vous. Le changement de statut passe par une route dédiée. */
public record UpdateReservationRequest(
        @NotNull(message = "L'heure de début est obligatoire") Instant debut,
        Long employeId
) {}
