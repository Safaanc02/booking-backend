package com.example.booking.notification;

import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class EmailCanal implements CanalNotification {

    private final JavaMailSender mailSender;
    private final String expediteur;

    public EmailCanal(JavaMailSender mailSender,
                      @Value("${app.mail.expediteur}") String expediteur) {
        this.mailSender = mailSender;
        this.expediteur = expediteur;
    }

    @Override
    public Canal canal() {
        return Canal.EMAIL;
    }

    @Override
    public void envoyer(Message message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mime, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());

            helper.setFrom(expediteur);
            helper.setTo(message.destinataire());
            helper.setSubject(message.sujet());
            // Texte d'abord, HTML ensuite : c'est l'ordre attendu d'un multipart/alternative.
            helper.setText(message.corpsTexte(), message.corpsHtml());

            mailSender.send(mime);
        } catch (Exception e) {
            throw new IllegalStateException("Envoi de l'email impossible : " + e.getMessage(), e);
        }
    }
}
