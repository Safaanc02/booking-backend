package com.example.booking.service;

import com.example.booking.dto.StatistiquesSalon;
import com.example.booking.model.Employe;
import com.example.booking.model.HoraireOuverture;
import com.example.booking.model.enums.OrigineReservation;
import com.example.booking.model.enums.StatutReservation;
import com.example.booking.repository.EmployeRepository;
import com.example.booking.repository.HoraireOuvertureRepository;
import com.example.booking.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Les chiffres d'un salon sur une période.
 *
 * C'est la valeur que l'outil apporte par-dessus le carnet papier. Un gérant
 * connaît son carnet ; il ne connaît pas son taux de remplissage, son manque à
 * gagner sur les absences, ni la part de ses rendez-vous qui ne passe plus par
 * le téléphone. Ces trois nombres-là se disent en rendez-vous commercial, et
 * ils étaient déjà en base — il n'y avait qu'à compter.
 *
 * Tout passe par des agrégations SQL. Ramener quelques milliers de
 * réservations pour en tirer cinq nombres coûterait de la mémoire et du temps
 * pour rien.
 */
@Service
@Transactional(readOnly = true)
public class StatistiquesService {

    /** Ce qui compte comme « pas encore joué » : promis, mais pas réalisé. */
    private static final Set<StatutReservation> EN_COURS =
            Set.of(StatutReservation.EN_ATTENTE, StatutReservation.CONFIRMEE);

    /** Ce qui occupe une place dans l'agenda, honoré ou non. */
    private static final Set<StatutReservation> OCCUPANTS =
            Set.of(StatutReservation.EN_ATTENTE, StatutReservation.CONFIRMEE,
                   StatutReservation.HONOREE, StatutReservation.ABSENT);

    private final ReservationRepository reservations;
    private final HoraireOuvertureRepository horaires;
    private final EmployeRepository employes;
    private final DisponibiliteService disponibilites;

    public StatistiquesService(ReservationRepository reservations,
                               HoraireOuvertureRepository horaires,
                               EmployeRepository employes,
                               DisponibiliteService disponibilites) {
        this.reservations = reservations;
        this.horaires = horaires;
        this.employes = employes;
        this.disponibilites = disponibilites;
    }

    public StatistiquesSalon pourSalon(Long salonId, LocalDate depuis, LocalDate jusqua) {
        Instant debut = depuis.atStartOfDay(disponibilites.zone()).toInstant();
        // Borne haute exclusive sur le lendemain : « jusqu'au 30 » doit inclure
        // le 30 en entier, sinon on ampute la période d'une journée sans le dire.
        Instant fin = jusqua.plusDays(1).atStartOfDay(disponibilites.zone()).toInstant();

        Map<StatutReservation, long[]> nombres = new EnumMap<>(StatutReservation.class);
        Map<StatutReservation, BigDecimal> montants = new EnumMap<>(StatutReservation.class);
        for (Object[] ligne : reservations.bilanParStatut(salonId, debut, fin)) {
            StatutReservation statut = (StatutReservation) ligne[0];
            nombres.put(statut, new long[]{ ((Number) ligne[1]).longValue() });
            montants.put(statut, (BigDecimal) ligne[2]);
        }

        long honores  = compte(nombres, StatutReservation.HONOREE);
        long absents  = compte(nombres, StatutReservation.ABSENT);
        long aVenir   = compte(nombres, StatutReservation.EN_ATTENTE) + compte(nombres, StatutReservation.CONFIRMEE);
        long annules  = compte(nombres, StatutReservation.ANNULEE_CLIENT) + compte(nombres, StatutReservation.ANNULEE_SALON);

        BigDecimal chiffre  = montant(montants, StatutReservation.HONOREE);
        BigDecimal attendu  = montant(montants, StatutReservation.EN_ATTENTE)
                .add(montant(montants, StatutReservation.CONFIRMEE));
        BigDecimal manque   = montant(montants, StatutReservation.ABSENT);

        /* Le taux d'absence se calcule sur les rendez-vous joués, pas sur tous :
           compter les rendez-vous de la semaine prochaine dans le dénominateur
           ferait baisser le taux à mesure qu'on remplit l'agenda, ce qui n'a
           aucun sens. */
        long joues = honores + absents;
        int tauxAbsence = joues == 0 ? 0 : (int) Math.round(absents * 100.0 / joues);

        Map<OrigineReservation, Long> origines = new EnumMap<>(OrigineReservation.class);
        for (Object[] ligne : reservations.bilanParOrigine(salonId, debut, fin)) {
            origines.put((OrigineReservation) ligne[0], ((Number) ligne[1]).longValue());
        }
        long enLigne   = origines.getOrDefault(OrigineReservation.EN_LIGNE, 0L);
        long telephone = origines.getOrDefault(OrigineReservation.TELEPHONE, 0L);
        long total     = enLigne + telephone;
        int partEnLigne = total == 0 ? 0 : (int) Math.round(enLigne * 100.0 / total);

        List<StatistiquesSalon.LignePrestation> prestations = new ArrayList<>();
        for (Object[] l : reservations.bilanParPrestation(salonId, debut, fin, StatutReservation.HONOREE)) {
            prestations.add(new StatistiquesSalon.LignePrestation(
                    (String) l[0], ((Number) l[1]).longValue(), (BigDecimal) l[2]));
        }

        Map<Long, String> nomsEmployes = new HashMap<>();
        for (Employe e : employes.findBySalonIdOrderByOrdreAscPrenomAsc(salonId)) {
            nomsEmployes.put(e.getId(), e.nomComplet());
        }
        List<StatistiquesSalon.LigneEmploye> equipe = new ArrayList<>();
        for (Object[] l : reservations.bilanParEmploye(salonId, debut, fin, StatutReservation.HONOREE)) {
            Long id = ((Number) l[0]).longValue();
            equipe.add(new StatistiquesSalon.LigneEmploye(
                    id, nomsEmployes.getOrDefault(id, "Praticien retiré"),
                    ((Number) l[1]).longValue(), (BigDecimal) l[2]));
        }

        return new StatistiquesSalon(
                depuis, jusqua,
                honores, aVenir, annules, absents,
                chiffre, attendu, manque,
                tauxRemplissage(salonId, depuis, jusqua, debut, fin),
                tauxAbsence,
                partEnLigne, enLigne, telephone,
                prestations, equipe);
    }

    /**
     * Part du temps ouvrable réellement vendue.
     *
     * Le dénominateur est la somme des heures d'ouverture de chaque praticien
     * sur la période, et non celles du salon : un salon ouvert dix heures avec
     * trois praticiens vend trente heures, pas dix. Calculer sur l'ouverture du
     * salon donnerait des taux au-dessus de 100 % dès qu'ils sont deux, et un
     * chiffre supérieur à cent ne se montre pas à un client.
     *
     * Rien n'est rendu tant que les horaires ne sont pas saisis : un taux
     * calculé sur une base inventée est pire qu'une case vide, parce qu'on le
     * croit.
     */
    private Integer tauxRemplissage(Long salonId, LocalDate depuis, LocalDate jusqua,
                                    Instant debut, Instant fin) {
        List<Employe> actifs = employes.findBySalonIdOrderByOrdreAscPrenomAsc(salonId).stream().filter(Employe::isActif).toList();
        if (actifs.isEmpty()) return null;

        long minutesOuvrables = 0;
        for (Employe e : actifs) {
            List<HoraireOuverture> siens = horaires.findByEmployeIdOrderByJourSemaineAscHeureDebutAsc(e.getId());
            // Sans horaire personnel, le praticien suit celui du salon.
            List<HoraireOuverture> applicables = siens.isEmpty()
                    ? horaires.findBySalonIdOrderByJourSemaineAscHeureDebutAsc(salonId)
                    : siens;
            if (applicables.isEmpty()) continue;

            for (LocalDate jour = depuis; !jour.isAfter(jusqua); jour = jour.plusDays(1)) {
                for (HoraireOuverture h : applicables) {
                    if (h.getJourSemaine() != jour.getDayOfWeek()) continue;
                    minutesOuvrables += Duration.between(h.getHeureDebut(), h.getHeureFin()).toMinutes();
                }
            }
        }
        if (minutesOuvrables <= 0) return null;

        Double occupees = reservations.minutesOccupees(salonId, debut, fin, OCCUPANTS);
        double vendues = occupees == null ? 0 : occupees;
        // Plafonné à 100 : un dépassement signale des rendez-vous posés hors
        // horaires, pas un salon rempli à 130 %. On préfère un chiffre lisible.
        return (int) Math.min(100, Math.round(vendues * 100.0 / minutesOuvrables));
    }

    private long compte(Map<StatutReservation, long[]> m, StatutReservation s) {
        long[] v = m.get(s);
        return v == null ? 0 : v[0];
    }

    private BigDecimal montant(Map<StatutReservation, BigDecimal> m, StatutReservation s) {
        return m.getOrDefault(s, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
