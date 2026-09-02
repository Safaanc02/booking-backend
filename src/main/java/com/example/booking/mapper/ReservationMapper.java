package com.example.booking.mapper;

import com.example.booking.dto.ReservationResponse;
import com.example.booking.model.Reservation;
import com.example.booking.model.enums.StatutReservation;

import java.time.Duration;
import java.time.Instant;

public final class ReservationMapper {

    private ReservationMapper() {}

    /**
     * Même règle que ReservationService.annuler : un statut encore actif, et
     * le préavis du salon respecté. Dupliquer la condition serait un risque de
     * dérive — elle est ici volontairement réduite à sa forme la plus simple,
     * et le service reste seul juge au moment de l'écriture.
     */
    private static boolean estAnnulable(Reservation r) {
        if (r.getStatut() == null || !r.getStatut().bloqueLeCreneau()) return false;
        Instant limite = r.getDebut().minus(Duration.ofHours(delai(r)));
        return Instant.now().isBefore(limite);
    }

    private static int delai(Reservation r) {
        return r.getSalon() != null ? r.getSalon().getDelaiAnnulationHeures() : 24;
    }

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
                avisDepose,
                estAnnulable(r),
                delai(r)
        );
    }
}
