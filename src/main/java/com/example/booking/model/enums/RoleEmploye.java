package com.example.booking.model.enums;

public enum RoleEmploye {
    /** Consulte son propre planning. Aucun droit d'administration. */
    PRATICIEN,
    /**
     * Gère le salon comme le propriétaire — catalogue, équipe, horaires,
     * absences, agenda, avis — mais ne peut ni le supprimer ni en changer le
     * propriétaire. C'est la délégation qu'attend un gérant d'enseigne.
     */
    GESTIONNAIRE;

    public boolean peutGerer() {
        return this == GESTIONNAIRE;
    }
}
