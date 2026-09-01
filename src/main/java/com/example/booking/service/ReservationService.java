package com.example.booking.service;

import com.example.booking.dto.CreateReservationRequest;
import com.example.booking.dto.CreneauDisponible;
import com.example.booking.dto.ReservationResponse;
import com.example.booking.dto.UpdateReservationRequest;
import com.example.booking.mapper.ReservationMapper;
import com.example.booking.model.Employe;
import com.example.booking.model.EmployePrestation;
import com.example.booking.model.Prestation;
import com.example.booking.model.Reservation;
import com.example.booking.model.Salon;
import com.example.booking.model.User;
import com.example.booking.model.enums.SalonStatut;
import com.example.booking.model.enums.StatutReservation;
import com.example.booking.repository.EmployePrestationRepository;
import com.example.booking.repository.EmployeRepository;
import com.example.booking.repository.PrestationRepository;
import com.example.booking.repository.ReservationRepository;
import com.example.booking.repository.SalonRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final SalonRepository salonRepository;
    private final PrestationRepository prestationRepository;
    private final EmployeRepository employeRepository;
    private final EmployePrestationRepository employePrestationRepository;
    private final DisponibiliteService disponibilites;
    private final CurrentUserService currentUser;

    public ReservationService(ReservationRepository reservationRepository,
                              SalonRepository salonRepository,
                              PrestationRepository prestationRepository,
                              EmployeRepository employeRepository,
                              EmployePrestationRepository employePrestationRepository,
                              DisponibiliteService disponibilites,
                              CurrentUserService currentUser) {
        this.reservationRepository = reservationRepository;
        this.salonRepository = salonRepository;
        this.prestationRepository = prestationRepository;
        this.employeRepository = employeRepository;
        this.employePrestationRepository = employePrestationRepository;
        this.disponibilites = disponibilites;
        this.currentUser = currentUser;
    }

    /* ---------- Lecture ---------- */

    @Transactional(readOnly = true)
    public Page<ReservationResponse> getAll(Pageable pageable) {
        return reservationRepository.findAll(pageable).map(ReservationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ReservationResponse getById(Long id) {
        Reservation r = charger(id);
        verifierAcces(r);
        return ReservationMapper.toResponse(r);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getMine() {
        User me = currentUser.getOrCreate();
        return reservationRepository.findByClientIdOrderByDebutDesc(me.getId())
                .stream().map(ReservationMapper::toResponse).toList();
    }

    /* ---------- Écriture ---------- */

    public ReservationResponse create(CreateReservationRequest req) {
        Salon salon = salonRepository.findById(req.salonId())
                .orElseThrow(() -> new NoSuchElementException("Salon introuvable : " + req.salonId()));
        if (salon.getStatut() != SalonStatut.ACTIF) {
            throw new IllegalStateException("Ce salon n'accepte pas encore de réservations");
        }

        Prestation prestation = prestationRepository.findById(req.prestationId())
                .orElseThrow(() -> new NoSuchElementException("Prestation introuvable : " + req.prestationId()));
        if (prestation.getSalon() == null || !prestation.getSalon().getId().equals(salon.getId())) {
            throw new NoSuchElementException("Cette prestation n'appartient pas à ce salon");
        }

        Employe employe = resoudreEmploye(req, salon.getId());
        Duration duree = dureeEffective(employe.getId(), prestation);
        Instant fin = req.debut().plus(duree);

        User client = currentUser.getOrCreate();

        Reservation r = Reservation.builder()
                .client(client)
                .salon(salon)
                .employe(employe)
                .prestation(prestation)
                .debut(req.debut())
                .fin(fin)
                .statut(StatutReservation.CONFIRMEE)
                .prixFige(prestation.getPrix())
                .nomPrestationFige(prestation.getNom())
                .noteClient(req.noteClient())
                .build();

        return ReservationMapper.toResponse(enregistrer(r));
    }

    public ReservationResponse update(Long id, UpdateReservationRequest req) {
        Reservation r = charger(id);
        verifierAcces(r);

        Employe employe = req.employeId() != null
                ? employeRepository.findById(req.employeId())
                    .orElseThrow(() -> new NoSuchElementException("Praticien introuvable : " + req.employeId()))
                : r.getEmploye();

        Duration duree = dureeEffective(employe.getId(), r.getPrestation());
        r.setEmploye(employe);
        r.setDebut(req.debut());
        r.setFin(req.debut().plus(duree));

        return ReservationMapper.toResponse(enregistrer(r));
    }

    /** Annulation par le client, dans le respect du délai fixé par le salon. */
    public ReservationResponse annuler(Long id) {
        Reservation r = charger(id);
        verifierAcces(r);

        if (!r.getStatut().bloqueLeCreneau()) {
            throw new IllegalStateException("Cette réservation n'est plus active");
        }

        Instant limite = r.getDebut().minus(Duration.ofHours(r.getSalon().getDelaiAnnulationHeures()));
        boolean horsDelai = Instant.now().isAfter(limite);
        if (horsDelai && !currentUser.isAdmin()) {
            throw new IllegalStateException(
                    "L'annulation n'est plus possible : le délai est de "
                    + r.getSalon().getDelaiAnnulationHeures() + " h avant le rendez-vous");
        }

        r.setStatut(StatutReservation.ANNULEE_CLIENT);
        r.setAnnuleeLe(Instant.now());
        return ReservationMapper.toResponse(reservationRepository.save(r));
    }

    public void delete(Long id) {
        Reservation r = charger(id);
        verifierAcces(r);
        reservationRepository.delete(r);
    }

    /* ---------- Interne ---------- */

    /**
     * Enregistre en traduisant la violation de la contrainte d'exclusion.
     *
     * C'est la contrainte PostgreSQL, et non un contrôle applicatif, qui arbitre
     * la course entre deux clients validant le même créneau à la même seconde.
     */
    private Reservation enregistrer(Reservation r) {
        try {
            return reservationRepository.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            if (estChevauchement(e)) {
                throw new IllegalStateException("Ce créneau vient d'être réservé");
            }
            throw e;
        }
    }

    private boolean estChevauchement(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && msg.contains("pas_de_chevauchement")) {
                return true;
            }
        }
        return false;
    }

    /** Praticien demandé, ou premier libre à cette heure en mode « sans préférence ». */
    private Employe resoudreEmploye(CreateReservationRequest req, Long salonId) {
        if (req.employeId() != null) {
            Employe e = employeRepository.findById(req.employeId())
                    .orElseThrow(() -> new NoSuchElementException("Praticien introuvable : " + req.employeId()));
            if (!e.getSalon().getId().equals(salonId)) {
                throw new NoSuchElementException("Ce praticien n'appartient pas à ce salon");
            }
            return e;
        }

        LocalDate date = LocalDate.ofInstant(req.debut(), disponibilites.zone());
        LocalTime heure = LocalTime.ofInstant(req.debut(), disponibilites.zone());

        List<CreneauDisponible> creneaux = disponibilites.creneaux(salonId, req.prestationId(), date, null);
        Long choisi = creneaux.stream()
                .filter(c -> c.heure().equals(heure))
                .flatMap(c -> c.employesDisponibles().stream())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucun praticien disponible à cette heure"));

        return employeRepository.findById(choisi)
                .orElseThrow(() -> new NoSuchElementException("Praticien introuvable : " + choisi));
    }

    private Duration dureeEffective(Long employeId, Prestation prestation) {
        if (prestation == null) {
            throw new IllegalStateException("Réservation sans prestation : durée indéterminable");
        }
        int minutes = employePrestationRepository
                .findByEmployeIdAndPrestationId(employeId, prestation.getId())
                .map(EmployePrestation::dureeEffective)
                .orElseThrow(() -> new IllegalStateException("Ce praticien ne réalise pas cette prestation"));
        return Duration.ofMinutes(minutes);
    }

    private Reservation charger(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Réservation introuvable : " + id));
    }

    /** Admin, ou le client qui a posé la réservation. */
    private void verifierAcces(Reservation r) {
        if (currentUser.isAdmin()) return;

        String keycloakId = currentUser.currentKeycloakId().orElse(null);
        boolean proprietaire = r.getClient() != null
                && keycloakId != null
                && keycloakId.equals(r.getClient().getKeycloakId());

        if (!proprietaire) {
            throw new SecurityException("Accès refusé à cette réservation");
        }
    }
}
