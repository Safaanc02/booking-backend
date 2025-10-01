package com.example.booking.config;

import com.example.booking.model.User;
import com.example.booking.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UserSyncFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public UserSyncFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String keycloakId = jwt.getClaim("sub");
            String username   = jwt.getClaim("preferred_username");
            String email      = jwt.getClaim("email");
            String fullName   = jwt.getClaim("name");

            // Déterminer rôle principal
            String role = "CLIENT";
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) {
                List<String> roles = (List<String>) realmAccess.get("roles");
                if (roles != null && !roles.isEmpty()) {
                    if (roles.contains("admin")) role = "ADMIN";
                    else if (roles.contains("proprietaire")) role = "PROPRIETAIRE";
                }
            }

            // Vérifier si l’utilisateur existe déjà en DB
            Optional<User> existing = userRepository.findByKeycloakId(keycloakId);

            if (existing.isPresent()) {
                User user = existing.get();
                user.setUsername(username);
                user.setEmail(email);
                user.setFullName(fullName);
                user.setRole(role);
                userRepository.save(user);
            } else {
                User newUser = User.builder()
                        .keycloakId(keycloakId)
                        .username(username)
                        .email(email)
                        .fullName(fullName)
                        .role(role)
                        .enabled(true)
                        .build();
                userRepository.save(newUser);
            }
        }

        filterChain.doFilter(request, response);
    }
}
