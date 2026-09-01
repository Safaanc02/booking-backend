package com.example.booking.model;

import com.example.booking.notification.CanalNotification;
import com.example.booking.notification.TypeNotification;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Trace d'un message envoyé, ou tenté. Voir la migration V6 pour le rôle d'idempotence. */
@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TypeNotification type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CanalNotification.Canal canal;

    @Column(nullable = false, length = 180)
    private String destinataire;

    @Column(name = "creee_le", nullable = false, insertable = false, updatable = false)
    private Instant creeeLe;

    @Column(name = "envoyee_le")
    private Instant envoyeeLe;

    @Column(columnDefinition = "text")
    private String erreur;
}
