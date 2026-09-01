package com.example.booking.dto;

import java.time.Instant;

public record AvisResponse(
        Long id,
        Long salonId,
        String salonNom,
        /** Prénom seul : un avis public n'a pas à exposer l'identité complète du client. */
        String auteur,
        String employe,
        String prestation,
        int note,
        String commentaire,
        String reponseSalon,
        Instant reponseLe,
        Instant creeLe,
        String statut
) {}
