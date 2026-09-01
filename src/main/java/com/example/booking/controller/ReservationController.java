package com.example.booking.controller;

import com.example.booking.dto.CreateReservationRequest;
import com.example.booking.dto.ReservationResponse;
import com.example.booking.dto.UpdateReservationRequest;
import com.example.booking.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Les exceptions remontent telles quelles jusqu'à GlobalExceptionHandler.
 *
 * Les méthodes étaient auparavant enveloppées dans un try/catch (Exception e)
 * renvoyant 404 : un refus d'accès (403) et une panne serveur (500) étaient
 * donc tous deux présentés comme « ressource introuvable ».
 */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /** Liste paginée, réservée à l'administration. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ReservationResponse>> getAll(Pageable pageable) {
        return ResponseEntity.ok(reservationService.getAll(pageable));
    }

    /** Mes réservations. Déclaré avant /{id} pour que "me" ne soit pas lu comme un identifiant. */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('CLIENT','PRO','ADMIN')")
    public ResponseEntity<List<ReservationResponse>> getMine() {
        return ResponseEntity.ok(reservationService.getMine());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT','PRO','ADMIN')")
    public ResponseEntity<ReservationResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT','PRO','ADMIN')")
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody CreateReservationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT','PRO','ADMIN')")
    public ResponseEntity<ReservationResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReservationRequest req
    ) {
        return ResponseEntity.ok(reservationService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENT','PRO','ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reservationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
