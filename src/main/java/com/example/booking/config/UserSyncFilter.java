package com.example.booking.config;

import com.example.booking.service.CurrentUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Maintient en base le miroir local de l'utilisateur Keycloak porté par le JWT.
 *
 * ⚠️ Ordre d'exécution : ce filtre DOIT tourner après BearerTokenAuthenticationFilter.
 * Il était auparavant placé après SecurityContextHolderFilter, qui s'exécute avant
 * toute authentification — le contexte était donc systématiquement vide et le filtre
 * ne faisait jamais rien.
 */
public class UserSyncFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserSyncFilter.class);

    private final CurrentUserService currentUserService;

    public UserSyncFilter(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof Jwt jwt) {
            try {
                currentUserService.syncFromJwt(jwt);
            } catch (Exception e) {
                // La synchronisation du miroir ne doit jamais faire échouer la requête.
                log.warn("Synchronisation de l'utilisateur impossible : {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}
