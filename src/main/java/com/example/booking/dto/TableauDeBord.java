package com.example.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * L'état de la plateforme, pour l'équipe qui l'exploite.
 *
 * Volontairement court. Un tableau de bord qui affiche tout ce qu'on sait
 * compter ne fait rien remarquer : on le regarde une fois, puis plus jamais.
 * Quatre questions, et une seule qui appelle une action.
 *
 * La dernière, `salonsDormants`, est celle qui vaut d'être livrée. Un salon
 * installé qui ne reçoit aucune réservation ne restera pas : il ne verra
 * jamais ce que le produit lui apporte, et il partira sans rien dire. C'est le
 * seul chiffre du tableau qu'on peut encore rattraper — d'où la liste nommée
 * plutôt qu'un simple compte.
 */
public record TableauDeBord(
        /** Salons par statut : ACTIF, EN_ATTENTE, SUSPENDU. */
        Map<String, Long> reseau,

        /** Réservations des trente derniers jours, par statut. */
        Map<String, Long> activite,

        /** Somme des prestations honorées sur la période, au tarif figé. */
        BigDecimal volumeMad,

        /** Demandes de démonstration par statut. */
        Map<String, Long> fileCommerciale,

        /**
         * Part des demandes traitées qui ont mené à un salon référencé.
         *
         * Rapportée aux demandes sorties de la file — converties ou perdues —
         * et non au total : compter les demandes encore en cours comme des
         * échecs ferait baisser le taux à chaque nouvelle arrivée, c'est-à-dire
         * précisément quand les choses vont bien.
         */
        Integer tauxConversionPourcent,

        /** Salons actifs sans aucune réservation sur la période. */
        List<SalonDormant> salonsDormants
) {
    public record SalonDormant(
            Long id,
            String nom,
            String ville,
            Instant installeLe,
            /** Nulle si le salon n'a jamais rien reçu. */
            Instant derniereReservation,
            /** Vrai si son catalogue est encore vide — il n'a pas fini d'être installé. */
            boolean catalogueVide
    ) {}
}
