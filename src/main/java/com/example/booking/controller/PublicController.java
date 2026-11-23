package com.example.booking.controller;

import com.example.booking.dto.ApercuAnnulation;
import com.example.booking.dto.AvisResponse;
import com.example.booking.dto.CreneauDisponible;
import com.example.booking.dto.DemandeDemoConfirmation;
import com.example.booking.dto.DemandeDemoRequest;
import com.example.booking.dto.DisponibilitesResponse;
import com.example.booking.dto.EmployeResponse;
import com.example.booking.dto.ProchaineDispo;
import com.example.booking.dto.SalonDetailResponse;
import com.example.booking.dto.SalonResponse;
import com.example.booking.dto.SalonPublicResponse;
import com.example.booking.dto.CreneauxDuJour;
import com.example.booking.dto.HoraireResponse;
import com.example.booking.config.OrigineRequete;
import com.example.booking.model.enums.SalonCategorie;
import com.example.booking.service.DemandeDemoService;
import com.example.booking.service.DisponibiliteService;
import com.example.booking.service.AvisService;
import com.example.booking.service.ReservationService;
import com.example.booking.service.PublicCatalogService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Parcours de découverte, sans authentification.
 *
 * Un visiteur doit pouvoir chercher, comparer et consulter les disponibilités
 * avant de créer un compte : l'inscription imposée trop tôt est le premier
 * facteur d'abandon du tunnel. La connexion n'intervient qu'à la confirmation.
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final PublicCatalogService catalogue;
    private final DisponibiliteService disponibilites;

    private final AvisService avis;
    private final ReservationService reservations;
    private final DemandeDemoService demandes;
    /** L'adresse publique du site, pour les balises de partage et le plan du site. */
    private final String urlPublique;

    public PublicController(PublicCatalogService catalogue,
                            DisponibiliteService disponibilites,
                            AvisService avis,
                            ReservationService reservations,
                            DemandeDemoService demandes,
                            @org.springframework.beans.factory.annotation.Value("${app.url-publique}") String urlPublique) {
        this.catalogue = catalogue;
        this.disponibilites = disponibilites;
        this.avis = avis;
        this.reservations = reservations;
        this.demandes = demandes;
        this.urlPublique = urlPublique.replaceAll("/+$", "");
    }

    /* ---------- Demande de démonstration ---------- */

    /**
     * Un professionnel demande à être rappelé.
     *
     * Rien n'est créé côté comptes : c'est une prise de contact. La réponse
     * est volontairement identique qu'on ait enregistré la demande ou écarté
     * un doublon récent — la route est publique, et distinguer les deux cas
     * donnerait le moyen de savoir qui s'est déjà manifesté.
     *
     * Le débit est limité par IP en amont, dans RateLimitFilter.
     */
    @PostMapping("/demandes-demo")
    public ResponseEntity<DemandeDemoConfirmation> demanderDemo(
            @Valid @RequestBody DemandeDemoRequest requete,
            jakarta.servlet.http.HttpServletRequest http) {
        demandes.enregistrer(requete, OrigineRequete.de(http));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new DemandeDemoConfirmation(
                "Votre demande est enregistrée. Un conseiller vous rappelle sous 48 heures "
                        + "pour convenir d'une installation."));
    }

    /* ---------- Annulation depuis un email ---------- */

    /** Ce que le porteur du lien voit avant de confirmer. Ne modifie rien. */
    @GetMapping("/reservations/apercu")
    public ResponseEntity<ApercuAnnulation> apercuAnnulation(@RequestParam String token) {
        // Pas de cache : l'état du rendez-vous peut changer entre deux consultations.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(reservations.apercuParJeton(token));
    }

    /**
     * Annulation effective.
     *
     * ⚠️ En POST, jamais en GET, et ce n'est pas une question de pureté REST :
     * les clients de messagerie, antivirus et aperçus de liens préchargent les
     * URL en GET. Un lien d'annulation en GET verrait des rendez-vous annulés
     * sans que personne n'ait cliqué.
     */
    @PostMapping("/reservations/annuler")
    public ResponseEntity<ApercuAnnulation> annulerParLien(@RequestParam String token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(reservations.annulerParJeton(token));
    }

    /** Avis publiés d'un salon, du plus récent au plus ancien. */
    @GetMapping("/salons/{id}/avis")
    public ResponseEntity<Page<AvisResponse>> avis(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return cache60(avis.parSalon(id, PageRequest.of(page, Math.min(size, 50))));
    }

    /**
     * Recherche publique.
     *
     * `metier` filtre sur l'ensemble des métiers exercés, pas sur la seule
     * catégorie principale : un institut déclaré en coiffure qui fait aussi
     * les ongles remonte bien sous « Onglerie ».
     */
    @GetMapping("/salons")
    public ResponseEntity<Page<SalonPublicResponse>> rechercher(
            @RequestParam(required = false) String ville,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) SalonCategorie metier,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Integer rayon,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        if ((lat == null) != (lng == null)) {
            throw new IllegalArgumentException(
                    "Renseignez lat et lng ensemble : une coordonnée seule ne situe rien");
        }
        if (lat == null) {
            return cache60(catalogue.rechercher(ville, q, metier, pageable));
        }
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new IllegalArgumentException("Coordonnées hors du globe");
        }

        double rayonKm = rayon == null ? RAYON_DEFAUT_KM
                : Math.min(RAYON_MAX_KM, Math.max(1, rayon));
        var autour = new PublicCatalogService.Proximite(lat, lng, rayonKm);

        // Pas de cache partagé sur une recherche située : l'URL porte la
        // position de la personne qui cherche. Un proxy la garderait, et les
        // journaux d'accès avec. C'est aussi la réponse la moins réutilisable
        // du service — deux visiteurs ne sont jamais au même endroit.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header(EN_TETE_NON_SITUES,
                        String.valueOf(catalogue.compterNonSitues(ville, q, metier)))
                .body(catalogue.rechercher(ville, q, metier, autour, pageable));
    }

    /**
     * Premier créneau libre de plusieurs salons, en un appel.
     *
     * Route séparée de la recherche, et non un champ de plus dans ses
     * résultats. Trois raisons.
     *
     * La recherche doit rester rapide : elle décide de l'affichage de la
     * page, alors que la disponibilité n'enrichit que des cartes déjà
     * visibles. Les mêler ferait attendre la liste entière pour un
     * complément.
     *
     * Leurs durées de vie diffèrent : un catalogue bouge rarement, un agenda
     * à chaque réservation. Une réponse unique se cacherait au rythme du plus
     * volatil des deux.
     *
     * Et la recherche sert aussi là où la disponibilité n'a rien à faire —
     * une page d'administration, un export.
     */
    @GetMapping("/salons/prochaines-dispos")
    public ResponseEntity<Map<Long, ProchaineDispo>> prochainesDispos(
            @RequestParam List<Long> ids,
            @RequestParam(defaultValue = "7") int jours
    ) {
        // Borné : la route calcule, elle ne se contente pas de lire. Sans
        // plafond, une liste d'identifiants suffisamment longue occuperait le
        // moteur de disponibilité pour toute une page de résultats d'un autre.
        if (ids.size() > 50) {
            throw new IllegalArgumentException("Au plus 50 salons par appel");
        }
        return cache60(catalogue.prochainesDispos(ids, Math.min(Math.max(jours, 1), 30)));
    }

    @GetMapping("/salons/{id}")
    public ResponseEntity<SalonDetailResponse> fiche(@PathVariable Long id) {
        return cache60(catalogue.fiche(id));
    }

    @GetMapping("/salons/{id}/employes")
    public ResponseEntity<List<EmployeResponse>> employes(
            @PathVariable Long id,
            @RequestParam Long prestationId
    ) {
        return cache60(catalogue.employesPour(id, prestationId));
    }

    /** La route la plus appelée du produit. */
    @GetMapping("/salons/{id}/disponibilites")
    public ResponseEntity<DisponibilitesResponse> creneaux(
            @PathVariable Long id,
            @RequestParam Long prestationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long employeId
    ) {
        List<CreneauDisponible> creneaux = disponibilites.creneaux(id, prestationId, date, employeId);
        // Cache court : un créneau peut être pris à tout moment, mais 60 s
        // absorbent les rafales d'un même visiteur qui compare les jours.
        return cache60(new DisponibilitesResponse(date, id, null, creneaux));
    }

    /**
     * Les prochains jours ayant au moins un créneau libre.
     * Évite au visiteur de cliquer jour après jour sur un agenda vide.
     */
    @GetMapping("/salons/{id}/prochaines-dispos")
    public ResponseEntity<List<LocalDate>> prochainsJours(
            @PathVariable Long id,
            @RequestParam Long prestationId,
            @RequestParam(required = false) Long employeId,
            @RequestParam(defaultValue = "7") int jours
    ) {
        return cache60(disponibilites.prochainsJoursDisponibles(id, prestationId, employeId, Math.min(jours, 30)));
    }

    /** Les horaires d'ouverture d'un salon. Une cliente a le droit de savoir quand venir. */
    @GetMapping("/salons/{id}/horaires")
    public ResponseEntity<List<HoraireResponse>> horaires(@PathVariable Long id) {
        return cache60(catalogue.horairesPublics(id));
    }

    /**
     * Les balises de partage d'une fiche, en fragment HTML.
     *
     * Le site est une application : son index.html est le même pour toutes
     * les pages, et un lien de salon partagé sur WhatsApp n'avait ni image ni
     * titre — au Maroc, c'est le premier canal de recommandation. Caddy
     * inclut ce fragment dans l'en-tête de la page servie sous /salon/*.
     *
     * Toujours 200, même pour un salon inconnu : ce fragment est injecté dans
     * du HTML, et un corps d'erreur JSON y ferait une page cassée.
     */
    @GetMapping(value = "/salons/{id}/og", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> balisesDePartage(@PathVariable String id) {
        String titre = "DarZin — Réservez votre salon de beauté";
        String description = "Réservez en ligne coiffure, barbier, onglerie et hammam partout au Maroc.";
        String image = urlPublique + "/logo-darzin-complet.png";
        String url = urlPublique + "/salon/" + id;
        try {
            SalonDetailResponse salon = catalogue.fiche(Long.parseLong(id));
            titre = salon.getNom() + " · " + (salon.getVille() != null ? salon.getVille() : "DarZin");
            String lieu = java.util.stream.Stream.of(salon.getQuartier(), salon.getVille())
                    .filter(v -> v != null && !v.isBlank()).collect(java.util.stream.Collectors.joining(", "));
            description = (salon.getDescription() != null && !salon.getDescription().isBlank()
                    ? salon.getDescription()
                    : "Réservez en ligne chez " + salon.getNom() + (lieu.isEmpty() ? "" : " à " + lieu) + ".");
            if (!salon.getPhotos().isEmpty()) {
                String premiere = salon.getPhotos().get(0);
                image = premiere.startsWith("http") ? premiere : urlPublique + premiere;
            }
        } catch (RuntimeException ignore) {
            /* Salon inconnu ou identifiant mal formé : balises génériques. */
        }
        String html = "<title>" + echapper(titre) + "</title>\n"
                + "<meta property=\"og:type\" content=\"website\">\n"
                + "<meta property=\"og:title\" content=\"" + echapper(titre) + "\">\n"
                + "<meta property=\"og:description\" content=\"" + echapper(description) + "\">\n"
                + "<meta property=\"og:image\" content=\"" + echapper(image) + "\">\n"
                + "<meta property=\"og:url\" content=\"" + echapper(url) + "\">\n"
                + "<meta property=\"og:site_name\" content=\"DarZin\">\n"
                + "<meta name=\"twitter:card\" content=\"summary_large_image\">\n"
                + "<meta name=\"description\" content=\"" + echapper(description) + "\">\n";
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(html);
    }

    /**
     * Le plan du site, engendré depuis le catalogue.
     *
     * Caddy répondait 200 avec la page d'accueil à /sitemap.xml : Google
     * recevait du HTML en guise de plan et indexait des adresses fausses comme
     * valides.
     */
    @GetMapping(value = "/sitemap.xml", produces = "application/xml;charset=UTF-8")
    public ResponseEntity<String> sitemap() {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String chemin : List.of("/", "/recherche", "/professionnels")) {
            xml.append("  <url><loc>").append(echapper(urlPublique + chemin)).append("</loc></url>\n");
        }
        catalogue.rechercher(null, null, null, org.springframework.data.domain.PageRequest.of(0, 500))
                .forEach(salon -> xml.append("  <url><loc>")
                        .append(echapper(urlPublique + "/salon/" + salon.getId()))
                        .append("</loc></url>\n"));
        xml.append("</urlset>\n");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePublic())
                .body(xml.toString());
    }

    private static String echapper(String texte) {
        return texte == null ? "" : texte
                .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /**
     * La journée d'une ville, heure par heure : qui peut vous recevoir.
     *
     * Le renversement de l'accueil — l'heure d'abord, le salon ensuite.
     * Soixante secondes de cache : la réponse bouge à chaque réservation, mais
     * une minute de retard sur « 7 salons libres à 16:30 » ne trompe personne,
     * et la route est appelée par chaque visite de la page d'accueil.
     */
    @GetMapping("/creneaux-du-jour")
    public ResponseEntity<CreneauxDuJour> creneauxDuJour(
            @RequestParam(required = false) String ville,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate jour = date != null ? date : LocalDate.now(disponibilites.zone());
        return cache60(catalogue.creneauxDuJour(ville, jour));
    }

    @GetMapping("/villes")
    public ResponseEntity<List<String>> villes() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(catalogue.villes());
    }

    /** Rayon retenu quand la recherche n'en précise pas : la taille d'une grande ville. */
    private static final double RAYON_DEFAUT_KM = 25;

    /**
     * Rayon maximal. Au-delà, « autour de moi » ne veut plus rien dire : à
     * 200 km de Casablanca on est à Marrakech, et le classement par distance
     * remplacerait le choix d'une ville au lieu de l'affiner.
     */
    private static final double RAYON_MAX_KM = 100;

    /** Nombre de salons correspondants qu'aucun repère ne situe. */
    private static final String EN_TETE_NON_SITUES = "X-Salons-Non-Situes";

    private <T> ResponseEntity<T> cache60(T corps) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                .body(corps);
    }
}
