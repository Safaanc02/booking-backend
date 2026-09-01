package com.example.booking.mapper;

import com.example.booking.dto.ReservationResponse;
import com.example.booking.model.Reservation;

public final class ReservationMapper {

    private ReservationMapper() {}

    public static ReservationResponse toResponse(Reservation r) {
        return toResponse(r, false);
    }

    public static ReservationResponse toResponse(Reservation r, boolean avisDepose) {
        return new ReservationResponse(
                r.getId(),
                r.getSalon() != null ? r.getSalon().getId() : null,
                r.getSalon() != null ? r.getSalon().getNom() : null,
                r.getPrestation() != null ? r.getPrestation().getId() : null,
                // Le libellé figé prend le relais si la prestation a été retirée du catalogue.
                r.getNomPrestationFige(),
                r.getEmploye() != null ? r.getEmploye().getId() : null,
                r.getEmploye() != null ? r.getEmploye().nomComplet() : null,
                r.getDebut(),
                r.getFin(),
                r.getStatut() != null ? r.getStatut().name() : null,
                r.getPrixFige(),
                r.getNoteClient(),
                avisDepose
        );
    }
}
