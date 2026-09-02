package com.example.booking.config.ratelimit;

/**
 * Seau à jetons.
 *
 * Un seau plutôt qu'un compteur par fenêtre fixe : le compteur autorise une
 * rafale à cheval sur deux fenêtres, soit le double du débit annoncé sur
 * quelques instants. Le seau lisse naturellement, tout en tolérant une rafale
 * bornée par sa capacité — ce qui correspond à un visiteur réel qui compare
 * plusieurs jours de suite.
 */
final class SeauJetons {

    private final double capacite;
    private final double jetonsParMilliseconde;

    private double jetons;
    private long dernierAppel;

    SeauJetons(int capacite, int parMinute) {
        this.capacite = capacite;
        this.jetonsParMilliseconde = parMinute / 60_000.0;
        this.jetons = capacite;
        this.dernierAppel = System.currentTimeMillis();
    }

    /** Retire un jeton si possible. Synchronisé : un seau est partagé entre requêtes d'une même origine. */
    synchronized boolean consommer() {
        recharger();
        if (jetons >= 1.0) {
            jetons -= 1.0;
            return true;
        }
        return false;
    }

    /** Secondes à attendre avant le prochain jeton, au moins une. */
    synchronized long attenteSecondes() {
        recharger();
        double manque = 1.0 - jetons;
        if (manque <= 0) return 0;
        return Math.max(1, (long) Math.ceil(manque / jetonsParMilliseconde / 1000.0));
    }

    synchronized boolean inactifDepuis(long millisecondes) {
        return System.currentTimeMillis() - dernierAppel > millisecondes;
    }

    private void recharger() {
        long maintenant = System.currentTimeMillis();
        jetons = Math.min(capacite, jetons + (maintenant - dernierAppel) * jetonsParMilliseconde);
        dernierAppel = maintenant;
    }
}
