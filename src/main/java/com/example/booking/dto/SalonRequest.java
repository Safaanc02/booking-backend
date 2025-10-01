package com.example.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SalonRequest {
    @NotBlank(message = "Le nom du salon est obligatoire")
    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    private String nom;

    @NotBlank(message = "L'adresse est obligatoire")
    @Size(max = 200, message = "L'adresse ne doit pas dépasser 200 caractères")
    private String adresse;

    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @Size(min = 8, max = 15, message = "Numéro de téléphone invalide")
    private String telephone;

    @Email(message = "Email invalide")
    private String email;
}

