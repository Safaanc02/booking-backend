package com.example.booking.photos;

/**
 * Les formats d'image acceptés, reconnus à leur signature.
 *
 * Le type déclaré par le navigateur ne prouve rien : il se choisit librement
 * dans la requête. Un fichier annoncé `image/png` peut être n'importe quoi, et
 * le servir ensuite avec ce type revient à laisser un tiers décider de ce que
 * le navigateur d'un visiteur va interpréter. On lit donc les premiers octets.
 *
 * Trois formats, et pas de SVG. Un SVG est un document XML : il porte des
 * scripts, des références externes, et s'exécute dans le contexte du domaine
 * qui le sert. Aucun salon n'a besoin de vectoriel pour montrer sa devanture.
 */
public enum TypeImage {

    JPEG("image/jpeg", ".jpg"),
    PNG("image/png", ".png"),
    WEBP("image/webp", ".webp");

    private final String mime;
    private final String extension;

    TypeImage(String mime, String extension) {
        this.mime = mime;
        this.extension = extension;
    }

    public String mime() {
        return mime;
    }

    public String extension() {
        return extension;
    }

    /** Reconnaît le format d'après les premiers octets, ou rend null. */
    public static TypeImage reconnaitre(byte[] contenu) {
        if (contenu == null || contenu.length < 12) return null;

        // JPEG : FF D8 FF
        if (octets(contenu, 0, 0xFF, 0xD8, 0xFF)) return JPEG;

        // PNG : 89 50 4E 47 0D 0A 1A 0A
        if (octets(contenu, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return PNG;

        // WebP : « RIFF » … « WEBP », le second à l'octet 8.
        if (octets(contenu, 0, 'R', 'I', 'F', 'F') && octets(contenu, 8, 'W', 'E', 'B', 'P')) {
            return WEBP;
        }

        return null;
    }

    private static boolean octets(byte[] contenu, int depuis, int... attendus) {
        if (contenu.length < depuis + attendus.length) return false;
        for (int i = 0; i < attendus.length; i++) {
            if ((contenu[depuis + i] & 0xFF) != attendus[i]) return false;
        }
        return true;
    }
}
