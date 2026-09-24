package com.example.booking.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * « Est-ce que les courriels partent vraiment ? »
 *
 * La question paraît triviale et ne l'est pas. Les notifications sont
 * volontairement asynchrones et rattrapées : une panne d'envoi ne fait jamais
 * échouer une réservation, elle se contente d'une ligne dans les journaux.
 * C'est la bonne décision — au pire la cliente n'a pas son courriel, elle a
 * quand même son rendez-vous — mais elle a un revers : rien, à l'écran, ne dit
 * que plus aucun message ne part depuis trois jours.
 *
 * Deux façons de s'en apercevoir : lire les journaux, ou recevoir l'appel d'un
 * gérant. Ce diagnostic en propose une troisième, et il répond en face.
 *
 * Il sert surtout le jour du branchement d'un vrai serveur d'envoi : un
 * identifiant de travers, un port bloqué en sortie, un expéditeur refusé parce
 * que le domaine n'est pas vérifié — trois échecs courants qui, sans lui, se
 * découvrent par l'absence de quelque chose.
 */
@Service
public class DiagnosticCourriel {

    private final CanalNotification canal;
    private final String expediteur;
    private final String hote;
    private final boolean actif;

    public DiagnosticCourriel(CanalNotification canal,
                              @Value("${app.mail.expediteur}") String expediteur,
                              @Value("${spring.mail.host}") String hote,
                              @Value("${app.mail.actif:true}") boolean actif) {
        this.canal = canal;
        this.expediteur = expediteur;
        this.hote = hote;
        this.actif = actif;
    }

    /**
     * Envoie un message d'essai et rend compte, sans rien avaler.
     *
     * @param destinataire une adresse réelle, que l'on va aller consulter
     */
    public Resultat essayer(String destinataire) {
        if (!actif) {
            return new Resultat(false, hote, expediteur, false,
                    "L'envoi est désactivé (app.mail.actif=false). Aucun message ne part, "
                    + "quelle que soit la configuration du serveur.");
        }

        /* Une boîte de test n'est pas un serveur d'envoi. Le distinguer ici
           évite le faux positif le plus coûteux : « le test passe », alors que
           le message est simplement tombé dans Mailpit et n'ira nulle part. */
        boolean boiteDeTest = hote == null
                || hote.isBlank()
                || hote.equals("localhost")
                || hote.equals("127.0.0.1")
                || hote.contains("mailpit")
                || hote.contains("maildev");

        try {
            canal.envoyer(new CanalNotification.Message(
                    destinataire,
                    "DarZin — essai d'envoi",
                    """
                    <p>Ce message confirme que le serveur d'envoi de DarZin fonctionne.</p>
                    <p style="color:#78716c;font-size:13px;">
                      Expéditeur : %s<br>Serveur : %s
                    </p>
                    """.formatted(expediteur, hote),
                    "Ce message confirme que le serveur d'envoi de DarZin fonctionne.\n"
                    + "Expéditeur : " + expediteur + "\nServeur : " + hote));

            return new Resultat(true, hote, expediteur, boiteDeTest,
                    boiteDeTest
                        ? "Message remis à la boîte aux lettres de test. Il n'a PAS quitté le "
                          + "serveur : aucune cliente ne recevra ses confirmations tant qu'un "
                          + "vrai serveur d'envoi n'est pas configuré."
                        : "Message accepté par le serveur d'envoi. Vérifiez la boîte de réception, "
                          + "et les indésirables.");
        } catch (Exception e) {
            return new Resultat(false, hote, expediteur, boiteDeTest,
                    "Échec : " + e.getClass().getSimpleName() + " — " + e.getMessage());
        }
    }

    public record Resultat(boolean envoye, String serveur, String expediteur,
                           boolean boiteDeTest, String message) {}
}
