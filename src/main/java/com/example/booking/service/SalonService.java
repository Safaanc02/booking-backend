package com.example.booking.service;

import com.example.booking.dto.SalonRequest;
import com.example.booking.dto.SalonResponse;
import com.example.booking.model.Salon;
import com.example.booking.model.User;
import com.example.booking.repository.SalonRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Transactional
public class SalonService {

    private final SalonRepository salonRepository;
    private final CurrentUserService currentUser;

    public SalonService(SalonRepository salonRepository, CurrentUserService currentUser) {
        this.salonRepository = salonRepository;
        this.currentUser = currentUser;
    }

    /* ---------- Lecture ---------- */

    @Transactional(readOnly = true)
    public Optional<Salon> getSalonById(Long id) {
        return salonRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Page<SalonResponse> getAllSalons(Pageable pageable) {
        return salonRepository.findAll(pageable).map(this::toResponse);
    }

    /* ---------- Écriture ---------- */

    public SalonResponse createSalon(SalonRequest request) {
        User owner = currentUser.getOrCreate();

        Salon salon = Salon.builder()
                .nom(request.getNom())
                .adresse(request.getAdresse())
                .ville(request.getVille())
                .telephone(request.getTelephone())
                .email(request.getEmail())
                .owner(owner)
                .build();

        return toResponse(salonRepository.save(salon));
    }

    public Optional<SalonResponse> updateSalon(Long id, SalonRequest request) {
        Salon salon = charger(id);
        verifierProprietaire(salon);

        salon.setNom(request.getNom());
        salon.setAdresse(request.getAdresse());
        salon.setVille(request.getVille());
        salon.setTelephone(request.getTelephone());
        salon.setEmail(request.getEmail());

        return Optional.of(toResponse(salonRepository.save(salon)));
    }

    public void deleteSalon(Long id) {
        Salon salon = charger(id);
        verifierProprietaire(salon);
        salonRepository.delete(salon);
    }

    /* ---------- Helpers ---------- */

    private Salon charger(Long id) {
        return salonRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Salon introuvable : " + id));
    }

    /**
     * Second rempart derrière le @PreAuthorize du contrôleur : celui-ci peut être
     * oublié sur une nouvelle route, le service est le dernier point de passage.
     */
    private void verifierProprietaire(Salon salon) {
        if (currentUser.isAdmin()) {
            return;
        }
        String keycloakId = currentUser.currentKeycloakId().orElse(null);
        boolean proprietaire = salon.getOwner() != null
                && keycloakId != null
                && keycloakId.equals(salon.getOwner().getKeycloakId());

        if (!proprietaire) {
            throw new SecurityException("Vous n'êtes pas propriétaire de ce salon");
        }
    }

    public SalonResponse toResponse(Salon salon) {
        return SalonResponse.builder()
                .id(salon.getId())
                .nom(salon.getNom())
                .adresse(salon.getAdresse())
                .ville(salon.getVille())
                .telephone(salon.getTelephone())
                .email(salon.getEmail())
                .ownerId(salon.getOwner() != null ? salon.getOwner().getId() : null)
                .build();
    }
}
