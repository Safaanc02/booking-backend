package com.example.booking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;

@Data
public class HoraireRequest {
    /** Numérotation ISO : lundi = 1, dimanche = 7. */
    @NotNull @Min(1) @Max(7)
    private Integer jourSemaine;

    @NotNull private LocalTime heureDebut;
    @NotNull private LocalTime heureFin;
}
