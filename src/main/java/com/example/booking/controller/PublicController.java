package com.example.booking.controller;

import com.example.booking.dto.AvisResponse;
import com.example.booking.dto.CreneauDisponible;
import com.example.booking.dto.DisponibilitesResponse;
import com.example.booking.dto.EmployeResponse;
import com.example.booking.dto.SalonDetailResponse;
import com.example.booking.dto.SalonResponse;
import com.example.booking.service.DisponibiliteService;
import com.example.booking.service.AvisService;
import com.example.booking.service.PublicCatalogService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Parcours de découverte, sans authentification.
 *
 * Un visiteur doit pouvoir chercher, comparer et consulter les disponibilités
 * avant de créer un compte : l'inscription imposée trop tôt est le premier
 * facteur d'abandon du tunnel. La connexion n'intervient qu'à la confirmation.
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final PublicCatalogService catalogue;
    private final DisponibiliteService disponibilites;

    private final AvisService avis;

    public PublicController(PublicCatalogService catalogue, DisponibiliteService disponibilites, AvisService avis) {
        this.catalogue = catalogue;
        this.disponibilites = disponibilites;
        this.avis = avis;
    }

    /** Avis publiés d'un salon, du plus récent au plus ancien. */
    @GetMapping("/salons/{id}/avis")
    public ResponseEntity<Page<AvisResponse>> avis(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return cache60(avis.parSalon(id, PageRequest.of(page, Math.min(size, 50))));
    }

    @GetMapping("/salons")
    public ResponseEntity<Page<SalonResponse>> rechercher(
            @RequestParam(required = false) String ville,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return cache60(catalogue.rechercher(ville, q, pageable));
    }

    @GetMapping("/salons/{id}")
    public ResponseEntity<SalonDetailResponse> fiche(@PathVariable Long id) {
        return cache60(catalogue.fiche(id));
    }

    @GetMapping("/salons/{id}/employes")
    public ResponseEntity<List<EmployeResponse>> employes(
            @PathVariable Long id,
            @RequestParam Long prestationId
    ) {
        return cache60(catalogue.employesPour(id, prestationId));
    }

    /** La route la plus appelée du produit. */
    @GetMapping("/salons/{id}/disponibilites")
    public ResponseEntity<DisponibilitesResponse> creneaux(
            @PathVariable Long id,
            @RequestParam Long prestationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long employeId
    ) {
        List<CreneauDisponible> creneaux = disponibilites.creneaux(id, prestationId, date, employeId);
        // Cache court : un créneau peut être pris à tout moment, mais 60 s
        // absorbent les rafales d'un même visiteur qui compare les jours.
        return cache60(new DisponibilitesResponse(date, id, null, creneaux));
    }

    /**
     * Les prochains jours ayant au moins un créneau libre.
     * Évite au visiteur de cliquer jour après jour sur un agenda vide.
     */
    @GetMapping("/salons/{id}/prochaines-dispos")
    public ResponseEntity<List<LocalDate>> prochainsJours(
            @PathVariable Long id,
            @RequestParam Long prestationId,
            @RequestParam(required = false) Long employeId,
            @RequestParam(defaultValue = "7") int jours
    ) {
        return cache60(disponibilites.prochainsJoursDisponibles(id, prestationId, employeId, Math.min(jours, 30)));
    }

    @GetMapping("/villes")
    public ResponseEntity<List<String>> villes() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(catalogue.villes());
    }

    private <T> ResponseEntity<T> cache60(T corps) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                .body(corps);
    }
}
