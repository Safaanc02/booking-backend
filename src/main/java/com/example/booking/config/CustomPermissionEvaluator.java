package com.example.booking.config;

import com.example.booking.model.Salon;
import com.example.booking.repository.SalonRepository;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.Optional;

@Component("permission") // important pour que SpEL "@permission" marche
public class CustomPermissionEvaluator implements PermissionEvaluator {

    private final SalonRepository salonRepository;

    public CustomPermissionEvaluator(SalonRepository salonRepository) {
        this.salonRepository = salonRepository;
    }

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {
        return false; // non utilisé dans notre cas
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType, Object permission) {
        if (authentication == null || targetId == null || targetType == null) {
            return false;
        }

        if (targetType.equalsIgnoreCase("Salon")) {
            Long salonId = (Long) targetId;
            Optional<Salon> salonOpt = salonRepository.findById(salonId);

            if (salonOpt.isEmpty()) return false;

            Salon salon = salonOpt.get();
            String username = authentication.getName();

            // 🔹 Autorisé si ADMIN
            boolean isAdmin = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch(role -> role.equals("ROLE_admin") || role.equals("ROLE_ADMIN"));

            if (isAdmin) return true;

            // 🔹 Autorisé si l'utilisateur est le propriétaire du salon
            return salon.getOwner() != null && salon.getOwner().getUsername().equals(username);
        }

        return false;
    }
}
