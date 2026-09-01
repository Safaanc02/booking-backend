package com.example.booking.config;

import java.util.List;
import java.util.Locale;

/**
 * Source de vérité unique pour les rôles.
 *
 * Le projet mélangeait jusqu'ici trois vocabulaires : "admin"/"ADMIN",
 * "pro"/"PRO"/"ROLE_PRO" et "proprietaire"/"PROPRIETAIRE". On normalise tout
 * en majuscules sans préfixe côté métier, avec le préfixe ROLE_ côté Spring
 * Security (convention hasRole).
 */
public final class Roles {

    private Roles() {}

    public static final String ADMIN  = "ADMIN";
    public static final String PRO    = "PRO";
    public static final String CLIENT = "CLIENT";

    public static final String ROLE_ADMIN  = "ROLE_" + ADMIN;
    public static final String ROLE_PRO    = "ROLE_" + PRO;
    public static final String ROLE_CLIENT = "ROLE_" + CLIENT;

    /** Rôles applicatifs, du plus fort au plus faible. */
    private static final List<String> HIERARCHIE = List.of(ADMIN, PRO, CLIENT);

    /**
     * Normalise un rôle venu de Keycloak vers notre vocabulaire.
     * "proprietaire" et "owner" sont d'anciens alias de PRO.
     */
    public static String normalize(String rawRole) {
        if (rawRole == null) return null;
        String r = rawRole.trim().toUpperCase(Locale.ROOT);
        if (r.startsWith("ROLE_")) {
            r = r.substring("ROLE_".length());
        }
        return switch (r) {
            case "PROPRIETAIRE", "OWNER" -> PRO;
            default -> r;
        };
    }

    /**
     * Rôle applicatif le plus élevé présent dans la liste, CLIENT par défaut.
     * Les rôles techniques de Keycloak (offline_access, uma_authorization…)
     * sont ignorés puisqu'absents de la hiérarchie.
     */
    public static String highest(List<String> rawRoles) {
        if (rawRoles == null || rawRoles.isEmpty()) return CLIENT;
        return HIERARCHIE.stream()
                .filter(role -> rawRoles.stream()
                        .map(Roles::normalize)
                        .anyMatch(role::equals))
                .findFirst()
                .orElse(CLIENT);
    }
}
