package com.example.booking.service;

import com.example.booking.dto.ClientSalon;
import com.example.booking.model.NoteClient;
import com.example.booking.model.Reservation;
import com.example.booking.repository.NoteClientRepository;
import com.example.booking.repository.ReservationRepository;
import com.example.booking.repository.SalonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ce qu'un salon sait de ses clients.
 *
 * Tout est calculé depuis les réservations, à la demande. Une table de clients
 * tenue à jour en parallèle dériverait au premier oubli, et un compteur de
 * visites faux est pire qu'absent : c'est dessus qu'on s'appuie pour
 * reconnaître un habitué, ou pour se méfier de quelqu'un qui ne vient jamais.
 *
 * Seule la note est stockée — elle ne se déduit de rien.
 */
@Service
@Transactional(readOnly = true)
public class FicheClientService {

    private final ReservationRepository reservations;
    private final NoteClientRepository notes;
    private final SalonRepository salons;
    private final CurrentUserService utilisateurCourant;

    public FicheClientService(ReservationRepository reservations,
                              NoteClientRepository notes,
                              SalonRepository salons,
                              CurrentUserService utilisateurCourant) {
        this.reservations = reservations;
        this.notes = notes;
        this.salons = salons;
        this.utilisateurCourant = utilisateurCourant;
    }

    /**
     * Les clients du salon, du plus récemment venu au plus ancien.
     *
     * Sans historique : une liste de deux cents fiches ne va pas transporter
     * deux mille lignes de rendez-vous pour n'en afficher aucune.
     */
    public List<ClientSalon> lister(Long salonId, String q) {
        Map<String, String> parCle = notes.findBySalonId(salonId).stream()
                .collect(Collectors.toMap(NoteClient::getCle, NoteClient::getTexte, (a, b) -> a));

        return reservations.clientsDuSalon(salonId, vide(q), null).stream()
                .map(l -> depuisLigne(l, parCle.get((String) l[0]), null))
                .toList();
    }

    /** Une fiche, historique compris. */
    public ClientSalon fiche(Long salonId, String cle) {
        // Filtré en base, et non après coup : agréger tous les clients du
        // salon pour n'en garder qu'un ferait payer l'ouverture d'une fiche au
        // prix de la liste entière.
        List<Object[]> lignes = reservations.clientsDuSalon(salonId, null, cle);
        if (lignes.isEmpty()) {
            throw new NoSuchElementException("Ce client n'a aucun rendez-vous dans ce salon");
        }

        List<ClientSalon.ReservationResume> historique =
                reservations.reservationsDuClient(salonId, cle).stream()
                        .map(FicheClientService::resumer)
                        .toList();

        String note = notes.findBySalonIdAndCle(salonId, cle).map(NoteClient::getTexte).orElse(null);
        return depuisLigne(lignes.get(0), note, historique);
    }

    /**
     * Enregistre la note du salon. Un texte vide l'efface.
     *
     * Effacer plutôt que garder une chaîne vide : une note vide qui subsiste
     * laisse croire qu'il y a quelque chose à lire, et fait rouvrir la fiche
     * pour rien.
     */
    @Transactional
    public String enregistrerNote(Long salonId, String cle, String texte) {
        String propre = texte == null ? "" : texte.trim();

        if (propre.isEmpty()) {
            notes.findBySalonIdAndCle(salonId, cle).ifPresent(notes::delete);
            return null;
        }

        NoteClient note = notes.findBySalonIdAndCle(salonId, cle)
                .orElseGet(() -> NoteClient.builder()
                        .salon(salons.findById(salonId)
                                .orElseThrow(() -> new NoSuchElementException("Salon introuvable")))
                        .cle(cle)
                        .build());
        note.setTexte(propre);
        note.setMajLe(Instant.now());
        note.setMajPar(utilisateurCourant.getOrCreate());
        return notes.save(note).getTexte();
    }

    /* ---------- Mappers ---------- */

    private static ClientSalon depuisLigne(Object[] l, String note,
                                           List<ClientSalon.ReservationResume> historique) {
        return new ClientSalon(
                (String) l[0],
                (String) l[1],
                (String) l[2],
                nombre(l[3]), nombre(l[4]), nombre(l[5]),
                (Instant) l[6], (Instant) l[7],
                l[8] == null ? BigDecimal.ZERO : (BigDecimal) l[8],
                note,
                historique);
    }

    /**
     * count() rend un Long, sum() un BigDecimal, et le pilote peut choisir
     * l'un ou l'autre selon la version. Passer par Number évite un
     * ClassCastException qui ne se verrait qu'en production.
     */
    private static int nombre(Object valeur) {
        return valeur == null ? 0 : ((Number) valeur).intValue();
    }

    private static ClientSalon.ReservationResume resumer(Reservation r) {
        return new ClientSalon.ReservationResume(
                r.getId(),
                r.getDebut(),
                r.getNomPrestationFige(),
                r.getEmploye() != null
                        ? (r.getEmploye().getPrenom() + " " + r.getEmploye().getNom()).trim()
                        : null,
                r.getStatut() != null ? r.getStatut().name() : null,
                r.getPrixFige(),
                r.getOrigine() != null ? r.getOrigine().name() : null);
    }

    private static String vide(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
