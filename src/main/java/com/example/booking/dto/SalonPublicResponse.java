package com.example.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Ce qu'un salon montre de lui à n'importe qui.
 *
 * Distinct de {@link SalonResponse}, qui sert les routes pro et admin. Celui-ci
 * rendait jusqu'ici l'adresse e-mail du salon, l'identifiant de son
 * propriétaire et le rôle de l'appelant à toute personne qui demandait
 * <code>/api/public/salons</code> — une récolte d'adresses de gérants en une
 * requête, et la base d'un démarchage que nous ne contrôlons pas. Un DTO
 * public ne porte que ce qu'une cliente a besoin de savoir pour choisir.
 *
 * Trois champs d'horaires s'y ajoutent, calculés à l'heure de Casablanca :
 * « ouvert là, maintenant », et sinon quand ça change. C'est la question qu'on
 * se pose devant une carte, et elle restait sans réponse.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalonPublicResponse {
    private Long id;
    private String nom;
    private String description;
    private String adresse;
    private String ville;
    private String quartier;
    private String telephone;
    private String categorie;
    private List<String> metiers;
    private BigDecimal noteMoyenne;
    private Integer nombreAvis;
    private BigDecimal prixMin;
    private Double latitude;
    private Double longitude;
    private Double distanceKm;
    @Builder.Default
    private List<String> photos = List.of();
    private Integer delaiAnnulationHeures;

    /** Nul tant que le salon n'a pas saisi ses horaires : on ne dit pas « fermé » par défaut. */
    private Boolean ouvertMaintenant;
    /** Heure de fermeture de la plage en cours, si ouvert. */
    private LocalTime fermeA;
    /** Prochaine ouverture, si fermé — aujourd'hui plus tard, ou un autre jour. */
    private LocalTime ouvreA;
    /** Jour ISO (1 = lundi) de la prochaine ouverture quand ce n'est pas aujourd'hui. */
    private Integer ouvreJour;
}
