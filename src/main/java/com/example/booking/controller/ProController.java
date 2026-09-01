package com.example.booking.controller;

import com.example.booking.dto.AbsenceRequest;
import com.example.booking.dto.EmployeRequest;
import com.example.booking.dto.EmployeResponse;
import com.example.booking.dto.HoraireRequest;
import com.example.booking.dto.HoraireResponse;
import com.example.booking.service.EquipeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Back-office professionnel : équipe, affectations, horaires, absences.
 *
 * /api/pro/** est déjà restreint aux rôles PRO et ADMIN dans SecurityConfig.
 * Chaque route vérifie en plus la propriété de la ressource visée — le rôle
 * seul ne dit pas de QUEL salon on parle.
 */
@RestController
@RequestMapping("/api/pro")
public class ProController {

    private final EquipeService equipe;

    public ProController(EquipeService equipe) {
        this.equipe = equipe;
    }

    /* ---------- Équipe ---------- */

    @GetMapping("/salons/{salonId}/employes")
    @PreAuthorize("hasRole('ADMIN') or @permission.isOwnerSalon(#salonId, authentication)")
    public ResponseEntity<List<EmployeResponse>> lister(@PathVariable Long salonId) {
        return ResponseEntity.ok(equipe.lister(salonId));
    }

    @PostMapping("/salons/{salonId}/employes")
    @PreAuthorize("hasRole('ADMIN') or @permission.isOwnerSalon(#salonId, authentication)")
    public ResponseEntity<EmployeResponse> creer(@PathVariable Long salonId,
                                                 @Valid @RequestBody EmployeRequest req) {
        return ResponseEntity.ok(equipe.creer(salonId, req));
    }

    @PutMapping("/employes/{employeId}")
    @PreAuthorize("hasRole('ADMIN') or @permission.isOwnerEmploye(#employeId, authentication)")
    public ResponseEntity<EmployeResponse> modifier(@PathVariable Long employeId,
                                                    @Valid @RequestBody EmployeRequest req) {
        return ResponseEntity.ok(equipe.modifier(employeId, req));
    }

    @DeleteMapping("/employes/{employeId}")
    @PreAuthorize("hasRole('ADMIN') or @permission.isOwnerEmploye(#employeId, authentication)")
    public ResponseEntity<Void> desactiver(@PathVariable Long employeId) {
        equipe.desactiver(employeId);
        return ResponseEntity.noContent().build();
    }

    /** Remplace la liste des prestations que ce praticien sait réaliser. */
    @PutMapping("/employes/{employeId}/prestations")
    @PreAuthorize("hasRole('ADMIN') or @permission.isOwnerEmploye(#employeId, authentication)")
    public ResponseEntity<Void> affecter(@PathVariable Long employeId,
                                         @RequestBody Map<String, List<Long>> corps) {
        equipe.affecterPrestations(employeId, corps.getOrDefault("prestationIds", List.of()));
        return ResponseEntity.noContent().build();
    }

    /* ---------- Horaires ---------- */

    @GetMapping("/salons/{salonId}/horaires")
    @PreAuthorize("hasRole('ADMIN') or @permission.isOwnerSalon(#salonId, authentication)")
    public ResponseEntity<List<HoraireResponse>> horairesSalon(@PathVariable Long salonId) {
        return ResponseEntity.ok(equipe.horairesSalon(salonId));
    }

    /** Remplace la semaine complète. Un diff partiel serait plus fragile pour un gain nul. */
    @PutMapping("/salons/{salonId}/horaires")
    @PreAuthorize("hasRole('ADMIN') or @permission.isOwnerSalon(#salonId, authentication)")
    public ResponseEntity<List<HoraireResponse>> definirHorairesSalon(
            @PathVariable Long salonId,
            @Valid @RequestBody List<HoraireRequest> semaine) {
        return ResponseEntity.ok(equipe.remplacerHorairesSalon(salonId, semaine));
    }

    @GetMapping("/employes/{employeId}/horaires")
    @PreAuthorize("hasRole('ADMIN') or @permission.isOwnerEmploye(#employeId, authentication)")
    public ResponseEntity<List<HoraireResponse>> horairesEmploye(@PathVariable Long employeId) {
        return ResponseEntity.ok(equipe.horairesEmploye(employeId));
    }

    @PutMapping("/employes/{employeId}/horaires")
    @PreAuthorize("hasRole('ADMIN') or @permission.isOwnerEmploye(#employeId, authentication)")
    public ResponseEntity<List<HoraireResponse>> definirHorairesEmploye(
            @PathVariable Long employeId,
            @Valid @RequestBody List<HoraireRequest> semaine) {
        return ResponseEntity.ok(equipe.remplacerHorairesEmploye(employeId, semaine));
    }

    /* ---------- Absences ---------- */

    @PostMapping("/absences")
    @PreAuthorize("hasRole('ADMIN') "
            + "or (#req.employeId != null and @permission.isOwnerEmploye(#req.employeId, authentication)) "
            + "or (#req.salonId   != null and @permission.isOwnerSalon(#req.salonId, authentication))")
    public ResponseEntity<Map<String, Long>> creerAbsence(@Valid @RequestBody AbsenceRequest req) {
        return ResponseEntity.ok(Map.of("id", equipe.creerAbsence(req)));
    }

    @DeleteMapping("/absences/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> supprimerAbsence(@PathVariable Long id) {
        equipe.supprimerAbsence(id);
        return ResponseEntity.noContent().build();
    }
}
