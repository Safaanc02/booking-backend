package com.example.booking.service;

import com.example.booking.config.CustomPermissionEvaluator;
import com.example.booking.dto.SalonRequest;
import com.example.booking.geo.LocalisationService;
import com.example.booking.model.RepereGeo;
import com.example.booking.model.Salon;
import com.example.booking.model.User;
import com.example.booking.model.enums.SalonCategorie;
import com.example.booking.model.enums.SalonStatut;
import com.example.booking.repository.RepereGeoRepository;
import com.example.booking.repository.SalonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Le sort des coordonnées d'un salon lors d'une modification.
 *
 * Cette règle décide où un salon apparaît dans « autour de moi », et se casse
 * sans bruit : aucune erreur, aucune trace, seulement un salon qui se déplace
 * de quelques kilomètres. Un relevé perdu ne revient pas — personne ne
 * ressort mesurer la position d'un salon déjà enregistré.
 */
class SalonCoordonneesTest {

    /** Le repère du Maarif, tel que la migration V13 l'enregistre. */
    private static final double MAARIF_LAT = 33.5834, MAARIF_LNG = -7.6329;
    /** Celui de Gauthier, à trois kilomètres. */
    private static final double GAUTHIER_LAT = 33.5883, GAUTHIER_LNG = -7.6222;
    /** Un point relevé sur une carte, à l'intérieur du Maarif. */
    private static final double RELEVE_LAT = 33.5801, RELEVE_LNG = -7.6350;

    private SalonRepository salons;
    private SalonService service;

    @BeforeEach
    void avant() {
        salons = mock(SalonRepository.class);
        // Le dépôt rend ce qu'on lui donne : le test regarde l'objet modifié,
        // pas une écriture en base.
        when(salons.save(any(Salon.class))).thenAnswer(i -> i.getArgument(0));

        RepereGeoRepository reperes = mock(RepereGeoRepository.class);
        when(reperes.duQuartier(eq("Casablanca"), eq("Maarif")))
                .thenReturn(Optional.of(repere("Maarif", MAARIF_LAT, MAARIF_LNG)));
        when(reperes.duQuartier(eq("Casablanca"), eq("Gauthier")))
                .thenReturn(Optional.of(repere("Gauthier", GAUTHIER_LAT, GAUTHIER_LNG)));
        when(reperes.deLaVille(eq("Casablanca")))
                .thenReturn(Optional.of(repere(null, 33.5731, -7.5898)));

        CurrentUserService comptes = mock(CurrentUserService.class);
        when(comptes.getOrCreate()).thenReturn(new User());

        CustomPermissionEvaluator droits = mock(CustomPermissionEvaluator.class);
        // C'est la surcharge à un seul argument que verifierPeutGerer appelle.
        when(droits.peutGererSalon(anyLong())).thenReturn(true);

        service = new SalonService(salons, comptes, droits, new LocalisationService(reperes));
    }

    @Test
    @DisplayName("Un salon référencé sans coordonnées est placé au centre de son quartier")
    void repliSurLeQuartier() {
        Salon salon = enregistre(null, null, "Maarif");
        when(salons.findById(1L)).thenReturn(Optional.of(salon));

        service.updateSalon(1L, requete("Maarif", null, null));

        assertThat(salon.getLatitude()).isEqualTo(MAARIF_LAT);
        assertThat(salon.getLongitude()).isEqualTo(MAARIF_LNG);
    }

    @Test
    @DisplayName("Des coordonnées fournies remplacent le repli")
    void leReleveRemplace() {
        Salon salon = enregistre(MAARIF_LAT, MAARIF_LNG, "Maarif");
        when(salons.findById(1L)).thenReturn(Optional.of(salon));

        service.updateSalon(1L, requete("Maarif", RELEVE_LAT, RELEVE_LNG));

        assertThat(salon.getLatitude()).isEqualTo(RELEVE_LAT);
        assertThat(salon.getLongitude()).isEqualTo(RELEVE_LNG);
    }

    @Test
    @DisplayName("Une modification sans le champ ne perd pas le relevé")
    void leReleveSurvitAUnFormulaireIncomplet() {
        /*
         * Le cas qui a motivé ce test.
         *
         * La route est un PUT : le premier formulaire d'édition de fiche qui
         * oublierait ce champ ramènerait tous les salons relevés au centre de
         * leur quartier. Rien ne le signalerait — ni erreur, ni journal — et
         * la position exacte serait définitivement perdue.
         */
        Salon salon = enregistre(RELEVE_LAT, RELEVE_LNG, "Maarif");
        when(salons.findById(1L)).thenReturn(Optional.of(salon));

        service.updateSalon(1L, requete("Maarif", null, null));

        assertThat(salon.getLatitude())
                .as("le relevé doit survivre à une requête qui ne le porte pas")
                .isEqualTo(RELEVE_LAT);
        assertThat(salon.getLongitude()).isEqualTo(RELEVE_LNG);
    }

    @Test
    @DisplayName("Un déménagement écarte le relevé de l'ancienne adresse")
    void leDemenagementInvalideLeReleve() {
        // L'autre moitié de la règle : garder un point qui désigne l'adresse
        // d'avant serait pire que le perdre. Le salon apparaîtrait à des
        // kilomètres de là où il est, sans que personne sache pourquoi.
        Salon salon = enregistre(RELEVE_LAT, RELEVE_LNG, "Maarif");
        when(salons.findById(1L)).thenReturn(Optional.of(salon));

        service.updateSalon(1L, requete("Gauthier", null, null));

        assertThat(salon.getLatitude()).isEqualTo(GAUTHIER_LAT);
        assertThat(salon.getLongitude()).isEqualTo(GAUTHIER_LNG);
    }

    @Test
    @DisplayName("Un déménagement avec relevé garde le relevé fourni")
    void leDemenagementAvecReleve() {
        Salon salon = enregistre(RELEVE_LAT, RELEVE_LNG, "Maarif");
        when(salons.findById(1L)).thenReturn(Optional.of(salon));

        service.updateSalon(1L, requete("Gauthier", 33.5890, -7.6210));

        assertThat(salon.getLatitude()).isEqualTo(33.5890);
        assertThat(salon.getLongitude()).isEqualTo(-7.6210);
    }

    @Test
    @DisplayName("Une ville hors du référentiel laisse le salon non situé, sans échouer")
    void villeInconnue() {
        // Ni exception ni coordonnées inventées : le salon reste cherchable par
        // nom et par ville, seulement absent du classement par distance.
        Salon salon = enregistre(null, null, null);
        salon.setVille("Zagora");
        when(salons.findById(1L)).thenReturn(Optional.of(salon));

        SalonRequest requete = requete(null, null, null);
        requete.setVille("Zagora");
        service.updateSalon(1L, requete);

        assertThat(salon.getLatitude()).isNull();
        assertThat(salon.getLongitude()).isNull();
    }

    /* ---------- Fabriques ---------- */

    private static RepereGeo repere(String quartier, double lat, double lng) {
        return RepereGeo.builder().ville("Casablanca").quartier(quartier)
                .latitude(lat).longitude(lng).build();
    }

    private Salon enregistre(Double lat, Double lng, String quartier) {
        return Salon.builder()
                .id(1L).nom("Atlas Barber").adresse("8 Rue Al Massira")
                .ville("Casablanca").quartier(quartier)
                .latitude(lat).longitude(lng)
                .telephone("0655443322").categorie(SalonCategorie.BARBIER)
                .statut(SalonStatut.ACTIF).delaiAnnulationHeures(24)
                .build();
    }

    private SalonRequest requete(String quartier, Double lat, Double lng) {
        SalonRequest r = new SalonRequest();
        r.setNom("Atlas Barber");
        r.setAdresse("8 Rue Al Massira");
        r.setVille("Casablanca");
        r.setQuartier(quartier);
        r.setLatitude(lat);
        r.setLongitude(lng);
        r.setTelephone("0655443322");
        r.setCategorie(SalonCategorie.BARBIER);
        return r;
    }
}
