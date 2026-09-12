package com.example.booking.photos;

import com.example.booking.config.CustomPermissionEvaluator;
import com.example.booking.model.PhotoSalon;
import com.example.booking.model.Salon;
import com.example.booking.repository.PhotoSalonRepository;
import com.example.booking.repository.SalonRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/** Photos d'un salon : dépôt, retrait, ordre. */
@Service
@Transactional
public class PhotoService {

    private final PhotoSalonRepository photos;
    private final SalonRepository salons;
    private final StockagePhotos stockage;
    private final CustomPermissionEvaluator droits;
    private final int maxParSalon;
    private final int tailleMaxOctets;

    public PhotoService(PhotoSalonRepository photos,
                        SalonRepository salons,
                        StockagePhotos stockage,
                        CustomPermissionEvaluator droits,
                        @Value("${app.photos.max-par-salon:6}") int maxParSalon,
                        @Value("${app.photos.taille-max-mo:5}") int tailleMaxMo) {
        this.photos = photos;
        this.salons = salons;
        this.stockage = stockage;
        this.droits = droits;
        this.maxParSalon = maxParSalon;
        this.tailleMaxOctets = tailleMaxMo * 1024 * 1024;
    }

    /**
     * Dépose une photo.
     *
     * L'ordre des contrôles n'est pas indifférent : les droits d'abord, la
     * taille ensuite, le format en dernier. Refuser pour cause de format un
     * envoi qu'on aurait de toute façon refusé faute de droits renseignerait
     * un inconnu sur ce que le salon contient déjà.
     */
    public PhotoSalon deposer(Long salonId, byte[] contenu, String nomOrigine) {
        Salon salon = salons.findById(salonId)
                .orElseThrow(() -> new NoSuchElementException("Salon introuvable : " + salonId));
        if (!droits.peutGererSalon(salonId)) {
            throw new SecurityException("Vous ne gérez pas ce salon");
        }

        if (contenu == null || contenu.length == 0) {
            throw new IllegalArgumentException("Fichier vide");
        }
        if (contenu.length > tailleMaxOctets) {
            throw new IllegalArgumentException(
                    "Image trop lourde : " + (contenu.length / 1024 / 1024) + " Mo pour "
                    + (tailleMaxOctets / 1024 / 1024) + " Mo au maximum");
        }
        if (photos.countBySalonId(salonId) >= maxParSalon) {
            throw new IllegalArgumentException(
                    "Ce salon a déjà " + maxParSalon + " photos — retirez-en une avant d'en ajouter");
        }

        /*
         * Le format est reconnu aux premiers octets, pas au type déclaré.
         *
         * Le type annoncé dans la requête se choisit librement. Un fichier dit
         * `image/png` peut être n'importe quoi, et le servir ensuite sous ce
         * type revient à laisser un tiers décider de ce que le navigateur d'un
         * visiteur va interpréter.
         */
        TypeImage type = TypeImage.reconnaitre(contenu);
        if (type == null) {
            throw new IllegalArgumentException(
                    "Format non reconnu" + (nomOrigine != null ? " pour « " + nomOrigine + " »" : "")
                    + " — attendu JPEG, PNG ou WebP");
        }

        String fichier = stockage.ecrire(contenu, type);
        int rang = photos.findBySalonIdOrderByOrdreAscIdAsc(salonId).stream()
                .mapToInt(PhotoSalon::getOrdre).max().orElse(-1) + 1;

        return photos.save(PhotoSalon.builder()
                .salon(salon).fichier(fichier).typeMime(type.mime())
                .taille(contenu.length).ordre(rang)
                .build());
    }

    public void supprimer(Long photoId) {
        PhotoSalon photo = photos.findById(photoId)
                .orElseThrow(() -> new NoSuchElementException("Photo introuvable : " + photoId));
        if (!droits.peutGererSalon(photo.getSalon().getId())) {
            throw new SecurityException("Vous ne gérez pas ce salon");
        }
        // La ligne d'abord, le fichier ensuite : si la transaction échoue après
        // la suppression du fichier, la fiche pointerait une image absente.
        photos.delete(photo);
        stockage.supprimer(photo.getFichier());
    }

    /**
     * Réordonne les photos d'un salon. La première sert de couverture.
     *
     * La liste reçue fait foi et doit être complète : un rang manquant
     * laisserait deux photos au même rang, donc une couverture qui change
     * d'un affichage à l'autre.
     */
    public List<PhotoSalon> reordonner(Long salonId, List<Long> idsDansLOrdre) {
        if (!droits.peutGererSalon(salonId)) {
            throw new SecurityException("Vous ne gérez pas ce salon");
        }
        List<PhotoSalon> actuelles = photos.findBySalonIdOrderByOrdreAscIdAsc(salonId);
        if (idsDansLOrdre.size() != actuelles.size()
                || !actuelles.stream().map(PhotoSalon::getId).allMatch(idsDansLOrdre::contains)) {
            throw new IllegalArgumentException(
                    "L'ordre doit citer exactement les " + actuelles.size() + " photos du salon");
        }
        Map<Long, PhotoSalon> parId = actuelles.stream()
                .collect(Collectors.toMap(PhotoSalon::getId, p -> p));
        for (int i = 0; i < idsDansLOrdre.size(); i++) {
            parId.get(idsDansLOrdre.get(i)).setOrdre(i);
        }
        return photos.saveAll(actuelles.stream()
                .sorted((a, b) -> Integer.compare(a.getOrdre(), b.getOrdre())).toList());
    }

    @Transactional(readOnly = true)
    public List<PhotoSalon> duSalon(Long salonId) {
        return photos.findBySalonIdOrderByOrdreAscIdAsc(salonId);
    }

    /** Noms de fichiers par salon, dans l'ordre, pour toute une page de résultats. */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> parSalon(Collection<Long> salonIds) {
        if (salonIds.isEmpty()) return Map.of();
        Map<Long, List<String>> parSalon = new LinkedHashMap<>();
        for (PhotoSalon p : photos.desSalons(salonIds)) {
            parSalon.computeIfAbsent(p.getSalon().getId(), k -> new java.util.ArrayList<>())
                    .add(p.getFichier());
        }
        return parSalon;
    }

    @Transactional(readOnly = true)
    public PhotoSalon parFichier(String fichier) {
        return photos.findByFichier(fichier)
                .orElseThrow(() -> new NoSuchElementException("Photo introuvable"));
    }

    public byte[] contenu(PhotoSalon photo) {
        return stockage.lire(photo.getFichier());
    }
}
