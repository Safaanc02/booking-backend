package com.example.booking.photos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Les fichiers d'images sur le disque.
 *
 * Séparé du service métier pour une raison précise : le jour où l'hébergement
 * n'offrira plus de disque persistant, c'est cette classe qu'on remplacera par
 * un stockage objet, et elle seule. Le reste du code ne connaît qu'un nom de
 * fichier.
 *
 * ⚠️ Le répertoire doit survivre aux redéploiements. Sur un hébergement dont le
 * disque est éphémère — la plupart des offres gérées —, les photos
 * disparaîtraient sans erreur, laissant des images cassées sur les fiches.
 */
@Component
public class StockagePhotos {

    private static final Logger log = LoggerFactory.getLogger(StockagePhotos.class);
    private static final SecureRandom ALEA = new SecureRandom();

    private final Path repertoire;

    public StockagePhotos(@Value("${app.photos.repertoire:./donnees/photos}") String repertoire) {
        this.repertoire = Path.of(repertoire).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.repertoire);
        } catch (IOException e) {
            throw new UncheckedIOException("Répertoire des photos inutilisable : " + this.repertoire, e);
        }
        log.info("Photos stockées dans {}", this.repertoire);
    }

    /**
     * Écrit le contenu et rend le nom engendré.
     *
     * Le nom vient d'un tirage aléatoire, jamais du fichier envoyé. Un nom
     * d'origine peut porter des séparateurs de chemin, des caractères réservés,
     * ou simplement celui d'un fichier déjà là — trois façons d'écrire ailleurs
     * que prévu. L'extension se déduit du type réellement détecté, pas de ce
     * que l'appelant déclare.
     */
    public String ecrire(byte[] contenu, TypeImage type) {
        byte[] graine = new byte[16];
        ALEA.nextBytes(graine);
        String nom = HexFormat.of().formatHex(graine) + type.extension();

        try {
            Files.write(resoudre(nom), contenu);
        } catch (IOException e) {
            throw new UncheckedIOException("Écriture de la photo impossible", e);
        }
        return nom;
    }

    public byte[] lire(String nom) {
        try {
            return Files.readAllBytes(resoudre(nom));
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture de la photo impossible : " + nom, e);
        }
    }

    public boolean existe(String nom) {
        return Files.isRegularFile(resoudre(nom));
    }

    /**
     * Supprime le fichier, sans échouer s'il a déjà disparu.
     *
     * Une ligne en base sans fichier est un désagrément ; une exception qui
     * empêche de retirer cette ligne enferme le gérant avec une photo qu'il ne
     * peut plus effacer.
     */
    public void supprimer(String nom) {
        try {
            Files.deleteIfExists(resoudre(nom));
        } catch (IOException e) {
            log.warn("Photo {} non supprimée du disque : {}", nom, e.getMessage());
        }
    }

    /**
     * Chemin d'un nom, garanti à l'intérieur du répertoire.
     *
     * Les noms sont engendrés ici, donc sûrs par construction — mais celui qui
     * arrive d'une requête HTTP a d'abord traversé la base, et une garantie qui
     * repose sur « personne n'insérera jamais de ../ » n'en est pas une. Le
     * contrôle coûte une comparaison de chemins.
     */
    private Path resoudre(String nom) {
        Path chemin = repertoire.resolve(nom).normalize();
        if (!chemin.startsWith(repertoire)) {
            throw new IllegalArgumentException("Nom de fichier hors du répertoire des photos");
        }
        return chemin;
    }
}
