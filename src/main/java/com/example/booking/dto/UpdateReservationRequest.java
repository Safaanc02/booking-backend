// com.example.booking.dto.UpdateReservationRequest
package com.example.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateReservationRequest(
        @NotNull Long creneauId,
        @NotNull
        @Pattern(regexp = "PENDING|CONFIRMED|CANCELLED", message = "statut invalide")
        String statut
) {}
