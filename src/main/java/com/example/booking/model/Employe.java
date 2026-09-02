package com.example.booking.model;

import com.example.booking.model.enums.RoleEmploye;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un praticien rattaché à un salon. C'est lui qu'on réserve, pas le salon. */
@Entity
@Table(name = "employe")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "salon_id")
    private Salon salon;

    /**
     * Compte rattaché, nul tant que la personne ne s'est pas connectée.
     *
     * C'est ce lien qui donne des droits : sans lui, la fiche n'est qu'une
     * ligne d'agenda.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Ce que la personne peut faire, une fois son compte rattaché. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RoleEmploye role = RoleEmploye.PRATICIEN;

    @Column(nullable = false)
    private String prenom;

    private String nom;
    private String titre;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;

    @Column(nullable = false)
    @Builder.Default
    private int ordre = 0;

    public String nomComplet() {
        return nom == null || nom.isBlank() ? prenom : prenom + " " + nom;
    }
}
