package com.example.booking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Ce qu'un gérant veut savoir de son salon, et qu'aucun écran ne lui disait.
 *
 * Il connaît son carnet — il l'a sous les yeux toute la journée. Ce qu'il ne
 * connaît pas, c'est ce que le carnet ne montre pas : combien de rendez-vous
 * en un mois, pour quel montant, quelle part de son temps ouvert est
 * réellement vendue, combien de clientes ne sont pas venues, et si l'outil lui
 * fait gagner des appels ou non.
 *
 * Tout est déjà en base. Il n'y avait qu'à compter.
 */
public record StatistiquesSalon(
        LocalDate depuis,
        LocalDate jusqua,

        /** Rendez-vous honorés sur la période. */
        long honores,
        /** Encore à venir ou en attente : ce qui est promis, pas encore réalisé. */
        long aVenir,
        /** Annulés, par le client ou par le salon. */
        long annules,
        /** Clientes qui ne se sont pas présentées. */
        long absents,

        /** Encaissé sur les rendez-vous honorés. */
        BigDecimal chiffreAffaires,
        /** Ce que les rendez-vous à venir représentent, s'ils sont tous honorés. */
        BigDecimal chiffreAttendu,
        /** Perdu faute de présentation — le chiffre qui fait réagir. */
        BigDecimal manqueAGagner,

        /**
         * Part du temps d'ouverture réellement vendue, en pourcentage.
         *
         * null quand le salon n'a pas encore saisi ses horaires : mieux vaut
         * ne rien afficher qu'un taux calculé sur une base inventée.
         */
        Integer tauxRemplissage,
        /** Part des rendez-vous passés où la cliente n'est pas venue, en pourcentage. */
        int tauxAbsence,

        /** Part des rendez-vous pris en ligne plutôt qu'au téléphone, en pourcentage. */
        int partEnLigne,
        long reservationsEnLigne,
        long reservationsTelephone,

        List<LignePrestation> prestations,
        List<LigneEmploye> equipe
) {
    /** Une prestation, ce qu'elle rapporte et combien de fois elle part. */
    public record LignePrestation(String nom, long nombre, BigDecimal montant) {}

    /** Un praticien, son volume et ce qu'il rapporte. */
    public record LigneEmploye(Long id, String nom, long nombre, BigDecimal montant) {}
}
