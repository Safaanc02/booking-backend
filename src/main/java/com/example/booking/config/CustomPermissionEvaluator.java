package com.example.booking.config;

import com.example.booking.model.enums.RoleEmploye;
import com.example.booking.repository.AbsenceRepository;
import com.example.booking.repository.EmployeRepository;
import com.example.booking.repository.PrestationRepository;
import com.example.booking.repository.SalonRepository;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Optional;

/**
 * Qui a le droit de faire quoi sur une ressource.
 *
 * Exposé sous le nom "permission" pour les expressions SpEL :
 *     @PreAuthorize("hasRole('ADMIN') or @permission.peutGererSalon(#id, authentication)")
 *
 * Trois niveaux de droit sur un salon :
 *
 *   propriétaire   — tout, y compris supprimer le salon
 *   GESTIONNAIRE   — administration déléguée : catalogue, équipe, horaires,
 *                    absences, agenda, avis. Ni suppression, ni changement de
 *                    propriétaire.
 *   PRATICIEN      — son propre planning, rien d'autre.
 *
 * La délégation existe parce que sans elle un gérant d'enseigne devait prêter
 * son mot de passe à chaque responsable de boutique.
 *
 * Toutes les résolutions passent par des requêtes dédiées et non par
 * navigation d'objets : cet évaluateur s'exécute HORS transaction, et suivre
 * une association paresseuse y lève une LazyInitializationException.
 *
 * La comparaison se fait sur le keycloakId (claim « sub »), jamais sur le
 * username : celui-ci peut changer côté Keycloak, le sub est immuable.
 */
@Component("permission")
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private final SalonRepository salonRepository;
    private final PrestationRepository prestationRepository;
    private final EmployeRepository employeRepository;
    private final AbsenceRepository absenceRepository;

    public CustomPermissionEvaluator(SalonRepository salonRepository,
                                     PrestationRepository prestationRepository,
                                     EmployeRepository employeRepository,
                                     AbsenceRepository absenceRepository) {
        this.salonRepository = salonRepository;
        this.prestationRepository = prestationRepository;
        this.employeRepository = employeRepository;
        this.absenceRepository = absenceRepository;
    }

    /* ---------- Administration d'un salon ---------- */

    /** Propriétaire, gestionnaire délégué, ou administrateur. */
    public boolean peutGererSalon(Long salonId, Authentication authentication) {
        if (salonId == null) return false;
        if (estAdmin(authentication)) return true;

        String keycloakId = keycloakIdCourant(authentication);
        if (keycloakId == null) return false;

        boolean proprietaire = salonRepository.keycloakIdDuProprietaire(salonId)
                .map(keycloakId::equals)
                .orElse(false);
        if (proprietaire) return true;

        return employeRepository.roleDansSalon(salonId, keycloakId)
                .map(RoleEmploye::peutGerer)
                .orElse(false);
    }

    /** Surcharge sans argument, pour les services qui lisent le contexte courant. */
    public boolean peutGererSalon(Long salonId) {
        return peutGererSalon(salonId, SecurityContextHolder.getContext().getAuthentication());
    }

    public boolean peutGererPrestation(Long prestationId, Authentication authentication) {
        return viaSalon(prestationRepository.salonDeLaPrestation(prestationId), authentication);
    }

    public boolean peutGererEmploye(Long employeId, Authentication authentication) {
        return viaSalon(employeRepository.salonDeLEmploye(employeId), authentication);
    }

    public boolean peutGererAbsence(Long absenceId, Authentication authentication) {
        return viaSalon(absenceRepository.salonConcerne(absenceId), authentication);
    }

    /* ---------- Appartenance à un salon ---------- */

    /**
     * Le compte est-il membre actif de ce salon, à quelque titre ?
     *
     * Sert à la consultation de son propre planning : un praticien n'administre
     * rien, mais doit voir ses rendez-vous.
     */
    public boolean estMembreSalon(Long salonId, Authentication authentication) {
        if (salonId == null) return false;
        if (peutGererSalon(salonId, authentication)) return true;

        String keycloakId = keycloakIdCourant(authentication);
        return keycloakId != null
                && employeRepository.roleDansSalon(salonId, keycloakId).isPresent();
    }

    /* ---------- Contrat PermissionEvaluator ---------- */

    @Override
    public boolean hasPermission(Authentication authentication, Object cible, Object permission) {
        return false; // non utilisé : les expressions SpEL nommées suffisent
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable identifiant,
                                 String type, Object permission) {
        if (identifiant == null || type == null) return false;
        Long id = versId(identifiant);
        if (id == null) return false;

        return switch (type.toLowerCase()) {
            case "salon"      -> peutGererSalon(id, authentication);
            case "prestation" -> peutGererPrestation(id, authentication);
            case "employe"    -> peutGererEmploye(id, authentication);
            case "absence"    -> peutGererAbsence(id, authentication);
            default           -> false;
        };
    }

    /* ---------- Helpers ---------- */

    private boolean viaSalon(Optional<Long> salonId, Authentication authentication) {
        if (estAdmin(authentication)) return true;
        return salonId.map(id -> peutGererSalon(id, authentication)).orElse(false);
    }

    private boolean estAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(Roles.ROLE_ADMIN::equals);
    }

    private String keycloakIdCourant(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString("sub");
        }
        return null;
    }

    /** Java 17 : pas de pattern matching dans switch, d'où le if/else. */
    private Long versId(Serializable identifiant) {
        if (identifiant instanceof Number) return ((Number) identifiant).longValue();
        if (identifiant instanceof String) {
            try {
                return Long.valueOf((String) identifiant);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
