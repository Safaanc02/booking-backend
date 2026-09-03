package com.example.booking.dto;

/** Résultat d'un référencement : le salon créé, et l'état du compte du gérant. */
public record ReferencementResponse(
        SalonResponse salon,
        String emailProprietaire,
        /** false si un compte existait déjà pour cet e-mail — il a été réutilisé. */
        boolean compteCree,
        /**
         * L'invitation à définir un mot de passe est-elle partie ?
         *
         * Un échec n'annule pas le référencement : le salon existe, seule
         * l'invitation reste à renvoyer.
         */
        boolean invitationEnvoyee,
        String message
) {}
