package com.example.booking.service;

import com.example.booking.dto.AgendaResponse;
import com.example.booking.dto.ReservationProRequest;
import com.example.booking.model.Employe;
import com.example.booking.model.EmployePrestation;
import com.example.booking.model.Prestation;
import com.example.booking.model.Reservation;
import com.example.booking.model.Salon;
import com.example.booking.model.enums.OrigineReservation;
import com.example.booking.model.enums.StatutReservation;
import com.example.booking.notification.ReservationCreee;
import com.example.booking.repository.EmployePrestationRepository;
import com.example.booking.repository.EmployeRepository;
import com.example.booking.repository.PrestationRepository;
import com.example.booking.repository.ReservationRepository;
import com.example.booking.config.CustomPermissionEvaluator;
import com.example.booking.repository.SalonRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

/** Agenda du salon et saisie des rendez-vous pris hors ligne. */
@Service
@Transactional
public class AgendaService {

    private final ReservationRepository reservationRepository;
    private final SalonRepository salonRepository;
    private final PrestationRepository prestationRepository;
    private final EmployeRepository employeRepository;
    private final EmployePrestationRepository employePrestationRepository;
    private final DisponibiliteService disponibilites;
    private final CurrentUserService currentUser;
    private final CustomPermissionEvaluator droits;
    private final ApplicationEventPublisher evenements;

    public AgendaService(ReservationRepository reservationRepository,
                         SalonRepository salonRepository,
                         PrestationRepository prestationRepository,
                         EmployeRepository employeRepository,
                         EmployePrestationRepository employePrestationRepository,
                         DisponibiliteService disponibilites,
                         CurrentUserService currentUser,
                         CustomPermissionEvaluator droits,
                         ApplicationEventPublisher evenements) {
        this.reservationRepository = reservationRepository;
        this.salonRepository = salonRepository;
        this.prestationRepository = prestationRepository;
        this.employeRepository = employeRepository;
        this.employePrestationRepository = employePrestationRepository;
        this.disponibilites = disponibilites;
        this.currentUser = currentUser;
        this.droits = droits;
        this.evenements = evenements;
    }

    /** Rendez-vous du salon sur `jours` jours à partir de `date`. */
    @Transactional(readOnly = true)
    public List<AgendaResponse> agenda(Long salonId, LocalDate date, int jours) {
        var zone = disponibilites.zone();
        Instant debut = date.atStartOfDay(zone).toInstant();
        Instant fin = date.plusDays(Math.max(1, jours)).atStartOfDay(zone).toInstant();

        return reservationRepository.agenda(salonId, debut, fin).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Planning personnel du compte connecté.
     *
     * Un praticien n'administre rien, mais doit pouvoir consulter ses propres
     * rendez-vous. Il n'était jusqu'ici titulaire d'aucun droit : la seule
     * façon de voir son planning était d'emprunter le compte du propriétaire.
     *
     * Renvoie les rendez-vous de toutes ses fiches, un même compte pouvant
     * travailler dans plusieurs salons.
     */
    @Transactional(readOnly = true)
    public List<AgendaResponse> monPlanning(LocalDate date, int jours) {
        String keycloakId = currentUser.currentKeycloakId()
                .orElseThrow(() -> new SecurityException("Authentification requise"));

        List<Long> mesFiches = employeRepository.mesFiches(keycloakId).stream()
                .map(Employe::getId)
                .toList();
        if (mesFiches.isEmpty()) {
            // Compte sans fiche : aucun planning, ce n'est pas une erreur.
            return List.of();
        }

        var zone = disponibilites.zone();
        Instant debut = date.atStartOfDay(zone).toInstant();
        Instant fin = date.plusDays(Math.max(1, jours)).atStartOfDay(zone).toInstant();

        return reservationRepository.planningDesPraticiens(mesFiches, debut, fin).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Rendez-vous pris par téléphone ou au comptoir.
     *
     * Sans cette saisie, les créneaux réservés hors ligne restent proposés en
     * ligne et produisent de vraies doubles réservations.
     */
    public AgendaResponse creerHorsLigne(Long salonId, ReservationProRequest req) {
        Salon salon = salonRepository.findById(salonId)
                .orElseThrow(() -> new NoSuchElementException("Salon introuvable : " + salonId));

        Prestation prestation = prestationRepository.findById(req.prestationId())
                .orElseThrow(() -> new NoSuchElementException("Prestation introuvable"));
        if (!prestation.getSalon().getId().equals(salonId)) {
            throw new NoSuchElementException("Cette prestation n'appartient pas à ce salon");
        }

        Employe employe = employeRepository.findById(req.employeId())
                .orElseThrow(() -> new NoSuchElementException("Praticien introuvable"));
        if (!employe.getSalon().getId().equals(salonId)) {
            throw new NoSuchElementException("Ce praticien n'appartient pas à ce salon");
        }

        int minutes = employePrestationRepository
                .findByEmployeIdAndPrestationId(employe.getId(), prestation.getId())
                .map(EmployePrestation::dureeEffective)
                .orElseThrow(() -> new IllegalStateException("Ce praticien ne réalise pas cette prestation"));

        OrigineReservation origine;
        try {
            origine = req.origine() != null
                    ? OrigineReservation.valueOf(req.origine())
                    : OrigineReservation.TELEPHONE;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Origine inconnue : " + req.origine());
        }

        Reservation r = Reservation.builder()
                .salon(salon)
                .employe(employe)
                .prestation(prestation)
                .debut(req.debut())
                .fin(req.debut().plus(Duration.ofMinutes(minutes)))
                .statut(StatutReservation.CONFIRMEE)
                .prixFige(prestation.getPrix())
                .nomPrestationFige(prestation.getNom())
                .clientNomLibre(req.clientNom())
                .clientTelephoneLibre(vide(req.clientTelephone()))
                .noteClient(vide(req.note()))
                .origine(origine)
                .build();

        try {
            Reservation enregistree = reservationRepository.saveAndFlush(r);
            evenements.publishEvent(new ReservationCreee(enregistree.getId()));
            return toResponse(enregistree);
        } catch (DataIntegrityViolationException e) {
            if (estChevauchement(e)) {
                throw new IllegalStateException("Ce praticien a déjà un rendez-vous sur ce créneau");
            }
            throw e;
        }
    }

    /** Le salon marque le rendez-vous honoré, absent, ou l'annule de son côté. */
    public AgendaResponse changerStatut(Long reservationId, StatutReservation statut) {
        Reservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NoSuchElementException("Réservation introuvable : " + reservationId));

        verifierProprietaireSalon(r.getSalon());

        if (statut == StatutReservation.ANNULEE_CLIENT) {
            throw new IllegalArgumentException(
                    "Une annulation par le salon se marque ANNULEE_SALON, pas ANNULEE_CLIENT");
        }
        r.setStatut(statut);
        if (statut == StatutReservation.ANNULEE_SALON) {
            r.setAnnuleeLe(Instant.now());
        }
        return toResponse(reservationRepository.save(r));
    }

    /* ---------- Helpers ---------- */

    /**
     * Délègue à l'évaluateur : propriétaire, gestionnaire délégué ou
     * administrateur. Refaire le calcul ici serait une troisième copie de la
     * même règle.
     */
    private void verifierProprietaireSalon(Salon salon) {
        if (!droits.peutGererSalon(salon.getId())) {
            throw new SecurityException("Vous n'avez pas les droits de gestion sur ce salon");
        }
    }

    private boolean estChevauchement(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("pas_de_chevauchement")) return true;
        }
        return false;
    }

    private String vide(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private AgendaResponse toResponse(Reservation r) {
        return new AgendaResponse(
                r.getId(), r.getDebut(), r.getFin(),
                r.getStatut().name(),
                r.getOrigine() != null ? r.getOrigine().name() : null,
                r.nomClient(), r.telephoneClient(),
                r.getNomPrestationFige(),
                r.getEmploye() != null ? r.getEmploye().getId() : null,
                r.getEmploye() != null ? r.getEmploye().nomComplet() : null,
                r.getPrixFige(), r.getNoteClient());
    }
}
