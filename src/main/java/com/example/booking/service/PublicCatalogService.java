package com.example.booking.service;

import com.example.booking.dto.EmployeResponse;
import com.example.booking.dto.PrestationResponse;
import com.example.booking.dto.ProchaineDispo;
import com.example.booking.dto.SalonDetailResponse;
import com.example.booking.dto.SalonResponse;
import com.example.booking.geo.Distance;
import com.example.booking.model.Employe;
import com.example.booking.photos.PhotoService;
import com.example.booking.model.EmployePrestation;
import com.example.booking.model.Prestation;
import com.example.booking.model.Salon;
import com.example.booking.model.enums.SalonCategorie;
import com.example.booking.model.enums.SalonStatut;
import com.example.booking.repository.EmployePrestationRepository;
import com.example.booking.repository.EmployeRepository;
import com.example.booking.repository.PrestationRepository;
import com.example.booking.repository.SalonRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/** Lectures du catalogue exposées sans authentification. Seuls les salons ACTIF sont visibles. */
@Service
@Transactional(readOnly = true)
public class PublicCatalogService {

    private final SalonRepository salonRepository;
    private final EmployeRepository employeRepository;
    private final EmployePrestationRepository employePrestationRepository;
    private final PrestationRepository prestationRepository;
    private final PhotoService photoService;
    /**
     * Le moteur de disponibilité, pour annoncer un premier créneau dès la
     * liste. Sens unique : le moteur ignore le catalogue, il n'y a pas de
     * cycle.
     */
    private final DisponibiliteService disponibilites;

    public PublicCatalogService(SalonRepository salonRepository,
                                EmployeRepository employeRepository,
                                EmployePrestationRepository employePrestationRepository,
                                PrestationRepository prestationRepository,
                                DisponibiliteService disponibilites,
                                PhotoService photoService) {
        this.salonRepository = salonRepository;
        this.employeRepository = employeRepository;
        this.employePrestationRepository = employePrestationRepository;
        this.prestationRepository = prestationRepository;
        this.disponibilites = disponibilites;
        this.photoService = photoService;
    }

    /**
     * Recherche publique, prix d'entrée compris.
     *
     * Le prix est réclamé en une requête pour toute la page, après la
     * pagination : une carte sans tarif n'aide personne à choisir, et le
     * contrat d'API l'annonçait déjà sans que rien ne le fournisse.
     */
    public Page<SalonResponse> rechercher(String ville, String q,
                                         SalonCategorie metier, Pageable pageable) {
        return rechercher(ville, q, metier, null, pageable);
    }

    /**
     * Recherche publique, éventuellement autour d'un point.
     *
     * {@code autour} nul : recherche ordinaire, les salons remontent dans
     * l'ordre de la base. {@code autour} renseigné : seuls les salons situés
     * dans le rayon remontent, du plus proche au plus lointain, chacun avec sa
     * distance.
     */
    public Page<SalonResponse> rechercher(String ville, String q, SalonCategorie metier,
                                          Proximite autour, Pageable pageable) {
        Page<Salon> page = autour == null
                ? salonRepository.rechercher(
                        SalonStatut.ACTIF, vide(ville), vide(q), metier, pageable)
                : salonRepository.rechercherAutour(
                        SalonStatut.ACTIF.name(), vide(ville),
                        autour.latitude(), autour.longitude(),
                        autour.latitude() - Distance.degresLatitude(autour.rayonKm()),
                        autour.latitude() + Distance.degresLatitude(autour.rayonKm()),
                        autour.longitude() - Distance.degresLongitude(autour.rayonKm(), autour.latitude()),
                        autour.longitude() + Distance.degresLongitude(autour.rayonKm(), autour.latitude()),
                        autour.rayonKm(), vide(q),
                        metier != null ? metier.name() : null,
                        pageable);

        List<Long> ids = page.getContent().stream().map(Salon::getId).toList();
        Map<Long, BigDecimal> prix = ids.isEmpty() ? Map.of()
                : prestationRepository.prixDEntreeParSalon(ids).stream()
                        .collect(Collectors.toMap(
                                ligne -> (Long) ligne[0],
                                ligne -> (BigDecimal) ligne[1]));

        // Les couvertures en un appel pour toute la page, comme les prix : vingt
        // cartes, vingt requêtes pour une image chacune, sur la route la plus
        // consultée du produit.
        Map<Long, List<String>> photos = photoService.parSalon(ids);

        return page.map(salon -> {
            SalonResponse reponse = toResponse(salon);
            reponse.setPrixMin(prix.get(salon.getId()));
            reponse.setPhotos(urls(photos.getOrDefault(salon.getId(), List.of())));
            if (autour != null && salon.getLatitude() != null && salon.getLongitude() != null) {
                // Recalculée ici plutôt que rapportée par la requête : la
                // projection native reste ainsi exactement l'entité, sans
                // colonne surnuméraire à mapper. Même formule des deux côtés.
                reponse.setDistanceKm(Distance.km(
                        autour.latitude(), autour.longitude(),
                        salon.getLatitude(), salon.getLongitude()));
            }
            return reponse;
        });
    }

    /**
     * Premier créneau libre de chacun des salons demandés.
     *
     * Le prix figurait déjà dans la liste de résultats, la disponibilité non :
     * on ouvrait les fiches une par une pour découvrir que la première est
     * complète jusqu'à jeudi. Le contrat d'API l'annonçait depuis le début.
     *
     * Calculé en un appel pour toute la page, et non par carte. Vingt cartes
     * qui interrogent chacune le moteur, c'est vingt allers-retours sur la
     * route la plus consultée du produit — et une liste qui se remplit par
     * saccades.
     *
     * L'horizon est court, sept jours par défaut. Au-delà, « prochaine
     * disponibilité » cesse d'être un argument : un salon libre dans douze
     * jours est un salon complet, et le dire ainsi vaut mieux que d'annoncer
     * une date que personne n'attendra. La boucle s'arrête au premier jour
     * trouvé, si bien que le cas courant — libre aujourd'hui ou demain — ne
     * coûte qu'un ou deux calculs.
     */
    public Map<Long, ProchaineDispo> prochainesDispos(Collection<Long> salonIds, int jours) {
        if (salonIds.isEmpty()) return Map.of();

        Map<Long, Long> vitrines = prestationRepository.prestationVitrineParSalon(salonIds).stream()
                .collect(Collectors.toMap(l -> (Long) l[0], l -> (Long) l[1]));

        Map<Long, ProchaineDispo> resultat = new LinkedHashMap<>();
        for (Long salonId : salonIds) {
            Long prestationId = vitrines.get(salonId);
            // Catalogue vide : le salon vient d'être référencé et son
            // paramétrage n'est pas terminé. Rien à annoncer, et surtout pas
            // « complet », qui laisserait croire à un agenda plein.
            if (prestationId == null) continue;

            List<LocalDate> ouverts = disponibilites.prochainsJoursDisponibles(
                    salonId, prestationId, null, 1);
            if (ouverts.isEmpty()) continue;

            LocalDate jour = ouverts.get(0);
            // Au-delà de l'horizon, on préfère ne rien dire : voir la section
            // ci-dessus. prochainsJoursDisponibles balaie l'horizon complet du
            // moteur, plus large que celui d'une liste de résultats.
            if (jour.isAfter(LocalDate.now(disponibilites.zone()).plusDays(jours))) continue;

            disponibilites.creneaux(salonId, prestationId, jour, null).stream().findFirst()
                    .ifPresent(c -> resultat.put(salonId, new ProchaineDispo(jour, c.heure())));
        }
        return resultat;
    }

    /**
     * Salons correspondant aux mêmes critères mais qu'aucun repère ne situe.
     *
     * Ils sont absents d'un classement par distance, faute de point. Le nombre
     * est remonté à l'interface pour qu'elle le dise : sans cela, ouvrir une
     * ville hors du référentiel ferait disparaître ses salons de « autour de
     * moi » sans que personne s'en aperçoive.
     */
    public long compterNonSitues(String ville, String q, SalonCategorie metier) {
        return salonRepository.compterNonSitues(SalonStatut.ACTIF, vide(ville), vide(q), metier);
    }

    /** Point de référence et rayon d'une recherche par proximité. */
    public record Proximite(double latitude, double longitude, double rayonKm) {
    }

    public SalonDetailResponse fiche(Long id) {
        Salon salon = salonRepository.findById(id)
                .filter(s -> s.getStatut() == SalonStatut.ACTIF)
                .orElseThrow(() -> new NoSuchElementException("Salon introuvable : " + id));

        List<PrestationResponse> prestations = salon.getPrestations().stream()
                .filter(Prestation::isActif)
                .sorted(Comparator.comparingInt(Prestation::getOrdre).thenComparing(Prestation::getNom))
                .map(this::toResponse)
                .toList();

        List<EmployeResponse> employes = employeRepository
                .findBySalonIdAndActifTrueOrderByOrdreAscPrenomAsc(id).stream()
                .map(e -> toResponse(e, null))
                .toList();

        return SalonDetailResponse.builder()
                .id(salon.getId())
                .nom(salon.getNom())
                .description(salon.getDescription())
                .adresse(salon.getAdresse())
                .ville(salon.getVille())
                .quartier(salon.getQuartier())
                .telephone(salon.getTelephone())
                .email(salon.getEmail())
                .categorie(salon.getCategorie() != null ? salon.getCategorie().name() : null)
                .metiers(SalonService.metiersDe(salon))
                .photos(urls(photoService.duSalon(id).stream()
                        .map(com.example.booking.model.PhotoSalon::getFichier).toList()))
                .noteMoyenne(salon.getNoteMoyenne())
                .nombreAvis(salon.getNombreAvis())
                .delaiAnnulationHeures(salon.getDelaiAnnulationHeures())
                .prestations(prestations)
                .employes(employes)
                .build();
    }

    /** Praticiens sachant réaliser une prestation, avec leur durée effective. */
    public List<EmployeResponse> employesPour(Long salonId, Long prestationId) {
        return employeRepository.findCapables(salonId, prestationId).stream()
                .map(e -> {
                    Integer duree = employePrestationRepository
                            .findByEmployeIdAndPrestationId(e.getId(), prestationId)
                            .map(EmployePrestation::dureeEffective)
                            .orElse(null);
                    return toResponse(e, duree);
                })
                .toList();
    }

    public List<String> villes() {
        return salonRepository.villesDistinctes(SalonStatut.ACTIF);
    }

    /**
     * Noms de fichiers vers adresses servables.
     *
     * Relatives, et non absolues. Le site tourne derrière une seule adresse en
     * production, plusieurs en développement, et un tunnel change la sienne à
     * chaque ouverture — une adresse absolue enregistrée ou mise en cache
     * pointerait tôt ou tard vers un hôte qui n'existe plus.
     */
    private static List<String> urls(List<String> fichiers) {
        return fichiers.stream().map(f -> "/api/public/photos/" + f).toList();
    }

    /* ---------- Mappers ---------- */

    private String vide(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /**
     * Délègue au mapper de SalonService, qui est le seul.
     *
     * Ce service en avait une copie mot pour mot. Deux mappers pour un même
     * objet, c'est un champ ajouté d'un côté et oublié de l'autre, et une API
     * qui répond différemment selon la route empruntée — sans erreur, sans
     * trace. C'est arrivé trois fois.
     */
    private SalonResponse toResponse(Salon s) {
        return SalonService.toResponse(s);
    }

    private PrestationResponse toResponse(Prestation p) {
        return PrestationResponse.builder()
                .id(p.getId()).nom(p.getNom()).description(p.getDescription())
                .categorie(p.getCategorie())
                .prix(p.getPrix()).dureeMinutes(p.getDureeMinutes())
                .salonId(p.getSalon() != null ? p.getSalon().getId() : null)
                .build();
    }

    private EmployeResponse toResponse(Employe e, Integer dureeMinutes) {
        // Le rôle et le compte rattaché ne sortent pas côté public : ils
        // n'intéressent que le salon.
        return new EmployeResponse(e.getId(), e.getPrenom(), e.getNom(),
                e.getTitre(), e.getPhotoUrl(), dureeMinutes, null, null);
    }
}
