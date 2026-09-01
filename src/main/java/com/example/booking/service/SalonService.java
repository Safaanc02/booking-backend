package com.example.booking.service;

import com.example.booking.dto.SalonRequest;
import com.example.booking.dto.SalonResponse;
import com.example.booking.model.Salon;
import com.example.booking.model.User;
import com.example.booking.model.enums.SalonCategorie;
import com.example.booking.model.enums.SalonStatut;
import com.example.booking.repository.SalonRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    /** Les salons du professionnel connecté. */
    @Transactional(readOnly = true)
    public List<SalonResponse> mesSalons() {
        User me = currentUser.getOrCreate();
        return salonRepository.findByOwnerIdOrderByNomAsc(me.getId())
                .stream().map(this::toResponse).toList();
    }

    /* ---------- Écriture ---------- */

    /**
     * Un salon naît EN_ATTENTE : il n'apparaît pas dans la recherche publique
     * avant validation par la plateforme.
     */
    public SalonResponse createSalon(SalonRequest request) {
        User owner = currentUser.getOrCreate();

        Salon salon = Salon.builder()
                .nom(request.getNom())
                .description(request.getDescription())
                .adresse(request.getAdresse())
                .ville(request.getVille())
                .quartier(request.getQuartier())
                .telephone(request.getTelephone())
                .email(request.getEmail())
                .categorie(request.getCategorie() != null ? request.getCategorie() : SalonCategorie.COIFFURE)
                .statut(SalonStatut.EN_ATTENTE)
                .delaiAnnulationHeures(request.getDelaiAnnulationHeures() != null
                        ? request.getDelaiAnnulationHeures() : 24)
                .owner(owner)
                .build();

        return toResponse(salonRepository.save(salon));
    }

    public Optional<SalonResponse> updateSalon(Long id, SalonRequest request) {
        Salon salon = charger(id);
        verifierProprietaire(salon);

        salon.setNom(request.getNom());
        salon.setDescription(request.getDescription());
        salon.setAdresse(request.getAdresse());
        salon.setVille(request.getVille());
        salon.setQuartier(request.getQuartier());
        salon.setTelephone(request.getTelephone());
        salon.setEmail(request.getEmail());
        if (request.getCategorie() != null) salon.setCategorie(request.getCategorie());
        if (request.getDelaiAnnulationHeures() != null) {
            salon.setDelaiAnnulationHeures(request.getDelaiAnnulationHeures());
        }

        return Optional.of(toResponse(salonRepository.save(salon)));
    }

    /** Réservé à l'administration — la validation d'un salon n'appartient pas au professionnel. */
    public SalonResponse changerStatut(Long id, SalonStatut statut) {
        Salon salon = charger(id);
        salon.setStatut(statut);
        return toResponse(salonRepository.save(salon));
    }

    @Transactional(readOnly = true)
    public Page<SalonResponse> parStatut(SalonStatut statut, Pageable pageable) {
        return salonRepository.findByStatutOrderByCreeLeAsc(statut, pageable).map(this::toResponse);
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

    /** Second rempart derrière le @PreAuthorize : celui-ci peut être oublié sur une nouvelle route. */
    private void verifierProprietaire(Salon salon) {
        if (currentUser.isAdmin()) return;

        String keycloakId = currentUser.currentKeycloakId().orElse(null);
        boolean proprietaire = salon.getOwner() != null
                && keycloakId != null
                && keycloakId.equals(salon.getOwner().getKeycloakId());

        if (!proprietaire) {
            throw new SecurityException("Vous n'êtes pas propriétaire de ce salon");
        }
    }

    public SalonResponse toResponse(Salon s) {
        return SalonResponse.builder()
                .id(s.getId())
                .nom(s.getNom())
                .description(s.getDescription())
                .adresse(s.getAdresse())
                .ville(s.getVille())
                .quartier(s.getQuartier())
                .telephone(s.getTelephone())
                .email(s.getEmail())
                .categorie(s.getCategorie() != null ? s.getCategorie().name() : null)
                .noteMoyenne(s.getNoteMoyenne())
                .nombreAvis(s.getNombreAvis())
                .statut(s.getStatut() != null ? s.getStatut().name() : null)
                .delaiAnnulationHeures(s.getDelaiAnnulationHeures())
                .ownerId(s.getOwner() != null ? s.getOwner().getId() : null)
                .build();
    }
}
