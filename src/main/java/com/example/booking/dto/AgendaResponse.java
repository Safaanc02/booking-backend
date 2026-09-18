package com.example.booking.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Une ligne d'agenda vue par le salon. Le nom du client prime sur tout le reste.
 *
 * Le salon y figure parce que deux lectures n'en portent aucun dans leur
 * chemin : « mon planning », qui rassemble les rendez-vous d'un praticien
 * travaillant dans plusieurs salons, et « ma journée », qui rassemble ceux de
 * tous les salons d'un gérant. Sans ces deux champs, la liste mélangeait des
 * établissements sans que rien ne les distingue.
 */
public record AgendaResponse(
        Long id,
        Long salonId,
        String salonNom,
        Instant debut,
        Instant fin,
        String statut,
        String origine,
        String client,
        String clientTelephone,
        String prestation,
        Long employeId,
        String employe,
        BigDecimal prix,
        String noteClient
) {}
