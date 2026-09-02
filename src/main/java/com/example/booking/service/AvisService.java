package com.example.booking.service;

import com.example.booking.dto.AvisRequest;
import com.example.booking.dto.AvisResponse;
import com.example.booking.model.Avis;
import com.example.booking.model.Reservation;
import com.example.booking.model.Salon;
import com.example.booking.model.enums.StatutAvis;
import com.example.booking.model.enums.StatutReservation;
import com.example.booking.repository.AvisRepository;
import com.example.booking.repository.ReservationRepository;
import com.example.booking.config.CustomPermissionEvaluator;
import com.example.booking.repository.SalonRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.NoSuchElementException;

@Service
@Transactional
public class AvisService {

    private final AvisRepository avisRepository;
    private final ReservationRepository reservationRepository;
    private final SalonRepository salonRepository;
    private final CurrentUserService currentUser;
    private final CustomPermissionEvaluator droits;

    public AvisService(AvisRepository avisRepository,
                       ReservationRepository reservationRepository,
                       SalonRepository salonRepository,
                       CurrentUserService currentUser,
                       CustomPermissionEvaluator droits) {
        this.avisRepository = avisRepository;
        this.reservationRepository = reservationRepository;
        this.salonRepository = salonRepository;
        this.currentUser = currentUser;
        this.droits = droits;
    }

    /* ---------- Lecture ---------- */

    @Transactional(readOnly = true)
    public Page<AvisResponse> parSalon(Long salonId, Pageable pageable) {
        return avisRepository.findBySalonIdAndStatutOrderByCreeLeDesc(salonId, StatutAvis.PUBLIE, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<AvisResponse> parStatut(StatutAvis statut, Pageable pageable) {
        return avisRepository.findByStatutOrderByCreeLeDesc(statut, pageable).map(this::toResponse);
    }

    /* ---------- Dépôt ---------- */

    /**
     * Un avis n'est recevable que si le rendez-vous a effectivement eu lieu.
     *
     * Trois barrières, dans cet ordre : c'est bien le client concerné, le
     * rendez-vous est marqué HONOREE par le salon, et il n'a pas déjà été
     * commenté. La troisième est doublée d'une contrainte d'unicité en base,
     * seule garantie en cas de double soumission.
     */
    public AvisResponse deposer(AvisRequest req) {
        Reservation r = reservationRepository.findById(req.reservationId())
                .orElseThrow(() -> new NoSuchElementException("Réservation introuvable : " + req.reservationId()));

        String keycloakId = currentUser.currentKeycloakId().orElse(null);
        boolean estLeClient = r.getClient() != null && keycloakId != null
                && keycloakId.equals(r.getClient().getKeycloakId());
        if (!estLeClient && !currentUser.isAdmin()) {
            throw new SecurityException("Cette réservation n'est pas la vôtre");
        }

        if (r.getStatut() != StatutReservation.HONOREE) {
            throw new IllegalStateException(
                    "Vous pourrez donner votre avis une fois le rendez-vous passé et confirmé par le salon");
        }

        if (avisRepository.existsByReservationId(r.getId())) {
            throw new IllegalStateException("Vous avez déjà donné votre avis sur ce rendez-vous");
        }

        Avis avis = Avis.builder()
                .reservation(r)
                .salon(r.getSalon())
                .employe(r.getEmploye())
                .client(r.getClient())
                .note(req.note().shortValue())
                .commentaire(vide(req.commentaire()))
                .statut(StatutAvis.PUBLIE)
                .build();

        Avis enregistre;
        try {
            enregistre = avisRepository.saveAndFlush(avis);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Vous avez déjà donné votre avis sur ce rendez-vous");
        }

        rafraichirNote(r.getSalon().getId());
        return toResponse(enregistre);
    }

    /* ---------- Droit de réponse ---------- */

    public AvisResponse repondre(Long avisId, String reponse) {
        Avis avis = charger(avisId);
        verifierProprietaireSalon(avis.getSalon());

        if (reponse == null || reponse.isBlank()) {
            throw new IllegalArgumentException("La réponse ne peut pas être vide");
        }
        avis.setReponseSalon(reponse.trim());
        avis.setReponseLe(Instant.now());
        return toResponse(avisRepository.save(avis));
    }

    /* ---------- Modération ---------- */

    public AvisResponse changerStatut(Long avisId, StatutAvis statut) {
        Avis avis = charger(avisId);
        avis.setStatut(statut);
        Avis enregistre = avisRepository.save(avis);
        // Masquer un avis doit retirer sa note de la moyenne.
        rafraichirNote(avis.getSalon().getId());
        return toResponse(enregistre);
    }

    /* ---------- Interne ---------- */

    /**
     * Recalcule la note dénormalisée du salon.
     *
     * Recalcul complet plutôt qu'incrémental : c'est une poignée de lignes, et
     * un compteur incrémenté se désynchronise au premier avis masqué ou
     * supprimé.
     */
    private void rafraichirNote(Long salonId) {
        Object[] stats = avisRepository.statistiques(salonId);
        // Hibernate renvoie une ligne unique, éventuellement emballée dans un tableau.
        Object[] ligne = (stats.length == 1 && stats[0] instanceof Object[] inner) ? inner : stats;

        Double moyenne = (Double) ligne[0];
        Long nombre = (Long) ligne[1];

        Salon salon = salonRepository.findById(salonId)
                .orElseThrow(() -> new NoSuchElementException("Salon introuvable : " + salonId));
        salon.setNombreAvis(nombre == null ? 0 : nombre.intValue());
        salon.setNoteMoyenne(moyenne == null ? null
                : BigDecimal.valueOf(moyenne).setScale(1, RoundingMode.HALF_UP));
        salonRepository.save(salon);
    }

    private Avis charger(Long id) {
        return avisRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Avis introuvable : " + id));
    }

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

    private String vide(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private AvisResponse toResponse(Avis a) {
        return new AvisResponse(
                a.getId(),
                a.getSalon().getId(),
                a.getSalon().getNom(),
                prenomSeul(a),
                a.getEmploye() != null ? a.getEmploye().getPrenom() : null,
                a.getReservation() != null ? a.getReservation().getNomPrestationFige() : null,
                a.getNote(),
                a.getCommentaire(),
                a.getReponseSalon(),
                a.getReponseLe(),
                a.getCreeLe(),
                a.getStatut().name());
    }

    /** « Salma I. » : identifiable par le salon, pas par un inconnu. */
    private String prenomSeul(Avis a) {
        if (a.getClient() == null) return "Client";
        String complet = a.getClient().getFullName();
        if (complet == null || complet.isBlank()) return a.getClient().getUsername();
        String[] mots = complet.trim().split("\\s+");
        return mots.length == 1 ? mots[0] : mots[0] + " " + mots[1].charAt(0) + ".";
    }
}
