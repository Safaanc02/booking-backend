package com.example.booking.dto;

import com.example.booking.model.enums.RoleEmploye;
import jakarta.validation.constraints.Email;
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

    /**
     * Rôle une fois le compte rattaché. PRATICIEN par défaut.
     *
     * GESTIONNAIRE donne les mêmes droits d'administration que le
     * propriétaire, hors suppression du salon.
     */
    private RoleEmploye role;

    /**
     * Email du compte à rattacher, facultatif.
     *
     * La personne doit s'être connectée au moins une fois : le compte est créé
     * dans Keycloak, et l'application n'en garde un miroir qu'après une
     * première authentification. Sans rattachement, la fiche reste une simple
     * ligne d'agenda, sans aucun droit.
     */
    @Email(message = "Email invalide")
    @Size(max = 180) private String email;
}
