package com.example.booking.config.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du seau à jetons.
 *
 * Le temps est maîtrisé, jamais attendu. La version précédente dormait puis
 * mesurait : elle échouait une fois sur deux dès que la machine était
 * occupée, parce qu'à 60 000 jetons/minute une milliseconde entre deux
 * assertions suffit à en accorder un. Un test qui lâche sous charge lâche en
 * intégration continue, au pire moment, et personne n'y croit plus.
 *
 * Le test vit dans le même paquet que la classe, volontairement
 * package-private. Il l'instancie donc directement : la version précédente
 * passait par la réflexion sans que rien ne l'exige.
 */
class SeauJetonsTest {

    /** Horloge que le test avance lui-même. */
    private static final class Horloge implements LongSupplier {
        private long millisecondes = 1_000_000L;

        @Override public long getAsLong() {
            return millisecondes;
        }

        void avancer(long duree) {
            millisecondes += duree;
        }
    }

    @Test
    @DisplayName("La capacité borne la rafale, puis tout est refusé")
    void rafaleBorneeParLaCapacite() {
        SeauJetons seau = new SeauJetons(5, 60, new Horloge());

        for (int i = 1; i <= 5; i++) {
            assertThat(seau.consommer()).as("appel n°%d dans la capacité", i).isTrue();
        }
        assertThat(seau.consommer()).as("le sixième dépasse la capacité").isFalse();
    }

    @Test
    @DisplayName("Le seau se recharge avec le temps")
    void rechargeAvecLeTemps() {
        Horloge horloge = new Horloge();
        // 6000 jetons/minute = 100 par seconde, soit un jeton toutes les 10 ms.
        SeauJetons seau = new SeauJetons(2, 6000, horloge);

        assertThat(seau.consommer()).isTrue();
        assertThat(seau.consommer()).isTrue();
        assertThat(seau.consommer()).as("seau vide, et le temps n'a pas bougé").isFalse();

        horloge.avancer(10);
        assertThat(seau.consommer()).as("un jeton regagné en 10 ms").isTrue();
    }

    @Test
    @DisplayName("La recharge ne dépasse jamais la capacité")
    void rechargePlafonnee() {
        Horloge horloge = new Horloge();
        SeauJetons seau = new SeauJetons(3, 60_000, horloge);

        // Une heure d'inactivité : de quoi regagner un million de jetons si
        // rien ne plafonnait.
        horloge.avancer(3_600_000);

        assertThat(seau.consommer()).isTrue();
        assertThat(seau.consommer()).isTrue();
        assertThat(seau.consommer()).isTrue();
        assertThat(seau.consommer())
                .as("un seau plein reste plafonné à sa capacité, il n'accumule pas")
                .isFalse();
    }

    @Test
    @DisplayName("L'attente annoncée est d'au moins une seconde")
    void attenteAuMoinsUneSeconde() {
        SeauJetons seau = new SeauJetons(1, 60, new Horloge());
        seau.consommer();

        assertThat(seau.attenteSecondes())
                .as("Retry-After: 0 inviterait le client à réessayer immédiatement")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Un seau touché à l'instant n'est pas inactif")
    void inactiviteMesureeSurLaMemeHorloge() {
        Horloge horloge = new Horloge();
        SeauJetons seau = new SeauJetons(1, 60, horloge);

        assertThat(seau.inactifDepuis(1000)).as("créé à l'instant").isFalse();
        horloge.avancer(1001);
        assertThat(seau.inactifDepuis(1000)).as("plus d'une seconde sans appel").isTrue();
    }
}
