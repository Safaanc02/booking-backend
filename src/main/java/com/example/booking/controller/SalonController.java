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
    @PreAuthorize("hasAnyRole('PRO','ADMIN')")
    public ResponseEntity<SalonResponse> createSalon(@Valid @RequestBody SalonRequest request) {
        SalonResponse created = salonService.createSalon(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Les salons du professionnel connecté. */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('PRO','ADMIN')")
    public ResponseEntity<java.util.List<SalonResponse>> mesSalons() {
        return ResponseEntity.ok(salonService.mesSalons());
    }

    /**
     * Fiche brute d'un salon — propriétaire ou administration.
     *
     * Sans restriction, tout compte authentifié lisait n'importe quel salon,
     * y compris EN_ATTENTE ou SUSPENDU. Le parcours public passe par
     * /api/public/salons/{id}, qui filtre sur ACTIF.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @permission.peutGererSalon(#id, authentication)")
    public ResponseEntity<SalonResponse> getSalonById(@PathVariable Long id) {
        return salonService.getSalonById(id)
                .map(salon -> ResponseEntity.ok(salonService.toResponse(salon)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Tous les salons, tous statuts confondus — administration uniquement.
     *
     * Cette route ne filtrait pas sur le statut, contrairement à la recherche
     * publique : un simple compte client voyait les salons non encore validés
     * et leurs propriétaires. Un professionnel utilise /api/salons/me.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<SalonResponse>> getAllSalons(Pageable pageable) {
        return ResponseEntity.ok(salonService.getAllSalons(pageable));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @permission.peutGererSalon(#id, authentication)")
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
    @PreAuthorize("hasRole('ADMIN') or @permission.peutGererSalon(#id, authentication)")
    public ResponseEntity<Void> deleteSalon(@PathVariable Long id) {
        salonService.deleteSalon(id);
        return ResponseEntity.noContent().build();
    }
}
