package com.example.booking.service;

import com.example.booking.config.Roles;
import com.example.booking.model.User;
import com.example.booking.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Point d'entrée unique vers l'utilisateur courant.
 *
 * SalonService et ReservationService portaient chacun leur propre copie de
 * getOrCreateCurrentUser(). Les deux omettaient `role` et `enabled`, ce qui
 * violait la contrainte NOT NULL sur users.role dès qu'un utilisateur créait
 * sa première ressource. Corriger à deux endroits invite à en oublier un :
 * la logique vit désormais ici seulement.
 */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /* ---------- Contexte de sécurité ---------- */

    public Optional<Jwt> currentJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return Optional.of(jwt);
        }
        return Optional.empty();
    }

    public Optional<String> currentKeycloakId() {
        return currentJwt().map(jwt -> jwt.getClaimAsString("sub"));
    }

    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(Roles.ROLE_ADMIN::equals);
    }

    /* ---------- Miroir local de l'utilisateur Keycloak ---------- */

    /** L'utilisateur courant, créé au premier appel s'il n'existe pas encore. */
    @Transactional
    public User getOrCreate() {
        Jwt jwt = currentJwt().orElseThrow(
                () -> new IllegalStateException("Aucun utilisateur authentifié dans le contexte"));
        return syncFromJwt(jwt);
    }

    /** L'utilisateur courant, sans le créer. */
    @Transactional(readOnly = true)
    public User require() {
        return currentKeycloakId()
                .flatMap(userRepository::findByKeycloakId)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur courant introuvable"));
    }

    /**
     * Crée ou met à jour le miroir local à partir du token.
     * N'écrit en base que si quelque chose a réellement changé.
     */
    @Transactional
    public User syncFromJwt(Jwt jwt) {
        String keycloakId = jwt.getClaimAsString("sub");
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new IllegalStateException("Token sans claim 'sub'");
        }

        String username = jwt.getClaimAsString("preferred_username");
        String email    = jwt.getClaimAsString("email");
        String fullName = jwt.getClaimAsString("name");
        String role     = roleFromJwt(jwt);

        Optional<User> existing = userRepository.findByKeycloakId(keycloakId);
        if (existing.isEmpty()) {
            return userRepository.save(User.builder()
                    .keycloakId(keycloakId)
                    .username(username != null ? username : keycloakId)
                    .email(email)
                    .fullName(fullName)
                    .role(role)        // ← oublié par les anciennes copies
                    .enabled(true)     // ← idem
                    .build());
        }

        User user = existing.get();
        boolean changed = false;
        if (username != null && !Objects.equals(user.getUsername(), username)) { user.setUsername(username); changed = true; }
        if (!Objects.equals(user.getEmail(), email))       { user.setEmail(email);       changed = true; }
        if (!Objects.equals(user.getFullName(), fullName)) { user.setFullName(fullName); changed = true; }
        if (!Objects.equals(user.getRole(), role))         { user.setRole(role);         changed = true; }

        return changed ? userRepository.save(user) : user;
    }

    /** Rôle applicatif le plus élevé porté par le token. */
    private String roleFromJwt(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) return Roles.CLIENT;

        Object rawRoles = realmAccess.get("roles");
        if (!(rawRoles instanceof Collection<?> roles)) return Roles.CLIENT;

        List<String> asStrings = roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
        return Roles.highest(asStrings);
    }
}
