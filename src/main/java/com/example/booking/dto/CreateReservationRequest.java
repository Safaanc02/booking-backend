package com.example.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateReservationRequest(
        @NotNull(message = "Le salon est obligatoire") Long salonId,
        @NotNull(message = "La prestation est obligatoire") Long prestationId,
        /** Nul pour « sans préférence » : le premier praticien libre est attribué. */
        Long employeId,
        @NotNull(message = "L'heure de début est obligatoire") Instant debut,
        @Size(max = 500, message = "Note trop longue (500 caractères max)") String noteClient
) {}
