package com.example.booking.service;

import com.example.booking.dto.DemandeDemoRequest;
import com.example.booking.dto.DemandeDemoResponse;
import com.example.booking.model.DemandeDemo;
import com.example.booking.model.Salon;
import com.example.booking.model.User;
import com.example.booking.model.enums.StatutDemandeDemo;
import com.example.booking.repository.DemandeDemoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Demandes de démonstration : la file d'attente commerciale.
 *
 * Le formulaire est public et ne crée aucun compte. Deux gardes valent d'être
 * signalés : le débit est déjà limité par IP sur /api/public/** (voir
 * RateLimitFilter), et une même adresse ne peut pas redéposer dans la journée
 * — sans quoi la table se remplit d'elle-même au premier robot venu.
 */
@Service
public class DemandeDemoService {

    private static final Logger log = LoggerFactory.getLogger(DemandeDemoService.class);

    /**
     * Fenêtre du garde anti-doublon.
     *
     * Vingt-quatre heures et non une éternité : un gérant qui rappelle le
     * lendemain parce que personne ne l'a contacté est un signal, pas un
     * doublon à jeter.
     */
    private static final Duration FENETRE_DOUBLON = Duration.ofHours(24);

    private final DemandeDemoRepository depot;

    public DemandeDemoService(DemandeDemoRepository depot) {
        this.depot = depot;
    }

    @Transactional
    public DemandeDemo enregistrer(DemandeDemoRequest r) {
        String email = r.getEmail().trim();

        if (depot.dejaDemandeDepuis(email, Instant.now().minus(FENETRE_DOUBLON))) {
            // On ne dit pas au visiteur qu'une demande existe déjà pour cette
            // adresse : la route est publique, et ce serait un moyen de tester
            // qui s'est inscrit. Le message reste celui du succès, côté
            // contrôleur, et rien n'est écrit.
            log.info("Demande de démonstration ignorée, doublon récent : {}", email);
            return null;
        }

        DemandeDemo demande = depot.save(DemandeDemo.builder()
                .nomEtablissement(r.getNomEtablissement().trim())
                .typeEtablissement(r.getTypeEtablissement())
                .specialite(vide(r.getSpecialite()))
                .ville(r.getVille().trim())
                .quartier(vide(r.getQuartier()))
                .anciennete(r.getAnciennete())
                .nombreCollaborateurs(r.getNombreCollaborateurs())
                .proprietaireLocal(r.getProprietaireLocal())
                .outilActuel(vide(r.getOutilActuel()))
                .prenom(r.getPrenom().trim())
                .nom(vide(r.getNom()))
                .telephone(r.getTelephone().trim())
                .email(email)
                .ice(vide(r.getIce()))
                .message(vide(r.getMessage()))
                .statut(StatutDemandeDemo.NOUVELLE)
                .build());

        log.info("Demande de démonstration #{} : {} à {} ({}, {})",
                demande.getId(), demande.getNomEtablissement(), demande.getVille(),
                demande.getTypeEtablissement(), demande.getAnciennete());
        return demande;
    }

    @Transactional(readOnly = true)
    public Page<DemandeDemoResponse> lister(StatutDemandeDemo statut, Pageable pageable) {
        return depot.parStatut(statut, pageable).map(DemandeDemoService::versReponse);
    }

    @Transactional(readOnly = true)
    public long compter(StatutDemandeDemo statut) {
        return depot.countByStatut(statut);
    }

    /**
     * Change le statut d'une demande, et note qui l'a fait.
     *
     * `note` est facultative et remplace la précédente quand elle est fournie :
     * un conseiller qui reprend un dossier trois semaines plus tard a besoin de
     * savoir ce qui s'est dit, pas de deviner.
     */
    @Transactional
    public DemandeDemoResponse changerStatut(Long id, StatutDemandeDemo statut,
                                            String note, User agent) {
        DemandeDemo demande = depot.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Demande introuvable"));

        demande.setStatut(statut);
        if (note != null && !note.isBlank()) demande.setNoteInterne(note.trim());
        demande.setTraiteLe(Instant.now());
        demande.setTraitePar(agent);
        return versReponse(depot.save(demande));
    }

    /**
     * Referme la boucle : la demande qui a mené à ce salon est marquée convertie.
     *
     * Sans cela, la colonne salon_id resterait décorative et le rendement du
     * formulaire — combien de demandes deviennent des salons — ne serait pas
     * mesurable. Un référencement qui ne vient d'aucune demande, cas du
     * démarchage direct, ne rattache rien : c'est normal, pas une erreur.
     *
     * @return l'identifiant de la demande convertie, s'il y en avait une
     */
    @Transactional
    public Optional<Long> rattacherAuSalon(String email, Salon salon, User agent) {
        return depot.ouvertePour(email).map(demande -> {
            demande.setSalon(salon);
            demande.setStatut(StatutDemandeDemo.CONVERTIE);
            demande.setTraiteLe(Instant.now());
            demande.setTraitePar(agent);
            depot.save(demande);
            log.info("Demande #{} convertie : salon {} référencé", demande.getId(), salon.getNom());
            return demande.getId();
        });
    }

    private static String vide(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static DemandeDemoResponse versReponse(DemandeDemo d) {
        return new DemandeDemoResponse(
                d.getId(), d.getNomEtablissement(), d.getTypeEtablissement(),
                d.getSpecialite(), d.getVille(), d.getQuartier(),
                d.getAnciennete(), d.getNombreCollaborateurs(), d.getProprietaireLocal(),
                d.getOutilActuel(), d.getPrenom(), d.getNom(), d.getTelephone(),
                d.getEmail(), d.getIce(), d.getMessage(),
                d.getStatut(), d.getNoteInterne(),
                d.getSalon() != null ? d.getSalon().getId() : null,
                d.getSalon() != null ? d.getSalon().getNom() : null,
                d.getCreeLe(), d.getTraiteLe(),
                d.getTraitePar() != null ? d.getTraitePar().getUsername() : null);
    }
}
