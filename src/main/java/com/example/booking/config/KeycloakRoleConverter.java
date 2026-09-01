package com.example.booking.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Transforme realm_access.roles du JWT Keycloak en autorités Spring Security.
 *
 * Les rôles sont normalisés en majuscules et préfixés ROLE_ : un realm déclarant
 * "admin" produit ROLE_ADMIN, ce qui rend hasRole('ADMIN') fonctionnel partout.
 */
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return List.of();
        }

        Object rawRoles = realmAccess.get("roles");
        if (!(rawRoles instanceof Collection<?> roles)) {
            return List.of();
        }

        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(Roles::normalize)
                .filter(r -> r != null && !r.isBlank())
                .distinct()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toUnmodifiableList());
    }
}
