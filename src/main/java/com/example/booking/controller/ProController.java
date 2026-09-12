package com.example.booking.controller;

import com.example.booking.dto.AbsenceRequest;
import com.example.booking.dto.AbsenceResponse;
import com.example.booking.dto.AgendaResponse;
import com.example.booking.dto.ReservationProRequest;
import com.example.booking.dto.EmployeRequest;
import com.example.booking.dto.EmployeResponse;
import com.example.booking.dto.HoraireRequest;
import com.example.booking.dto.HoraireResponse;
import com.example.booking.model.enums.StatutReservation;
import com.example.booking.service.AgendaService;
import com.example.booking.dto.AvisResponse;
import com.example.booking.service.AvisService;
import com.example.booking.service.EquipeService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
    private final AgendaService agenda;
    private final AvisService avis;

    public ProController(EquipeService equipe, AgendaService agenda, AvisService avis) {
        this.equipe = equipe;
        this.agenda = agenda;
        this.avis = avis;
    }

    /**
     * Droit de réponse du salon.
     *
     * Un avis négatif auquel le gérant répond posément fait souvent meilleure
     * impression qu'un avis neutre laissé sans suite.
     */
    @PostMapping("/avis/{avisId}/reponse")
    @PreAuthorize("hasAnyRole('PRO','ADMIN')")
    public ResponseEntity<AvisResponse> repondre(@PathVariable Long avisId,
                                                 @RequestBody Map<String, String> corps) {
        return ResponseEntity.ok(avis.repondre(avisId, corps.get("reponse")));
    }

    /* ---------- Agenda ---------- */

    /** Rendez-vous du salon. `jours` vaut 1 pour la vue jour, 7 pour la semaine. */
    @GetMapping("/salons/{salonId}/agenda")
    @PreAuthorize("hasRole('ADMIN') or @permission.peutGererSalon(#salonId, authentication)")
    public ResponseEntity<List<AgendaResponse>> agenda(
            @PathVariable Long salonId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "1") int jours) {
        return ResponseEntity.ok(agenda.agenda(salonId, date, jours));
    }

    /**
     * Planning personnel : les rendez-vous du compte connecté, tous salons
     * confondus.
     *
     * Aucun contrôle de propriété — chacun ne voit que ses propres fiches, la
     * résolution se fait sur le keycloakId du jeton. Le rôle PRO suffit :
     * l'accès est celui d'un membre d'équipe, pas d'un administrateur.
     */
    @GetMapping("/mon-planning")
    @PreAuthorize("hasAnyRole('PRO','ADMIN')")
    public ResponseEntity<List<AgendaResponse>> monPlanning(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "1") int jours) {
        return ResponseEntity.ok(agenda.monPlanning(date, jours));
    }

    /** Rendez-vous pris par téléphone ou au comptoir, pour un client sans compte. */
    @PostMapping("/salons/{salonId}/reservations")
    @PreAuthorize("hasRole('ADMIN') or @permission.peutGererSalon(#salonId, authentication)")
    public ResponseEntity<AgendaResponse> creerHorsLigne(
            @PathVariable Long salonId,
            @Valid @RequestBody ReservationProRequest req) {
        return ResponseEntity.ok(agenda.creerHorsLigne(salonId, req));
    }

    /** HONOREE, ABSENT ou ANNULEE_SALON. La propriété est vérifiée dans le service. */
    @PatchMapping("/reservations/{id}/statut")
    @PreAuthorize("hasAnyRole('PRO','ADMIN')")
    public ResponseEntity<AgendaResponse> changerStatut(
            @PathVariable Long id,
            @RequestParam StatutReservation statut) {
        return ResponseEntity.ok(agenda.changerStatut(id, statut));
    }

    /* ---------- Équipe ---------- */

    @GetMapping("/salons/{salonId}/employes")
    @PreAuthorize("hasRole('ADMIN') or @permission.peutGererSalon(#salonId, authentication)")
    public ResponseEntity<List<EmployeResponse>> lister(@PathVariable Long salonId) {
        return ResponseEntity.ok(equipe.lister(salonId));
    }

    @PostMapping("/salons/{salonId}/employes")
    @PreAuthorize("hasRole('ADMIN') or @permission.peutGererSalon(#salonId, authentication)")
    public ResponseEntity<EmployeResponse> creer(@PathVariable Long salonId,
                                                 @Valid @RequestBody EmployeRequest req) {
        return ResponseEntity.ok(equipe.creer(salonId, req));
    }

    @PutMapping("/employes/{employeId}")
    @PreAuthorize("hasRole('ADMIN') or @permission.peutGererEmploye(#employeId, authentication)")
    public ResponseEntity<EmployeResponse> modifier(@PathVariable Long employeId,
                                                    @Valid @RequestBody EmployeRequest req) {
        return ResponseEntity.ok(equipe.modifier(employeId, req));
    }

    @DeleteMapping("/employes/{employeId}")
    @PreAuthorize("hasRole('ADMIN') or @permission.peutGererEmploye(#employeId, authentication)")
    public ResponseEntity<Void> desactiver(@PathVariable Long employeId) {
        equipe.desactiver(employeId);
        return ResponseEntity.noContent().build();
    }

    /** Remplace la liste des prestations que ce praticien sait réaliser. */
    @PutMapping("/employes/{employeId}/prestations")
    @PreAuthorize("hasRole('ADMIN') or @permission.peutGererEmploye(#employeId, authentication)")
    public ResponseEntity<Void> affecter(@PathVariable Long employeId,
                                         @RequestBody Map<String, List<Long>> corps) {
        equipe.affecterPrestations(employeId, corps.getOrDefault("prestationIds", List.of()));
        return ResponseEntity.noContent().build();
    }

    /* ---------- Horaires ---------- */

    @GetMapping("/salons/{salonId}/horaires")
    @PreAuthorize("hasRole('ADMIN') or @permission.peutGererSalon(#salonId, authentication)")
    public ResponseEntity<List<HoraireResponse>> horairesSalon(@PathVariable Long salonId) {
        return ResponseEntity.ok(equipe.horairesSalon(salonId));
    }

    /** Remplace la semaine complète. Un diff partiel serait plus fragile pour un gain nul. */
    @PutMapping("/salons/{salonId}/horaires")
    @PreAuthorize("hasRole('ADMIN') or @permission.peutGererSalon(#salonId, authentication)")
    public ResponseEntity<List<HoraireResponse>> definirHorairesSalon(
            @PathVariable Long salonId,
            @Valid @RequestBody List<HoraireRequest> semaine) {
        return ResponseEntity.ok(equipe.remplacerHorairesSalon(salonId, semaine));
    }

    @GetMapping("/employes/{employeId}/horaires")
    @PreAuthorize("hasRole('ADMIN') or @permission.peutGererEmploye(#employeId, authentication)")
    public ResponseEntity<List<HoraireResponse>> horairesEmploye(@PathVariable Long employeId) {
        return ResponseEntity.ok(equipe.horairesEmploye(employeId));
    }

    @PutMapping("/employes/{employeId}/horaires")
    @PreAuthorize("hasRole('ADMIN') or @permission.peutGererEmploye(#employeId, authentication)")
    public ResponseEntity<List<HoraireResponse>> definirHorairesEmploye(
            @PathVariable Long employeId,
            @Valid @RequestBody List<HoraireRequest> semaine) {
        return ResponseEntity.ok(equipe.remplacerHorairesEmploye(employeId, semaine));
    }

    /* ---------- Absences ---------- */

    /**
     * Congés et fermetures du salon.
     *
     * La lecture manquait : on pouvait créer une absence et la supprimer, mais
     * pas la retrouver. Un gérant qui fermait une semaine n'avait aucun moyen
     * de vérifier ce qu'il avait saisi, ni de corriger une date — sauf à
     * deviner l'identifiant. Écrire sans pouvoir relire n'est pas une
     * fonctionnalité.
     */
    @GetMapping("/salons/{salonId}/absences")
    @PreAuthorize("hasRole('ADMIN') or @permission.peutGererSalon(#salonId, authentication)")
    public ResponseEntity<List<AbsenceResponse>> absences(@PathVariable Long salonId) {
        return ResponseEntity.ok(equipe.absencesDuSalon(salonId));
    }

    @PostMapping("/absences")
    @PreAuthorize("hasRole('ADMIN') "
            + "or (#req.employeId != null and @permission.peutGererEmploye(#req.employeId, authentication)) "
            + "or (#req.salonId   != null and @permission.peutGererSalon(#req.salonId, authentication))")
    public ResponseEntity<Map<String, Long>> creerAbsence(@Valid @RequestBody AbsenceRequest req) {
        return ResponseEntity.ok(Map.of("id", equipe.creerAbsence(req)));
    }

    /**
     * Suppression d'une absence.
     *
     * Était réservée à l'administration : un professionnel pouvait déclarer un
     * congé mais pas le retirer, et une saisie erronée bloquait son agenda
     * définitivement.
     */
    @DeleteMapping("/absences/{id}")
    @PreAuthorize("hasRole('ADMIN') or @permission.peutGererAbsence(#id, authentication)")
    public ResponseEntity<Void> supprimerAbsence(@PathVariable Long id) {
        equipe.supprimerAbsence(id);
        return ResponseEntity.noContent().build();
    }
}
