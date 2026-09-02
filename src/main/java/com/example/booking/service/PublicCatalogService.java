package com.example.booking.service;

import com.example.booking.dto.EmployeResponse;
import com.example.booking.dto.PrestationResponse;
import com.example.booking.dto.SalonDetailResponse;
import com.example.booking.dto.SalonResponse;
import com.example.booking.model.Employe;
import com.example.booking.model.EmployePrestation;
import com.example.booking.model.Prestation;
import com.example.booking.model.Salon;
import com.example.booking.model.enums.SalonStatut;
import com.example.booking.repository.EmployePrestationRepository;
import com.example.booking.repository.EmployeRepository;
import com.example.booking.repository.SalonRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/** Lectures du catalogue exposées sans authentification. Seuls les salons ACTIF sont visibles. */
@Service
@Transactional(readOnly = true)
public class PublicCatalogService {

    private final SalonRepository salonRepository;
    private final EmployeRepository employeRepository;
    private final EmployePrestationRepository employePrestationRepository;

    public PublicCatalogService(SalonRepository salonRepository,
                                EmployeRepository employeRepository,
                                EmployePrestationRepository employePrestationRepository) {
        this.salonRepository = salonRepository;
        this.employeRepository = employeRepository;
        this.employePrestationRepository = employePrestationRepository;
    }

    public Page<SalonResponse> rechercher(String ville, String q, Pageable pageable) {
        return salonRepository.rechercher(SalonStatut.ACTIF, vide(ville), vide(q), pageable)
                .map(this::toResponse);
    }

    public SalonDetailResponse fiche(Long id) {
        Salon salon = salonRepository.findById(id)
                .filter(s -> s.getStatut() == SalonStatut.ACTIF)
                .orElseThrow(() -> new NoSuchElementException("Salon introuvable : " + id));

        List<PrestationResponse> prestations = salon.getPrestations().stream()
                .filter(Prestation::isActif)
                .sorted(Comparator.comparingInt(Prestation::getOrdre).thenComparing(Prestation::getNom))
                .map(this::toResponse)
                .toList();

        List<EmployeResponse> employes = employeRepository
                .findBySalonIdAndActifTrueOrderByOrdreAscPrenomAsc(id).stream()
                .map(e -> toResponse(e, null))
                .toList();

        return SalonDetailResponse.builder()
                .id(salon.getId())
                .nom(salon.getNom())
                .description(salon.getDescription())
                .adresse(salon.getAdresse())
                .ville(salon.getVille())
                .quartier(salon.getQuartier())
                .telephone(salon.getTelephone())
                .email(salon.getEmail())
                .categorie(salon.getCategorie() != null ? salon.getCategorie().name() : null)
                .noteMoyenne(salon.getNoteMoyenne())
                .nombreAvis(salon.getNombreAvis())
                .delaiAnnulationHeures(salon.getDelaiAnnulationHeures())
                .prestations(prestations)
                .employes(employes)
                .build();
    }

    /** Praticiens sachant réaliser une prestation, avec leur durée effective. */
    public List<EmployeResponse> employesPour(Long salonId, Long prestationId) {
        return employeRepository.findCapables(salonId, prestationId).stream()
                .map(e -> {
                    Integer duree = employePrestationRepository
                            .findByEmployeIdAndPrestationId(e.getId(), prestationId)
                            .map(EmployePrestation::dureeEffective)
                            .orElse(null);
                    return toResponse(e, duree);
                })
                .toList();
    }

    public List<String> villes() {
        return salonRepository.villesDistinctes(SalonStatut.ACTIF);
    }

    /* ---------- Mappers ---------- */

    private String vide(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private SalonResponse toResponse(Salon s) {
        return SalonResponse.builder()
                .id(s.getId()).nom(s.getNom()).description(s.getDescription())
                .adresse(s.getAdresse()).ville(s.getVille()).quartier(s.getQuartier())
                .telephone(s.getTelephone()).email(s.getEmail())
                .categorie(s.getCategorie() != null ? s.getCategorie().name() : null)
                .noteMoyenne(s.getNoteMoyenne())
                .nombreAvis(s.getNombreAvis())
                .statut(s.getStatut() != null ? s.getStatut().name() : null)
                .delaiAnnulationHeures(s.getDelaiAnnulationHeures())
                .ownerId(s.getOwner() != null ? s.getOwner().getId() : null)
                .build();
    }

    private PrestationResponse toResponse(Prestation p) {
        return PrestationResponse.builder()
                .id(p.getId()).nom(p.getNom()).description(p.getDescription())
                .categorie(p.getCategorie())
                .prix(p.getPrix()).dureeMinutes(p.getDureeMinutes())
                .salonId(p.getSalon() != null ? p.getSalon().getId() : null)
                .build();
    }

    private EmployeResponse toResponse(Employe e, Integer dureeMinutes) {
        // Le rôle et le compte rattaché ne sortent pas côté public : ils
        // n'intéressent que le salon.
        return new EmployeResponse(e.getId(), e.getPrenom(), e.getNom(),
                e.getTitre(), e.getPhotoUrl(), dureeMinutes, null, null);
    }
}
