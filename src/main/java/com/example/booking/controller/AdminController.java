package com.example.booking.controller;

import com.example.booking.dto.SalonResponse;
import com.example.booking.model.enums.SalonStatut;
import com.example.booking.service.SalonService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Administration de la plateforme.
 *
 * Le préfixe /api/admin/** est déjà restreint au rôle ADMIN dans SecurityConfig ;
 * le @PreAuthorize ici est une seconde barrière, au cas où le filtre évoluerait.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final SalonService salonService;

    public AdminController(SalonService salonService) {
        this.salonService = salonService;
    }

    /** File de validation : les salons créés par des professionnels, en attente. */
    @GetMapping("/salons")
    public ResponseEntity<Page<SalonResponse>> aValider(
            @RequestParam(defaultValue = "EN_ATTENTE") SalonStatut statut,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok(salonService.parStatut(statut, pageable));
    }

    /** Valider (ACTIF), suspendre (SUSPENDU) ou remettre en attente un salon. */
    @PatchMapping("/salons/{id}/statut")
    public ResponseEntity<SalonResponse> changerStatut(
            @PathVariable Long id,
            @RequestParam SalonStatut statut
    ) {
        return ResponseEntity.ok(salonService.changerStatut(id, statut));
    }
}
