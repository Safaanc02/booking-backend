package com.example.booking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

/**
 * Une photo d'un salon.
 *
 * La ligne décrit la photo, le binaire vit sur le disque. `fichier` est le nom
 * engendré par le serveur — jamais celui fourni par l'appelant.
 */
@Entity
@Table(name = "photo_salon")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhotoSalon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salon_id", nullable = false)
    private Salon salon;

    @Column(nullable = false, length = 80, unique = true)
    private String fichier;

    @Column(name = "type_mime", nullable = false, length = 40)
    private String typeMime;

    @Column(nullable = false)
    private int taille;

    /** Rang d'affichage. La première sert de couverture partout. */
    @Column(nullable = false)
    @Builder.Default
    private int ordre = 0;

    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private Instant creeLe;
}
