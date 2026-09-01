package com.example.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmployeRequest {
    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 80) private String prenom;
    @Size(max = 80) private String nom;
    @Size(max = 80) private String titre;
    @Size(max = 500) private String photoUrl;
    private Integer ordre;
}
