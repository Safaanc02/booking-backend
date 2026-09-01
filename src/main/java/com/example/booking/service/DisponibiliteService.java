package com.example.booking.service;

import com.example.booking.dto.CreneauDisponible;
import com.example.booking.model.Absence;
import com.example.booking.model.Employe;
import com.example.booking.model.EmployePrestation;
import com.example.booking.model.HoraireOuverture;
import com.example.booking.model.Prestation;
import com.example.booking.model.Reservation;
import com.example.booking.model.enums.StatutReservation;
import com.example.booking.repository.AbsenceRepository;
import com.example.booking.repository.EmployePrestationRepository;
import com.example.booking.repository.EmployeRepository;
import com.example.booking.repository.HoraireOuvertureRepository;
import com.example.booking.repository.PrestationRepository;
import com.example.booking.repository.ReservationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Moteur de disponibilité — le cœur du produit.
 *
 * Aucun créneau n'est stocké. On lit les règles (horaires, absences) et les
 * réservations bloquantes, puis on calcule les heures de début réservables.
 *
 * Fuseau : les horaires d'ouverture sont exprimés en heure locale, les
 * réservations en Instant. La conversion passe systématiquement par la zone
 * configurée. Au Maroc ce n'est pas cosmétique : le pays vit en UTC+1 mais
 * repasse à UTC+0 pendant le Ramadan, soit deux bascules par an. Construire
 * les instants depuis l'heure locale via ZonedDateTime fait porter cette
 * subtilité à la bibliothèque plutôt qu'à nous.
 */
@Service
@Transactional(readOnly = true)
public class DisponibiliteService {

    /** Granularité d'affichage des créneaux. Standard du secteur. */
    private static final Duration PAS = Duration.ofMinutes(15);

    private final PrestationRepository prestationRepository;
    private final EmployeRepository employeRepository;
    private final EmployePrestationRepository employePrestationRepository;
    private final HoraireOuvertureRepository horaireRepository;
    private final AbsenceRepository absenceRepository;
    private final ReservationRepository reservationRepository;
    private final ZoneId zone;
    private final Duration delaiPrevenance;
    private final int horizonJours;

    public DisponibiliteService(PrestationRepository prestationRepository,
                                EmployeRepository employeRepository,
                                EmployePrestationRepository employePrestationRepository,
                                HoraireOuvertureRepository horaireRepository,
                                AbsenceRepository absenceRepository,
                                ReservationRepository reservationRepository,
                                @Value("${app.fuseau:Africa/Casablanca}") String fuseau,
                                @Value("${app.reservation.delai-prevenance-minutes:120}") long delaiMinutes,
                                @Value("${app.reservation.horizon-jours:60}") int horizonJours) {
        this.prestationRepository = prestationRepository;
        this.employeRepository = employeRepository;
        this.employePrestationRepository = employePrestationRepository;
        this.horaireRepository = horaireRepository;
        this.absenceRepository = absenceRepository;
        this.reservationRepository = reservationRepository;
        this.zone = ZoneId.of(fuseau);
        this.delaiPrevenance = Duration.ofMinutes(delaiMinutes);
        this.horizonJours = horizonJours;
    }

    public ZoneId zone() {
        return zone;
    }

    /**
     * Heures de début réservables pour une prestation, un jour donné.
     *
     * @param employeId praticien souhaité, ou null pour « sans préférence »
     */
    public List<CreneauDisponible> creneaux(Long salonId, Long prestationId, LocalDate date, Long employeId) {
        Prestation prestation = prestationRepository.findById(prestationId)
                .orElseThrow(() -> new NoSuchElementException("Prestation introuvable : " + prestationId));

        if (prestation.getSalon() == null || !prestation.getSalon().getId().equals(salonId)) {
            throw new NoSuchElementException("Cette prestation n'appartient pas au salon " + salonId);
        }
        if (!prestation.isActif()) {
            return List.of();
        }
        if (horsHorizon(date)) {
            return List.of();
        }

        List<Employe> candidats = employeId != null
                ? employeRepository.findById(employeId).map(List::of).orElse(List.of())
                : employeRepository.findCapables(salonId, prestationId);

        Instant bornesDebut = date.atStartOfDay(zone).toInstant();
        Instant bornesFin = date.plusDays(1).atStartOfDay(zone).toInstant();
        Instant planchers = Instant.now().plus(delaiPrevenance);

        // TreeMap : les créneaux ressortent triés par heure, et le regroupement
        // par heure dédoublonne naturellement le mode « sans préférence ».
        TreeMap<LocalTime, List<Long>> parHeure = new TreeMap<>();

        for (Employe employe : candidats) {
            if (!employe.isActif() || !employe.getSalon().getId().equals(salonId)) {
                continue;
            }

            Optional<EmployePrestation> lien =
                    employePrestationRepository.findByEmployeIdAndPrestationId(employe.getId(), prestationId);
            if (lien.isEmpty()) {
                continue; // ce praticien ne réalise pas cette prestation
            }
            Duration duree = Duration.ofMinutes(lien.get().dureeEffective());

            List<Plage> plages = plagesDeTravail(employe, salonId, date);
            if (plages.isEmpty()) {
                continue; // ne travaille pas ce jour-là
            }

            List<Intervalle> occupe = occupation(employe.getId(), salonId, bornesDebut, bornesFin);

            for (Plage plage : plages) {
                LocalTime t = plage.debut();
                while (!t.plus(duree).isAfter(plage.fin()) && t.plus(duree).isAfter(t)) {
                    Instant debut = date.atTime(t).atZone(zone).toInstant();
                    Instant fin = debut.plus(duree);

                    boolean assezTot = !debut.isBefore(planchers);
                    if (assezTot && libre(debut, fin, occupe)) {
                        parHeure.computeIfAbsent(t, k -> new ArrayList<>()).add(employe.getId());
                    }

                    LocalTime suivant = t.plus(PAS);
                    if (!suivant.isAfter(t)) break; // garde-fou : passage de minuit
                    t = suivant;
                }
            }
        }

        List<CreneauDisponible> resultat = new ArrayList<>();
        parHeure.forEach((heure, employes) -> {
            Instant debut = date.atTime(heure).atZone(zone).toInstant();
            employes.sort(Comparator.naturalOrder());
            resultat.add(new CreneauDisponible(heure, debut, List.copyOf(employes)));
        });
        return resultat;
    }

    /** Les prochains jours comportant au moins un créneau libre. */
    public List<LocalDate> prochainsJoursDisponibles(Long salonId, Long prestationId, Long employeId, int nbJours) {
        List<LocalDate> jours = new ArrayList<>();
        LocalDate curseur = LocalDate.now(zone);
        int examines = 0;

        while (jours.size() < nbJours && examines < horizonJours) {
            if (!creneaux(salonId, prestationId, curseur, employeId).isEmpty()) {
                jours.add(curseur);
            }
            curseur = curseur.plusDays(1);
            examines++;
        }
        return jours;
    }

    /* ---------- Interne ---------- */

    private boolean horsHorizon(LocalDate date) {
        LocalDate aujourdhui = LocalDate.now(zone);
        return date.isBefore(aujourdhui) || date.isAfter(aujourdhui.plusDays(horizonJours));
    }

    /**
     * Plages de travail du praticien ce jour-là.
     * À défaut d'horaires personnels, il suit ceux du salon.
     */
    private List<Plage> plagesDeTravail(Employe employe, Long salonId, LocalDate date) {
        var jour = date.getDayOfWeek();

        List<HoraireOuverture> horaires =
                horaireRepository.findByEmployeIdAndJourSemaineOrderByHeureDebut(employe.getId(), jour);
        if (horaires.isEmpty()) {
            horaires = horaireRepository.findBySalonIdAndJourSemaineOrderByHeureDebut(salonId, jour);
        }

        return horaires.stream()
                .map(h -> new Plage(h.getHeureDebut(), h.getHeureFin()))
                .toList();
    }

    /** Réservations bloquantes et absences, fusionnées en intervalles occupés. */
    private List<Intervalle> occupation(Long employeId, Long salonId, Instant debut, Instant fin) {
        List<Intervalle> occupe = new ArrayList<>();

        for (Reservation r : reservationRepository.occupationEmploye(
                employeId, StatutReservation.BLOQUANTS, debut, fin)) {
            occupe.add(new Intervalle(r.getDebut(), r.getFin()));
        }
        for (Absence a : absenceRepository.chevauchant(employeId, salonId, debut, fin)) {
            occupe.add(new Intervalle(a.getDebut(), a.getFin()));
        }
        return occupe;
    }

    /**
     * Deux intervalles se chevauchent si debutA < finB ET debutB < finA.
     *
     * Les comparaisons sont strictes : un rendez-vous qui finit à 14:00 et un
     * autre qui commence à 14:00 ne se chevauchent pas. Utiliser <= ici
     * rejetterait des créneaux parfaitement valables — c'est l'erreur classique.
     */
    private boolean libre(Instant debut, Instant fin, List<Intervalle> occupe) {
        return occupe.stream().noneMatch(o -> debut.isBefore(o.fin()) && o.debut().isBefore(fin));
    }

    private record Plage(LocalTime debut, LocalTime fin) {}

    private record Intervalle(Instant debut, Instant fin) {}
}
