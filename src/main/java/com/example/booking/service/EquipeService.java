package com.example.booking.service;

import com.example.booking.dto.AbsenceRequest;
import com.example.booking.dto.AbsenceResponse;
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
import com.example.booking.model.User;
import com.example.booking.model.enums.RoleEmploye;
import com.example.booking.repository.AbsenceRepository;
import com.example.booking.repository.EmployePrestationRepository;
import com.example.booking.repository.EmployeRepository;
import com.example.booking.repository.HoraireOuvertureRepository;
import com.example.booking.repository.PrestationRepository;
import com.example.booking.repository.SalonRepository;
import com.example.booking.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.Instant;
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
    private final UserRepository userRepository;
    /** Même fuseau que le moteur de disponibilité : « aujourd'hui » doit désigner le même jour. */
    private final ZoneId zone;

    public EquipeService(EmployeRepository employeRepository,
                         EmployePrestationRepository employePrestationRepository,
                         HoraireOuvertureRepository horaireRepository,
                         AbsenceRepository absenceRepository,
                         SalonRepository salonRepository,
                         PrestationRepository prestationRepository,
                         UserRepository userRepository,
                         @Value("${app.fuseau:Africa/Casablanca}") String fuseau) {
        this.employeRepository = employeRepository;
        this.employePrestationRepository = employePrestationRepository;
        this.horaireRepository = horaireRepository;
        this.absenceRepository = absenceRepository;
        this.salonRepository = salonRepository;
        this.prestationRepository = prestationRepository;
        this.userRepository = userRepository;
        this.zone = ZoneId.of(fuseau);
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
                .role(req.getRole() != null ? req.getRole() : RoleEmploye.PRATICIEN)
                .user(compteDepuisEmail(req.getEmail()))
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
        if (req.getRole() != null) e.setRole(req.getRole());
        // Un email vide détache le compte : c'est ainsi qu'on retire un accès.
        e.setUser(compteDepuisEmail(req.getEmail()));
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

    /**
     * Congés et fermetures d'un salon, du plus proche au plus lointain.
     *
     * Depuis le début du jour courant, et non depuis l'instant présent : une
     * fermeture qui s'achève ce matin doit rester à l'écran jusqu'au soir.
     * Elle explique les créneaux manquants de la journée, et c'est aussi celle
     * qu'on corrige le plus souvent.
     */
    @Transactional(readOnly = true)
    public List<AbsenceResponse> absencesDuSalon(Long salonId) {
        Instant debutDuJour = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        return absenceRepository.aVenirPourSalon(salonId, debutDuJour).stream()
                .map(a -> new AbsenceResponse(
                        a.getId(),
                        a.getEmploye() != null ? a.getEmploye().getId() : null,
                        a.getEmploye() != null
                                ? (a.getEmploye().getPrenom() + " " + a.getEmploye().getNom()).trim()
                                : null,
                        a.getDebut(), a.getFin(), a.getMotif()))
                .toList();
    }

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

    /**
     * Compte correspondant à cet email, ou null si l'email est vide.
     *
     * Le compte doit préexister : il est créé dans Keycloak, et l'application
     * n'en garde un miroir qu'après une première connexion. Refuser
     * explicitement vaut mieux qu'un rattachement silencieusement ignoré.
     */
    private User compteDepuisEmail(String email) {
        if (email == null || email.isBlank()) return null;
        return userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Aucun compte pour " + email.trim()
                        + ". La personne doit s'être connectée au moins une fois."));
    }

    private Employe chargerEmploye(Long id) {
        return employeRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Praticien introuvable : " + id));
    }

    private EmployeResponse toResponse(Employe e) {
        return new EmployeResponse(
                e.getId(), e.getPrenom(), e.getNom(), e.getTitre(), e.getPhotoUrl(), null,
                e.getRole() != null ? e.getRole().name() : null,
                e.getUser() != null ? e.getUser().getEmail() : null);
    }

    private HoraireResponse toResponse(HoraireOuverture h) {
        return new HoraireResponse(h.getId(), h.getJourSemaine().getValue(), h.getHeureDebut(), h.getHeureFin());
    }
}
