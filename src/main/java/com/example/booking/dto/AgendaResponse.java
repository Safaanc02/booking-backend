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
        String noteClient,
        /**
         * Combien de fois cette cliente n'est pas venue, dans CE salon.
         *
         * « Les clientes ne viennent pas » est la première plainte de tous les
         * salons, et le statut ABSENT ne déclenchait rien : on constatait le
         * problème sans rien en faire. Le compteur ne punit personne — il met
         * l'information sous les yeux du gérant au moment où elle sert, quand
         * il regarde sa journée et décide s'il rappelle pour confirmer.
         *
         * Dans ce salon seulement : une absence chez le voisin ne le regarde
         * pas, et ne doit pas suivre une cliente d'un établissement à l'autre.
         *
         * Nul, et non zéro, là où le compteur n'a pas été calculé — le planning
         * personnel d'un praticien, qui peut traverser plusieurs salons. Un
         * zéro affirmerait « cette cliente est toujours venue », ce qu'on ne
         * sait pas ; l'absence de valeur n'affirme rien.
         */
        Integer absencesClient
) {}
