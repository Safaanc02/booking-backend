package com.example.booking.service;

import com.example.booking.dto.PrestationRequest;
import com.example.booking.dto.PrestationResponse;
import com.example.booking.model.Prestation;
import com.example.booking.model.Salon;
import com.example.booking.repository.PrestationRepository;
import com.example.booking.repository.SalonRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@Transactional
public class PrestationService {

    private final PrestationRepository prestationRepository;
    private final SalonRepository salonRepository;

    public PrestationService(PrestationRepository prestationRepository, SalonRepository salonRepository) {
        this.prestationRepository = prestationRepository;
        this.salonRepository = salonRepository;
    }

    /* ---------- Lecture ---------- */

    public List<PrestationResponse> getAllPrestations() {
        return prestationRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<PrestationResponse> getBySalon(Long salonId) {
        return prestationRepository.findBySalonId(salonId).stream()
                .map(this::toResponse)
                .toList();
    }

    public PrestationResponse getPrestationById(Long id) {
        return toResponse(charger(id));
    }

    /* ---------- Écriture ---------- */

    public PrestationResponse createPrestation(Long salonId, PrestationRequest request) {
        Salon salon = salonRepository.findById(salonId)
                .orElseThrow(() -> new NoSuchElementException("Salon introuvable : " + salonId));

        Prestation prestation = Prestation.builder()
                .nom(request.getNom())
                .description(request.getDescription())
                .categorie(request.getCategorie())
                .prix(request.getPrix())
                .dureeMinutes(request.getDureeMinutes())
                .salon(salon)
                .build();

        return toResponse(prestationRepository.save(prestation));
    }

    public PrestationResponse updatePrestation(Long id, PrestationRequest request) {
        Prestation existing = charger(id);

        existing.setNom(request.getNom());
        existing.setDescription(request.getDescription());
        existing.setCategorie(request.getCategorie());
        existing.setPrix(request.getPrix());
        existing.setDureeMinutes(request.getDureeMinutes());

        return toResponse(prestationRepository.save(existing));
    }

    public void deletePrestation(Long id) {
        prestationRepository.delete(charger(id));
    }

    /* ---------- Helpers ---------- */

    private Prestation charger(Long id) {
        return prestationRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Prestation introuvable : " + id));
    }

    private PrestationResponse toResponse(Prestation p) {
        return PrestationResponse.builder()
                .id(p.getId())
                .nom(p.getNom())
                .description(p.getDescription())
                .categorie(p.getCategorie())
                .prix(p.getPrix())
                .dureeMinutes(p.getDureeMinutes())
                .salonId(p.getSalon() != null ? p.getSalon().getId() : null)
                .build();
    }
}
