package com.example.booking.service;

import com.example.booking.dto.CreneauDisponible;
import com.example.booking.model.Absence;
import com.example.booking.model.Employe;
import com.example.booking.model.EmployePrestation;
import com.example.booking.model.HoraireOuverture;
import com.example.booking.model.Prestation;
import com.example.booking.model.Reservation;
import com.example.booking.model.Salon;
import com.example.booking.repository.AbsenceRepository;
import com.example.booking.repository.EmployePrestationRepository;
import com.example.booking.repository.EmployeRepository;
import com.example.booking.repository.HoraireOuvertureRepository;
import com.example.booking.repository.PrestationRepository;
import com.example.booking.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests du moteur de disponibilité.
 *
 * Un bug ici ne provoque pas une erreur visible : il envoie un client se
 * présenter pour rien, ou fait perdre des créneaux au salon. Les cas limites
 * comptent donc autant que le cas nominal.
 */
class DisponibiliteServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Africa/Casablanca");
    private static final Long SALON_ID = 1L;
    private static final Long PRESTATION_ID = 10L;
    private static final Long EMPLOYE_ID = 100L;

    private PrestationRepository prestationRepository;
    private EmployeRepository employeRepository;
    private EmployePrestationRepository employePrestationRepository;
    private HoraireOuvertureRepository horaireRepository;
    private AbsenceRepository absenceRepository;
    private ReservationRepository reservationRepository;
    private DisponibiliteService service;

    /** Volontairement loin dans le futur : le délai de prévenance ne doit pas interférer. */
    private LocalDate date;
    private Salon salon;
    private Employe employe;
    private Prestation prestation;

    @BeforeEach
    void setUp() {
        prestationRepository = mock(PrestationRepository.class);
        employeRepository = mock(EmployeRepository.class);
        employePrestationRepository = mock(EmployePrestationRepository.class);
        horaireRepository = mock(HoraireOuvertureRepository.class);
        absenceRepository = mock(AbsenceRepository.class);
        reservationRepository = mock(ReservationRepository.class);

        service = new DisponibiliteService(
                prestationRepository, employeRepository, employePrestationRepository,
                horaireRepository, absenceRepository, reservationRepository,
                "Africa/Casablanca", 120, 60);

        date = LocalDate.now(ZONE).plusDays(10);

        salon = Salon.builder().id(SALON_ID).nom("Dar Zine").build();
        employe = Employe.builder().id(EMPLOYE_ID).salon(salon).prenom("Sofia").actif(true).build();
        prestation = Prestation.builder()
                .id(PRESTATION_ID).salon(salon).nom("Coupe femme")
                .prix(BigDecimal.valueOf(200)).dureeMinutes(45).actif(true).build();

        when(prestationRepository.findById(PRESTATION_ID)).thenReturn(Optional.of(prestation));
        when(employeRepository.findCapables(SALON_ID, PRESTATION_ID)).thenReturn(List.of(employe));
        when(employeRepository.findById(EMPLOYE_ID)).thenReturn(Optional.of(employe));
        when(employePrestationRepository.findByEmployeIdAndPrestationId(EMPLOYE_ID, PRESTATION_ID))
                .thenReturn(Optional.of(EmployePrestation.builder()
                        .employe(employe).prestation(prestation).build()));

        // Pas d'horaire personnel : l'employé hérite de ceux du salon, 09:00-12:00.
        when(horaireRepository.findByEmployeIdAndJourSemaineOrderByHeureDebut(eq(EMPLOYE_ID), any()))
                .thenReturn(List.of());
        when(horaireRepository.findBySalonIdAndJourSemaineOrderByHeureDebut(eq(SALON_ID), any()))
                .thenReturn(List.of(horaire(LocalTime.of(9, 0), LocalTime.of(12, 0))));

        when(reservationRepository.occupationEmploye(anyLong(), any(), any(), any())).thenReturn(List.of());
        when(absenceRepository.chevauchant(anyLong(), anyLong(), any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Génère un créneau tous les quarts d'heure, jusqu'à ce que la prestation tienne dans la plage")
    void creneauxNominaux() {
        List<String> heures = heures(service.creneaux(SALON_ID, PRESTATION_ID, date, null));

        // 09:00 à 11:15 : 11:15 + 45 min = 12:00, exactement la fin de plage.
        assertThat(heures).containsExactly(
                "09:00", "09:15", "09:30", "09:45", "10:00",
                "10:15", "10:30", "10:45", "11:00", "11:15");
    }

    @Test
    @DisplayName("Le dernier créneau finit pile à la fermeture, pas après")
    void dernierCreneauTientDansLaPlage() {
        List<String> heures = heures(service.creneaux(SALON_ID, PRESTATION_ID, date, null));

        assertThat(heures).endsWith("11:15");
        // 11:30 + 45 min = 12:15, au-delà de la fermeture.
        assertThat(heures).doesNotContain("11:30", "11:45", "12:00");
    }

    @Test
    @DisplayName("Une réservation existante retire les créneaux qui la chevauchent, et eux seuls")
    void chevauchementAvecReservation() {
        Instant debutOccupe = instant(10, 0);
        when(reservationRepository.occupationEmploye(eq(EMPLOYE_ID), any(), any(), any()))
                .thenReturn(List.of(Reservation.builder()
                        .debut(debutOccupe)
                        .fin(instant(10, 45))
                        .build()));

        List<String> heures = heures(service.creneaux(SALON_ID, PRESTATION_ID, date, null));

        // 09:15 + 45 min = 10:00 : le créneau touche la réservation sans la chevaucher.
        // C'est le cas que rate un test de chevauchement écrit avec <= au lieu de <.
        assertThat(heures).containsExactly("09:00", "09:15", "10:45", "11:00", "11:15");
    }

    @Test
    @DisplayName("Une absence couvrant la journée vide l'agenda")
    void absenceCouvrante() {
        when(absenceRepository.chevauchant(eq(EMPLOYE_ID), eq(SALON_ID), any(), any()))
                .thenReturn(List.of(Absence.builder()
                        .debut(instant(8, 0))
                        .fin(instant(13, 0))
                        .motif("Formation")
                        .build()));

        assertThat(service.creneaux(SALON_ID, PRESTATION_ID, date, null)).isEmpty();
    }

    @Test
    @DisplayName("Aucun horaire ce jour-là : le praticien ne travaille pas")
    void aucunHoraire() {
        when(horaireRepository.findBySalonIdAndJourSemaineOrderByHeureDebut(eq(SALON_ID), any()))
                .thenReturn(List.of());

        assertThat(service.creneaux(SALON_ID, PRESTATION_ID, date, null)).isEmpty();
    }

    @Test
    @DisplayName("Un praticien qui ne réalise pas la prestation n'est jamais proposé")
    void praticienNonHabilite() {
        when(employePrestationRepository.findByEmployeIdAndPrestationId(EMPLOYE_ID, PRESTATION_ID))
                .thenReturn(Optional.empty());

        assertThat(service.creneaux(SALON_ID, PRESTATION_ID, date, EMPLOYE_ID)).isEmpty();
    }

    @Test
    @DisplayName("La durée propre au praticien prime sur celle de la prestation")
    void dureeSpecifique() {
        when(employePrestationRepository.findByEmployeIdAndPrestationId(EMPLOYE_ID, PRESTATION_ID))
                .thenReturn(Optional.of(EmployePrestation.builder()
                        .employe(employe).prestation(prestation)
                        .dureeSpecifiqueMinutes(180)   // 3 h au lieu de 45 min
                        .build()));

        // Sur une plage de 3 h, une prestation de 3 h ne tient qu'une fois, à l'ouverture.
        assertThat(heures(service.creneaux(SALON_ID, PRESTATION_ID, date, null)))
                .containsExactly("09:00");
    }

    @Test
    @DisplayName("Une prestation désactivée n'est pas réservable")
    void prestationInactive() {
        prestation.setActif(false);
        assertThat(service.creneaux(SALON_ID, PRESTATION_ID, date, null)).isEmpty();
    }

    @Test
    @DisplayName("Une date passée ou au-delà de l'horizon ne renvoie rien")
    void horsHorizon() {
        assertThat(service.creneaux(SALON_ID, PRESTATION_ID, LocalDate.now(ZONE).minusDays(1), null)).isEmpty();
        assertThat(service.creneaux(SALON_ID, PRESTATION_ID, LocalDate.now(ZONE).plusDays(90), null)).isEmpty();
    }

    @Test
    @DisplayName("Le délai de prévenance masque les créneaux trop proches")
    void delaiPrevenance() {
        // Service configuré avec un délai de 100 ans : plus rien n'est réservable.
        DisponibiliteService strict = new DisponibiliteService(
                prestationRepository, employeRepository, employePrestationRepository,
                horaireRepository, absenceRepository, reservationRepository,
                "Africa/Casablanca", 60L * 24 * 365 * 100, 60);

        assertThat(strict.creneaux(SALON_ID, PRESTATION_ID, date, null)).isEmpty();
    }

    @Test
    @DisplayName("Deux praticiens libres à la même heure ne produisent qu'un créneau")
    void dedoublonnageSansPreference() {
        Employe autre = Employe.builder().id(200L).salon(salon).prenom("Youssef").actif(true).build();
        when(employeRepository.findCapables(SALON_ID, PRESTATION_ID)).thenReturn(List.of(employe, autre));
        when(employePrestationRepository.findByEmployeIdAndPrestationId(200L, PRESTATION_ID))
                .thenReturn(Optional.of(EmployePrestation.builder()
                        .employe(autre).prestation(prestation).build()));
        when(horaireRepository.findByEmployeIdAndJourSemaineOrderByHeureDebut(eq(200L), any()))
                .thenReturn(List.of());

        List<CreneauDisponible> creneaux = service.creneaux(SALON_ID, PRESTATION_ID, date, null);

        assertThat(heures(creneaux)).containsExactly(
                "09:00", "09:15", "09:30", "09:45", "10:00",
                "10:15", "10:30", "10:45", "11:00", "11:15");
        assertThat(creneaux.get(0).employesDisponibles())
                .as("l'heure n'apparaît qu'une fois, mais porte les deux praticiens")
                .containsExactly(EMPLOYE_ID, 200L);
    }

    @Test
    @DisplayName("Une pause déjeuner crée un trou, pas une plage continue")
    void pauseDejeuner() {
        when(horaireRepository.findBySalonIdAndJourSemaineOrderByHeureDebut(eq(SALON_ID), any()))
                .thenReturn(List.of(
                        horaire(LocalTime.of(9, 0), LocalTime.of(10, 0)),
                        horaire(LocalTime.of(14, 0), LocalTime.of(15, 0))));

        assertThat(heures(service.creneaux(SALON_ID, PRESTATION_ID, date, null)))
                .containsExactly("09:00", "09:15", "14:00", "14:15");
    }

    /* ---------- Helpers ---------- */

    private HoraireOuverture horaire(LocalTime debut, LocalTime fin) {
        return HoraireOuverture.builder()
                .salon(salon)
                .jourSemaine(DayOfWeek.MONDAY) // ignoré : le dépôt est mocké sur any()
                .heureDebut(debut).heureFin(fin)
                .build();
    }

    private Instant instant(int heure, int minute) {
        return date.atTime(heure, minute).atZone(ZONE).toInstant();
    }

    private List<String> heures(List<CreneauDisponible> creneaux) {
        return creneaux.stream().map(c -> c.heure().toString()).toList();
    }
}
