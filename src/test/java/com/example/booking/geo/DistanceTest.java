package com.example.booking.geo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le calcul de distance et le cadre qui le précède.
 *
 * Le cadre est la partie qu'un test doit tenir. Trop large, la recherche est
 * seulement un peu plus lente ; trop étroit, elle perd des salons pourtant
 * dans le rayon — et rien ne le signale, ni côté client ni dans les journaux.
 */
class DistanceTest {

    private static final double CASA_LAT = 33.5731, CASA_LNG = -7.5898;
    private static final double RABAT_LAT = 34.0209, RABAT_LNG = -6.8416;
    private static final double MARRAKECH_LAT = 31.6295, MARRAKECH_LNG = -7.9811;
    private static final double DAKHLA_LAT = 23.6848, DAKHLA_LNG = -15.9580;

    @Test
    @DisplayName("Distances connues, à vol d'oiseau")
    void distancesConnues() {
        // Casablanca–Rabat : 87 km d'orthodromie (l'autoroute en fait 91).
        assertThat(Distance.km(CASA_LAT, CASA_LNG, RABAT_LAT, RABAT_LNG))
                .isCloseTo(87, org.assertj.core.data.Offset.offset(2.0));

        assertThat(Distance.km(CASA_LAT, CASA_LNG, MARRAKECH_LAT, MARRAKECH_LNG))
                .isCloseTo(220, org.assertj.core.data.Offset.offset(5.0));

        assertThat(Distance.km(CASA_LAT, CASA_LNG, DAKHLA_LAT, DAKHLA_LNG))
                .isCloseTo(1368, org.assertj.core.data.Offset.offset(10.0));
    }

    @Test
    @DisplayName("Deux salons au même point sont à zéro, pas à NaN")
    void memePoint() {
        double d = Distance.km(CASA_LAT, CASA_LNG, CASA_LAT, CASA_LNG);
        assertThat(d).isNotNaN().isZero();
    }

    @Test
    @DisplayName("Deux points antipodaux ne rendent pas NaN")
    void antipodes() {
        // sqrt(a) peut dépasser 1 par arrondi du flottant, et asin(1,0000001)
        // vaut NaN. Le résultat se propagerait ensuite dans un tri, où il
        // ordonne au hasard sans jamais lever d'erreur.
        double d = Distance.km(0, 0, 0, 180);
        assertThat(d).isNotNaN().isCloseTo(20015, org.assertj.core.data.Offset.offset(10.0));
    }

    @Test
    @DisplayName("Le cadre d'index contient tout le cercle, dans toutes les directions")
    void leCadreContientLeCercle() {
        double[][] centres = {
                {CASA_LAT, CASA_LNG},
                {35.7595, -5.8340},   // Tanger, le point le plus au nord
                {DAKHLA_LAT, DAKHLA_LNG},
        };

        for (double[] centre : centres) {
            for (double rayon : new double[] {1, 5, 25, 100}) {
                double dLat = Distance.degresLatitude(rayon);
                double dLng = Distance.degresLongitude(rayon, centre[0]);

                for (int cap = 0; cap < 360; cap += 5) {
                    double[] bord = aDistance(centre[0], centre[1], rayon, cap);

                    assertThat(bord[0])
                            .as("latitude au cap %d°, rayon %.0f km depuis (%.2f, %.2f)",
                                    cap, rayon, centre[0], centre[1])
                            .isBetween(centre[0] - dLat, centre[0] + dLat);
                    assertThat(bord[1])
                            .as("longitude au cap %d°, rayon %.0f km depuis (%.2f, %.2f)",
                                    cap, rayon, centre[0], centre[1])
                            .isBetween(centre[1] - dLng, centre[1] + dLng);
                }
            }
        }
    }

    @Test
    @DisplayName("Le cadre reste borné quand le cosinus de la latitude s'effondre")
    void pasDeCadreInfiniAuPole() {
        assertThat(Distance.degresLongitude(25, 89.999)).isEqualTo(180.0);
        assertThat(Distance.degresLongitude(25, 90)).isEqualTo(180.0);
        assertThat(Distance.degresLongitude(25, -90)).isEqualTo(180.0);
        // Une latitude marocaine, elle, doit rester serrée : sinon le premier
        // tri ne trie plus rien et l'index ne sert à rien.
        assertThat(Distance.degresLongitude(25, CASA_LAT)).isLessThan(0.3);
    }

    /**
     * Point situé à {@code km} du départ, dans la direction {@code capDegres}.
     * Formule directe de la navigation orthodromique — indépendante de
     * haversine, pour que le test ne valide pas le code par lui-même.
     */
    private static double[] aDistance(double lat, double lng, double km, double capDegres) {
        double r = 6371.0088;
        double d = km / r;
        double cap = Math.toRadians(capDegres);
        double phi1 = Math.toRadians(lat), lambda1 = Math.toRadians(lng);

        double phi2 = Math.asin(Math.sin(phi1) * Math.cos(d)
                + Math.cos(phi1) * Math.sin(d) * Math.cos(cap));
        double lambda2 = lambda1 + Math.atan2(
                Math.sin(cap) * Math.sin(d) * Math.cos(phi1),
                Math.cos(d) - Math.sin(phi1) * Math.sin(phi2));

        return new double[] {Math.toDegrees(phi2), Math.toDegrees(lambda2)};
    }
}
