package com.example.booking.dto;

public record EmployeResponse(
        Long id,
        String prenom,
        String nom,
        String titre,
        String photoUrl,
        Integer dureeMinutes,
        /** PRATICIEN ou GESTIONNAIRE. */
        String role,
        /** Email du compte rattaché, nul si la fiche n'a pas d'accès. */
        String compteEmail
) {}
