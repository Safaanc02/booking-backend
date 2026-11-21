package com.example.booking.config;

import jakarta.servlet.http.HttpServletRequest;

/**
 * L'adresse d'où vient un appel, derrière le proxy.
 *
 * Caddy AJOUTE l'adresse réelle à la fin de X-Forwarded-For sans retirer ce
 * que le client y a mis. Le limiteur de débit prenait le premier élément :
 * <code>curl -H "X-Forwarded-For: 1.2.3.4"</code> obtenait donc un seau neuf
 * à chaque requête, avec une valeur différente à chaque fois. Il protégeait
 * de la maladresse, pas de l'intention.
 *
 * Le dernier élément est celui que le proxy écrit, et le seul qu'on ne peut
 * pas forger de l'extérieur. Sans proxy — en développement — l'en-tête est
 * absent et l'adresse distante fait foi.
 */
public final class OrigineRequete {

    private OrigineRequete() {}

    public static String de(HttpServletRequest request) {
        String transmis = request.getHeader("X-Forwarded-For");
        if (transmis != null && !transmis.isBlank()) {
            String[] parties = transmis.split(",");
            String dernier = parties[parties.length - 1].trim();
            if (!dernier.isEmpty()) return dernier;
        }
        String reel = request.getRemoteAddr();
        return reel != null ? reel : "inconnu";
    }
}
