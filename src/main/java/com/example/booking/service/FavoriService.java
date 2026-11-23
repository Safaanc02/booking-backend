package com.example.booking.service;

import com.example.booking.model.Favori;
import com.example.booking.model.Salon;
import com.example.booking.model.User;
import com.example.booking.model.enums.SalonStatut;
import com.example.booking.repository.FavoriRepository;
import com.example.booking.repository.SalonRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Les salons qu'une cliente garde.
 *
 * Tout est rapporté à la personne connectée : un favori n'a pas d'autre
 * propriétaire possible, et aucune route ne prend d'identifiant de client.
 */
@Service
@Transactional
public class FavoriService {

    private final FavoriRepository favoris;
    private final SalonRepository salons;
    private final CurrentUserService currentUser;

    public FavoriService(FavoriRepository favoris, SalonRepository salons, CurrentUserService currentUser) {
        this.favoris = favoris;
        this.salons = salons;
        this.currentUser = currentUser;
    }

    /** Les identifiants des salons gardés, du plus récent au plus ancien. */
    @Transactional(readOnly = true)
    public List<Long> mesSalons() {
        User moi = currentUser.getOrCreate();
        return favoris.findByClientIdOrderByCreeLeDesc(moi.getId()).stream()
                .map(f -> f.getSalon().getId())
                .toList();
    }

    public void garder(Long salonId) {
        User moi = currentUser.getOrCreate();
        Salon salon = salons.findById(salonId)
                .filter(s -> s.getStatut() == SalonStatut.ACTIF)
                .orElseThrow(() -> new NoSuchElementException("Salon introuvable : " + salonId));
        if (favoris.existsByClientIdAndSalonId(moi.getId(), salonId)) return;
        try {
            favoris.saveAndFlush(Favori.builder().client(moi).salon(salon).build());
        } catch (DataIntegrityViolationException e) {
            /* Deux clics rapprochés : le second trouve le premier déjà là. */
        }
    }

    public void retirer(Long salonId) {
        User moi = currentUser.getOrCreate();
        favoris.findByClientIdAndSalonId(moi.getId(), salonId).ifPresent(favoris::delete);
    }
}
