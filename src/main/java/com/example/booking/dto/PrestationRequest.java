package com.example.booking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PrestationRequest {
    @NotBlank(message = "Le nom de la prestation est obligatoire")
    @Size(max = 100, message = "Nom trop long")
    private String nom;
    private String description; // 🔹 Ajout du champ manquant

    @Min(value = 1, message = "Le prix doit être supérieur à 0")
    private double prix;

    @NotBlank(message = "La durée est obligatoire")
    private String duree; // ex: "30min", "1h"
}
