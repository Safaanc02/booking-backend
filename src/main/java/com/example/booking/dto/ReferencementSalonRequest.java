package com.example.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Référencement d'un salon par l'administration, pour le compte d'un gérant.
 *
 * C'est le modèle du métier : le salon ne s'inscrit pas seul, on l'installe
 * pour lui. Le paramétrage du catalogue est justement ce qui décourage le
 * plus, et le lui épargner est la meilleure garantie qu'il reste.
 */
@Data
public class ReferencementSalonRequest {

    @NotNull(message = "Les informations du salon sont obligatoires")
    @Valid
    private SalonRequest salon;

    @NotNull(message = "Les informations du gérant sont obligatoires")
    @Valid
    private Proprietaire proprietaire;

    /** Valider immédiatement, quand l'installation est faite en présence du gérant. */
    private Boolean validerImmediatement;

    @Data
    public static class Proprietaire {

        /**
         * L'e-mail sert d'identifiant de connexion.
         *
         * Un seul élément à retenir plutôt que deux : six mois plus tard, un
         * gérant ne se souvient pas d'un nom d'utilisateur choisi par un
         * conseiller.
         */
        @NotBlank(message = "L'e-mail du gérant est obligatoire")
        @Email(message = "E-mail invalide")
        @Size(max = 180)
        private String email;

        @NotBlank(message = "Le prénom du gérant est obligatoire")
        @Size(max = 80)
        private String prenom;

        @Size(max = 80)
        private String nom;

        @Pattern(regexp = "^$|^(?:\\+212|0)[5-7]\\d{8}$",
                 message = "Numéro marocain invalide (attendu : 0612345678 ou +212612345678)")
        private String telephone;
    }
}
