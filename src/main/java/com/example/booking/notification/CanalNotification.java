package com.example.booking.notification;

/**
 * Un moyen d'acheminer un message.
 *
 * L'email est le canal de départ, mais ce n'est pas celui qui portera le plus
 * au Maroc : WhatsApp et le SMS y dominent largement les usages. Cette
 * interface existe pour que brancher un second canal reste l'ajout d'un
 * adaptateur, et non une réécriture du service de notification.
 */
public interface CanalNotification {

    Canal canal();

    /** Doit lever une exception en cas d'échec : l'appelant journalise l'erreur. */
    void envoyer(Message message);

    enum Canal { EMAIL, SMS, WHATSAPP }

    /** Le corps texte sert de repli pour les clients qui n'affichent pas le HTML. */
    record Message(String destinataire, String sujet, String corpsHtml, String corpsTexte) {}
}
