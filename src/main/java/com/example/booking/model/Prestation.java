package com.example.booking.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Une prestation proposée par un salon : « Coupe femme », « Barbe »…
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prestation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    private String description;

    /** Montant en dirhams (MAD). BigDecimal, jamais double : pas d'arrondi flottant sur de l'argent. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal prix;

    /**
     * Durée en minutes.
     *
     * Le DTO exposait auparavant un String libre (« 30min », « 1h ») que le
     * service passait à Integer.parseInt() — ce qui levait une
     * NumberFormatException sur toute valeur autre qu'un nombre nu.
     * C'est un Integer de bout en bout désormais.
     */
    @Column(nullable = false)
    private Integer dureeMinutes;

    @ManyToOne
    @JoinColumn(name = "salon_id")
    private Salon salon;
}
