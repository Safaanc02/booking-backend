package com.example.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ReservationResponse(
        Long id,
        Long salonId,
        String salonNom,
        Long prestationId,
        String prestation,
        Long employeId,
        String employe,
        Instant debut,
        Instant fin,
        String statut,
        BigDecimal prix,
        String noteClient,
        /** Permet à l'interface de proposer, ou non, le dépôt d'un avis. */
        boolean avisDepose
) {}
