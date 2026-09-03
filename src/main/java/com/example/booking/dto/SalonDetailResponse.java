package com.example.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

import java.util.List;

/** Fiche complète : le salon, son catalogue et son équipe, en une requête. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalonDetailResponse {
    private Long id;
    private String nom;
    private String description;
    private String adresse;
    private String ville;
    private String quartier;
    private String telephone;
    private String email;
    /** Métier principal : c'est lui qui donne au salon sa couleur. */
    private String categorie;
    /** Tous les métiers exercés, principal compris. */
    private java.util.List<String> metiers;
    /** Nulle tant qu'aucun avis n'a été déposé : sans avis n'est pas noté zéro. */
    private BigDecimal noteMoyenne;
    private Integer nombreAvis;
    private Integer delaiAnnulationHeures;
    private List<PrestationResponse> prestations;
    private List<EmployeResponse> employes;
}
