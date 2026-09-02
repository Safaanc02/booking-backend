package com.example.booking.controller;

import com.example.booking.dto.PrestationRequest;
import com.example.booking.dto.PrestationResponse;
import com.example.booking.service.PrestationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Les réponses exposent des DTO, jamais l'entité Prestation : sérialiser
 * l'entité ferait fuiter le salon complet — et sa liste de prestations — en
 * cascade dans chaque réponse.
 */
@RestController
@RequestMapping("/api/prestations")
public class PrestationController {

    private final PrestationService prestationService;

    public PrestationController(PrestationService prestationService) {
        this.prestationService = prestationService;
    }

    /** Catalogue de toute la plateforme — administration uniquement. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PrestationResponse>> getAllPrestations() {
        return ResponseEntity.ok(prestationService.getAllPrestations());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @permission.isOwnerPrestation(#id, authentication)")
    public ResponseEntity<PrestationResponse> getPrestationById(@PathVariable Long id) {
        return ResponseEntity.ok(prestationService.getPrestationById(id));
    }

    /**
     * Catalogue d'un salon, vu par son propriétaire — y compris avant
     * validation, pour pouvoir le paramétrer. Le parcours public lit la fiche
     * /api/public/salons/{id}, qui n'expose que les salons ACTIF.
     */
    @GetMapping("/salon/{salonId}")
    @PreAuthorize("hasRole('ADMIN') or @permission.isOwnerSalon(#salonId, authentication)")
    public ResponseEntity<List<PrestationResponse>> getBySalon(@PathVariable Long salonId) {
        return ResponseEntity.ok(prestationService.getBySalon(salonId));
    }

    @PostMapping("/salon/{salonId}")
    @PreAuthorize("hasRole('ADMIN') or @permission.isOwnerSalon(#salonId, authentication)")
    public ResponseEntity<PrestationResponse> createPrestation(
            @PathVariable Long salonId,
            @Valid @RequestBody PrestationRequest request
    ) {
        PrestationResponse created = prestationService.createPrestation(salonId, request);
        return ResponseEntity.created(URI.create("/api/prestations/" + created.getId())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @permission.isOwnerPrestation(#id, authentication)")
    public ResponseEntity<PrestationResponse> updatePrestation(
            @PathVariable Long id,
            @Valid @RequestBody PrestationRequest request
    ) {
        return ResponseEntity.ok(prestationService.updatePrestation(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @permission.isOwnerPrestation(#id, authentication)")
    public ResponseEntity<Void> deletePrestation(@PathVariable Long id) {
        prestationService.deletePrestation(id);
        return ResponseEntity.noContent().build();
    }
}
