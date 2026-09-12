package com.example.booking.controller;

import com.example.booking.model.PhotoSalon;
import com.example.booking.photos.PhotoService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Photos d'un salon : dépôt par le gérant, lecture par tout le monde.
 *
 * Les deux vivent dans le même fichier parce qu'elles se lisent ensemble —
 * ce qu'on accepte d'écrire décide de ce qu'on servira.
 */
@RestController
public class PhotoController {

    private final PhotoService photos;

    public PhotoController(PhotoService photos) {
        this.photos = photos;
    }

    /* ---------- Le gérant ---------- */

    @GetMapping("/api/pro/salons/{salonId}/photos")
    @PreAuthorize("hasRole('ADMIN') or @permission.peutGererSalon(#salonId, authentication)")
    public ResponseEntity<List<Map<String, Object>>> lister(@PathVariable Long salonId) {
        return ResponseEntity.ok(photos.duSalon(salonId).stream().map(PhotoController::decrire).toList());
    }

    @PostMapping(value = "/api/pro/salons/{salonId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or @permission.peutGererSalon(#salonId, authentication)")
    public ResponseEntity<Map<String, Object>> deposer(@PathVariable Long salonId,
                                                       @RequestParam("fichier") MultipartFile fichier) {
        byte[] contenu;
        try {
            contenu = fichier.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du fichier envoyé impossible", e);
        }
        return ResponseEntity.ok(decrire(photos.deposer(salonId, contenu, fichier.getOriginalFilename())));
    }

    @DeleteMapping("/api/pro/photos/{photoId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> supprimer(@PathVariable Long photoId) {
        // Le droit se vérifie dans le service : il faut d'abord charger la
        // photo pour connaître son salon, et l'annotation ne peut pas le faire.
        photos.supprimer(photoId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/pro/salons/{salonId}/photos/ordre")
    @PreAuthorize("hasRole('ADMIN') or @permission.peutGererSalon(#salonId, authentication)")
    public ResponseEntity<List<Map<String, Object>>> reordonner(@PathVariable Long salonId,
                                                                @RequestBody List<Long> ids) {
        return ResponseEntity.ok(photos.reordonner(salonId, ids).stream()
                .map(PhotoController::decrire).toList());
    }

    /* ---------- Tout le monde ---------- */

    /**
     * Sert une photo.
     *
     * Trois précautions, dans l'ordre où elles comptent.
     *
     * Le type servi vient de la base, où il a été inscrit d'après les premiers
     * octets du fichier — jamais d'après ce que l'appelant avait déclaré au
     * dépôt. `nosniff` complète : sans lui, un navigateur peut décider
     * lui-même du type d'après le contenu, et servir en exécutable ce qu'on
     * annonçait en image.
     *
     * `inline` plutôt que `attachment` : c'est une image de page, pas un
     * téléchargement. Mais le nom de fichier est celui du serveur, pour ne pas
     * renvoyer un nom d'origine qu'on n'a jamais contrôlé.
     *
     * Cache long et immuable : le nom est tiré au sort à l'écriture, donc une
     * photo modifiée est une photo nouvelle sous un autre nom. Rien ne
     * justifie de la redemander.
     */
    @GetMapping("/api/public/photos/{fichier}")
    public ResponseEntity<byte[]> servir(@PathVariable String fichier) {
        PhotoSalon photo = photos.parFichier(fichier);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getTypeMime()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + photo.getFichier() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(photos.contenu(photo));
    }

    private static Map<String, Object> decrire(PhotoSalon p) {
        return Map.of(
                "id", p.getId(),
                "fichier", p.getFichier(),
                "url", "/api/public/photos/" + p.getFichier(),
                "typeMime", p.getTypeMime(),
                "taille", p.getTaille(),
                "ordre", p.getOrdre());
    }
}
