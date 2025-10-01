package com.example.booking.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreneauRequest {
    @NotNull(message = "L'heure de début est obligatoire")
    @Future(message = "L'heure de début doit être dans le futur")
    private LocalDateTime debut;

    @NotNull(message = "L'heure de fin est obligatoire")
    @Future(message = "L'heure de fin doit être dans le futur")
    private LocalDateTime fin;

    @NotNull(message = "Le salon est obligatoire")
    private Long salonId;
}
