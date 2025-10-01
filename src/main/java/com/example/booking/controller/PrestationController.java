package com.example.booking.controller;

import com.example.booking.dto.PrestationRequest;
import com.example.booking.dto.PrestationResponse;
import com.example.booking.model.Prestation;
import com.example.booking.service.PrestationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/prestations")
public class PrestationController {

    private final PrestationService prestationService;

    public PrestationController(PrestationService prestationService) {
        this.prestationService = prestationService;
    }

    /**
     * 🔹 Récupérer toutes les prestations
     */
    @GetMapping
    public ResponseEntity<List<Prestation>> getAllPrestations() {
        return ResponseEntity.ok(prestationService.getAllPrestations());
    }

    /**
     * 🔹 Récupérer une prestation par ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Prestation> getPrestationById(@PathVariable Long id) {
        return ResponseEntity.ok(prestationService.getPrestationById(id));
    }

    /**
     * 🔹 Créer une prestation dans un salon (Admin ou propriétaire du salon)
     */
    @PostMapping("/salon/{salonId}")
    @PreAuthorize("hasRole('admin') or @permission.isOwnerSalon(#salonId, authentication)")
    public ResponseEntity<PrestationResponse> createPrestation(
            @PathVariable Long salonId,
            @Valid @RequestBody PrestationRequest request
    ) {
        PrestationResponse created = prestationService.createPrestation(salonId, request);
        return ResponseEntity.created(URI.create("/api/prestations/" + created.getId())).body(created);
    }

    /**
     * 🔹 Mettre à jour une prestation
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('admin') or @permission.isOwnerPrestation(#id, authentication)")
    public ResponseEntity<Prestation> updatePrestation(
            @PathVariable Long id,
            @Valid @RequestBody PrestationRequest request
    ) {
        return ResponseEntity.ok(prestationService.updatePrestation(id, request));
    }

    /**
     * 🔹 Supprimer une prestation
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('admin') or @permission.isOwnerPrestation(#id, authentication)")
    public ResponseEntity<Void> deletePrestation(@PathVariable Long id) {
        prestationService.deletePrestation(id);
        return ResponseEntity.noContent().build();
    }
}
