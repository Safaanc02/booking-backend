package com.example.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Ce qu'un salon sait d'un de ses clients.
 *
 * Tout se calcule depuis les réservations, sauf la note — qui ne se déduit de
 * rien. Une table de clients tenue en parallèle dériverait au premier oubli,
 * et un compteur de visites faux est pire qu'absent : on s'appuie dessus pour
 * reconnaître un habitué.
 *
 * `cle` est l'identité du client pour ce salon : son numéro ramené à la forme
 * nationale, ou « compte:42 » à défaut de numéro. Opaque pour l'interface, qui
 * ne fait que la renvoyer.
 */
public record ClientSalon(
        String cle,
        String nom,
        String telephone,
        /** Rendez-vous honorés. C'est cela, « être venu ». */
        int visites,
        int annulations,
        /** Rendez-vous où le client n'est pas venu, sans prévenir. */
        int absences,
        Instant premiereVisite,
        Instant derniereVisite,
        /** Somme des prestations honorées, au tarif figé à la réservation. */
        BigDecimal totalDepense,
        /** Note privée du salon. Jamais partagée avec un autre salon. */
        String note,
        /** Historique détaillé — nul dans la liste, rempli sur la fiche. */
        List<ReservationResume> historique
) {
    /** Une ligne d'historique, telle que le salon la relit. */
    public record ReservationResume(
            Long id,
            Instant debut,
            String prestation,
            String employe,
            String statut,
            BigDecimal prix,
            String origine
    ) {}
}
