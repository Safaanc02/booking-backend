package com.example.booking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PrestationRequest {

    @NotBlank(message = "Le nom de la prestation est obligatoire")
    @Size(max = 100, message = "Nom trop long (100 caractères max)")
    private String nom;

    @Size(max = 500, message = "Description trop longue (500 caractères max)")
    private String description;

    @Size(max = 60, message = "Catégorie trop longue")
    private String categorie;

    /** Prix en dirhams (MAD). */
    @NotNull(message = "Le prix est obligatoire")
    @DecimalMin(value = "0.0", inclusive = false, message = "Le prix doit être supérieur à 0")
    private BigDecimal prix;

    /** Durée en minutes. Bornée : en dessous de 5 min c'est une saisie erronée, au-delà de 8 h aussi. */
    @NotNull(message = "La durée est obligatoire")
    @Min(value = 5, message = "La durée minimale est de 5 minutes")
    @Max(value = 480, message = "La durée maximale est de 8 heures")
    private Integer dureeMinutes;
}
