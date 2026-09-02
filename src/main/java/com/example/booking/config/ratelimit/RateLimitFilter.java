package com.example.booking.config.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitation de débit sur les routes publiques.
 *
 * La route des disponibilités est trivialement aspirable : sans garde-fou, on
 * offre à un concurrent la cartographie complète des agendas de tous les
 * salons, et on prend la charge correspondante.
 *
 * ⚠️ Compteurs en mémoire, donc par instance. Avec deux instances derrière un
 * répartiteur, la limite effective double. Passer à un compteur partagé (Redis)
 * avant toute mise à l'échelle horizontale.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final long INACTIVITE_MS = 10 * 60 * 1000L;

    private final Map<String, SeauJetons> seaux = new ConcurrentHashMap<>();
    private final int capacite;
    private final int parMinute;

    public RateLimitFilter(int capacite, int parMinute) {
        this.capacite = capacite;
        this.parMinute = parMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/public/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        SeauJetons seau = seaux.computeIfAbsent(origine(request), c -> new SeauJetons(capacite, parMinute));

        if (seau.consommer()) {
            filterChain.doFilter(request, response);
            return;
        }

        long attente = seau.attenteSecondes();
        log.debug("Débit dépassé pour {} sur {}", origine(request), request.getRequestURI());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(attente));
        // Même format que GlobalExceptionHandler : le client n'a qu'une forme
        // d'erreur à savoir lire.
        response.getWriter().write("""
                {"timestamp":"%s","status":429,"code":"DEBIT_DEPASSE",\
                "message":"Trop de requêtes, réessayez dans %d seconde(s)"}"""
                .formatted(Instant.now().toString(), attente));
    }

    /**
     * Origine de l'appel.
     *
     * X-Forwarded-For d'abord : derrière un reverse proxy, l'adresse distante
     * est celle du proxy et tous les visiteurs partageraient un seul seau.
     * Cet en-tête est falsifiable — il ne doit être pris en compte que si le
     * proxy en amont le réécrit, ce qui est le cas d'un déploiement normal.
     */
    private String origine(HttpServletRequest request) {
        String transmis = request.getHeader("X-Forwarded-For");
        if (transmis != null && !transmis.isBlank()) {
            return transmis.split(",")[0].trim();
        }
        String reel = request.getRemoteAddr();
        return reel != null ? reel : "inconnu";
    }

    /** Purge les seaux inactifs : sans cela la carte croît avec le nombre d'adresses vues. */
    public int purger() {
        int avant = seaux.size();
        seaux.entrySet().removeIf(e -> e.getValue().inactifDepuis(INACTIVITE_MS));
        return avant - seaux.size();
    }

    public int seauxActifs() {
        return seaux.size();
    }
}
