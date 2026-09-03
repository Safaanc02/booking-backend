package com.example.booking.controller;

import com.example.booking.dto.ApercuAnnulation;
import com.example.booking.dto.AvisResponse;
import com.example.booking.dto.CreneauDisponible;
import com.example.booking.dto.DemandeDemoConfirmation;
import com.example.booking.dto.DemandeDemoRequest;
import com.example.booking.dto.DisponibilitesResponse;
import com.example.booking.dto.EmployeResponse;
import com.example.booking.dto.SalonDetailResponse;
import com.example.booking.dto.SalonResponse;
import com.example.booking.model.enums.SalonCategorie;
import com.example.booking.service.DemandeDemoService;
import com.example.booking.service.DisponibiliteService;
import com.example.booking.service.AvisService;
import com.example.booking.service.ReservationService;
import com.example.booking.service.PublicCatalogService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
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
    private final ReservationService reservations;
    private final DemandeDemoService demandes;

    public PublicController(PublicCatalogService catalogue,
                            DisponibiliteService disponibilites,
                            AvisService avis,
                            ReservationService reservations,
                            DemandeDemoService demandes) {
        this.catalogue = catalogue;
        this.disponibilites = disponibilites;
        this.avis = avis;
        this.reservations = reservations;
        this.demandes = demandes;
    }

    /* ---------- Demande de démonstration ---------- */

    /**
     * Un professionnel demande à être rappelé.
     *
     * Rien n'est créé côté comptes : c'est une prise de contact. La réponse
     * est volontairement identique qu'on ait enregistré la demande ou écarté
     * un doublon récent — la route est publique, et distinguer les deux cas
     * donnerait le moyen de savoir qui s'est déjà manifesté.
     *
     * Le débit est limité par IP en amont, dans RateLimitFilter.
     */
    @PostMapping("/demandes-demo")
    public ResponseEntity<DemandeDemoConfirmation> demanderDemo(
            @Valid @RequestBody DemandeDemoRequest requete) {
        demandes.enregistrer(requete);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new DemandeDemoConfirmation(
                "Votre demande est enregistrée. Un conseiller vous rappelle sous 48 heures "
                        + "pour convenir d'une installation."));
    }

    /* ---------- Annulation depuis un email ---------- */

    /** Ce que le porteur du lien voit avant de confirmer. Ne modifie rien. */
    @GetMapping("/reservations/apercu")
    public ResponseEntity<ApercuAnnulation> apercuAnnulation(@RequestParam String token) {
        // Pas de cache : l'état du rendez-vous peut changer entre deux consultations.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(reservations.apercuParJeton(token));
    }

    /**
     * Annulation effective.
     *
     * ⚠️ En POST, jamais en GET, et ce n'est pas une question de pureté REST :
     * les clients de messagerie, antivirus et aperçus de liens préchargent les
     * URL en GET. Un lien d'annulation en GET verrait des rendez-vous annulés
     * sans que personne n'ait cliqué.
     */
    @PostMapping("/reservations/annuler")
    public ResponseEntity<ApercuAnnulation> annulerParLien(@RequestParam String token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(reservations.annulerParJeton(token));
    }

    /** Avis publiés d'un salon, du plus récent au plus ancien. */
    @GetMapping("/salons/{id}/avis")
    public ResponseEntity<Page<AvisResponse>> avis(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return cache60(avis.parSalon(id, PageRequest.of(page, Math.min(size, 50))));
    }

    /**
     * Recherche publique.
     *
     * `metier` filtre sur l'ensemble des métiers exercés, pas sur la seule
     * catégorie principale : un institut déclaré en coiffure qui fait aussi
     * les ongles remonte bien sous « Onglerie ».
     */
    @GetMapping("/salons")
    public ResponseEntity<Page<SalonResponse>> rechercher(
            @RequestParam(required = false) String ville,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) SalonCategorie metier,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return cache60(catalogue.rechercher(ville, q, metier, pageable));
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
