package com.example.booking.service;

import com.example.booking.dto.AgendaResponse;
import com.example.booking.dto.ReservationProRequest;
import com.example.booking.model.Employe;
import com.example.booking.model.EmployePrestation;
import com.example.booking.model.Prestation;
import com.example.booking.model.Reservation;
import com.example.booking.model.Salon;
import com.example.booking.model.enums.OrigineReservation;
import com.example.booking.model.enums.StatutReservation;
import com.example.booking.notification.ReservationAnnuleeParSalon;
import com.example.booking.notification.ReservationCreee;
import com.example.booking.repository.EmployePrestationRepository;
import com.example.booking.repository.EmployeRepository;
import com.example.booking.repository.PrestationRepository;
import com.example.booking.repository.ReservationRepository;
import com.example.booking.config.CustomPermissionEvaluator;
import com.example.booking.repository.SalonRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/** Agenda du salon et saisie des rendez-vous pris hors ligne. */
@Service
@Transactional
public class AgendaService {

    private final ReservationRepository reservationRepository;
    private final SalonRepository salonRepository;
    private final PrestationRepository prestationRepository;
    private final EmployeRepository employeRepository;
    private final EmployePrestationRepository employePrestationRepository;
    private final DisponibiliteService disponibilites;
    private final CurrentUserService currentUser;
    private final CustomPermissionEvaluator droits;
    private final ApplicationEventPublisher evenements;

    public AgendaService(ReservationRepository reservationRepository,
                         SalonRepository salonRepository,
                         PrestationRepository prestationRepository,
                         EmployeRepository employeRepository,
                         EmployePrestationRepository employePrestationRepository,
                         DisponibiliteService disponibilites,
                         CurrentUserService currentUser,
                         CustomPermissionEvaluator droits,
                         ApplicationEventPublisher evenements) {
        this.reservationRepository = reservationRepository;
        this.salonRepository = salonRepository;
        this.prestationRepository = prestationRepository;
        this.employeRepository = employeRepository;
        this.employePrestationRepository = employePrestationRepository;
        this.disponibilites = disponibilites;
        this.currentUser = currentUser;
        this.droits = droits;
        this.evenements = evenements;
    }

    /** Rendez-vous du salon sur `jours` jours à partir de `date`. */
    @Transactional(readOnly = true)
    public List<AgendaResponse> agenda(Long salonId, LocalDate date, int jours) {
        var zone = disponibilites.zone();
        Instant debut = date.atStartOfDay(zone).toInstant();
        Instant fin = date.plusDays(Math.max(1, jours)).atStartOfDay(zone).toInstant();

        Map<String, Integer> absences = absencesDuSalon(salonId);
        return reservationRepository.agenda(salonId, debut, fin).stream()
                .map((r) -> toResponse(r, absences))
                .toList();
    }

    /**
     * Planning personnel du compte connecté.
     *
     * Un praticien n'administre rien, mais doit pouvoir consulter ses propres
     * rendez-vous. Il n'était jusqu'ici titulaire d'aucun droit : la seule
     * façon de voir son planning était d'emprunter le compte du propriétaire.
     *
     * Renvoie les rendez-vous de toutes ses fiches, un même compte pouvant
     * travailler dans plusieurs salons.
     */
    @Transactional(readOnly = true)
    public List<AgendaResponse> monPlanning(LocalDate date, int jours) {
        String keycloakId = currentUser.currentKeycloakId()
                .orElseThrow(() -> new SecurityException("Authentification requise"));

        List<Long> mesFiches = employeRepository.mesFiches(keycloakId).stream()
                .map(Employe::getId)
                .toList();
        if (mesFiches.isEmpty()) {
            // Compte sans fiche : aucun planning, ce n'est pas une erreur.
            return List.of();
        }

        var zone = disponibilites.zone();
        Instant debut = date.atStartOfDay(zone).toInstant();
        Instant fin = date.plusDays(Math.max(1, jours)).atStartOfDay(zone).toInstant();

        return reservationRepository.planningDesPraticiens(mesFiches, debut, fin).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * La journée du gérant, tous ses salons confondus.
     *
     * Un gérant peut en tenir plusieurs, et sa question du matin ne porte pas
     * sur l'un d'eux mais sur l'ensemble : qu'est-ce qui m'attend aujourd'hui.
     * Y répondre depuis le navigateur imposait un appel par salon — trente et
     * un pour certains comptes de démonstration — là où une seule requête
     * suffit.
     *
     * `queJePeuxGerer` est la même source que « mes salons » : ce qu'on voit
     * ici est exactement ce qu'on a le droit de gérer, sans second jeu de
     * règles à tenir en accord avec le premier.
     */
    @Transactional(readOnly = true)
    public List<AgendaResponse> maJournee(LocalDate date) {
        String keycloakId = currentUser.currentKeycloakId()
                .orElseThrow(() -> new SecurityException("Authentification requise"));

        List<Long> salons = salonRepository.queJePeuxGerer(keycloakId).stream()
                .map(Salon::getId)
                .toList();
        // Un compte sans salon n'a pas de journée : ce n'est pas une erreur.
        if (salons.isEmpty()) return List.of();

        var zone = disponibilites.zone();
        Instant debut = date.atStartOfDay(zone).toInstant();
        Instant fin = date.plusDays(1).atStartOfDay(zone).toInstant();

        Map<String, Integer> absences = absencesDesSalons(salons);
        return reservationRepository.journeeDeSalons(salons, debut, fin).stream()
                .map((r) -> toResponse(r, absences))
                .toList();
    }

    /**
     * Rendez-vous pris par téléphone ou au comptoir.
     *
     * Sans cette saisie, les créneaux réservés hors ligne restent proposés en
     * ligne et produisent de vraies doubles réservations.
     */
    public AgendaResponse creerHorsLigne(Long salonId, ReservationProRequest req) {
        Salon salon = salonRepository.findById(salonId)
                .orElseThrow(() -> new NoSuchElementException("Salon introuvable : " + salonId));

        Prestation prestation = prestationRepository.findById(req.prestationId())
                .orElseThrow(() -> new NoSuchElementException("Prestation introuvable"));
        if (!prestation.getSalon().getId().equals(salonId)) {
            throw new NoSuchElementException("Cette prestation n'appartient pas à ce salon");
        }

        Employe employe = employeRepository.findById(req.employeId())
                .orElseThrow(() -> new NoSuchElementException("Praticien introuvable"));
        if (!employe.getSalon().getId().equals(salonId)) {
            throw new NoSuchElementException("Ce praticien n'appartient pas à ce salon");
        }

        int minutes = employePrestationRepository
                .findByEmployeIdAndPrestationId(employe.getId(), prestation.getId())
                .map(EmployePrestation::dureeEffective)
                .orElseThrow(() -> new IllegalStateException("Ce praticien ne réalise pas cette prestation"));

        OrigineReservation origine;
        try {
            origine = req.origine() != null
                    ? OrigineReservation.valueOf(req.origine())
                    : OrigineReservation.TELEPHONE;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Origine inconnue : " + req.origine());
        }

        Reservation r = Reservation.builder()
                .salon(salon)
                .employe(employe)
                .prestation(prestation)
                .debut(req.debut())
                .fin(req.debut().plus(Duration.ofMinutes(minutes)))
                .statut(StatutReservation.CONFIRMEE)
                .prixFige(prestation.getPrix())
                .nomPrestationFige(prestation.getNom())
                .clientNomLibre(req.clientNom())
                .clientTelephoneLibre(vide(req.clientTelephone()))
                .noteClient(vide(req.note()))
                .origine(origine)
                .build();

        try {
            Reservation enregistree = reservationRepository.saveAndFlush(r);
            evenements.publishEvent(new ReservationCreee(enregistree.getId()));
            return toResponse(enregistree);
        } catch (DataIntegrityViolationException e) {
            if (estChevauchement(e)) {
                throw new IllegalStateException("Ce praticien a déjà un rendez-vous sur ce créneau");
            }
            throw e;
        }
    }

    /** Le salon marque le rendez-vous honoré, absent, ou l'annule de son côté. */
    /**
     * Les transitions qu'un salon a le droit de faire.
     *
     * Il n'y en avait aucune : « honoré » se posait sur un rendez-vous de la
     * semaine prochaine, et un rendez-vous annulé repassait « confirmé » alors
     * que le créneau avait été repris — la contrainte d'exclusion refusait au
     * commit, et le gérant recevait une erreur 500 sans explication.
     *
     * EN_ATTENTE → CONFIRMEE, ANNULEE_SALON
     * CONFIRMEE  → HONOREE (une fois commencé), ABSENT (une fois commencé), ANNULEE_SALON
     * HONOREE    ↔ ABSENT (une erreur de pointage se corrige)
     * ANNULEE_*  → CONFIRMEE (on réactive, si le créneau est encore libre)
     */
    private static final Map<StatutReservation, java.util.Set<StatutReservation>> TRANSITIONS = Map.of(
            StatutReservation.EN_ATTENTE,     java.util.Set.of(StatutReservation.CONFIRMEE, StatutReservation.ANNULEE_SALON),
            StatutReservation.CONFIRMEE,      java.util.Set.of(StatutReservation.HONOREE, StatutReservation.ABSENT, StatutReservation.ANNULEE_SALON),
            StatutReservation.HONOREE,        java.util.Set.of(StatutReservation.ABSENT),
            StatutReservation.ABSENT,         java.util.Set.of(StatutReservation.HONOREE),
            StatutReservation.ANNULEE_SALON,  java.util.Set.of(StatutReservation.CONFIRMEE),
            StatutReservation.ANNULEE_CLIENT, java.util.Set.of(StatutReservation.CONFIRMEE));

    public AgendaResponse changerStatut(Long reservationId, StatutReservation statut) {
        Reservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NoSuchElementException("Réservation introuvable : " + reservationId));

        verifierProprietaireSalon(r.getSalon());

        if (statut == StatutReservation.ANNULEE_CLIENT) {
            throw new IllegalArgumentException(
                    "Une annulation par le salon se marque ANNULEE_SALON, pas ANNULEE_CLIENT");
        }
        StatutReservation actuel = r.getStatut();
        if (actuel == statut) {
            return toResponse(r);
        }
        if (!TRANSITIONS.getOrDefault(actuel, java.util.Set.of()).contains(statut)) {
            throw new IllegalStateException("Un rendez-vous " + libelle(actuel)
                    + " ne peut pas passer à " + libelle(statut));
        }
        // « Honoré » et « absent » se constatent, ils ne se prédisent pas.
        if ((statut == StatutReservation.HONOREE || statut == StatutReservation.ABSENT)
                && r.getDebut().isAfter(Instant.now())) {
            throw new IllegalStateException("Ce rendez-vous n'a pas encore commencé : "
                    + "on ne peut pas encore dire si la cliente est venue");
        }

        r.setStatut(statut);
        if (statut == StatutReservation.ANNULEE_SALON) {
            r.setAnnuleeLe(Instant.now());
        }

        Reservation enregistree;
        try {
            // Flushé ici, pas au commit : si la réactivation heurte un créneau
            // repris entre-temps, c'est maintenant qu'on veut le savoir, avec
            // un message, et non en 500 à la fin de la requête.
            enregistree = reservationRepository.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            if (estChevauchement(e)) {
                throw new IllegalStateException("Ce créneau a été repris entre-temps : "
                        + "le rendez-vous ne peut pas être réactivé");
            }
            throw e;
        }

        if (statut == StatutReservation.ANNULEE_SALON) {
            // La cliente doit le savoir avant de partir. Publié dans la
            // transaction, consommé après son commit.
            evenements.publishEvent(new ReservationAnnuleeParSalon(enregistree.getId()));
        }
        return toResponse(enregistree);
    }

    private static String libelle(StatutReservation s) {
        return switch (s) {
            case EN_ATTENTE -> "en attente";
            case CONFIRMEE -> "confirmé";
            case HONOREE -> "honoré";
            case ABSENT -> "marqué absent";
            case ANNULEE_SALON -> "annulé par le salon";
            case ANNULEE_CLIENT -> "annulé par la cliente";
        };
    }

    /* ---------- Helpers ---------- */

    /**
     * Délègue à l'évaluateur : propriétaire, gestionnaire délégué ou
     * administrateur. Refaire le calcul ici serait une troisième copie de la
     * même règle.
     */
    private void verifierProprietaireSalon(Salon salon) {
        if (!droits.peutGererSalon(salon.getId())) {
            throw new SecurityException("Vous n'avez pas les droits de gestion sur ce salon");
        }
    }

    private boolean estChevauchement(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("pas_de_chevauchement")) return true;
        }
        return false;
    }

    private String vide(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private AgendaResponse toResponse(Reservation r) {
        return toResponse(r, null);
    }

    /**
     * La même conversion, en sachant combien de fois chaque cliente a manqué.
     *
     * Le compteur arrive en carte plutôt que d'être interrogé ligne par ligne :
     * une journée chargée compte quarante rendez-vous, et quarante
     * allers-retours en base seraient payés par le gérant en attente devant
     * son écran.
     */
    private AgendaResponse toResponse(Reservation r, Map<String, Integer> absences) {
        Integer manquees = absences == null ? null : absences.getOrDefault(cleClient(r), 0);
        return new AgendaResponse(
                r.getId(),
                r.getSalon() != null ? r.getSalon().getId() : null,
                r.getSalon() != null ? r.getSalon().getNom() : null,
                r.getDebut(), r.getFin(),
                r.getStatut().name(),
                r.getOrigine() != null ? r.getOrigine().name() : null,
                r.nomClient(), r.telephoneClient(),
                r.getNomPrestationFige(),
                r.getEmploye() != null ? r.getEmploye().getId() : null,
                r.getEmploye() != null ? r.getEmploye().nomComplet() : null,
                r.getPrixFige(), r.getNoteClient(),
                manquees);
    }

    /**
     * L'identité d'une cliente pour un salon.
     *
     * La même clé que celle des fiches clients : le numéro ramené à la forme
     * nationale, ou « compte:42 » à défaut. Deux identités différentes pour la
     * même personne donneraient un compteur à zéro là où le salon en attend
     * trois — et c'est sur ce compteur qu'il décide de rappeler.
     *
     * La normalisation reproduit celle de la base (V16) : on ne garde que les
     * chiffres, et un numéro au format international marocain redevient
     * national.
     */
    private String cleClient(Reservation r) {
        String tel = r.telephoneClient();
        if (tel != null && !tel.isBlank()) {
            String chiffres = tel.replaceAll("\\D", "");
            if (chiffres.startsWith("212")) chiffres = "0" + chiffres.substring(3);
            if (!chiffres.isBlank()) return chiffres;
        }
        return r.getClient() != null ? "compte:" + r.getClient().getId() : null;
    }

    /** Les absences déjà constatées dans ce salon, par cliente. */
    private Map<String, Integer> absencesDuSalon(Long salonId) {
        Map<String, Integer> par = new HashMap<>();
        for (Object[] l : reservationRepository.absencesParClient(salonId)) {
            if (l[0] != null) par.put((String) l[0], ((Number) l[1]).intValue());
        }
        return par;
    }

    /** Les absences de plusieurs salons à la fois, pour la journée d'un gérant. */
    private Map<String, Integer> absencesDesSalons(Collection<Long> salonIds) {
        Map<String, Integer> par = new HashMap<>();
        for (Long id : salonIds) {
            absencesDuSalon(id).forEach((cle, n) -> par.merge(cle, n, Integer::sum));
        }
        return par;
    }
}
