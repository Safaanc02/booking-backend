package com.example.booking.dto;

import com.example.booking.model.enums.AncienneteEtablissement;
import com.example.booking.model.enums.TypeEtablissement;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Demande de démonstration déposée par un professionnel.
 *
 * Aucun compte n'est créé : c'est une prise de contact, pas une inscription.
 * Ce qui est obligatoire l'est parce qu'on ne peut pas rappeler quelqu'un sans
 * — nom de l'établissement, ville, métier, ancienneté, prénom, téléphone,
 * e-mail. Tout le reste affine la priorité de la visite, et un champ de plus
 * exigé ici coûte des prospects.
 */
@Data
public class DemandeDemoRequest {

    @NotBlank(message = "Le nom de l'établissement est obligatoire")
    @Size(max = 150)
    private String nomEtablissement;

    @NotNull(message = "Le type d'établissement est obligatoire")
    private TypeEtablissement typeEtablissement;

    @Size(max = 150)
    private String specialite;

    @NotBlank(message = "La ville est obligatoire")
    @Size(max = 100)
    private String ville;

    @Size(max = 100)
    private String quartier;

    @NotNull(message = "L'ancienneté est obligatoire")
    private AncienneteEtablissement anciennete;

    @Min(value = 1, message = "Au moins une personne")
    @Max(value = 200, message = "Nombre trop élevé")
    private Short nombreCollaborateurs;

    private Boolean proprietaireLocal;

    @Size(max = 100)
    private String outilActuel;

    @NotBlank(message = "Le prénom est obligatoire")
    @Size(max = 80)
    private String prenom;

    @Size(max = 80)
    private String nom;

    @NotBlank(message = "Le téléphone est obligatoire")
    @Pattern(regexp = "^(?:\\+212|0)[5-7]\\d{8}$",
             message = "Numéro marocain attendu, par exemple 0661234567")
    private String telephone;

    @NotBlank(message = "L'e-mail est obligatoire")
    @Email(message = "E-mail invalide")
    @Size(max = 180)
    private String email;

    /**
     * Identifiant Commun de l'Entreprise : quinze chiffres.
     *
     * Facultatif, et affiché en dernier. Le réclamer d'emblée transforme une
     * prise de contact en formalité administrative — or il ne sert qu'au
     * moment du contrat, et un gérant ne le connaît pas de mémoire.
     */
    @Pattern(regexp = "^$|^\\d{15}$", message = "L'ICE compte quinze chiffres")
    private String ice;

    @Size(max = 1000)
    private String message;
}
