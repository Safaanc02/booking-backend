package com.example.booking.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * La journée d'une ville, heure par heure : qui peut vous recevoir, et quand.
 *
 * C'est le renversement de l'accueil. Tous les sites demandent « quel
 * salon ? » puis « quelle heure ? » ; une cliente se demande d'abord « j'ai
 * une heure à 16:30, qui me prend ? ». Cette réponse-là tient dans une liste
 * d'heures portant chacune les salons libres à cet instant.
 */
public record CreneauxDuJour(LocalDate date, String ville, List<Heure> heures) {
    public record Heure(LocalTime heure, List<Long> salons) {}
}
