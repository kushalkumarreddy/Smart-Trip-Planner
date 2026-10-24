package com.example.tripplanner.service;

import com.example.tripplanner.dto.*;
import com.example.tripplanner.entity.*;
import com.example.tripplanner.exception.BadRequestException;
import com.example.tripplanner.exception.ResourceNotFoundException;
import com.example.tripplanner.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Trip Version History Service Tests")
class TripVersionServiceTest {

    @Mock
    private TripRepository tripRepository;
    @Mock
    private DestinationRepository destinationRepository;
    @Mock
    private ItineraryItemRepository itineraryItemRepository;
    @Mock
    private TripVersionRepository tripVersionRepository;
    @Mock
    private RouteService routeService;
    @Mock
    private ItineraryService itineraryService;
    @Mock
    private TripService tripService;

    private TripVersionService tripVersionService;
    private ObjectMapper objectMapper;

    private User owner;
    private User collaborator;
    private Trip trip;
    private Destination dest1;
    private Destination dest2;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        tripVersionService = new TripVersionService(
                tripRepository,
                destinationRepository,
                itineraryItemRepository,
                tripVersionRepository,
                routeService,
                itineraryService,
                tripService
        );

        owner = new User();
        owner.setId(1L);
        owner.setName("Alice Traveler");
        owner.setEmail("alice@example.com");

        collaborator = new User();
        collaborator.setId(2L);
        collaborator.setName("Bob Viewer");
        collaborator.setEmail("bob@example.com");

        trip = new Trip();
        trip.setId(100L);
        trip.setTripName("Karnataka Heritage Tour");
        trip.setUser(owner);
        trip.setStartLocation("Bangalore, Karnataka");
        trip.setStartLat(12.9716);
        trip.setStartLng(77.5946);
        trip.setStartDate(LocalDate.of(2026, 11, 1));
        trip.setEndDate(LocalDate.of(2026, 11, 5));
        trip.setTransportMode("CAR");
        trip.setTravelStyle("BALANCED");
        trip.setTravelersCount(2);

        dest1 = new Destination();
        dest1.setId(10L);
        dest1.setTrip(trip);
        dest1.setName("Mysore");
        dest1.setLatitude(12.2958);
        dest1.setLongitude(76.6394);
        dest1.setSequenceOrder(0);
        dest1.setStayDurationDays(2);
        dest1.setStayDurationNights(1);
        dest1.setHotelName("Grand Mercure Mysore");

        dest2 = new Destination();
        dest2.setId(11L);
        dest2.setTrip(trip);
        dest2.setName("Coorg");
        dest2.setLatitude(12.3375);
        dest2.setLongitude(75.8069);
        dest2.setSequenceOrder(1);
        dest2.setStayDurationDays(2);
        dest2.setStayDurationNights(2);
        dest2.setHotelName("Heritage Resort Coorg");

        List<Destination> dests = new ArrayList<>();
        dests.add(dest1);
        dests.add(dest2);
        trip.setDestinations(dests);
    }

    @Test
    @DisplayName("createSnapshot creates version 1 with full snapshot JSON and author metadata")
    void testCreateSnapshotSuccess() throws Exception {
        when(tripVersionRepository.findMaxVersionNumberByTripId(100L)).thenReturn(0);
        when(tripVersionRepository.findByTripOrderByVersionNumberAsc(trip)).thenReturn(Collections.emptyList());
        when(tripVersionRepository.save(any(TripVersion.class))).thenAnswer(inv -> {
            TripVersion v = inv.getArgument(0);
            v.setId(501L);
            v.setCreatedAt(LocalDateTime.now());
            return v;
        });

        TripVersion version = tripVersionService.createSnapshot(
                trip,
                "Initial trip creation",
                "TRIP_CREATE",
                owner
        );

        assertThat(version).isNotNull();
        assertThat(version.getVersionNumber()).isEqualTo(1);
        assertThat(version.getChangeDescription()).isEqualTo("Initial trip creation");
        assertThat(version.getChangeSource()).isEqualTo("TRIP_CREATE");
        assertThat(version.getCreatedByName()).isEqualTo("Alice Traveler");
        assertThat(version.getCreatedByEmail()).isEqualTo("alice@example.com");

        // Verify snapshot content
        TripSnapshotDto snap = objectMapper.readValue(version.getSnapshotData(), TripSnapshotDto.class);
        assertThat(snap.getTripId()).isEqualTo(100L);
        assertThat(snap.getStartLocation()).isEqualTo("Bangalore, Karnataka");
        assertThat(snap.getDestinations()).hasSize(2);
        assertThat(snap.getDestinations().get(0).getName()).isEqualTo("Mysore");
        assertThat(snap.getDestinations().get(0).getHotelName()).isEqualTo("Grand Mercure Mysore");
        assertThat(snap.getDestinations().get(1).getName()).isEqualTo("Coorg");
    }

    @Test
    @DisplayName("enforceRetentionPolicy removes oldest versions when count exceeds limit 30")
    void testEnforceRetentionPolicy() {
        when(tripVersionRepository.countByTrip(trip)).thenReturn(35L);

        List<TripVersion> excessList = new ArrayList<>();
        for (int i = 1; i <= 35; i++) {
            TripVersion tv = new TripVersion();
            tv.setId((long) i);
            tv.setVersionNumber(i);
            excessList.add(tv);
        }
        when(tripVersionRepository.findByTripOrderByVersionNumberAsc(trip)).thenReturn(excessList);
        when(tripVersionRepository.save(any(TripVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        tripVersionService.createSnapshot(trip, "Another edit", "EDIT", owner);

        // Max limit is 30, so 35 - 30 = 5 oldest versions should be deleted
        ArgumentCaptor<List<TripVersion>> captor = ArgumentCaptor.forClass(List.class);
        verify(tripVersionRepository).deleteAll(captor.capture());
        assertThat(captor.getValue()).hasSize(5);
        assertThat(captor.getValue().get(0).getVersionNumber()).isEqualTo(1);
        assertThat(captor.getValue().get(4).getVersionNumber()).isEqualTo(5);
    }

    @Test
    @DisplayName("getTripVersions returns list sorted newest first with formatted summaries")
    void testGetTripVersions() throws Exception {
        when(tripRepository.findById(100L)).thenReturn(Optional.of(trip));
        when(tripVersionRepository.findMaxVersionNumberByTripId(100L)).thenReturn(2);

        TripSnapshotDto snap1 = new TripSnapshotDto();
        snap1.setTripId(100L);
        snap1.setStartLocation("Bangalore");
        TripSnapshotDto.DestinationSnapshotDto dSnap1 = new TripSnapshotDto.DestinationSnapshotDto();
        dSnap1.setName("Mysore");
        snap1.setDestinations(Collections.singletonList(dSnap1));
        snap1.setTotalDistance(145.0);
        snap1.setTotalTravelTimeMinutes(180);

        TripVersion v1 = new TripVersion(trip, 1, "Initial", "CREATE", "Alice", "alice@example.com",
                objectMapper.writeValueAsString(snap1));
        v1.setId(1L);
        v1.setCreatedAt(LocalDateTime.of(2026, 10, 1, 10, 0));

        TripSnapshotDto snap2 = new TripSnapshotDto();
        snap2.setTripId(100L);
        snap2.setStartLocation("Bangalore");
        TripSnapshotDto.DestinationSnapshotDto dSnap2A = new TripSnapshotDto.DestinationSnapshotDto();
        dSnap2A.setName("Mysore");
        TripSnapshotDto.DestinationSnapshotDto dSnap2B = new TripSnapshotDto.DestinationSnapshotDto();
        dSnap2B.setName("Coorg");
        snap2.setDestinations(Arrays.asList(dSnap2A, dSnap2B));
        snap2.setTotalDistance(260.0);
        snap2.setTotalTravelTimeMinutes(330);

        TripVersion v2 = new TripVersion(trip, 2, "Added Coorg", "ADD_STOP", "Alice", "alice@example.com",
                objectMapper.writeValueAsString(snap2));
        v2.setId(2L);
        v2.setCreatedAt(LocalDateTime.of(2026, 10, 1, 12, 0));

        when(tripVersionRepository.findByTripOrderByVersionNumberDesc(trip)).thenReturn(Arrays.asList(v2, v1));

        List<TripVersionSummaryDto> summaries = tripVersionService.getTripVersions(100L, owner);

        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(0).getVersionNumber()).isEqualTo(2);
        assertThat(summaries.get(0).getIsCurrent()).isTrue();
        assertThat(summaries.get(0).getDestinationsSummary()).isEqualTo("Bangalore → Mysore → Coorg");

        assertThat(summaries.get(1).getVersionNumber()).isEqualTo(1);
        assertThat(summaries.get(1).getIsCurrent()).isFalse();
        assertThat(summaries.get(1).getDestinationsSummary()).isEqualTo("Bangalore → Mysore");

        verify(tripService).checkTripAccess(trip, owner, "VIEWER");
    }

    @Test
    @DisplayName("compareVersions detects destination, stay duration, accommodation, and route differences")
    void testCompareVersionsDetailedDiff() throws Exception {
        when(tripRepository.findById(100L)).thenReturn(Optional.of(trip));

        // Snapshot A: Bangalore -> Mysore (1D/0N, Hotel A)
        TripSnapshotDto snapA = new TripSnapshotDto();
        snapA.setVersionNumber(1);
        snapA.setStartLocation("Bangalore");
        snapA.setStartDate(LocalDate.of(2026, 11, 1));
        snapA.setEndDate(LocalDate.of(2026, 11, 2));
        snapA.setTransportMode("CAR");
        snapA.setTravelStyle("BALANCED");
        snapA.setTotalDistance(140.0);
        snapA.setTotalTravelTimeMinutes(170);

        TripSnapshotDto.DestinationSnapshotDto dA = new TripSnapshotDto.DestinationSnapshotDto();
        dA.setName("Mysore");
        dA.setStayDurationDays(1);
        dA.setStayDurationNights(0);
        dA.setHotelName("Hotel A");
        snapA.setDestinations(Collections.singletonList(dA));

        TripVersion verA = new TripVersion(trip, 1, "v1", "CREATE", "Alice", "alice@test.com",
                objectMapper.writeValueAsString(snapA));
        verA.setId(10L);

        // Snapshot B: Bangalore -> Mysore (2D/1N, Hotel B) -> Coorg (added)
        TripSnapshotDto snapB = new TripSnapshotDto();
        snapB.setVersionNumber(2);
        snapB.setStartLocation("Bangalore");
        snapB.setStartDate(LocalDate.of(2026, 11, 1));
        snapB.setEndDate(LocalDate.of(2026, 11, 4));
        snapB.setTransportMode("CAR");
        snapB.setTravelStyle("BALANCED");
        snapB.setTotalDistance(260.0);
        snapB.setTotalTravelTimeMinutes(320);

        TripSnapshotDto.DestinationSnapshotDto dB1 = new TripSnapshotDto.DestinationSnapshotDto();
        dB1.setName("Mysore");
        dB1.setStayDurationDays(2);
        dB1.setStayDurationNights(1);
        dB1.setHotelName("Hotel B");

        TripSnapshotDto.DestinationSnapshotDto dB2 = new TripSnapshotDto.DestinationSnapshotDto();
        dB2.setName("Coorg");
        dB2.setStayDurationDays(2);
        dB2.setStayDurationNights(1);
        snapB.setDestinations(Arrays.asList(dB1, dB2));

        TripVersion verB = new TripVersion(trip, 2, "v2", "UPDATE", "Alice", "alice@test.com",
                objectMapper.writeValueAsString(snapB));
        verB.setId(20L);

        when(tripVersionRepository.findByIdAndTripId(10L, 100L)).thenReturn(Optional.of(verA));
        when(tripVersionRepository.findByIdAndTripId(20L, 100L)).thenReturn(Optional.of(verB));

        TripVersionComparisonDto diff = tripVersionService.compareVersions(100L, 10L, 20L, owner);

        assertThat(diff).isNotNull();
        assertThat(diff.isIdentical()).isFalse();
        // Destination addition check
        assertThat(diff.getDestinationChanges()).anyMatch(c -> c.contains("Added stop: Coorg"));
        // Stay duration check
        assertThat(diff.getStayDurationChanges()).anyMatch(c -> c.contains("Mysore stay changed: 1D/0N ➔ 2D/1N"));
        // Hotel accommodation check
        assertThat(diff.getAccommodationChanges()).anyMatch(c -> c.contains("Mysore hotel changed: 'Hotel A' ➔ 'Hotel B'"));
        // Date check
        assertThat(diff.getTravelDateChanges()).isNotEmpty();
        // Distance difference
        assertThat(diff.getDistanceDiffKm()).isEqualTo(120.0);
        assertThat(diff.getTravelTimeDiffMinutes()).isEqualTo(150);
    }

    @Test
    @DisplayName("restoreVersion creates pre-restore backup snapshot, restores state and recalculates routes")
    void testRestoreVersionSuccess() throws Exception {
        when(tripRepository.findById(100L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        // Version to restore: 1 destination (Mysore only)
        TripSnapshotDto snapToRestore = new TripSnapshotDto();
        snapToRestore.setTripId(100L);
        snapToRestore.setVersionNumber(1);
        snapToRestore.setStartLocation("Bangalore, Karnataka");
        snapToRestore.setStartLat(12.9716);
        snapToRestore.setStartLng(77.5946);
        snapToRestore.setStartDate(LocalDate.of(2026, 11, 1));
        snapToRestore.setEndDate(LocalDate.of(2026, 11, 3));
        snapToRestore.setTransportMode("CAR");

        TripSnapshotDto.DestinationSnapshotDto dSnap = new TripSnapshotDto.DestinationSnapshotDto();
        dSnap.setName("Mysore");
        dSnap.setLatitude(12.2958);
        dSnap.setLongitude(76.6394);
        dSnap.setSequenceOrder(0);
        snapToRestore.setDestinations(Collections.singletonList(dSnap));

        TripVersion v1 = new TripVersion(trip, 1, "Initial state", "CREATE", "Alice", "alice@test.com",
                objectMapper.writeValueAsString(snapToRestore));
        v1.setId(10L);

        when(tripVersionRepository.findByIdAndTripId(10L, 100L)).thenReturn(Optional.of(v1));
        when(tripVersionRepository.findMaxVersionNumberByTripId(100L)).thenReturn(3);

        RouteSummaryDto freshRoute = new RouteSummaryDto();
        freshRoute.setTotalDistanceKm(145.0);
        freshRoute.setTotalTravelTimeMinutes(180);
        when(routeService.calculateCompleteRoute(anyString(), anyList(), anyString())).thenReturn(freshRoute);

        TripResponseDto expectedResponse = new TripResponseDto();
        expectedResponse.setId(100L);
        expectedResponse.setTripName("Karnataka Heritage Tour");
        when(tripService.getTripDtoById(100L, owner)).thenReturn(expectedResponse);

        TripResponseDto result = tripVersionService.restoreVersion(100L, 10L, owner);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);

        // Verify pre-restore snapshot was taken
        ArgumentCaptor<TripVersion> versionCaptor = ArgumentCaptor.forClass(TripVersion.class);
        verify(tripVersionRepository).save(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getChangeSource()).isEqualTo("PRE_RESTORE_BACKUP");
        assertThat(versionCaptor.getValue().getChangeDescription()).contains("Automatic backup before restoring Version 1");

        // Verify destinations deleted and restored
        verify(destinationRepository).deleteByTripId(100L);
        verify(destinationRepository).save(any());

        // Verify routes recalculated
        verify(routeService).calculateCompleteRoute(anyString(), anyList(), anyString());
    }

    @Test
    @DisplayName("restoreVersion throws AccessDeniedException when collaborator does not have EDITOR permissions")
    void testRestoreVersionAccessDenied() {
        when(tripRepository.findById(100L)).thenReturn(Optional.of(trip));
        doThrow(new AccessDeniedException("You do not have permission to edit this trip"))
                .when(tripService).checkTripAccess(trip, collaborator, "EDITOR");

        assertThatThrownBy(() -> tripVersionService.restoreVersion(100L, 10L, collaborator))
                .isInstanceOf(AccessDeniedException.class);

        verify(tripVersionRepository, never()).save(any());
        verify(destinationRepository, never()).deleteByTripId(any());
    }

    @Test
    @DisplayName("restoreVersion throws BadRequestException when version belongs to another trip")
    void testRestoreCrossTripVersionBlocked() {
        when(tripRepository.findById(100L)).thenReturn(Optional.of(trip));
        when(tripVersionRepository.findByIdAndTripId(999L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripVersionService.restoreVersion(100L, 999L, owner))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Target version does not exist or does not belong to this trip");
    }
}
