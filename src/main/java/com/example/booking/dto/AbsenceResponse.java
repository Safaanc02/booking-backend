package com.example.booking.dto;

import java.time.Instant;

/**
 * Une absence, telle que l'écran des congés la montre.
 *
 * `employeId` nul désigne une fermeture du salon entier ; renseigné, le congé
 * d'un praticien. Le nom accompagne l'identifiant pour que l'écran n'ait pas à
 * recroiser la liste de l'équipe pour afficher une ligne.
 */
public record AbsenceResponse(
        Long id,
        Long employeId,
        String employeNom,
        Instant debut,
        Instant fin,
        String motif) {}
