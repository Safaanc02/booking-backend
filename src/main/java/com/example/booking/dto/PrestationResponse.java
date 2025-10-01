package com.example.booking.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PrestationResponse {
    private Long id;

    private String nom;

    private String description;

    private BigDecimal prix;   // ✅ même type que dans l’entité
    private Integer duree;
    private Long salonId;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrix() {
        return prix;
    }

    public void setPrix(BigDecimal prix) {
        this.prix = prix;
    }

    public Integer getDuree() {
        return duree;
    }

    public void setDuree(Integer duree) {
        this.duree = duree;
    }

    public Long getSalonId() {
        return salonId;
    }

    public void setSalonId(Long salonId) {
        this.salonId = salonId;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
