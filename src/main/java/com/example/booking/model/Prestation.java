package com.example.booking.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Une prestation du catalogue d'un salon. */
@Entity
@Table(name = "prestation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prestation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "salon_id")
    private Salon salon;

    @Column(nullable = false, length = 120)
    private String nom;

    @Column(length = 500)
    private String description;

    /** Regroupement sur la fiche : « Coupe », « Couleur », « Soin ». */
    @Column(length = 60)
    private String categorie;

    /** Montant en dirhams. BigDecimal, jamais double : pas d'arrondi flottant sur de l'argent. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal prix;

    @Column(name = "duree_minutes", nullable = false)
    private Integer dureeMinutes;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;

    @Column(nullable = false)
    @Builder.Default
    private int ordre = 0;
}
