package com.example.booking.model;

import com.example.booking.model.enums.AncienneteEtablissement;
import com.example.booking.model.enums.StatutDemandeDemo;
import com.example.booking.model.enums.TypeEtablissement;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** Une demande de démonstration déposée par un professionnel. Voir la migration V9. */
@Entity
@Table(name = "demande_demo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandeDemo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom_etablissement", nullable = false, length = 150)
    private String nomEtablissement;

    /** Métier principal : le premier déclaré par le prospect. */
    @Enumerated(EnumType.STRING)
    @Column(name = "type_etablissement", nullable = false, length = 30)
    private TypeEtablissement typeEtablissement;

    /**
     * Tous les métiers déclarés, principal compris.
     *
     * Le formulaire n'en acceptait qu'un. Un institut qui fait la coiffure,
     * l'onglerie et l'esthétique devait en choisir un, et le conseiller ne
     * savait donc pas ce qu'il allait trouver sur place — ni le formulaire de
     * référencement quoi préremplir.
     *
     * EAGER : lu à chaque affichage de la file commerciale, et jamais seul.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "demande_demo_metier",
            joinColumns = @JoinColumn(name = "demande_id"))
    @Column(name = "metier", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<TypeEtablissement> metiers = new LinkedHashSet<>();

    /** Le métier principal fait toujours partie des métiers déclarés. */
    public void normaliserMetiers() {
        if (metiers == null) metiers = new LinkedHashSet<>();
        if (typeEtablissement != null) metiers.add(typeEtablissement);
    }

    @Column(length = 150)
    private String specialite;

    @Column(nullable = false, length = 100)
    private String ville;

    @Column(length = 100)
    private String quartier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AncienneteEtablissement anciennete;

    @Column(name = "nombre_collaborateurs")
    private Short nombreCollaborateurs;

    @Column(name = "proprietaire_local")
    private Boolean proprietaireLocal;

    @Column(name = "outil_actuel", length = 100)
    private String outilActuel;

    @Column(nullable = false, length = 80)
    private String prenom;

    @Column(length = 80)
    private String nom;

    @Column(nullable = false, length = 20)
    private String telephone;

    @Column(nullable = false, length = 190)
    private String email;

    @Column(length = 15)
    private String ice;

    @Column(length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutDemandeDemo statut = StatutDemandeDemo.NOUVELLE;

    @Column(name = "note_interne", length = 1000)
    private String noteInterne;

    /** Le salon référencé au terme de la demande, s'il l'a été. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salon_id")
    private Salon salon;

    // La valeur vient du DEFAULT now() de la base. Sans @Generated, Hibernate
    // ne la relit pas après insertion et le champ reste nul dans la réponse.
    @Generated(event = EventType.INSERT)
    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private Instant creeLe;

    @Column(name = "traite_le")
    private Instant traiteLe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "traite_par")
    private User traitePar;
}
