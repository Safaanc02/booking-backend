package com.example.booking.service;

import com.example.booking.model.Creneau;
import com.example.booking.repository.CreneauRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CreneauService {

    private final CreneauRepository creneauRepository;

    public CreneauService(CreneauRepository creneauRepository) {
        this.creneauRepository = creneauRepository;
    }

    public List<Creneau> getAllCreneaux() {
        return creneauRepository.findAll();
    }

    public Optional<Creneau> updateCreneau(Long id, Creneau creneauDetails) {
        return creneauRepository.findById(id)
                .map(c -> {
                    c.setDebut(creneauDetails.getDebut());
                    c.setFin(creneauDetails.getFin());
                    c.setDisponible(creneauDetails.isDisponible());
                    c.setPrestation(creneauDetails.getPrestation());

                    return creneauRepository.save(c);
                });
    }

    public Optional<Creneau> getCreneauById(Long id) {
        return creneauRepository.findById(id);
    }

    public Creneau saveCreneau(Creneau creneau) {
        return creneauRepository.save(creneau);
    }


    public void deleteCreneau(Long id) {
        creneauRepository.deleteById(id);
    }


}
