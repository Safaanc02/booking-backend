package com.example.booking.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Premier créneau libre d'un salon, tel que la liste de résultats l'annonce.
 *
 * Le prix disait déjà combien ; il manquait quand. Une liste où chaque carte
 * affiche un tarif mais aucune disponibilité oblige à ouvrir les fiches une
 * par une pour découvrir que la première est complète jusqu'à jeudi.
 *
 * Calculé sur la prestation la plus courte du salon : c'est elle qui a le plus
 * de créneaux à montrer, et l'interface le dit — « à partir de », comme pour
 * le prix. Un autre choix de prestation donnera une autre date.
 */
public record ProchaineDispo(LocalDate date, LocalTime premiereHeure) {}
