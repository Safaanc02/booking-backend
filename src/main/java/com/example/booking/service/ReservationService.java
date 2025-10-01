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
import com.example.booking.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final CreneauRepository creneauRepository;
    private final UserRepository userRepository;

    public ReservationService(ReservationRepository reservationRepository,
                              CreneauRepository creneauRepository,
                              UserRepository userRepository) {
        this.reservationRepository = reservationRepository;
        this.creneauRepository = creneauRepository;
        this.userRepository = userRepository;
    }

    /* ---------- Helpers sécurité ---------- */

    private Authentication getAuth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_admin") || a.equals("ROLE_ADMIN"));
    }

    /** 🔹 Récupère ou crée un User en BDD à partir du token Keycloak */
    private User getOrCreateCurrentUser() {
        Authentication auth = getAuth();
        Jwt jwt = (Jwt) auth.getPrincipal();

        String keycloakId = jwt.getClaim("sub");
        String username = jwt.getClaim("preferred_username");
        String email = jwt.getClaim("email");

        return userRepository.findByKeycloakId(keycloakId)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .keycloakId(keycloakId)
                            .username(username)
                            .email(email)
                            .fullName(jwt.getClaim("name"))
                            .build();
                    return userRepository.save(newUser);
                });
    }

    /* ---------- API basée DTO ---------- */

    public ReservationResponse create(CreateReservationRequest req) {
        if (reservationRepository.existsByCreneauId(req.creneauId())) {
            throw new IllegalStateException("Ce créneau est déjà réservé");
        }

        Creneau creneau = creneauRepository.findById(req.creneauId())
                .orElseThrow(() -> new NoSuchElementException("Créneau introuvable"));

        User client = getOrCreateCurrentUser();

        Reservation r = Reservation.builder()
                .client(client)
                .creneau(creneau)
                .statut(req.statut() != null ? req.statut() : "PENDING")
                .build();

        return ReservationMapper.toResponse(reservationRepository.save(r));
    }

    public ReservationResponse getById(Long id) {
        Reservation r = reservationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Réservation introuvable"));

        Authentication auth = getAuth();
        if (!isAdmin(auth) && !r.getClient().getKeycloakId().equals(((Jwt) auth.getPrincipal()).getClaim("sub"))) {
            throw new SecurityException("Accès refusé");
        }
        return ReservationMapper.toResponse(r);
    }

    public Page<ReservationResponse> getAll(Pageable pageable) {
        return reservationRepository.findAll(pageable).map(ReservationMapper::toResponse);
    }

    public List<ReservationResponse> getMine() {
        User currentUser = getOrCreateCurrentUser();
        return reservationRepository.findByClientUsername(currentUser.getUsername())
                .stream().map(ReservationMapper::toResponse).toList();
    }

    public ReservationResponse update(Long id, UpdateReservationRequest req) {
        Reservation r = reservationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Réservation introuvable"));

        Authentication auth = getAuth();
        String currentSub = ((Jwt) auth.getPrincipal()).getClaim("sub");

        if (!isAdmin(auth) && !r.getClient().getKeycloakId().equals(currentSub)) {
            throw new SecurityException("Accès refusé");
        }

        if (!r.getCreneau().getId().equals(req.creneauId())) {
            if (reservationRepository.existsByCreneauId(req.creneauId())) {
                throw new IllegalStateException("Nouveau créneau déjà réservé");
            }
            Creneau nouveau = creneauRepository.findById(req.creneauId())
                    .orElseThrow(() -> new NoSuchElementException("Nouveau créneau introuvable"));
            r.setCreneau(nouveau);
        }
        r.setStatut(req.statut());

        return ReservationMapper.toResponse(reservationRepository.save(r));
    }

    public void delete(Long id) {
        Reservation r = reservationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Réservation introuvable"));

        Authentication auth = getAuth();
        String currentSub = ((Jwt) auth.getPrincipal()).getClaim("sub");

        if (!isAdmin(auth) && !r.getClient().getKeycloakId().equals(currentSub)) {
            throw new SecurityException("Accès refusé");
        }
        reservationRepository.delete(r);
    }

    /* ---------- API legacy (encore utilisée quelque part ?) ---------- */

    public Reservation saveReservation(Reservation reservation) {
        return reservationRepository.save(reservation);
    }

    public Optional<Reservation> getReservationById(Long id) {
        return reservationRepository.findById(id);
    }

    public List<Reservation> getMyReservations() {
        return reservationRepository.findByClientUsername(getOrCreateCurrentUser().getUsername());
    }

    public List<Reservation> getAllReservations() {
        return reservationRepository.findAll();
    }

    public void deleteReservation(Long id) {
        reservationRepository.deleteById(id);
    }

    public Optional<Reservation> updateReservation(Long id, Reservation reservationDetails) {
        return reservationRepository.findById(id)
                .map(reservation -> {
                    reservation.setClient(reservationDetails.getClient());
                    reservation.setCreneau(reservationDetails.getCreneau());
                    reservation.setStatut(reservationDetails.getStatut());
                    return reservationRepository.save(reservation);
                });
    }
}
