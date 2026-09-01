package com.example.booking.service;

import com.example.booking.dto.AbsenceRequest;
import com.example.booking.dto.EmployeRequest;
import com.example.booking.dto.EmployeResponse;
import com.example.booking.dto.HoraireRequest;
import com.example.booking.dto.HoraireResponse;
import com.example.booking.model.Absence;
import com.example.booking.model.Employe;
import com.example.booking.model.EmployePrestation;
import com.example.booking.model.HoraireOuverture;
import com.example.booking.model.Prestation;
import com.example.booking.model.Salon;
import com.example.booking.repository.AbsenceRepository;
import com.example.booking.repository.EmployePrestationRepository;
import com.example.booking.repository.EmployeRepository;
import com.example.booking.repository.HoraireOuvertureRepository;
import com.example.booking.repository.PrestationRepository;
import com.example.booking.repository.SalonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.List;
import java.util.NoSuchElementException;

/** Équipe, horaires et absences — le paramétrage dont dépend le moteur de disponibilité. */
@Service
@Transactional
public class EquipeService {

    private final EmployeRepository employeRepository;
    private final EmployePrestationRepository employePrestationRepository;
    private final HoraireOuvertureRepository horaireRepository;
    private final AbsenceRepository absenceRepository;
    private final SalonRepository salonRepository;
    private final PrestationRepository prestationRepository;

    public EquipeService(EmployeRepository employeRepository,
                         EmployePrestationRepository employePrestationRepository,
                         HoraireOuvertureRepository horaireRepository,
                         AbsenceRepository absenceRepository,
                         SalonRepository salonRepository,
                         PrestationRepository prestationRepository) {
        this.employeRepository = employeRepository;
        this.employePrestationRepository = employePrestationRepository;
        this.horaireRepository = horaireRepository;
        this.absenceRepository = absenceRepository;
        this.salonRepository = salonRepository;
        this.prestationRepository = prestationRepository;
    }

    /* ---------- Employés ---------- */

    @Transactional(readOnly = true)
    public List<EmployeResponse> lister(Long salonId) {
        return employeRepository.findBySalonIdAndActifTrueOrderByOrdreAscPrenomAsc(salonId)
                .stream().map(this::toResponse).toList();
    }

    public EmployeResponse creer(Long salonId, EmployeRequest req) {
        Salon salon = salonRepository.findById(salonId)
                .orElseThrow(() -> new NoSuchElementException("Salon introuvable : " + salonId));

        Employe e = Employe.builder()
                .salon(salon)
                .prenom(req.getPrenom())
                .nom(req.getNom())
                .titre(req.getTitre())
                .photoUrl(req.getPhotoUrl())
                .ordre(req.getOrdre() != null ? req.getOrdre() : 0)
                .actif(true)
                .build();

        return toResponse(employeRepository.save(e));
    }

    public EmployeResponse modifier(Long employeId, EmployeRequest req) {
        Employe e = chargerEmploye(employeId);
        e.setPrenom(req.getPrenom());
        e.setNom(req.getNom());
        e.setTitre(req.getTitre());
        e.setPhotoUrl(req.getPhotoUrl());
        if (req.getOrdre() != null) e.setOrdre(req.getOrdre());
        return toResponse(employeRepository.save(e));
    }

    /**
     * Désactivation plutôt que suppression : les réservations passées doivent
     * conserver le praticien qui les a honorées.
     */
    public void desactiver(Long employeId) {
        Employe e = chargerEmploye(employeId);
        e.setActif(false);
        employeRepository.save(e);
    }

    /* ---------- Qui fait quoi ---------- */

    /** Remplace intégralement la liste des prestations réalisées par ce praticien. */
    public void affecterPrestations(Long employeId, List<Long> prestationIds) {
        Employe employe = chargerEmploye(employeId);

        employePrestationRepository.deleteAll(employePrestationRepository.findByEmployeId(employeId));
        employePrestationRepository.flush();

        for (Long pid : prestationIds) {
            Prestation p = prestationRepository.findById(pid)
                    .orElseThrow(() -> new NoSuchElementException("Prestation introuvable : " + pid));
            if (!p.getSalon().getId().equals(employe.getSalon().getId())) {
                throw new IllegalStateException("La prestation " + pid + " n'appartient pas au salon du praticien");
            }
            employePrestationRepository.save(EmployePrestation.builder()
                    .id(new EmployePrestation.Id(employeId, pid))
                    .employe(employe)
                    .prestation(p)
                    .build());
        }
    }

    /* ---------- Horaires ---------- */

    @Transactional(readOnly = true)
    public List<HoraireResponse> horairesSalon(Long salonId) {
        return horaireRepository.findBySalonIdOrderByJourSemaineAscHeureDebutAsc(salonId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<HoraireResponse> horairesEmploye(Long employeId) {
        return horaireRepository.findByEmployeIdOrderByJourSemaineAscHeureDebutAsc(employeId)
                .stream().map(this::toResponse).toList();
    }

    /** Remplace la semaine complète du salon. Plus simple et plus sûr qu'un diff partiel. */
    public List<HoraireResponse> remplacerHorairesSalon(Long salonId, List<HoraireRequest> semaine) {
        Salon salon = salonRepository.findById(salonId)
                .orElseThrow(() -> new NoSuchElementException("Salon introuvable : " + salonId));

        horaireRepository.deleteBySalonId(salonId);
        horaireRepository.flush();

        return semaine.stream()
                .map(h -> horaireRepository.save(HoraireOuverture.builder()
                        .salon(salon)
                        .jourSemaine(DayOfWeek.of(h.getJourSemaine()))
                        .heureDebut(h.getHeureDebut())
                        .heureFin(h.getHeureFin())
                        .build()))
                .map(this::toResponse)
                .toList();
    }

    public List<HoraireResponse> remplacerHorairesEmploye(Long employeId, List<HoraireRequest> semaine) {
        Employe employe = chargerEmploye(employeId);

        horaireRepository.deleteByEmployeId(employeId);
        horaireRepository.flush();

        return semaine.stream()
                .map(h -> horaireRepository.save(HoraireOuverture.builder()
                        .employe(employe)
                        .jourSemaine(DayOfWeek.of(h.getJourSemaine()))
                        .heureDebut(h.getHeureDebut())
                        .heureFin(h.getHeureFin())
                        .build()))
                .map(this::toResponse)
                .toList();
    }

    /* ---------- Absences ---------- */

    public Long creerAbsence(AbsenceRequest req) {
        boolean cibleUnique = (req.getEmployeId() == null) ^ (req.getSalonId() == null);
        if (!cibleUnique) {
            throw new IllegalArgumentException("Renseignez soit employeId, soit salonId — pas les deux");
        }
        if (!req.getFin().isAfter(req.getDebut())) {
            throw new IllegalArgumentException("La fin doit être postérieure au début");
        }

        Absence a = Absence.builder()
                .employe(req.getEmployeId() != null ? chargerEmploye(req.getEmployeId()) : null)
                .salon(req.getSalonId() != null
                        ? salonRepository.findById(req.getSalonId())
                            .orElseThrow(() -> new NoSuchElementException("Salon introuvable")) : null)
                .debut(req.getDebut())
                .fin(req.getFin())
                .motif(req.getMotif())
                .build();

        return absenceRepository.save(a).getId();
    }

    public void supprimerAbsence(Long id) {
        absenceRepository.deleteById(id);
    }

    /* ---------- Helpers ---------- */

    private Employe chargerEmploye(Long id) {
        return employeRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Praticien introuvable : " + id));
    }

    private EmployeResponse toResponse(Employe e) {
        return new EmployeResponse(e.getId(), e.getPrenom(), e.getNom(), e.getTitre(), e.getPhotoUrl(), null);
    }

    private HoraireResponse toResponse(HoraireOuverture h) {
        return new HoraireResponse(h.getId(), h.getJourSemaine().getValue(), h.getHeureDebut(), h.getHeureFin());
    }
}
