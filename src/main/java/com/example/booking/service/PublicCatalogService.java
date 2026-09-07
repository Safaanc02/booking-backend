package com.example.booking.service;

import com.example.booking.dto.EmployeResponse;
import com.example.booking.dto.PrestationResponse;
import com.example.booking.dto.SalonDetailResponse;
import com.example.booking.dto.SalonResponse;
import com.example.booking.geo.Distance;
import com.example.booking.model.Employe;
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
import java.util.Comparator;
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

    public PublicCatalogService(SalonRepository salonRepository,
                                EmployeRepository employeRepository,
                                EmployePrestationRepository employePrestationRepository,
                                PrestationRepository prestationRepository) {
        this.salonRepository = salonRepository;
        this.employeRepository = employeRepository;
        this.employePrestationRepository = employePrestationRepository;
        this.prestationRepository = prestationRepository;
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

        return page.map(salon -> {
            SalonResponse reponse = toResponse(salon);
            reponse.setPrixMin(prix.get(salon.getId()));
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

    /* ---------- Mappers ---------- */

    private String vide(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private SalonResponse toResponse(Salon s) {
        return SalonResponse.builder()
                .id(s.getId()).nom(s.getNom()).description(s.getDescription())
                .adresse(s.getAdresse()).ville(s.getVille()).quartier(s.getQuartier())
                .telephone(s.getTelephone()).email(s.getEmail())
                .categorie(s.getCategorie() != null ? s.getCategorie().name() : null)
                .metiers(SalonService.metiersDe(s))
                .noteMoyenne(s.getNoteMoyenne())
                .nombreAvis(s.getNombreAvis())
                .statut(s.getStatut() != null ? s.getStatut().name() : null)
                .delaiAnnulationHeures(s.getDelaiAnnulationHeures())
                .ownerId(s.getOwner() != null ? s.getOwner().getId() : null)
                .build();
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
