package com.example.booking.service;

import com.example.booking.dto.SalonRequest;
import com.example.booking.dto.SalonResponse;
import com.example.booking.geo.LocalisationService;
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
    private final LocalisationService localisation;

    public SalonService(SalonRepository salonRepository,
                        CurrentUserService currentUser,
                        CustomPermissionEvaluator droits,
                        LocalisationService localisation) {
        this.salonRepository = salonRepository;
        this.currentUser = currentUser;
        this.droits = droits;
        this.localisation = localisation;
    }

    /* ---------- Lecture ---------- */

    @Transactional(readOnly = true)
    public Optional<Salon> getSalonById(Long id) {
        return salonRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Page<SalonResponse> getAllSalons(Pageable pageable) {
        return salonRepository.findAll(pageable).map(SalonService::toResponse);
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
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
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
        // Sans point, le salon serait absent des recherches par proximité —
        // invisible pour le client, sans que le gérant sache pourquoi. Le
        // centre de son quartier vaut mieux que rien.
        localisation.situer(salon);

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
        // Le lieu est relevé avant d'être écrasé : c'est lui qui décide du sort
        // des coordonnées, quelques lignes plus bas.
        boolean lieuChange = !java.util.Objects.equals(salon.getVille(), request.getVille())
                || !java.util.Objects.equals(salon.getQuartier(), request.getQuartier());
        salon.setQuartier(request.getQuartier());
        /*
         * Coordonnées : la même règle que pour les métiers juste en dessous —
         * fournies, elles remplacent ; absentes, elles restent en place.
         *
         * Écraser sans condition serait un piège : cette route est un PUT, et
         * le premier formulaire d'édition de fiche qui oublierait ce champ
         * ramènerait tous les salons relevés au centre de leur quartier, sans
         * erreur ni trace. Un relevé se perd une fois et ne revient pas.
         *
         * Sauf si la ville ou le quartier changent : le point d'avant désigne
         * alors l'ancienne adresse. Le garder serait pire que le perdre — un
         * salon qui a déménagé apparaîtrait à des kilomètres de là où il est,
         * et personne ne saurait pourquoi. On l'efface, et le repli le replace
         * d'après le nouveau quartier.
         */
        if (request.getLatitude() != null && request.getLongitude() != null) {
            salon.setLatitude(request.getLatitude());
            salon.setLongitude(request.getLongitude());
        } else if (lieuChange) {
            salon.setLatitude(null);
            salon.setLongitude(null);
        }
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
        localisation.situer(salon);

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
        return salonRepository.findByStatutOrderByCreeLeAsc(statut, pageable).map(SalonService::toResponse);
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

    /**
     * Salon en réponse d'API. **Le seul mapper** — n'en réécrivez pas un second.
     *
     * Il en existait deux, identiques à la virgule près, ici et dans
     * PublicCatalogService. Chaque champ ajouté devait l'être aux deux, et
     * l'oubli ne se voyait pas : le champ sortait nul d'un côté, rempli de
     * l'autre, selon la route empruntée. Trois défauts en sont nés — les
     * métiers absents du catalogue public, les coordonnées jamais rendues, et
     * le référencement qui construisait son salon à part.
     *
     * Statique parce qu'il ne dépend d'aucun état : c'est ce qui permet au
     * service public de l'appeler sans dépendre de celui-ci.
     */
    public static SalonResponse toResponse(Salon s) {
        return SalonResponse.builder()
                .id(s.getId())
                .nom(s.getNom())
                .description(s.getDescription())
                .adresse(s.getAdresse())
                .ville(s.getVille())
                .quartier(s.getQuartier())
                .latitude(s.getLatitude())
                .longitude(s.getLongitude())
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
