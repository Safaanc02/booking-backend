package com.example.booking.comptes;

import com.example.booking.config.Roles;
import com.example.booking.dto.ReferencementResponse;
import com.example.booking.dto.ReferencementSalonRequest;
import com.example.booking.dto.SalonRequest;
import com.example.booking.geo.LocalisationService;
import com.example.booking.model.Salon;
import com.example.booking.model.User;
import com.example.booking.model.enums.SalonCategorie;
import com.example.booking.model.enums.SalonStatut;
import com.example.booking.repository.SalonRepository;
import com.example.booking.repository.UserRepository;
import com.example.booking.service.CurrentUserService;
import com.example.booking.service.DemandeDemoService;
import com.example.booking.service.SalonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Installation d'un salon par l'administration, pour le compte d'un gérant.
 *
 * Enchaîne les quatre gestes qui, faits à la main, prenaient une console
 * Keycloak et cinq écrans : créer le compte, lui donner le rôle métier, poser
 * le miroir local, créer le salon à son nom. Puis l'inviter à choisir son mot
 * de passe.
 */
@Service
public class ReferencementService {

    private static final Logger log = LoggerFactory.getLogger(ReferencementService.class);

    private final ComptesKeycloakService comptes;
    private final UserRepository userRepository;
    private final SalonRepository salonRepository;
    private final SalonService salonService;
    private final DemandeDemoService demandes;
    private final CurrentUserService utilisateurCourant;
    private final LocalisationService localisation;

    public ReferencementService(ComptesKeycloakService comptes,
                                UserRepository userRepository,
                                SalonRepository salonRepository,
                                SalonService salonService,
                                DemandeDemoService demandes,
                                CurrentUserService utilisateurCourant,
                                LocalisationService localisation) {
        this.comptes = comptes;
        this.userRepository = userRepository;
        this.salonRepository = salonRepository;
        this.salonService = salonService;
        this.demandes = demandes;
        this.utilisateurCourant = utilisateurCourant;
        this.localisation = localisation;
    }

    @Transactional
    public ReferencementResponse referencer(ReferencementSalonRequest requete) {
        var gerant = requete.getProprietaire();
        String email = gerant.getEmail().trim().toLowerCase();

        boolean dejaConnu = userRepository.findByEmail(email).isPresent();

        // 1. Le compte Keycloak — créé, ou réutilisé s'il existe déjà.
        String keycloakId = comptes.creerOuRetrouver(
                email, gerant.getPrenom(), gerant.getNom(), gerant.getTelephone());

        // 2. Le rôle métier, sans lequel POST /api/salons répondrait 403.
        comptes.attribuerRole(keycloakId, Roles.PRO.toLowerCase());

        // 3. Le miroir local.
        //
        // Posé ici et non attendu de la première connexion : il faut un
        // propriétaire pour créer le salon, et le gérant ne s'est pas encore
        // connecté. On adopte un miroir existant plutôt que d'en insérer un
        // second, l'e-mail et le nom d'utilisateur étant uniques.
        User proprietaire = miroirLocal(keycloakId, email, gerant);

        // 4. Le salon, à son nom.
        SalonRequest s = requete.getSalon();
        boolean valider = Boolean.TRUE.equals(requete.getValiderImmediatement());
        Salon salon = salonRepository.save(Salon.builder()
                .nom(s.getNom())
                .description(s.getDescription())
                .adresse(s.getAdresse())
                .ville(s.getVille())
                .quartier(s.getQuartier())
                .latitude(s.getLatitude())
                .longitude(s.getLongitude())
                .telephone(s.getTelephone())
                .email(s.getEmail())
                .categorie(s.getCategorie() != null ? s.getCategorie() : SalonCategorie.COIFFURE)
                .statut(valider ? SalonStatut.ACTIF : SalonStatut.EN_ATTENTE)
                .delaiAnnulationHeures(s.getDelaiAnnulationHeures() != null
                        ? s.getDelaiAnnulationHeures() : 24)
                .owner(proprietaire)
                // Les métiers exercés, et non la seule catégorie : ce service
                // construit le salon lui-même au lieu de passer par
                // SalonService, si bien qu'il faut y penser ici. Les oublier
                // laissait la table de liaison vide, donc le salon absent de
                // tout filtre par métier — y compris celui de sa propre
                // couleur.
                .metiers(s.getMetiers() != null
                        ? new java.util.LinkedHashSet<>(s.getMetiers())
                        : new java.util.LinkedHashSet<>())
                .build());
        salon.normaliserMetiers();
        // Même raison que pour les métiers ci-dessus : ce service n'emprunte
        // pas SalonService, donc rien ne situe le salon à sa place. Sans point,
        // un salon tout juste référencé serait absent des recherches « autour
        // de moi » — et le conseiller qui vient de l'installer n'aurait aucun
        // moyen de s'en apercevoir.
        localisation.situer(salon);
        salonRepository.save(salon);

        // 5. La demande de démonstration qui a mené là, si elle existe, est
        // marquée convertie. Un démarchage direct n'en a pas : on ne rattache
        // alors rien, et c'est le cas normal au début.
        demandes.rattacherAuSalon(email, salon, utilisateurCourant.getOrCreate())
                .ifPresent(id -> log.info("Référencement issu de la demande #{}", id));

        // 6. L'invitation. Volontairement après l'enregistrement : un serveur
        // de messagerie indisponible ne doit pas faire perdre le référencement.
        boolean invitation = comptes.inviter(keycloakId, email);

        log.info("Salon {} référencé pour {} (compte {}, invitation {})",
                salon.getNom(), email, dejaConnu ? "réutilisé" : "créé",
                invitation ? "envoyée" : "en échec");

        return new ReferencementResponse(
                salonService.toResponse(salon),
                email,
                !dejaConnu,
                invitation,
                message(dejaConnu, invitation, valider));
    }

    /* ------------------------------------------------------------------ */

    private User miroirLocal(String keycloakId, String email,
                             ReferencementSalonRequest.Proprietaire gerant) {
        Optional<User> existant = userRepository.findByKeycloakId(keycloakId)
                .or(() -> userRepository.findByEmail(email));

        String nomComplet = (gerant.getPrenom()
                + " " + (gerant.getNom() == null ? "" : gerant.getNom())).trim();

        if (existant.isPresent()) {
            User u = existant.get();
            u.setKeycloakId(keycloakId);
            u.setEmail(email);
            u.setFullName(nomComplet);
            u.setRole(Roles.PRO);
            u.setTelephone(gerant.getTelephone());
            return userRepository.save(u);
        }

        return userRepository.save(User.builder()
                .keycloakId(keycloakId)
                .username(email)
                .email(email)
                .fullName(nomComplet)
                .telephone(gerant.getTelephone())
                .role(Roles.PRO)
                .enabled(true)
                .build());
    }

    private String message(boolean dejaConnu, boolean invitation, boolean valide) {
        StringBuilder m = new StringBuilder();
        m.append(dejaConnu
                ? "Un compte existait déjà pour cette adresse, il a été réutilisé. "
                : "Compte créé. ");
        m.append(invitation
                ? "Un e-mail vient d'être envoyé au gérant pour qu'il choisisse son mot de passe. "
                : "⚠️ L'e-mail d'invitation n'a pas pu être envoyé — à renvoyer depuis cette page. ");
        m.append(valide
                ? "Le salon est en ligne."
                : "Le salon reste en attente de validation.");
        return m.toString();
    }
}
