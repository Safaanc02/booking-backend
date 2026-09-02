package com.example.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ReservationResponse(
        Long id,
        Long salonId,
        String salonNom,
        Long prestationId,
        String prestation,
        Long employeId,
        String employe,
        Instant debut,
        Instant fin,
        String statut,
        BigDecimal prix,
        String noteClient,
        /** Permet à l'interface de proposer, ou non, le dépôt d'un avis. */
        boolean avisDepose,
        /**
         * L'annulation est-elle encore possible ?
         *
         * Calculé côté serveur et non déduit dans le navigateur : le délai
         * appartient au salon, et l'interface n'a pas à le recalculer — elle
         * proposerait un bouton que le serveur refuserait ensuite.
         */
        boolean annulable,
        /** Préavis exigé par le salon, en heures, pour pouvoir l'expliquer au client. */
        int delaiAnnulationHeures
) {}
