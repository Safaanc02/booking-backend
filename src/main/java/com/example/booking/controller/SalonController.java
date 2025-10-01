package com.example.booking.controller;

import com.example.booking.dto.SalonRequest;
import com.example.booking.dto.SalonResponse;
import com.example.booking.service.SalonService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/salons")
public class SalonController {

    private final SalonService salonService;

    public SalonController(SalonService salonService) {
        this.salonService = salonService;
    }

    /**
     * 🔹 Créer un salon (PRO ou ADMIN uniquement)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('pro','admin')")
    public ResponseEntity<SalonResponse> createSalon(@Valid @RequestBody SalonRequest request) {
        SalonResponse created = salonService.createSalon(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 🔹 Récupérer un salon par ID (public)
     */
    @GetMapping("/{id}")
    public ResponseEntity<SalonResponse> getSalonById(@PathVariable Long id) {
        return salonService.getSalonById(id)
                .map(salon -> ResponseEntity.ok(salonService.toResponse(salon)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 🔹 Liste paginée + triable des salons (accessible à tous)
     * Exemple: GET /api/salons?page=0&size=10&sort=nom,asc
     */
    @GetMapping
    public ResponseEntity<Page<SalonResponse>> getAllSalons(Pageable pageable) {
        return ResponseEntity.ok(salonService.getAllSalons(pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin') or @permission.isOwnerSalon(#id, authentication)")
    public ResponseEntity<SalonResponse> updateSalon(
            @PathVariable Long id,
            @Valid @RequestBody SalonRequest request
    ) {
        return salonService.updateSalon(id, request)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }


    /**
     * 🔹 Supprimer un salon (ADMIN ou propriétaire du salon)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin') or @permission.isOwnerSalon(#id, authentication)")
    public ResponseEntity<Void> deleteSalon(@PathVariable Long id) {
        salonService.deleteSalon(id);
        return ResponseEntity.noContent().build();
    }
}
