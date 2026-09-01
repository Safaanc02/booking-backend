package com.example.booking.dto;

import java.time.LocalDate;
import java.util.List;

public record DisponibilitesResponse(
        LocalDate date,
        Long salonId,
        PrestationResponse prestation,
        List<CreneauDisponible> creneaux
) {}
