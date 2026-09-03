package com.example.booking.config.ratelimit;

import java.util.function.LongSupplier;

/**
 * Seau à jetons.
 *
 * Un seau plutôt qu'un compteur par fenêtre fixe : le compteur autorise une
 * rafale à cheval sur deux fenêtres, soit le double du débit annoncé sur
 * quelques instants. Le seau lisse naturellement, tout en tolérant une rafale
 * bornée par sa capacité — ce qui correspond à un visiteur réel qui compare
 * plusieurs jours de suite.
 *
 * L'horloge est injectable. Ce n'est pas une coquetterie : lue directement,
 * elle rendait les tests instables au point d'échouer une fois sur deux sous
 * charge. À 60 000 jetons/minute, une seule milliseconde écoulée entre deux
 * assertions accorde un jeton — le test « la recharge ne dépasse jamais la
 * capacité » supposait donc quatre appels réflexifs en moins d'une
 * milliseconde. Avec une horloge maîtrisée, le temps ne passe que lorsque le
 * test le décide.
 */
final class SeauJetons {

    private final double capacite;
    private final double jetonsParMilliseconde;
    private final LongSupplier horloge;

    private double jetons;
    private long dernierAppel;

    SeauJetons(int capacite, int parMinute) {
        this(capacite, parMinute, System::currentTimeMillis);
    }

    SeauJetons(int capacite, int parMinute, LongSupplier horloge) {
        this.capacite = capacite;
        this.jetonsParMilliseconde = parMinute / 60_000.0;
        this.horloge = horloge;
        this.jetons = capacite;
        this.dernierAppel = horloge.getAsLong();
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
        return horloge.getAsLong() - dernierAppel > millisecondes;
    }

    private void recharger() {
        long maintenant = horloge.getAsLong();
        jetons = Math.min(capacite, jetons + (maintenant - dernierAppel) * jetonsParMilliseconde);
        dernierAppel = maintenant;
    }
}
