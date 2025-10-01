// src/main/java/com/example/booking/dto/reservation/ReservationResponse.java
package com.example.booking.dto;

public record ReservationResponse(
        Long id,
        String clientUsername,
        Long creneauId,
        String statut
) {}
