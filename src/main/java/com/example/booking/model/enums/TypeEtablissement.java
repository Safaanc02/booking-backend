package com.example.booking.model.enums;

/**
 * Métier déclaré dans une demande de démonstration.
 *
 * Distinct de {@link SalonCategorie} à dessein : un prospect s'annonce parfois
 * « spa et hammam » là où le catalogue du produit range les deux sous SPA, et
 * AUTRE existe pour ne pas forcer un choix au moment le plus fragile du
 * parcours. La correspondance se fait au référencement, avec le gérant.
 */
public enum TypeEtablissement {
    COIFFURE, BARBIER, ONGLERIE, ESTHETIQUE, SPA_HAMMAM, AUTRE
}
