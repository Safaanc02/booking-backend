package com.example.booking.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Quel praticien sait réaliser quelle prestation, et en combien de temps. */
@Entity
@Table(name = "employe_prestation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployePrestation {

    @EmbeddedId
    private Id id;

    @MapsId("employeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employe_id")
    private Employe employe;

    @MapsId("prestationId")
    @ManyToOne(optional = false)
    @JoinColumn(name = "prestation_id")
    private Prestation prestation;

    /** Nulle si le praticien met le temps standard de la prestation. */
    @Column(name = "duree_specifique_minutes")
    private Integer dureeSpecifiqueMinutes;

    /** Durée effective pour ce praticien. */
    public int dureeEffective() {
        return dureeSpecifiqueMinutes != null
                ? dureeSpecifiqueMinutes
                : prestation.getDureeMinutes();
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {
        @Column(name = "employe_id")
        private Long employeId;
        @Column(name = "prestation_id")
        private Long prestationId;
    }
}
