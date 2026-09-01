package com.example.booking.dto;

public record EmployeResponse(
        Long id,
        String prenom,
        String nom,
        String titre,
        String photoUrl,
        Integer dureeMinutes
) {}
