package com.example.booking.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreneauResponse {
    private Long id;
    private LocalDateTime debut;
    private LocalDateTime fin;
    private Long salonId;
}
