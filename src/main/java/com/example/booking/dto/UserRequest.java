package com.example.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRequest {

    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    @Size(max = 50, message = "Nom trop long (50 caractères max)")
    private String username;

    @Email(message = "Email invalide")
    @NotBlank(message = "Email obligatoire")
    private String email;

    @NotBlank(message = "Le rôle est obligatoire (admin, client, pro)")
    private String role;

    @Size(max = 100, message = "Nom complet trop long (100 caractères max)")
    private String fullName;
}
