package com.example.booking.notification;

import com.example.booking.model.Reservation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * Jeton signé permettant d'annuler un rendez-vous depuis un email, sans se
 * connecter.
 *
 * Pourquoi c'est nécessaire : un client qui doit retrouver son mot de passe
 * pour annuler ne le fait pas — il ne vient simplement pas. Et un no-show
 * coûte au salon bien plus qu'une annulation.
 *
 * Le jeton porte l'identifiant du rendez-vous, l'instant de début, et une
 * péremption. Il est signé en HMAC-SHA256 avec un secret serveur.
 *
 * Inclure le début n'est pas décoratif : si le rendez-vous est déplacé, les
 * anciens liens cessent de fonctionner. Sans cela, un lien reçu pour un
 * créneau annulerait silencieusement le nouveau.
 */
@Component
public class JetonAnnulation {

    private static final String ALGO = "HmacSHA256";
    private static final Base64.Encoder ENCODEUR = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODEUR = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long validiteJours;

    public JetonAnnulation(@Value("${app.annulation.secret}") String secret,
                           @Value("${app.annulation.validite-jours:90}") long validiteJours) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "app.annulation.secret doit faire au moins 32 caractères — "
                    + "un secret court rend la signature devinable");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.validiteJours = validiteJours;
    }

    /** Jeton de la forme {payload}.{signature}, sûr dans une URL. */
    public String creer(Reservation r) {
        String charge = "%d:%d:%d".formatted(
                r.getId(),
                r.getDebut().getEpochSecond(),
                Instant.now().plusSeconds(validiteJours * 86_400).getEpochSecond());
        String charge64 = ENCODEUR.encodeToString(charge.getBytes(StandardCharsets.UTF_8));
        return charge64 + "." + signer(charge64);
    }

    /** Contenu vérifié d'un jeton. */
    public record Contenu(Long reservationId, Instant debut) {}

    /**
     * Vérifie signature et péremption, puis renvoie le contenu.
     *
     * L'appelant compare ensuite `debut` au rendez-vous réellement chargé : un
     * lien émis pour un créneau ne doit pas annuler celui qui l'a remplacé.
     *
     * @throws IllegalArgumentException si le jeton est mal formé, mal signé ou périmé
     */
    public Contenu verifier(String jeton) {
        if (jeton == null || jeton.isBlank()) {
            throw new IllegalArgumentException("Lien d'annulation absent");
        }
        String[] parties = jeton.split("\\.", 2);
        if (parties.length != 2) {
            throw new IllegalArgumentException("Lien d'annulation invalide");
        }

        // Comparaison à temps constant : une comparaison naïve fuit la
        // signature attendue octet par octet.
        if (!MessageDigest.isEqual(
                signer(parties[0]).getBytes(StandardCharsets.UTF_8),
                parties[1].getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Lien d'annulation invalide");
        }

        String[] champs = new String(DECODEUR.decode(parties[0]), StandardCharsets.UTF_8).split(":");
        if (champs.length != 3) {
            throw new IllegalArgumentException("Lien d'annulation invalide");
        }

        long id = Long.parseLong(champs[0]);
        long debut = Long.parseLong(champs[1]);
        long peremption = Long.parseLong(champs[2]);

        if (Instant.now().getEpochSecond() > peremption) {
            throw new IllegalArgumentException("Ce lien d'annulation a expiré");
        }
        return new Contenu(id, Instant.ofEpochSecond(debut));
    }

    private String signer(String charge64) {
        try {
            Mac mac = Mac.getInstance(ALGO);
            mac.init(new SecretKeySpec(secret, ALGO));
            return ENCODEUR.encodeToString(mac.doFinal(charge64.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Signature impossible", e);
        }
    }
}
