package com.example.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Une ligne d'agenda vue par le salon. Le nom du client prime sur tout le reste. */
public record AgendaResponse(
        Long id,
        Instant debut,
        Instant fin,
        String statut,
        String origine,
        String client,
        String clientTelephone,
        String prestation,
        Long employeId,
        String employe,
        BigDecimal prix,
        String noteClient
) {}
