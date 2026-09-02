package com.example.booking.config;

import com.example.booking.model.Employe;
import com.example.booking.model.Prestation;
import com.example.booking.model.Salon;
import com.example.booking.repository.AbsenceRepository;
import com.example.booking.repository.EmployeRepository;
import com.example.booking.repository.PrestationRepository;
import com.example.booking.repository.SalonRepository;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.io.Serializable;

/**
 * Évalue la propriété d'une ressource pour les @PreAuthorize.
 *
 * Exposé sous le nom "permission" pour les expressions SpEL du type :
 *     @PreAuthorize("hasRole('ADMIN') or @permission.isOwnerSalon(#id, authentication)")
 *
 * La comparaison se fait sur le keycloakId (claim "sub" du JWT), jamais sur le
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

    /* ---------- Méthodes appelées depuis les @PreAuthorize ---------- */

    /** L'appelant est-il admin, ou propriétaire de ce salon ? */
    public boolean isOwnerSalon(Long salonId, Authentication authentication) {
        if (salonId == null) return false;
        if (isAdmin(authentication)) return true;
        return salonRepository.findById(salonId)
                .map(salon -> isOwner(salon, authentication))
                .orElse(false);
    }

    /** Idem, via le salon qui porte la prestation. */
    public boolean isOwnerPrestation(Long prestationId, Authentication authentication) {
        if (prestationId == null) return false;
        if (isAdmin(authentication)) return true;
        return prestationRepository.findById(prestationId)
                .map(Prestation::getSalon)
                .map(salon -> isOwner(salon, authentication))
                .orElse(false);
    }

    /** Idem, via le salon qui emploie ce praticien. */
    public boolean isOwnerEmploye(Long employeId, Authentication authentication) {
        if (employeId == null) return false;
        if (isAdmin(authentication)) return true;
        return employeRepository.findById(employeId)
                .map(Employe::getSalon)
                .map(salon -> isOwner(salon, authentication))
                .orElse(false);
    }

    /**
     * Une absence vise soit un praticien, soit le salon entier : on remonte au
     * salon dans les deux cas.
     */
    public boolean isOwnerAbsence(Long absenceId, Authentication authentication) {
        if (absenceId == null) return false;
        if (isAdmin(authentication)) return true;
        String keycloakId = currentKeycloakId(authentication);
        if (keycloakId == null) return false;
        return absenceRepository.keycloakIdDuProprietaire(absenceId)
                .map(keycloakId::equals)
                .orElse(false);
    }

    /* ---------- Contrat PermissionEvaluator ---------- */

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        if (targetDomainObject instanceof Salon salon) {
            return isOwner(salon, authentication);
        }
        return false;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (targetId == null || targetType == null) return false;

        Long id = toId(targetId);
        if (id == null) return false;

        return switch (targetType.toLowerCase()) {
            case "salon"      -> isOwnerSalon(id, authentication);
            case "prestation" -> isOwnerPrestation(id, authentication);
            case "employe"    -> isOwnerEmploye(id, authentication);
            case "absence"    -> isOwnerAbsence(id, authentication);
            default           -> false;
        };
    }

    /* ---------- Helpers ---------- */

    private boolean isOwner(Salon salon, Authentication authentication) {
        if (salon == null || salon.getOwner() == null) return false;
        String keycloakId = currentKeycloakId(authentication);
        return keycloakId != null && keycloakId.equals(salon.getOwner().getKeycloakId());
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(Roles.ROLE_ADMIN::equals);
    }

    private String currentKeycloakId(Authentication authentication) {
        if (authentication == null) return null;
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString("sub");
        }
        return null;
    }

    /** Java 17 : pas de pattern matching dans switch, d'où le if/else. */
    private Long toId(Serializable targetId) {
        if (targetId instanceof Number) {
            return ((Number) targetId).longValue();
        }
        if (targetId instanceof String) {
            return parseOrNull((String) targetId);
        }
        return null;
    }

    private Long parseOrNull(String s) {
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
