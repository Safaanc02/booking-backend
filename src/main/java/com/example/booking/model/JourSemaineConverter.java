package com.example.booking.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.DayOfWeek;

/**
 * DayOfWeek ↔ SMALLINT, en numérotation ISO-8601 (lundi = 1, dimanche = 7).
 *
 * On ne stocke pas Enum.ordinal() : l'ordinal de DayOfWeek commence à 0, et un
 * décalage silencieux d'un jour est exactement le genre de bug qu'on ne
 * remarque qu'en production, un dimanche.
 */
@Converter(autoApply = true)
public class JourSemaineConverter implements AttributeConverter<DayOfWeek, Short> {

    @Override
    public Short convertToDatabaseColumn(DayOfWeek jour) {
        return jour == null ? null : (short) jour.getValue();
    }

    @Override
    public DayOfWeek convertToEntityAttribute(Short valeur) {
        return valeur == null ? null : DayOfWeek.of(valeur);
    }
}
