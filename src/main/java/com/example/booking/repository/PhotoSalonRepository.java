package com.example.booking.repository;

import com.example.booking.model.PhotoSalon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PhotoSalonRepository extends JpaRepository<PhotoSalon, Long> {

    List<PhotoSalon> findBySalonIdOrderByOrdreAscIdAsc(Long salonId);

    Optional<PhotoSalon> findByFichier(String fichier);

    long countBySalonId(Long salonId);

    /**
     * Photos de plusieurs salons, en un appel.
     *
     * Une liste de résultats affiche vingt cartes ; vingt requêtes pour une
     * couverture chacune se paient sur la route la plus consultée du produit.
     */
    @Query("""
            SELECT p FROM PhotoSalon p
            WHERE p.salon.id IN :salonIds
            ORDER BY p.salon.id, p.ordre, p.id
            """)
    List<PhotoSalon> desSalons(@Param("salonIds") Collection<Long> salonIds);
}
