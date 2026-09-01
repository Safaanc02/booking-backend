package com.example.booking.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Plage de travail récurrente, hebdomadaire.
 *
 * La cible est soit un salon, soit un employé — jamais les deux (contrainte
 * chk_horaire_cible). Un employé sans horaire propre hérite de celui du salon.
 * Une journée coupée par la pause déjeuner s'exprime par deux lignes.
 */
@Entity
@Table(name = "horaire_ouverture")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HoraireOuverture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salon_id")
    private Salon salon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employe_id")
    private Employe employe;

    @Column(name = "jour_semaine", nullable = false)
    private DayOfWeek jourSemaine;

    @Column(name = "heure_debut", nullable = false)
    private LocalTime heureDebut;

    @Column(name = "heure_fin", nullable = false)
    private LocalTime heureFin;
}
