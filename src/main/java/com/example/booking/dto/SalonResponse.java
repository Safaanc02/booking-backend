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
    /**
     * Prix de la prestation active la moins chère, pour un « à partir de ».
     *
     * Nul quand le catalogue est vide : le salon vient d'être référencé et son
     * paramétrage n'est pas terminé. Annoncer « à partir de 0 MAD » serait
     * pire que ne rien annoncer.
     */
    private BigDecimal prixMin;
    private String statut;
    private Integer delaiAnnulationHeures;
    private Long ownerId;
    /**
     * Lien du compte courant avec ce salon : PROPRIETAIRE ou GESTIONNAIRE.
     * Nul en lecture publique — cela ne concerne que le back-office.
     */
    private String monRole;
}
