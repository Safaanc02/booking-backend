package com.example.booking.config.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@ConditionalOnProperty(name = "app.rate-limit.actif", havingValue = "true", matchIfMissing = true)
public class RateLimitConfig {

    private final RateLimitFilter filtre;

    public RateLimitConfig(@Value("${app.rate-limit.capacite:60}") int capacite,
                           @Value("${app.rate-limit.par-minute:120}") int parMinute) {
        this.filtre = new RateLimitFilter(capacite, parMinute);
    }

    /**
     * Enregistré avant la chaîne de sécurité : refuser une requête excédentaire
     * ne doit pas coûter une validation de jeton ni un accès à la base.
     */
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitRegistration() {
        FilterRegistrationBean<RateLimitFilter> enregistrement = new FilterRegistrationBean<>(filtre);
        enregistrement.addUrlPatterns("/api/public/*");
        enregistrement.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return enregistrement;
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000L)
    public void purgerLesSeaux() {
        filtre.purger();
    }
}
