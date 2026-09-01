package com.example.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Fiche salon complète : le salon et son catalogue, en une seule requête. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalonDetailResponse {
    private Long id;
    private String nom;
    private String adresse;
    private String ville;
    private String telephone;
    private String email;
    private List<PrestationResponse> prestations;
}
