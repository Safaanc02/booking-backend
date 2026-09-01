package com.example.booking.notification;

import com.example.booking.model.Reservation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Compose le sujet, le HTML et le repli texte de chaque message. */
@Component
public class RedacteurEmail {

    private static final Locale FR = Locale.FRENCH;
    private static final DateTimeFormatter JOUR_HEURE =
            DateTimeFormatter.ofPattern("EEEE d MMMM 'à' HH:mm", FR);

    private final TemplateEngine moteur;
    private final ZoneId zone;
    private final String urlPublique;

    public RedacteurEmail(TemplateEngine moteur,
                          @Value("${app.fuseau:Africa/Casablanca}") String fuseau,
                          @Value("${app.url-publique}") String urlPublique) {
        this.moteur = moteur;
        this.zone = ZoneId.of(fuseau);
        this.urlPublique = urlPublique;
    }

    public CanalNotification.Message confirmationClient(Reservation r) {
        Context ctx = contexteCommun(r);
        ctx.setVariable("contenu", "email/confirmation-client :: contenu");
        ctx.setVariable("delaiAnnulation", r.getSalon().getDelaiAnnulationHeures());

        String sujet = "Rendez-vous confirmé — " + r.getSalon().getNom() + ", " + quand(r);
        String texte = """
                Bonjour %s,

                Votre rendez-vous chez %s est confirmé.

                Prestation : %s
                Avec       : %s
                Quand      : %s
                À régler   : %s

                %s
                %s

                Annulation gratuite jusqu'à %d h avant le rendez-vous.
                Gérer ma réservation : %s
                """.formatted(
                nomClient(r), r.getSalon().getNom(), r.getNomPrestationFige(),
                nomEmploye(r), quand(r), prix(r.getPrixFige()),
                nullVersVide(r.getSalon().getAdresse()), telephoneLisible(r.getSalon().getTelephone()),
                r.getSalon().getDelaiAnnulationHeures(), urlPublique + "/compte");

        return message(r.getClient().getEmail(), sujet, ctx, texte);
    }

    public CanalNotification.Message confirmationSalon(Reservation r) {
        Context ctx = contexteCommun(r);
        ctx.setVariable("contenu", "email/confirmation-salon :: contenu");
        ctx.setVariable("telephoneClient", telephoneLisible(r.telephoneClient()));
        ctx.setVariable("note", r.getNoteClient());
        ctx.setVariable("lienAgenda", urlPublique + "/pro/salon/" + r.getSalon().getId());

        String sujet = "Nouvelle réservation — " + quand(r) + " (" + nomClient(r) + ")";
        String texte = """
                Nouvelle réservation chez %s.

                Client     : %s
                Téléphone  : %s
                Prestation : %s
                Praticien  : %s
                Quand      : %s

                Agenda : %s
                """.formatted(
                r.getSalon().getNom(), nomClient(r), telephoneLisible(r.telephoneClient()),
                r.getNomPrestationFige(), nomEmploye(r), quand(r),
                urlPublique + "/pro/salon/" + r.getSalon().getId());

        return message(r.getSalon().getEmail(), sujet, ctx, texte);
    }

    public CanalNotification.Message rappelClient(Reservation r) {
        Context ctx = contexteCommun(r);
        ctx.setVariable("contenu", "email/rappel-client :: contenu");

        String sujet = "Rappel : votre rendez-vous demain chez " + r.getSalon().getNom();
        String texte = """
                Bonjour %s,

                Petit rappel : votre rendez-vous chez %s a lieu %s.

                Prestation : %s
                Avec       : %s
                Où         : %s

                Un empêchement ? Prévenez le salon au %s, ou annulez depuis votre compte : %s
                """.formatted(
                nomClient(r), r.getSalon().getNom(), quand(r),
                r.getNomPrestationFige(), nomEmploye(r), nullVersVide(r.getSalon().getAdresse()),
                telephoneLisible(r.getSalon().getTelephone()), urlPublique + "/compte");

        return message(r.getClient().getEmail(), sujet, ctx, texte);
    }

    /* ---------- Interne ---------- */

    private Context contexteCommun(Reservation r) {
        Context ctx = new Context(FR);
        ctx.setVariable("client", nomClient(r));
        ctx.setVariable("salon", r.getSalon().getNom());
        ctx.setVariable("prestation", r.getNomPrestationFige());
        ctx.setVariable("employe", nomEmploye(r));
        ctx.setVariable("dateHeure", quand(r));
        ctx.setVariable("prix", prix(r.getPrixFige()));
        ctx.setVariable("adresse", adresseComplete(r));
        ctx.setVariable("telephoneSalon", telephoneLisible(r.getSalon().getTelephone()));
        ctx.setVariable("lienCompte", urlPublique + "/compte");
        return ctx;
    }

    private CanalNotification.Message message(String destinataire, String sujet, Context ctx, String texte) {
        ctx.setVariable("sujet", sujet);
        String html = moteur.process("email/layout", ctx);
        return new CanalNotification.Message(destinataire, sujet, html, texte);
    }

    private String quand(Reservation r) {
        return JOUR_HEURE.format(Instant.ofEpochMilli(r.getDebut().toEpochMilli()).atZone(zone));
    }

    private String prix(BigDecimal montant) {
        return montant == null ? "—" : montant.stripTrailingZeros().toPlainString() + " MAD";
    }

    private String nomClient(Reservation r) {
        String n = r.nomClient();
        return n != null ? n : "cher client";
    }

    private String nomEmploye(Reservation r) {
        return r.getEmploye() != null ? r.getEmploye().nomComplet() : "l'équipe";
    }

    private String adresseComplete(Reservation r) {
        var s = r.getSalon();
        StringBuilder sb = new StringBuilder(nullVersVide(s.getAdresse()));
        if (s.getVille() != null) sb.append(sb.length() > 0 ? ", " : "").append(s.getVille());
        return sb.toString();
    }

    private String nullVersVide(String s) {
        return s == null ? "" : s;
    }

    /** 0661234567 -> 06 61 23 45 67. Le +212 est ramené au format national, plus familier. */
    private String telephoneLisible(String numero) {
        if (numero == null || numero.isBlank()) return "";
        String national = numero.replace("+212", "0").replaceAll("\\s", "");
        return national.replaceAll("(\\d{2})(?=\\d)", "$1 ").trim();
    }
}
