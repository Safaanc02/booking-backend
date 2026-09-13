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

import java.time.Instant;

/**
 * Note privée d'un salon sur un de ses clients.
 *
 * « Toujours en retard », « préfère Sofia », « allergique à l'ammoniaque » :
 * ces phrases sont écrites pour un carnet, pas pour une plateforme. Elles
 * appartiennent au salon qui les écrit, et à lui seul — les partager ferait
 * suivre un client d'un établissement à l'autre avec une réputation qu'il n'a
 * pas choisie et que personne ne lui a montrée.
 */
@Entity
@Table(name = "note_client")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoteClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salon_id", nullable = false)
    private Salon salon;

    /** Clé du client telle que la calcule la fiche. */
    @Column(nullable = false, length = 40)
    private String cle;

    @Column(nullable = false, columnDefinition = "text")
    private String texte;

    @Column(name = "maj_le", nullable = false)
    @Builder.Default
    private Instant majLe = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maj_par")
    private User majPar;
}
