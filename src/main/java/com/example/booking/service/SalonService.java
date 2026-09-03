package com.example.booking.service;

import com.example.booking.dto.SalonRequest;
import com.example.booking.dto.SalonResponse;
import com.example.booking.model.Salon;
import com.example.booking.model.User;
import com.example.booking.model.enums.SalonCategorie;
import com.example.booking.model.enums.SalonStatut;
import com.example.booking.config.CustomPermissionEvaluator;
import com.example.booking.repository.SalonRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@Transactional
public class SalonService {

    private final SalonRepository salonRepository;
    private final CurrentUserService currentUser;
    private final CustomPermissionEvaluator droits;

    public SalonService(SalonRepository salonRepository,
                        CurrentUserService currentUser,
                        CustomPermissionEvaluator droits) {
        this.salonRepository = salonRepository;
        this.currentUser = currentUser;
        this.droits = droits;
    }

    /* ---------- Lecture ---------- */

    @Transactional(readOnly = true)
    public Optional<Salon> getSalonById(Long id) {
        return salonRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Page<SalonResponse> getAllSalons(Pageable pageable) {
        return salonRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * Salons que le compte connecté peut administrer : les siens, et ceux
     * dont la gestion lui a été déléguée.
     */
    @Transactional(readOnly = true)
    public List<SalonResponse> mesSalons() {
        /*
         * On lit le « sub » du jeton, sans passer par getOrCreate().
         *
         * getOrCreate() insère le miroir de l'utilisateur s'il manque — une
         * écriture, impossible dans une transaction en lecture seule. Cela ne
         * se voyait pas tant que le filtre de synchronisation avait déjà créé
         * le miroir plus tôt dans la requête ; le jour où il échouait, cette
         * lecture répondait 500.
         */
        String keycloakId = currentUser.currentKeycloakId()
                .orElseThrow(() -> new SecurityException("Authentification requise"));

        return salonRepository.queJePeuxGerer(keycloakId).stream()
                .map(salon -> {
                    SalonResponse r = toResponse(salon);
                    boolean proprietaire = salon.getOwner() != null
                            && keycloakId.equals(salon.getOwner().getKeycloakId());
                    r.setMonRole(proprietaire ? "PROPRIETAIRE" : "GESTIONNAIRE");
                    return r;
                })
                .toList();
    }

    /* ---------- Écriture ---------- */

    /**
     * Un salon naît EN_ATTENTE : il n'apparaît pas dans la recherche publique
     * avant validation par la plateforme.
     */
    public SalonResponse createSalon(SalonRequest request) {
        User owner = currentUser.getOrCreate();

        Salon salon = Salon.builder()
                .nom(request.getNom())
                .description(request.getDescription())
                .adresse(request.getAdresse())
                .ville(request.getVille())
                .quartier(request.getQuartier())
                .telephone(request.getTelephone())
                .email(request.getEmail())
                .categorie(request.getCategorie() != null ? request.getCategorie() : SalonCategorie.COIFFURE)
                .statut(SalonStatut.EN_ATTENTE)
                .delaiAnnulationHeures(request.getDelaiAnnulationHeures() != null
                        ? request.getDelaiAnnulationHeures() : 24)
                .owner(owner)
                .metiers(request.getMetiers() != null
                        ? new java.util.LinkedHashSet<>(request.getMetiers())
                        : new java.util.LinkedHashSet<>())
                .build();
        // Le métier principal fait partie des métiers exercés : sans cela, un
        // salon serait absent du filtre correspondant à sa propre couleur.
        salon.normaliserMetiers();

        return toResponse(salonRepository.save(salon));
    }

    public Optional<SalonResponse> updateSalon(Long id, SalonRequest request) {
        Salon salon = charger(id);
        // Modification : le gestionnaire délégué y a droit comme le propriétaire.
        verifierPeutGerer(salon);

        salon.setNom(request.getNom());
        salon.setDescription(request.getDescription());
        salon.setAdresse(request.getAdresse());
        salon.setVille(request.getVille());
        salon.setQuartier(request.getQuartier());
        salon.setTelephone(request.getTelephone());
        salon.setEmail(request.getEmail());
        if (request.getCategorie() != null) salon.setCategorie(request.getCategorie());
        // Une liste vide efface, une liste absente laisse en place : le
        // formulaire du back-office envoie toujours la sélection complète,
        // tandis qu'un appel partiel ne doit pas vider les métiers par
        // omission.
        if (request.getMetiers() != null) {
            salon.setMetiers(new java.util.LinkedHashSet<>(request.getMetiers()));
        }
        salon.normaliserMetiers();
        if (request.getDelaiAnnulationHeures() != null) {
            salon.setDelaiAnnulationHeures(request.getDelaiAnnulationHeures());
        }

        return Optional.of(toResponse(salonRepository.save(salon)));
    }

    /** Réservé à l'administration — la validation d'un salon n'appartient pas au professionnel. */
    public SalonResponse changerStatut(Long id, SalonStatut statut) {
        Salon salon = charger(id);
        salon.setStatut(statut);
        return toResponse(salonRepository.save(salon));
    }

    @Transactional(readOnly = true)
    public Page<SalonResponse> parStatut(SalonStatut statut, Pageable pageable) {
        return salonRepository.findByStatutOrderByCreeLeAsc(statut, pageable).map(this::toResponse);
    }

    public void deleteSalon(Long id) {
        Salon salon = charger(id);
        // Suppression : propriétaire seulement. Déléguer la gestion d'une
        // boutique ne doit pas donner le droit de l'effacer.
        verifierProprietaireStrict(salon);
        salonRepository.delete(salon);
    }

    /* ---------- Helpers ---------- */

    private Salon charger(Long id) {
        return salonRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Salon introuvable : " + id));
    }

    /**
     * Second rempart derrière le @PreAuthorize du contrôleur : celui-ci peut
     * être oublié sur une nouvelle route, le service est le dernier point de
     * passage. Délègue à l'évaluateur plutôt que de refaire le calcul —
     * dupliquer cette règle a déjà produit un bug.
     */
    private void verifierPeutGerer(Salon salon) {
        if (!droits.peutGererSalon(salon.getId())) {
            throw new SecurityException("Vous n'avez pas les droits de gestion sur ce salon");
        }
    }

    /**
     * Propriétaire ou administrateur, à l'exclusion des gestionnaires délégués.
     *
     * Confier la gestion d'une boutique ne doit pas donner le droit de
     * l'effacer : c'est la seule opération que la délégation n'emporte pas.
     */
    private void verifierProprietaireStrict(Salon salon) {
        if (currentUser.isAdmin()) return;

        String keycloakId = currentUser.currentKeycloakId().orElse(null);
        boolean proprietaire = salon.getOwner() != null
                && keycloakId != null
                && keycloakId.equals(salon.getOwner().getKeycloakId());

        if (!proprietaire) {
            throw new SecurityException("Seul le propriétaire peut supprimer ce salon");
        }
    }

    /**
     * Les métiers d'un salon, le principal en tête.
     *
     * L'ordre n'est pas cosmétique : c'est celui de l'affichage, et le métier
     * principal est celui dont le salon porte la couleur. Le voir ailleurs
     * qu'en premier laisserait croire à une hiérarchie différente.
     */
    static java.util.List<String> metiersDe(Salon s) {
        java.util.LinkedHashSet<String> ordonnes = new java.util.LinkedHashSet<>();
        if (s.getCategorie() != null) ordonnes.add(s.getCategorie().name());
        if (s.getMetiers() != null) {
            s.getMetiers().stream().map(Enum::name).sorted().forEach(ordonnes::add);
        }
        return java.util.List.copyOf(ordonnes);
    }

    public SalonResponse toResponse(Salon s) {
        return SalonResponse.builder()
                .id(s.getId())
                .nom(s.getNom())
                .description(s.getDescription())
                .adresse(s.getAdresse())
                .ville(s.getVille())
                .quartier(s.getQuartier())
                .telephone(s.getTelephone())
                .email(s.getEmail())
                .categorie(s.getCategorie() != null ? s.getCategorie().name() : null)
                .metiers(metiersDe(s))
                .noteMoyenne(s.getNoteMoyenne())
                .nombreAvis(s.getNombreAvis())
                .statut(s.getStatut() != null ? s.getStatut().name() : null)
                .delaiAnnulationHeures(s.getDelaiAnnulationHeures())
                .ownerId(s.getOwner() != null ? s.getOwner().getId() : null)
                .build();
    }
}
