package com.example.booking.photos;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Réduit une image à une taille que le web peut porter.
 *
 * Tout ressort en JPEG : c'est le format que tous les navigateurs lisent, qui
 * pèse le moins pour une photo, et qui n'a pas de transparence à préserver —
 * une photo de salon n'en a pas. Le PNG d'un téléphone (capture d'écran) pèse
 * dix fois plus pour rien.
 */
final class Redimensionnement {

    static final int COTE_MAX = 1600;
    static final int COTE_VIGNETTE = 480;
    static final float QUALITE = 0.82f;

    record Resultat(byte[] principale, byte[] vignette, TypeImage type) {}

    private Redimensionnement() {}

    static Resultat reduire(byte[] contenu, TypeImage type) {
        if (type == TypeImage.WEBP) {
            return new Resultat(contenu, null, type);
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(contenu));
            if (source == null) {
                return new Resultat(contenu, null, type);
            }
            byte[] principale = jpeg(redimensionner(source, COTE_MAX));
            byte[] vignette = jpeg(redimensionner(source, COTE_VIGNETTE));
            return new Resultat(principale, vignette, TypeImage.JPEG);
        } catch (IOException e) {
            /* Une image qu'on ne sait pas relire est gardée telle quelle :
               mieux vaut une photo lourde qu'un téléversement refusé pour une
               raison que le gérant ne peut pas corriger. */
            return new Resultat(contenu, null, type);
        }
    }

    private static BufferedImage redimensionner(BufferedImage source, int coteMax) {
        int l = source.getWidth(), h = source.getHeight();
        double facteur = Math.min(1.0, (double) coteMax / Math.max(l, h));
        int nl = Math.max(1, (int) Math.round(l * facteur));
        int nh = Math.max(1, (int) Math.round(h * facteur));
        /* TYPE_INT_RGB : aplatit une éventuelle transparence sur du noir, ce
           qu'un JPEG ferait de toute façon — autant le décider. */
        BufferedImage cible = new BufferedImage(nl, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = cible.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, nl, nh);
        g.drawImage(source, 0, 0, nl, nh, null);
        g.dispose();
        return cible;
    }

    private static byte[] jpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("Aucun encodeur JPEG");
        ImageWriter writer = writers.next();
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(sortie)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITE);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return sortie.toByteArray();
    }
}
