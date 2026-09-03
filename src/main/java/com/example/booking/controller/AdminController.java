package com.example.booking.controller;

import com.example.booking.comptes.ReferencementService;
import com.example.booking.dto.AvisResponse;
import com.example.booking.dto.DemandeDemoResponse;
import com.example.booking.dto.ReferencementResponse;
import com.example.booking.dto.ReferencementSalonRequest;
import com.example.booking.dto.SalonResponse;
import com.example.booking.model.enums.SalonStatut;
import com.example.booking.model.enums.StatutAvis;
import com.example.booking.model.enums.StatutDemandeDemo;
import com.example.booking.notification.RappelPlanificateur;
import com.example.booking.service.AvisService;
import com.example.booking.service.CurrentUserService;
import com.example.booking.service.DemandeDemoService;
import com.example.booking.service.SalonService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
    private final RappelPlanificateur rappels;

    private final AvisService avis;
    private final ReferencementService referencement;
    private final DemandeDemoService demandes;
    private final CurrentUserService utilisateurCourant;

    public AdminController(SalonService salonService,
                           RappelPlanificateur rappels,
                           AvisService avis,
                           ReferencementService referencement,
                           DemandeDemoService demandes,
                           CurrentUserService utilisateurCourant) {
        this.salonService = salonService;
        this.rappels = rappels;
        this.avis = avis;
        this.referencement = referencement;
        this.demandes = demandes;
        this.utilisateurCourant = utilisateurCourant;
    }

    /* ---------- Demandes de démonstration ---------- */

    /** La file d'attente commerciale, par statut. */
    @GetMapping("/demandes-demo")
    public ResponseEntity<Page<DemandeDemoResponse>> demandes(
            @RequestParam(defaultValue = "NOUVELLE") StatutDemandeDemo statut,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(demandes.lister(statut, pageable));
    }

    /** Combien de demandes attendent d'être traitées — pour la pastille de l'onglet. */
    @GetMapping("/demandes-demo/nouvelles")
    public ResponseEntity<Long> nouvellesDemandes() {
        return ResponseEntity.ok(demandes.compter(StatutDemandeDemo.NOUVELLE));
    }

    /**
     * Fait avancer une demande, en notant qui s'en est occupé.
     *
     * L'agent est résolu par getOrCreate : un administrateur qui traite une
     * demande à sa toute première requête authentifiée n'a pas encore de
     * miroir local, et l'attribution serait perdue.
     */
    @PatchMapping("/demandes-demo/{id}")
    public ResponseEntity<DemandeDemoResponse> traiterDemande(
            @PathVariable Long id,
            @RequestParam StatutDemandeDemo statut,
            @RequestParam(required = false) String note) {
        return ResponseEntity.ok(
                demandes.changerStatut(id, statut, note, utilisateurCourant.getOrCreate()));
    }

    /**
     * Référence un salon pour le compte d'un gérant.
     *
     * C'est le modèle du métier : le salon ne s'inscrit pas seul, l'équipe
     * l'installe pour lui. Un seul appel crée le compte Keycloak, lui attribue
     * le rôle professionnel, pose le miroir local, crée le salon à son nom et
     * l'invite à choisir son mot de passe.
     *
     * Avant, il fallait une console Keycloak et cinq écrans par salon — ce qui
     * faisait de l'administration le goulot d'étranglement de la croissance.
     */
    @PostMapping("/salons")
    public ResponseEntity<ReferencementResponse> referencerSalon(
            @Valid @RequestBody ReferencementSalonRequest requete) {
        return ResponseEntity.status(HttpStatus.CREATED).body(referencement.referencer(requete));
    }

    /* ---------- Modération des avis ---------- */

    @GetMapping("/avis")
    public ResponseEntity<Page<AvisResponse>> avis(
            @RequestParam(defaultValue = "PUBLIE") StatutAvis statut,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(avis.parStatut(statut, pageable));
    }

    /** Modération a posteriori : un avis est publié d'emblée, masqué s'il dérape. */
    @PatchMapping("/avis/{id}/statut")
    public ResponseEntity<AvisResponse> modererAvis(@PathVariable Long id,
                                                    @RequestParam StatutAvis statut) {
        return ResponseEntity.ok(avis.changerStatut(id, statut));
    }

    /**
     * Relance immédiate des rappels de la veille.
     *
     * Utile après une interruption du service : la tâche planifiée reprend
     * d'elle-même à l'heure suivante, mais on peut vouloir rattraper tout de
     * suite. Sans risque de doublon, le journal des notifications tranche.
     */
    @PostMapping("/rappels")
    public ResponseEntity<java.util.Map<String, Integer>> relancerRappels() {
        return ResponseEntity.ok(java.util.Map.of("declenches", rappels.declencher()));
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
