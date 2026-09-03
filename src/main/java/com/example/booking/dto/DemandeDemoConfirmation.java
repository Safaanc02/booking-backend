package com.example.booking.dto;

/**
 * Ce que reçoit le professionnel après avoir déposé sa demande.
 *
 * Ni identifiant ni détail : la route est publique, et renvoyer le contenu
 * enregistré donnerait à n'importe qui le moyen de lire les demandes en
 * devinant des numéros. Seul le message compte, et il doit dire ce qui va se
 * passer ensuite plutôt que « merci ».
 */
public record DemandeDemoConfirmation(String message) {}
