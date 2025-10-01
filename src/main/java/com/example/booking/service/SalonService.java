package com.example.booking.service;

import com.example.booking.dto.SalonRequest;
import com.example.booking.dto.SalonResponse;
import com.example.booking.model.Salon;
import com.example.booking.model.User;
import com.example.booking.repository.SalonRepository;
import com.example.booking.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class SalonService {

    private final SalonRepository salonRepository;
    private final UserRepository userRepository;

    public SalonService(SalonRepository salonRepository, UserRepository userRepository) {
        this.salonRepository = salonRepository;
        this.userRepository = userRepository;
    }

    /* 🔹 Helpers */
    private Authentication getAuth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_admin") || a.equals("ROLE_ADMIN"));
    }
    public Optional<Salon> getSalonById(Long id) {
        return salonRepository.findById(id);
    }

    private User getOrCreateCurrentUser() {
        Authentication auth = getAuth();
        Jwt jwt = (Jwt) auth.getPrincipal();

        String keycloakId = jwt.getClaim("sub");
        String username = jwt.getClaim("preferred_username");
        String email = jwt.getClaim("email");
        String fullName = jwt.getClaim("name");

        return userRepository.findByKeycloakId(keycloakId)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .keycloakId(keycloakId)
                            .username(username)
                            .email(email)
                            .fullName(fullName)
                            .build();
                    return userRepository.save(newUser);
                });
    }
    public Page<SalonResponse> getAllSalonsDto(Pageable pageable) {
        return salonRepository.findAll(pageable)
                .map(this::toResponse); // conversion entité -> DTO
    }


    /* 🔹 Mapper */
    public SalonResponse toResponse(Salon salon) {
        return new SalonResponse(
                salon.getId(),
                salon.getNom(),
                salon.getAdresse(),
                salon.getTelephone(),
                salon.getEmail(),
                salon.getOwner() != null ? salon.getOwner().getId() : null
        );
    }

    /* 🔹 CRUD */

    public SalonResponse createSalon(@Valid SalonRequest request) {
        User owner = getOrCreateCurrentUser();

        Salon salon = Salon.builder()
                .nom(request.getNom())
                .adresse(request.getAdresse())
                .telephone(request.getTelephone())
                .email(request.getEmail())
                .owner(owner)
                .build();

        Salon saved = salonRepository.save(salon);
        return toResponse(saved);
    }

    public Page<SalonResponse> getAllSalons(Pageable pageable) {
        return salonRepository.findAll(pageable)
                .map(this::toResponse);
    }

    public Optional<SalonResponse> updateSalon(Long id, @Valid SalonRequest salonDetails) {
        Salon salon = salonRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Salon introuvable"));

        Authentication auth = getAuth();
        String currentSub = ((Jwt) auth.getPrincipal()).getClaim("sub");

        if (!isAdmin(auth) && !salon.getOwner().getKeycloakId().equals(currentSub)) {
            throw new SecurityException("Accès refusé : vous n'êtes pas propriétaire de ce salon");
        }

        salon.setNom(salonDetails.getNom());
        salon.setAdresse(salonDetails.getAdresse());
        salon.setTelephone(salonDetails.getTelephone());
        salon.setEmail(salonDetails.getEmail());

        Salon updated = salonRepository.save(salon);
        return Optional.of(toResponse(updated));
    }


    public void deleteSalon(Long id) {
        Salon salon = salonRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Salon introuvable"));

        Authentication auth = getAuth();
        String currentSub = ((Jwt) auth.getPrincipal()).getClaim("sub");

        if (!isAdmin(auth) && !salon.getOwner().getKeycloakId().equals(currentSub)) {
            throw new SecurityException("Accès refusé : vous n'êtes pas propriétaire de ce salon");
        }

        salonRepository.delete(salon);
    }
}
