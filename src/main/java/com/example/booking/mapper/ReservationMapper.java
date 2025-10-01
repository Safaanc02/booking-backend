// src/main/java/com/example//mapper/ReservationMapper.java
package com.example.booking.mapper;

import com.example.booking.dto.ReservationResponse;
import com.example.booking.model.Reservation;

public class ReservationMapper {
    public static ReservationResponse toResponse(Reservation r) {
        return new ReservationResponse(
                r.getId(),
                r.getClient() != null ? r.getClient().getUsername() : null,
                r.getCreneau() != null ? r.getCreneau().getId() : null,
                r.getStatut()
        );
    }
}
