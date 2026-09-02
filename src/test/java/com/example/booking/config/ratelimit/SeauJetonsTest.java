package com.example.booking.config.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du seau à jetons.
 *
 * La classe est volontairement package-private ; le test vit dans le même
 * paquet plutôt que d'élargir la visibilité pour lui faire plaisir.
 */
class SeauJetonsTest {

    @Test
    @DisplayName("La capacité borne la rafale, puis tout est refusé")
    void rafaleBorneeParLaCapacite() throws Exception {
        Object seau = nouveau(5, 60);

        for (int i = 1; i <= 5; i++) {
            assertThat(consommer(seau)).as("appel n°%d dans la capacité", i).isTrue();
        }
        assertThat(consommer(seau)).as("le sixième dépasse la capacité").isFalse();
    }

    @Test
    @DisplayName("Le seau se recharge avec le temps")
    void rechargeAvecLeTemps() throws Exception {
        // 6000 jetons/minute = 100 par seconde : 30 ms suffisent à en regagner ~3.
        Object seau = nouveau(2, 6000);

        assertThat(consommer(seau)).isTrue();
        assertThat(consommer(seau)).isTrue();
        assertThat(consommer(seau)).as("seau vide").isFalse();

        Thread.sleep(40);

        assertThat(consommer(seau)).as("rechargé après 40 ms").isTrue();
    }

    @Test
    @DisplayName("La recharge ne dépasse jamais la capacité")
    void rechargePlafonnee() throws Exception {
        Object seau = nouveau(3, 60_000);
        Thread.sleep(30); // de quoi regagner bien plus que 3 jetons

        assertThat(consommer(seau)).isTrue();
        assertThat(consommer(seau)).isTrue();
        assertThat(consommer(seau)).isTrue();
        assertThat(consommer(seau))
                .as("un seau plein reste plafonné à sa capacité, il n'accumule pas")
                .isFalse();
    }

    @Test
    @DisplayName("L'attente annoncée est d'au moins une seconde")
    void attenteAuMoinsUneSeconde() throws Exception {
        Object seau = nouveau(1, 60);
        consommer(seau);

        long attente = (long) methode(seau, "attenteSecondes").invoke(seau);
        assertThat(attente)
                .as("Retry-After: 0 inviterait le client à réessayer immédiatement")
                .isGreaterThanOrEqualTo(1);
    }

    /* ---------- Accès à une classe package-private ---------- */

    private Object nouveau(int capacite, int parMinute) throws Exception {
        Class<?> classe = Class.forName("com.example.booking.config.ratelimit.SeauJetons");
        Constructor<?> c = classe.getDeclaredConstructor(int.class, int.class);
        c.setAccessible(true);
        return c.newInstance(capacite, parMinute);
    }

    private boolean consommer(Object seau) throws Exception {
        return (boolean) methode(seau, "consommer").invoke(seau);
    }

    private Method methode(Object cible, String nom) throws Exception {
        Method m = cible.getClass().getDeclaredMethod(nom);
        m.setAccessible(true);
        return m;
    }
}
