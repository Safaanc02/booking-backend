package com.example.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String categorie;
    private Integer delaiAnnulationHeures;
    private List<PrestationResponse> prestations;
    private List<EmployeResponse> employes;
}
