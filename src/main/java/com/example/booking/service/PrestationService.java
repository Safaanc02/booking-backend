package com.example.booking.service;

import com.example.booking.dto.PrestationRequest;
import com.example.booking.dto.PrestationResponse;
import com.example.booking.model.Prestation;
import com.example.booking.model.Salon;
import com.example.booking.repository.PrestationRepository;
import com.example.booking.repository.SalonRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class PrestationService {

    private final PrestationRepository prestationRepository;
    private final SalonRepository salonRepository;


    public PrestationService(PrestationRepository prestationRepository, SalonRepository salonRepository) {
        this.prestationRepository = prestationRepository;
        this.salonRepository = salonRepository;
    }

    public List<Prestation> getAllPrestations() {
        return prestationRepository.findAll();
    }

    public Prestation getPrestationById(Long id) {
        return prestationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Prestation non trouvée avec id " + id));
    }

    public Prestation createPrestation(Prestation prestation) {
        return prestationRepository.save(prestation);
    }

    public Prestation updatePrestation(Long id, @Valid PrestationRequest prestation) {
        Prestation existing = getPrestationById(id);
        existing.setNom(prestation.getNom());
        existing.setDescription(prestation.getDescription());
        existing.setPrix(BigDecimal.valueOf(prestation.getPrix()));
        existing.setDuree(Integer.parseInt(prestation.getDuree()));
        return prestationRepository.save(existing);
    }

    public void deletePrestation(Long id) {
        prestationRepository.deleteById(id);
    }
    public PrestationResponse createPrestation(Long salonId, @Valid PrestationRequest request) {
        Salon salon = salonRepository.findById(salonId)
                .orElseThrow(() -> new NoSuchElementException("Salon introuvable"));

        Prestation prestation = new Prestation();
        prestation.setNom(request.getNom());
        prestation.setDescription(request.getDescription());
        prestation.setPrix(BigDecimal.valueOf(request.getPrix()));
        prestation.setDuree(Integer.parseInt(request.getDuree())); // si Integer dans ton DTO
        prestation.setSalon(salon);

        Prestation saved = prestationRepository.save(prestation);
        return mapToResponse(saved);
    }

    /* 🔹 Mapper interne */
    private PrestationResponse mapToResponse(Prestation prestation) {
        PrestationResponse response = new PrestationResponse();
        response.setId(prestation.getId());
        response.setNom(prestation.getNom());
        response.setDescription(prestation.getDescription());
        response.setPrix(prestation.getPrix());
        response.setDuree(prestation.getDuree());
        response.setSalonId(prestation.getSalon() != null ? prestation.getSalon().getId() : null);
        return response;
    }

}
