package com.example.booking.controller;

import com.example.booking.dto.SalonDetailResponse;
import com.example.booking.dto.SalonResponse;
import com.example.booking.service.PublicCatalogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

/**
 * Parcours de découverte, sans authentification.
 *
 * Un visiteur doit pouvoir chercher, comparer et consulter avant de créer un
 * compte : c'est l'inscription imposée trop tôt qui fait chuter la conversion.
 * La connexion n'intervient qu'au moment de confirmer une réservation.
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final PublicCatalogService catalogService;

    public PublicController(PublicCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/salons")
    public ResponseEntity<Page<SalonResponse>> rechercher(
            @RequestParam(required = false) String ville,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                .body(catalogService.rechercher(ville, q, pageable));
    }

    @GetMapping("/salons/{id}")
    public ResponseEntity<SalonDetailResponse> fiche(@PathVariable Long id) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                .body(catalogService.fiche(id));
    }

    @GetMapping("/villes")
    public ResponseEntity<List<String>> villes() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(catalogService.villes());
    }
}
