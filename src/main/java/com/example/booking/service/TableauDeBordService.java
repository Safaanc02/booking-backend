package com.example.booking.service;

import com.example.booking.dto.TableauDeBord;
import com.example.booking.model.enums.StatutDemandeDemo;
import com.example.booking.repository.DemandeDemoRepository;
import com.example.booking.repository.ReservationRepository;
import com.example.booking.repository.SalonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * L'état de la plateforme, pour l'équipe qui l'exploite.
 *
 * Quatre questions, et une seule qui appelle une action : quels salons
 * décrochent. Le reste situe.
 */
@Service
@Transactional(readOnly = true)
public class TableauDeBordService {

    private final SalonRepository salons;
    private final ReservationRepository reservations;
    private final DemandeDemoRepository demandes;

    public TableauDeBordService(SalonRepository salons,
                                ReservationRepository reservations,
                                DemandeDemoRepository demandes) {
        this.salons = salons;
        this.reservations = reservations;
        this.demandes = demandes;
    }

    public TableauDeBord etat(int jours) {
        Instant depuis = Instant.now().minus(Duration.ofDays(jours));

        Map<String, Long> reseau = compter(salons.compterParStatut());
        Map<String, Long> activite = compter(reservations.compterParStatutDepuis(depuis));

        Map<String, Long> file = new LinkedHashMap<>();
        for (StatutDemandeDemo s : StatutDemandeDemo.values()) {
            file.put(s.name(), demandes.countByStatut(s));
        }

        List<TableauDeBord.SalonDormant> dormants =
                salons.salonsSansReservationDepuis(depuis).stream()
                        .map(l -> new TableauDeBord.SalonDormant(
                                ((Number) l[0]).longValue(),
                                (String) l[1],
                                (String) l[2],
                                (Instant) l[3],
                                (Instant) l[4],
                                Boolean.TRUE.equals(l[5])))
                        .toList();

        return new TableauDeBord(
                reseau, activite,
                reservations.volumeHonoreDepuis(depuis),
                file,
                tauxConversion(file),
                dormants);
    }

    /**
     * Part des demandes sorties de la file qui ont mené à un salon.
     *
     * Rapportée aux demandes tranchées — converties ou perdues — et non au
     * total. Compter les demandes encore en cours comme des échecs ferait
     * baisser le taux à chaque nouvelle arrivée, c'est-à-dire précisément
     * quand les choses vont bien : un indicateur qui empire quand on réussit
     * n'est pas un indicateur.
     *
     * Nul tant que rien n'est tranché : afficher « 0 % » avant la première
     * décision décrirait un échec qui n'a pas eu lieu.
     */
    private static Integer tauxConversion(Map<String, Long> file) {
        long converties = file.getOrDefault(StatutDemandeDemo.CONVERTIE.name(), 0L);
        long perdues = file.getOrDefault(StatutDemandeDemo.PERDUE.name(), 0L);
        long tranchees = converties + perdues;
        return tranchees == 0 ? null : (int) Math.round(100.0 * converties / tranchees);
    }

    /** Les statuts absents valent zéro : un tableau à trous se lit mal. */
    private static Map<String, Long> compter(List<Object[]> lignes) {
        Map<String, Long> par = new LinkedHashMap<>();
        for (Object[] l : lignes) {
            par.put(String.valueOf(l[0]), ((Number) l[1]).longValue());
        }
        return par;
    }
}
