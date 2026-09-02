package com.example.booking.notification;

import com.example.booking.model.Reservation;
import com.example.booking.model.Salon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests du jeton d'annulation.
 *
 * Ce jeton autorise l'annulation d'un rendez-vous sans authentification :
 * la moindre faiblesse de vérification permettrait d'annuler celui d'autrui.
 */
class JetonAnnulationTest {

    private static final String SECRET = "un-secret-de-test-suffisamment-long-pour-passer";

    private JetonAnnulation jetons;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        jetons = new JetonAnnulation(SECRET, 90);
        reservation = Reservation.builder()
                .id(42L)
                .salon(Salon.builder().id(1L).nom("Dar Zine").build())
                .debut(Instant.now().plus(3, ChronoUnit.DAYS))
                .build();
    }

    @Test
    @DisplayName("Un jeton légitime redonne l'identifiant et le créneau")
    void allerRetour() {
        JetonAnnulation.Contenu contenu = jetons.verifier(jetons.creer(reservation));

        assertThat(contenu.reservationId()).isEqualTo(42L);
        // La précision descend à la seconde : le jeton ne transporte pas les millisecondes.
        assertThat(contenu.debut().getEpochSecond()).isEqualTo(reservation.getDebut().getEpochSecond());
    }

    @Test
    @DisplayName("Une signature falsifiée est rejetée")
    void signatureFalsifiee() {
        String jeton = jetons.creer(reservation);
        String charge = jeton.substring(0, jeton.indexOf('.'));

        assertThatThrownBy(() -> jetons.verifier(charge + ".signature-inventee"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalide");
    }

    @Test
    @DisplayName("Substituer la charge en gardant la signature est rejeté")
    void chargeSubstituee() {
        String jeton = jetons.creer(reservation);
        String signature = jeton.substring(jeton.indexOf('.') + 1);

        // On vise une autre réservation : c'est l'attaque évidente contre ce mécanisme.
        String autreCharge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("1:1788628500:1796132691".getBytes());

        assertThatThrownBy(() -> jetons.verifier(autreCharge + "." + signature))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Un jeton signé avec un autre secret est rejeté")
    void autreSecret() {
        String etranger = new JetonAnnulation("un-tout-autre-secret-lui-aussi-assez-long", 90)
                .creer(reservation);

        assertThatThrownBy(() -> jetons.verifier(etranger))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Un jeton périmé est rejeté")
    void jetonPerime() {
        // Validité négative : la péremption est datée d'hier, le jeton naît
        // expiré. Une validité de zéro ne suffirait pas — la comparaison est
        // stricte, et création puis vérification tombent dans la même seconde.
        JetonAnnulation perime = new JetonAnnulation(SECRET, -1);

        assertThatThrownBy(() -> perime.verifier(perime.creer(reservation)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiré");
    }

    @Test
    @DisplayName("Un jeton vide ou mal formé est rejeté sans exception technique")
    void malForme() {
        for (String mauvais : new String[]{null, "", "   ", "sans-point", "a.b.c.d", "!!.??"}) {
            assertThatThrownBy(() -> jetons.verifier(mauvais))
                    .as("jeton « %s »", mauvais)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("Un secret trop court fait échouer le démarrage, pas une requête")
    void secretTropCourt() {
        assertThatThrownBy(() -> new JetonAnnulation("court", 90))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("Le jeton passe dans une URL sans encodage supplémentaire")
    void surUrl() {
        String jeton = jetons.creer(reservation);

        assertThat(jeton)
                .as("base64url : ni +, ni /, ni = qui devraient être échappés")
                .matches("[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+");
    }
}
