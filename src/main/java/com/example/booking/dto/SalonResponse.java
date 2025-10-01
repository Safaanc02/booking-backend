package com.example.booking.dto;

import lombok.Data;

@Data
public class SalonResponse {
    private Long id;
    private String nom;
    private String adresse;
    private String telephone;
    private String email;
    private Long ownerId;

    public SalonResponse(Long id, String nom, String adresse, String telephone, String email, Long ownerId) {
        this.id = id;
        this.nom = nom;
        this.adresse = adresse;
        this.telephone = telephone;
        this.email = email;
        this.ownerId = ownerId;
    }

}

