package com.example.booking.repository;

import com.example.booking.model.RepereGeo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RepereGeoRepository extends JpaRepository<RepereGeo, Long> {

    /**
     * Repère d'un quartier précis.
     *
     * La comparaison passe par normaliser_libelle : « Guéliz », « Gueliz » et
     * « GUÉLIZ » sont le même quartier, et un conseiller qui saisit à la main
     * n'a pas à deviner l'orthographe de référence. La fonction est déclarée
     * IMMUTABLE et l'index unique de la table est construit dessus, si bien
     * que cette recherche s'y appuie directement.
     */
    @Query(value = """
            SELECT * FROM repere_geo r
             WHERE r.quartier IS NOT NULL
               AND normaliser_libelle(r.ville)    = normaliser_libelle(CAST(:ville AS varchar))
               AND normaliser_libelle(r.quartier) = normaliser_libelle(CAST(:quartier AS varchar))
             LIMIT 1
            """, nativeQuery = true)
    Optional<RepereGeo> duQuartier(@Param("ville") String ville, @Param("quartier") String quartier);

    /** Repère de la ville entière — le repli quand le quartier est inconnu ou absent. */
    @Query(value = """
            SELECT * FROM repere_geo r
             WHERE r.quartier IS NULL
               AND normaliser_libelle(r.ville) = normaliser_libelle(CAST(:ville AS varchar))
             LIMIT 1
            """, nativeQuery = true)
    Optional<RepereGeo> deLaVille(@Param("ville") String ville);
}
