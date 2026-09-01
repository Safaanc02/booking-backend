package com.example.booking.model;

import com.example.booking.model.enums.StatutAvis;
import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Un avis, adossé à un rendez-vous honoré. Voir la migration V7. */
@Entity
@Table(name = "avis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Avis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "reservation_id", unique = true)
    private Reservation reservation;

    @ManyToOne(optional = false)
    @JoinColumn(name = "salon_id")
    private Salon salon;

    @ManyToOne
    @JoinColumn(name = "employe_id")
    private Employe employe;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private User client;

    @Column(nullable = false)
    private Short note;

    @Column(length = 1000)
    private String commentaire;

    @Column(name = "reponse_salon", length = 1000)
    private String reponseSalon;

    @Column(name = "reponse_le")
    private Instant reponseLe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutAvis statut = StatutAvis.PUBLIE;

    // La valeur vient du DEFAULT now() de la base. Sans @Generated, Hibernate
    // ne la relit pas après insertion et le champ reste nul dans la réponse.
    @Generated(event = EventType.INSERT)
    @Column(name = "cree_le", nullable = false, insertable = false, updatable = false)
    private Instant creeLe;
}
