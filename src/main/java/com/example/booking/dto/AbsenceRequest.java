package com.example.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;

@Data
public class AbsenceRequest {
    /** L'un des deux, jamais les deux : congé d'un praticien ou fermeture du salon. */
    private Long employeId;
    private Long salonId;

    @NotNull private Instant debut;
    @NotNull private Instant fin;
    @Size(max = 200) private String motif;
}
