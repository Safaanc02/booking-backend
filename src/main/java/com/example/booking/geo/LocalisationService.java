package com.example.booking.geo;

import com.example.booking.model.RepereGeo;
import com.example.booking.model.Salon;
import com.example.booking.repository.RepereGeoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Donne un point à un salon.
 *
 * Trois sources, par confiance décroissante :
 *   1. les coordonnées relevées pour ce salon (sur une carte, au mètre près) ;
 *   2. le centre de son quartier (quelques centaines de mètres) ;
 *   3. le centre de sa ville (quelques kilomètres).
 *
 * Le repli compte autant que le relevé exact. Sans lui, un salon référencé
 * sans coordonnées disparaîtrait purement et simplement d'une recherche « près
 * de moi » — invisible pour le client, sans que le gérant comprenne pourquoi.
 * Mieux vaut le placer au centre de son quartier et le montrer.
 */
@Service
public class LocalisationService {

    private static final Logger log = LoggerFactory.getLogger(LocalisationService.class);

    private final RepereGeoRepository reperes;

    public LocalisationService(RepereGeoRepository reperes) {
        this.reperes = reperes;
    }

    /**
     * Complète les coordonnées du salon si elles manquent, sans jamais écraser
     * un relevé existant.
     *
     * @return true si le salon est situé au sortir de l'appel
     */
    @Transactional(readOnly = true)
    public boolean situer(Salon salon) {
        if (salon.getLatitude() != null && salon.getLongitude() != null) return true;

        Optional<RepereGeo> repere = Optional.empty();
        if (salon.getVille() != null && !salon.getVille().isBlank()) {
            if (salon.getQuartier() != null && !salon.getQuartier().isBlank()) {
                repere = reperes.duQuartier(salon.getVille(), salon.getQuartier());
            }
            if (repere.isEmpty()) {
                repere = reperes.deLaVille(salon.getVille());
            }
        }

        if (repere.isEmpty()) {
            // Pas une erreur : une ville hors du référentiel est simplement une
            // ville à ajouter. Le salon reste cherchable par nom et par ville,
            // il ne sortira pas d'une recherche par distance.
            log.info("Salon « {} » non situé : aucun repère pour {} / {}. "
                            + "Ajoutez une ligne dans repere_geo pour l'inclure "
                            + "dans les recherches par distance.",
                    salon.getNom(), salon.getVille(), salon.getQuartier());
            return false;
        }

        salon.setLatitude(repere.get().getLatitude());
        salon.setLongitude(repere.get().getLongitude());
        return true;
    }
}
