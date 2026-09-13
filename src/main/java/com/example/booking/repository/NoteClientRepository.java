package com.example.booking.repository;

import com.example.booking.model.NoteClient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NoteClientRepository extends JpaRepository<NoteClient, Long> {

    /**
     * Toutes les lectures filtrent sur le salon.
     *
     * Ce n'est pas une commodité de requête : une note écrite par un salon ne
     * doit jamais être lisible par un autre, et l'oubli du filtre serait
     * invisible — la note s'afficherait simplement, sur la fiche du bon client,
     * dans le mauvais établissement.
     */
    Optional<NoteClient> findBySalonIdAndCle(Long salonId, String cle);

    List<NoteClient> findBySalonId(Long salonId);
}
