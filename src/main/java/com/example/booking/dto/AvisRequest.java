package com.example.booking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AvisRequest(
        @NotNull(message = "La réservation est obligatoire") Long reservationId,
        @NotNull(message = "La note est obligatoire")
        @Min(value = 1, message = "La note va de 1 à 5")
        @Max(value = 5, message = "La note va de 1 à 5") Integer note,
        @Size(max = 1000, message = "Commentaire trop long (1000 caractères max)") String commentaire
) {}
