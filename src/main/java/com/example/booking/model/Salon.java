package com.example.booking.model;

import com.example.booking.model.enums.SalonCategorie;
import com.example.booking.model.enums.SalonStatut;
import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
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

    /**
     * Métier principal.
     *
     * Conservé alors que le salon peut en exercer plusieurs : l'identité
     * visuelle — la teinte de sa couverture, dans la liste comme sur sa fiche
     * — a besoin d'une seule couleur. Un établissement qui en afficherait
     * trois n'en aurait aucune.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SalonCategorie categorie = SalonCategorie.COIFFURE;

    /**
     * Tous les métiers exercés, principal compris.
     *
     * L'institut de quartier fait la coiffure, l'onglerie et l'esthétique ;
     * n'en retenir qu'un le rendait introuvable pour les deux autres.
     *
     * EAGER, contre l'usage : cet ensemble est lu à chaque affichage de salon
     * — liste de résultats, fiche, back-office — et jamais indépendamment.
     * En LAZY, chaque page de vingt résultats déclenchait vingt requêtes
     * supplémentaires, ou levait une LazyInitializationException hors
     * transaction, ce qui s'est déjà produit ailleurs dans ce projet.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "salon_metier",
            joinColumns = @JoinColumn(name = "salon_id"))
    @Column(name = "metier", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<SalonCategorie> metiers = new LinkedHashSet<>();

    /**
     * Le métier principal fait toujours partie des métiers exercés.
     *
     * Appelé par le service avant enregistrement plutôt que laissé à la
     * discipline des appelants : un salon dont la catégorie ne figurerait pas
     * dans ses métiers serait absent du filtre correspondant à sa propre
     * couleur — l'incohérence la plus déroutante possible pour un visiteur.
     */
    public void normaliserMetiers() {
        if (metiers == null) metiers = new LinkedHashSet<>();
        if (categorie != null) metiers.add(categorie);
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SalonStatut statut = SalonStatut.EN_ATTENTE;

    @Column(name = "delai_annulation_heures", nullable = false)
    @Builder.Default
    private int delaiAnnulationHeures = 24;

    // La valeur vient du DEFAULT now() de la base. Sans @Generated, Hibernate
    // ne la relit pas après insertion et le champ reste nul dans la réponse.
    @Generated(event = EventType.INSERT)
    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private Instant creeLe;

    /**
     * Note dénormalisée, recalculée à chaque écriture d'avis.
     * Nulle tant qu'aucun avis n'a été déposé — un salon sans avis n'est pas
     * un salon noté zéro.
     */
    @Column(name = "note_moyenne", precision = 2, scale = 1)
    private BigDecimal noteMoyenne;

    @Column(name = "nombre_avis", nullable = false)
    @Builder.Default
    private int nombreAvis = 0;

    @OneToMany(mappedBy = "salon", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Prestation> prestations = new ArrayList<>();

    @OneToMany(mappedBy = "salon", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Employe> employes = new ArrayList<>();
}
