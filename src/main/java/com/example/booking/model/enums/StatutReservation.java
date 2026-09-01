package com.example.booking.model.enums;

import java.util.Set;

public enum StatutReservation {
    EN_ATTENTE,
    CONFIRMEE,
    /** Annulée par le client — compte dans sa fiabilité. */
    ANNULEE_CLIENT,
    /** Annulée par le salon — ne pèse pas sur le client. */
    ANNULEE_SALON,
    /** Le client s'est présenté. */
    HONOREE,
    /** Le client ne s'est pas présenté. */
    ABSENT;

    /** Statuts qui occupent effectivement le créneau. Doit rester aligné sur la contrainte SQL pas_de_chevauchement. */
    public static final Set<StatutReservation> BLOQUANTS = Set.of(EN_ATTENTE, CONFIRMEE);

    public boolean bloqueLeCreneau() {
        return BLOQUANTS.contains(this);
    }
}
