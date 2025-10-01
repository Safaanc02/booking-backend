package com.example.booking.controller;

import com.example.booking.dto.CreateReservationRequest;
import com.example.booking.dto.ReservationResponse;
import com.example.booking.dto.UpdateReservationRequest;
import com.example.booking.model.Reservation;
import com.example.booking.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * 🔹 Liste paginée des réservations (ADMIN uniquement)
     */
    @GetMapping
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<Page<ReservationResponse>> getAll(Pageable pageable) {
        return ResponseEntity.ok(reservationService.getAll(pageable));
    }

    /**
     * 🔹 Récupérer une réservation par ID (ADMIN ou propriétaire)
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('admin','client','pro')")
    public ResponseEntity<ReservationResponse> getReservationById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(reservationService.getById(id));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 🔹 Créer une réservation (client, pro ou admin)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('client','pro','admin')")
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.create(req));
    }

    /**
     * 🔹 Mettre à jour une réservation (ADMIN ou propriétaire)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('client','pro','admin')")
    public ResponseEntity<ReservationResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReservationRequest req
    ) {
        try {
            return ResponseEntity.ok(reservationService.update(id, req));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 🔹 Voir mes réservations (client, pro, admin)
     */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('client','pro','admin')")
    public ResponseEntity<List<ReservationResponse>> getMine() {
        return ResponseEntity.ok(reservationService.getMine());
    }

    /**
     * 🔹 Supprimer une réservation (ADMIN ou propriétaire)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('client','pro','admin')")
    public ResponseEntity<Void> deleteReservation(@PathVariable Long id) {
        try {
            reservationService.delete(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
