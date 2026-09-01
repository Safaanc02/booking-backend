package com.example.booking.service;

import com.example.booking.dto.PrestationResponse;
import com.example.booking.dto.SalonDetailResponse;
import com.example.booking.dto.SalonResponse;
import com.example.booking.model.Prestation;
import com.example.booking.model.Salon;
import com.example.booking.repository.SalonRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/** Lectures du catalogue exposées sans authentification. */
@Service
@Transactional(readOnly = true)
public class PublicCatalogService {

    private final SalonRepository salonRepository;

    public PublicCatalogService(SalonRepository salonRepository) {
        this.salonRepository = salonRepository;
    }

    public Page<SalonResponse> rechercher(String ville, String q, Pageable pageable) {
        return salonRepository.rechercher(vide(ville), vide(q), pageable).map(this::toResponse);
    }

    public SalonDetailResponse fiche(Long id) {
        Salon salon = salonRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Salon introuvable : " + id));

        List<PrestationResponse> prestations = salon.getPrestations() == null
                ? List.of()
                : salon.getPrestations().stream().map(this::toResponse).toList();

        return SalonDetailResponse.builder()
                .id(salon.getId())
                .nom(salon.getNom())
                .adresse(salon.getAdresse())
                .ville(salon.getVille())
                .telephone(salon.getTelephone())
                .email(salon.getEmail())
                .prestations(prestations)
                .build();
    }

    public List<String> villes() {
        return salonRepository.villesDistinctes();
    }

    /** Une chaîne vide en paramètre de requête vaut « pas de filtre ». */
    private String vide(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private PrestationResponse toResponse(Prestation p) {
        return PrestationResponse.builder()
                .id(p.getId())
                .nom(p.getNom())
                .description(p.getDescription())
                .prix(p.getPrix())
                .dureeMinutes(p.getDureeMinutes())
                .salonId(p.getSalon() != null ? p.getSalon().getId() : null)
                .build();
    }

    private SalonResponse toResponse(Salon s) {
        return SalonResponse.builder()
                .id(s.getId()).nom(s.getNom()).adresse(s.getAdresse())
                .ville(s.getVille()).telephone(s.getTelephone()).email(s.getEmail())
                .ownerId(s.getOwner() != null ? s.getOwner().getId() : null)
                .build();
    }
}
