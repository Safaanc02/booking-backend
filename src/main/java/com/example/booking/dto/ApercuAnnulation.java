package com.example.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Ce que le porteur d'un lien d'annulation voit avant de confirmer.
 *
 * Volontairement avare : le lien circule par email, on n'y expose ni le nom du
 * client ni sa note. Juste de quoi reconnaître son rendez-vous.
 */
public record ApercuAnnulation(
        String salon,
        String salonTelephone,
        String prestation,
        String employe,
        Instant debut,
        BigDecimal prix,
        String statut,
        boolean annulable,
        int delaiAnnulationHeures
) {}
