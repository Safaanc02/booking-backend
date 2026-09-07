package com.example.booking.geo;

/**
 * Distance entre deux points du globe, en kilomètres.
 *
 * Formule de haversine, préférée à la loi des cosinus sphériques : cette
 * dernière perd sa précision sur les courtes distances, celles qui comptent
 * ici — on compare des salons d'une même ville, pas des continents.
 *
 * La même formule est écrite en SQL dans {@code SalonRepository.rechercherAutour}
 * pour filtrer et ordonner sans rapatrier toute la table. Java ne sert qu'à
 * afficher la distance des salons de la page retenue : deux écritures, un seul
 * calcul, à la précision du double près.
 */
public final class Distance {

    /** Rayon moyen de la Terre (IUGG), en kilomètres. */
    private static final double RAYON_TERRE_KM = 6371.0088;

    /**
     * Marge du cadre, en degrés — un dix-millième de seconde d'arc, soit trois
     * millimètres.
     *
     * Elle n'existe que pour absorber l'arrondi du flottant sur un point
     * exactement au bord. Élargir le cadre de trois millimètres ne fait
     * examiner aucune ligne de plus ; l'en priver ferait dépendre l'inclusion
     * d'un salon de bord du dernier bit d'un double.
     */
    private static final double MARGE = 1e-9;

    private Distance() {
    }

    public static double km(double latA, double lngA, double latB, double lngB) {
        double dLat = Math.toRadians(latB - latA);
        double dLng = Math.toRadians(lngB - lngA);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(latA)) * Math.cos(Math.toRadians(latB))
                        * Math.pow(Math.sin(dLng / 2), 2);
        // asin n'accepte que [-1, 1] : l'arrondi du flottant peut faire
        // dépasser 1 pour deux points antipodaux, et rendre NaN.
        return 2 * RAYON_TERRE_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /**
     * Demi-côté vertical, en degrés, du cadre qui circonscrit un cercle de
     * {@code rayonKm}.
     *
     * Le cadre sert de premier tri, servi par un index B-tree ; le calcul
     * exact ne s'applique ensuite qu'aux lignes retenues. Il déborde
     * volontairement le cercle — ses coins dépassent le rayon de 41 % —, d'où
     * le filtre exact qui suit dans la requête.
     *
     * Un cadre trop large ne coûte que quelques lignes examinées pour rien. Un
     * cadre trop étroit perd des salons pourtant dans le rayon, sans erreur ni
     * trace : c'est pourquoi les deux demi-côtés se déduisent du même rayon
     * terrestre que {@link #km}, et non d'un « 111 km par degré » approché.
     * Mélanger 111,0 et 111,32 suffisait à laisser un point plein est à 1 km
     * un mètre hors du cadre.
     */
    public static double degresLatitude(double rayonKm) {
        return Math.toDegrees(rayonKm / RAYON_TERRE_KM) * (1 + MARGE) + MARGE;
    }

    /**
     * Demi-côté horizontal, en degrés.
     *
     * Un degré de longitude se resserre vers les pôles : à Tanger il vaut
     * 90 km, à Dakhla 102. L'écart maximal en longitude d'un cercle d'angle δ
     * centré à la latitude φ vaut exactement asin(sin δ / cos φ) — un peu plus
     * que δ / cos φ, parce que le grand cercle s'écarte vers le pôle avant de
     * revenir. C'est dans cet « un peu plus » que se perdaient les points de
     * bord.
     */
    public static double degresLongitude(double rayonKm, double latitude) {
        double sinRayon = Math.sin(rayonKm / RAYON_TERRE_KM);
        double cosLatitude = Math.cos(Math.toRadians(latitude));
        // Le cercle enferme un pôle : toute longitude en fait partie. Aucun
        // cadre ne réduit alors quoi que ce soit, et seul le filtre exact
        // tranche. Sans ce cas, asin d'un argument hors [-1, 1] rendrait NaN,
        // et le cadre écarterait toutes les lignes au lieu de toutes les garder.
        if (sinRayon >= cosLatitude) return 180.0;
        return Math.min(180.0,
                Math.toDegrees(Math.asin(sinRayon / cosLatitude)) * (1 + MARGE) + MARGE);
    }
}
