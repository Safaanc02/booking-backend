package com.example.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Réservation saisie par le salon, pour un client qui n'a pas de compte. */
public record ReservationProRequest(
        @NotNull(message = "La prestation est obligatoire") Long prestationId,
        @NotNull(message = "Le praticien est obligatoire") Long employeId,
        @NotNull(message = "L'heure de début est obligatoire") Instant debut,

        @NotBlank(message = "Le nom du client est obligatoire")
        @Size(max = 120) String clientNom,

        @Pattern(regexp = "^$|^(?:\\+212|0)[5-7]\\d{8}$",
                 message = "Numéro marocain invalide (attendu : 0612345678 ou +212612345678)")
        String clientTelephone,

        @Size(max = 500) String note,

        /** TELEPHONE par défaut. */
        String origine
) {}
