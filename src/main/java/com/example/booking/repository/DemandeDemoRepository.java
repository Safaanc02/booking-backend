package com.example.booking.repository;

import com.example.booking.model.DemandeDemo;
import com.example.booking.model.enums.StatutDemandeDemo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface DemandeDemoRepository extends JpaRepository<DemandeDemo, Long> {

    /**
     * Les demandes d'un statut, les plus récentes d'abord.
     *
     * Jointures explicites sur le salon et l'agent : la projection les lit
     * pour l'écran d'administration, et une association nulle — le cas de
     * toute demande non convertie — ferait disparaître la ligne entière avec
     * la jointure interne implicite de JPQL.
     */
    @Query("""
            select d from DemandeDemo d
            left join fetch d.salon
            left join fetch d.traitePar
            where d.statut = :statut
            order by d.creeLe desc
            """)
    Page<DemandeDemo> parStatut(@Param("statut") StatutDemandeDemo statut, Pageable pageable);

    /** Garde anti-doublon : une même adresse ne repasse pas le formulaire aussitôt. */
    @Query("""
            select count(d) > 0 from DemandeDemo d
            where lower(d.email) = lower(:email) and d.creeLe > :depuis
            """)
    boolean dejaDemandeDepuis(@Param("email") String email, @Param("depuis") Instant depuis);

    /**
     * La demande ouverte la plus récente pour une adresse.
     *
     * Sert à refermer la boucle au référencement. Les demandes déjà converties
     * ou écartées sont exclues : rattacher un second salon à une demande close
     * fausserait la mesure du rendement du formulaire.
     */
    @Query("""
            select d from DemandeDemo d
            where lower(d.email) = lower(:email)
              and d.statut not in (
                com.example.booking.model.enums.StatutDemandeDemo.CONVERTIE,
                com.example.booking.model.enums.StatutDemandeDemo.PERDUE)
            order by d.creeLe desc
            limit 1
            """)
    Optional<DemandeDemo> ouvertePour(@Param("email") String email);

    long countByStatut(StatutDemandeDemo statut);
}
