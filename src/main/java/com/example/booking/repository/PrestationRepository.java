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
}
