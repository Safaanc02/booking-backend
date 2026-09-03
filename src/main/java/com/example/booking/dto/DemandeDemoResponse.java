package com.example.booking.dto;

import com.example.booking.model.enums.AncienneteEtablissement;
import com.example.booking.model.enums.StatutDemandeDemo;
import com.example.booking.model.enums.TypeEtablissement;

import java.time.Instant;

/** Une demande vue depuis l'administration. */
public record DemandeDemoResponse(
        Long id,
        String nomEtablissement,
        TypeEtablissement typeEtablissement,
        java.util.List<TypeEtablissement> metiers,
        String specialite,
        String ville,
        String quartier,
        AncienneteEtablissement anciennete,
        Short nombreCollaborateurs,
        Boolean proprietaireLocal,
        String outilActuel,
        String prenom,
        String nom,
        String telephone,
        String email,
        String ice,
        String message,
        StatutDemandeDemo statut,
        String noteInterne,
        Long salonId,
        String salonNom,
        Instant creeLe,
        Instant traiteLe,
        String traiteParNom
) {}
