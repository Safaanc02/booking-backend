package com.example.booking.service;

import com.example.booking.dto.CreateReservationRequest;
import com.example.booking.dto.ReservationResponse;
import com.example.booking.dto.UpdateReservationRequest;
import com.example.booking.mapper.ReservationMapper;
import com.example.booking.model.Creneau;
import com.example.booking.model.Reservation;
import com.example.booking.model.User;
import com.example.booking.repository.CreneauRepository;
import com.example.booking.repository.ReservationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final CreneauRepository creneauRepository;
    private final CurrentUserService currentUser;

    public ReservationService(ReservationRepository reservationRepository,
                              CreneauRepository creneauRepository,
                              CurrentUserService currentUser) {
        this.reservationRepository = reservationRepository;
        this.creneauRepository = creneauRepository;
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
        return reservationRepository.findByClientUsername(me.getUsername())
                .stream()
                .map(ReservationMapper::toResponse)
                .toList();
    }

    /* ---------- Écriture ---------- */

    public ReservationResponse create(CreateReservationRequest req) {
        // NOTE : ce contrôle ne protège pas d'une réservation concurrente. Deux
        // requêtes simultanées passent toutes les deux. La garantie réelle viendra
        // de la contrainte d'exclusion PostgreSQL prévue au jalon J1 (DI-05).
        if (reservationRepository.existsByCreneauId(req.creneauId())) {
            throw new IllegalStateException("Ce créneau est déjà réservé");
        }

        Creneau creneau = creneauRepository.findById(req.creneauId())
                .orElseThrow(() -> new NoSuchElementException("Créneau introuvable : " + req.creneauId()));

        Reservation r = Reservation.builder()
                .client(currentUser.getOrCreate())
                .creneau(creneau)
                .statut(req.statut() != null ? req.statut() : "PENDING")
                .build();

        return ReservationMapper.toResponse(reservationRepository.save(r));
    }

    public ReservationResponse update(Long id, UpdateReservationRequest req) {
        Reservation r = charger(id);
        verifierAcces(r);

        if (!r.getCreneau().getId().equals(req.creneauId())) {
            if (reservationRepository.existsByCreneauId(req.creneauId())) {
                throw new IllegalStateException("Le nouveau créneau est déjà réservé");
            }
            Creneau nouveau = creneauRepository.findById(req.creneauId())
                    .orElseThrow(() -> new NoSuchElementException("Créneau introuvable : " + req.creneauId()));
            r.setCreneau(nouveau);
        }
        r.setStatut(req.statut());

        return ReservationMapper.toResponse(reservationRepository.save(r));
    }

    public void delete(Long id) {
        Reservation r = charger(id);
        verifierAcces(r);
        reservationRepository.delete(r);
    }

    /* ---------- Helpers ---------- */

    private Reservation charger(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Réservation introuvable : " + id));
    }

    /** Admin, ou le client qui a posé la réservation. */
    private void verifierAcces(Reservation r) {
        if (currentUser.isAdmin()) {
            return;
        }
        String keycloakId = currentUser.currentKeycloakId().orElse(null);
        boolean proprietaire = r.getClient() != null
                && keycloakId != null
                && keycloakId.equals(r.getClient().getKeycloakId());

        if (!proprietaire) {
            throw new SecurityException("Accès refusé à cette réservation");
        }
    }
}
