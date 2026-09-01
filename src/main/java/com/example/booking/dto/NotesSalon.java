package com.example.booking.dto;

import java.math.BigDecimal;

/** Note moyenne et volume, tels qu'affichés sur une fiche ou dans les résultats. */
public record NotesSalon(BigDecimal moyenne, int nombre) {}
