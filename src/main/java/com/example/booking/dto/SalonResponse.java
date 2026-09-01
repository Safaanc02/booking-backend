package com.example.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalonResponse {
    private Long id;
    private String nom;
    private String description;
    private String adresse;
    private String ville;
    private String quartier;
    private String telephone;
    private String email;
    private String categorie;
    /** Nulle tant qu'aucun avis n'a été déposé : sans avis n'est pas noté zéro. */
    private BigDecimal noteMoyenne;
    private Integer nombreAvis;
    private String statut;
    private Integer delaiAnnulationHeures;
    private Long ownerId;
}
