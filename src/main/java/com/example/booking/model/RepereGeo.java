package com.example.booking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Centre d'une ville ou d'un quartier marocain.
 *
 * Sert à situer un salon dont les coordonnées exactes n'ont pas été relevées.
 * Table de référence, alimentée par migration : ouvrir une nouvelle ville
 * demande une ligne, pas une livraison de code.
 *
 * Quartier nul : le repère vaut pour la ville entière.
 */
@Entity
@Table(name = "repere_geo")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepereGeo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String ville;

    @Column(length = 80)
    private String quartier;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;
}
