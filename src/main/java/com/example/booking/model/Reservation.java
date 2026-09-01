package com.example.booking.model;

import com.example.booking.model.enums.StatutReservation;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un rendez-vous.
 *
 * Porte désormais directement ses bornes temporelles : le lien vers Creneau a
 * disparu avec la table, qui rendait impossible une prestation occupant
 * plusieurs plages consécutives.
 */
@Entity
@Table(name = "reservation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "client_id")
    private User client;

    @ManyToOne(optional = false)
    @JoinColumn(name = "salon_id")
    private Salon salon;

    @ManyToOne
    @JoinColumn(name = "employe_id")
    private Employe employe;

    /** Peut devenir nulle si la prestation est retirée du catalogue ; le libellé figé prend le relais. */
    @ManyToOne
    @JoinColumn(name = "prestation_id")
    private Prestation prestation;

    @Column(nullable = false)
    private Instant debut;

    /** Calculée : début + durée effective de la prestation pour ce praticien. */
    @Column(nullable = false)
    private Instant fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutReservation statut = StatutReservation.CONFIRMEE;

    /** Prix au moment de la réservation, en MAD. Une hausse de tarif ne rétroagit pas. */
    @Column(name = "prix_fige", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixFige;

    @Column(name = "nom_prestation_fige", nullable = false, length = 120)
    private String nomPrestationFige;

    @Column(name = "note_client", length = 500)
    private String noteClient;

    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private Instant creeLe;

    @Column(name = "annulee_le")
    private Instant annuleeLe;
}
