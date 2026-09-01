package com.example.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrestationResponse {
    private Long id;
    private String nom;
    private String description;
    private String categorie;
    /** Prix en dirhams (MAD). */
    private BigDecimal prix;
    private Integer dureeMinutes;
    private Long salonId;
}
