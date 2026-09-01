package com.example.booking.dto;

import java.time.LocalTime;

public record HoraireResponse(Long id, int jourSemaine, LocalTime heureDebut, LocalTime heureFin) {}
