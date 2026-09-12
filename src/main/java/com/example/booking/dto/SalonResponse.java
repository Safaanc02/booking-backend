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
    /** Métier principal : c'est lui qui donne au salon sa couleur. */
    private String categorie;
    /** Tous les métiers exercés, principal compris. */
    private java.util.List<String> metiers;
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
    /**
     * Point du salon, tel qu'il sert au classement par distance.
     *
     * Rendu parce que le gérant doit pouvoir le relire et le corriger depuis sa
     * fiche : l'API acceptait de l'écrire depuis le début, mais ne le rendait
     * pas, si bien qu'aucun formulaire ne pouvait le pré-remplir — on ne
     * pouvait que l'écraser à l'aveugle.
     *
     * Publier la position d'un commerce n'expose rien : c'est une adresse, et
     * elle est déjà écrite en toutes lettres à côté.
     */
    private Double latitude;
    private Double longitude;

    /**
     * Distance depuis le point demandé, en kilomètres.
     *
     * Nulle hors d'une recherche par proximité — la fiche d'un salon n'a pas
     * de distance dans l'absolu.
     *
     * À vol d'oiseau, et depuis le point connu du salon : le sien s'il a été
     * relevé, sinon le centre de son quartier. L'interface le dit ; annoncer
     * « à 300 m » quand le point est celui du quartier serait une précision
     * inventée.
     */
    private Double distanceKm;

    private String statut;
    private Integer delaiAnnulationHeures;
    private Long ownerId;
    /**
     * Lien du compte courant avec ce salon : PROPRIETAIRE ou GESTIONNAIRE.
     * Nul en lecture publique — cela ne concerne que le back-office.
     */
    private String monRole;
}
