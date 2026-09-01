package com.example.booking.model;

import com.example.booking.model.enums.SalonCategorie;
import com.example.booking.model.enums.SalonStatut;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Un salon de beauté.
 *
 * @Getter/@Setter plutôt que @Data : sur une entité JPA, @Data génère un
 * equals/hashCode couvrant tous les champs, collections lazy comprises.
 */
@Entity
@Table(name = "salon")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Salon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(nullable = false, length = 120)
    private String nom;

    @Column(columnDefinition = "text")
    private String description;

    private String adresse;
    private String ville;
    /** Au Maroc, le quartier est un repère plus parlant que le code postal. */
    private String quartier;

    private Double latitude;
    private Double longitude;

    private String telephone;
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SalonCategorie categorie = SalonCategorie.COIFFURE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SalonStatut statut = SalonStatut.EN_ATTENTE;

    @Column(name = "delai_annulation_heures", nullable = false)
    @Builder.Default
    private int delaiAnnulationHeures = 24;

    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private Instant creeLe;

    @OneToMany(mappedBy = "salon", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Prestation> prestations = new ArrayList<>();

    @OneToMany(mappedBy = "salon", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Employe> employes = new ArrayList<>();
}
