package com.example.booking.repository;

import com.example.booking.model.Prestation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PrestationRepository extends JpaRepository<Prestation, Long> {
    List<Prestation> findBySalonId(Long salonId);

    @Query("SELECT p.salon.id FROM Prestation p WHERE p.id = :id")
    Optional<Long> salonDeLaPrestation(@Param("id") Long id);

    /**
     * Prix d'entrée de plusieurs salons, en une seule requête.
     *
     * Une requête pour toute la page et non une par salon : la liste de
     * résultats en affiche vingt, et vingt allers-retours pour un chiffre
     * chacun se paient sur la route la plus consultée du produit.
     *
     * Seules les prestations actives comptent : annoncer « à partir de
     * 80 MAD » d'après une prestation retirée du catalogue est une promesse
     * qu'aucun créneau ne peut tenir.
     */
    @Query("""
            SELECT p.salon.id, MIN(p.prix) FROM Prestation p
            WHERE p.salon.id IN :salonIds AND p.actif = true
            GROUP BY p.salon.id
            """)
    List<Object[]> prixDEntreeParSalon(@Param("salonIds") Collection<Long> salonIds);

    /**
     * Prestation la plus courte de chaque salon, pour calculer une prochaine
     * disponibilité représentative.
     *
     * La plus courte, et non la moins chère : c'est elle qui a le plus de
     * créneaux libres à montrer. Annoncer « libre demain » d'après un balayage
     * de deux heures donnerait un salon plus occupé qu'il ne l'est, et
     * découragerait un client qui n'en voulait qu'une demi-heure.
     *
     * En un seul appel pour toute la page : vingt salons, vingt requêtes pour
     * un identifiant chacune, ce serait sur la route la plus consultée du
     * produit.
     *
     * Le départage par identifiant n'est pas décoratif — deux prestations de
     * même durée rendraient un ordre instable, et la disponibilité annoncée
     * changerait d'un rafraîchissement à l'autre sans que rien n'ait bougé.
     */
    @Query("""
            SELECT p.salon.id, p.id FROM Prestation p
            WHERE p.salon.id IN :salonIds
              AND p.actif = true
              AND p.dureeMinutes = (
                  SELECT MIN(q.dureeMinutes) FROM Prestation q
                  WHERE q.salon.id = p.salon.id AND q.actif = true
              )
              AND p.id = (
                  SELECT MIN(r.id) FROM Prestation r
                  WHERE r.salon.id = p.salon.id AND r.actif = true
                    AND r.dureeMinutes = p.dureeMinutes
              )
            """)
    List<Object[]> prestationVitrineParSalon(@Param("salonIds") Collection<Long> salonIds);
}
