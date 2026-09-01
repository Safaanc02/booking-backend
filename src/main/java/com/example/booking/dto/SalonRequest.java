package com.example.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    @NotBlank(message = "La ville est obligatoire")
    @Size(max = 80, message = "Nom de ville trop long")
    private String ville;

    /**
     * Numéro marocain : fixe 05, mobile 06 ou 07, au format national (0XXXXXXXXX)
     * ou international (+212XXXXXXXXX).
     */
    @NotBlank(message = "Le numéro de téléphone est obligatoire")
    @Pattern(
            regexp = "^(?:\\+212|0)[5-7]\\d{8}$",
            message = "Numéro marocain invalide (attendu : 0612345678 ou +212612345678)"
    )
    private String telephone;

    @Email(message = "Email invalide")
    @Size(max = 120, message = "Email trop long")
    private String email;
}
