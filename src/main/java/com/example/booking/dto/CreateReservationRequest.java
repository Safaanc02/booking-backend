// com.example.booking.dto.CreateReservationRequest
package com.example.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateReservationRequest(
        @NotNull Long creneauId,
        // PENDING / CONFIRMED / CANCELLED (optionnel, défaut côté service)
        @Pattern(regexp = "PENDING|CONFIRMED|CANCELLED", message = "statut invalide")
        String statut
) {}
