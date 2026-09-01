package com.example.booking.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Un salon de beauté.
 *
 * @Getter/@Setter plutôt que @Data : sur une entité JPA, @Data génère un
 * equals/hashCode couvrant tous les champs, y compris les collections lazy —
 * ce qui déclenche des chargements involontaires et casse les Set.
 *
 * Les accesseurs écrits à la main ont été retirés : ils doublonnaient Lombok.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Salon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    private String adresse;
    private String ville;
    private String telephone;
    private String email;

    /**
     * Le professionnel qui gère ce salon.
     *
     * L'entité portait auparavant DEUX relations vers User pour ce même concept :
     * `owner` (owner_id) et `proprietaire` (pro_id). Seule `owner` était alimentée,
     * `proprietaire` restait nulle — et CustomPermissionEvaluator lisait `owner`
     * tandis que le reste du code hésitait. `proprietaire` a été supprimée.
     */
    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;

    @OneToMany(mappedBy = "salon", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Prestation> prestations = new ArrayList<>();
}
