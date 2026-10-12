package com.example.tripplanner.service;

import com.example.tripplanner.dto.*;
import com.example.tripplanner.entity.*;
import com.example.tripplanner.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TripServiceTest {

    @Mock
    private TripRepository tripRepository;
    @Mock
    private DestinationRepository destinationRepository;
    @Mock
    private PlaceRepository placeRepository;
    @Mock
    private ItineraryItemRepository itineraryItemRepository;
    @Mock
    private RouteService routeService;
    @Mock
    private ItineraryService itineraryService;
    @Mock
    private RecommendationService recommendationService;
    @Mock
    private GoogleMapsService googleMapsService;
    @Mock
    private TripCollaboratorRepository tripCollaboratorRepository;
    @Mock
    private TripSuggestionRepository tripSuggestionRepository;

    @InjectMocks
    private TripService tripService;

    private User owner;
    private User editor;
    private User viewer;
    private User unauthorizedUser;
    private Trip trip;
    private Destination dest1;
    private Destination dest2;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setName("Trip Owner");
        owner.setEmail("owner@example.com");

        editor = new User();
        editor.setId(2L);
        editor.setName("Trip Editor");
        editor.setEmail("editor@example.com");

        viewer = new User();
        viewer.setId(3L);
        viewer.setName("Trip Viewer");
        viewer.setEmail("viewer@example.com");

        unauthorizedUser = new User();
        unauthorizedUser.setId(99L);
        unauthorizedUser.setName("Stranger");
        unauthorizedUser.setEmail("stranger@example.com");

        trip = new Trip();
        trip.setId(10L);
        trip.setTripName("Southern Circuit");
        trip.setUser(owner);
        trip.setStartLocation("Bengaluru");
        trip.setStartDate(LocalDate.now());
        trip.setEndDate(LocalDate.now().plusDays(3));
        trip.setTransportMode("CAR");

        dest1 = new Destination("Mysore", 0);
        dest1.setId(101L);
        dest1.setTrip(trip);
        dest1.setDistanceFromPrevious(143.5);
        dest1.setTravelTimeFromPrevious(180);

        dest2 = new Destination("Coorg", 1);
        dest2.setId(102L);
        dest2.setTrip(trip);
        dest2.setDistanceFromPrevious(118.0);
        dest2.setTravelTimeFromPrevious(160);

        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(destinationRepository.findByTripOrderBySequenceOrderAsc(trip))
                .thenReturn(Arrays.asList(dest1, dest2));
        when(destinationRepository.findById(101L)).thenReturn(Optional.of(dest1));
        when(destinationRepository.findById(102L)).thenReturn(Optional.of(dest2));

        // Setup collaborator roles
        TripCollaborator editorCollab = new TripCollaborator(trip, editor.getName(), editor.getEmail(), "EDITOR");
        TripCollaborator viewerCollab = new TripCollaborator(trip, viewer.getName(), viewer.getEmail(), "VIEWER");

        when(tripCollaboratorRepository.findByTripAndUserEmail(trip, "editor@example.com"))
                .thenReturn(Optional.of(editorCollab));
        when(tripCollaboratorRepository.findByTripAndUserEmail(trip, "viewer@example.com"))
                .thenReturn(Optional.of(viewerCollab));
        when(tripCollaboratorRepository.findByTripAndUserEmail(trip, "stranger@example.com"))
                .thenReturn(Optional.empty());

        RouteSummaryDto defaultSummary = new RouteSummaryDto();
        defaultSummary.setTotalDistanceKm(261.5);
        defaultSummary.setTotalTravelTimeMinutes(340);
        when(routeService.calculateCompleteRoute(anyString(), anyList(), anyString()))
                .thenReturn(defaultSummary);
    }

    @Test
    @DisplayName("Authorization: Owner has permission to delete trip, editor/viewer cannot")
    void testAuthorizationForDeleteTrip() {
        // Owner can delete
        tripService.deleteTrip(10L, owner);
        verify(tripRepository).delete(trip);

        // Editor cannot delete (throws 403 Forbidden)
        assertThatThrownBy(() -> tripService.deleteTrip(10L, editor))
                .isInstanceOf(AccessDeniedException.class);

        // Viewer cannot delete
        assertThatThrownBy(() -> tripService.deleteTrip(10L, viewer))
                .isInstanceOf(AccessDeniedException.class);

        // Stranger cannot delete
        assertThatThrownBy(() -> tripService.deleteTrip(10L, unauthorizedUser))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Authorization: Editor can reorder destinations, viewer cannot")
    void testAuthorizationForReorderDestinations() {
        RouteSummaryDto mockSummary = new RouteSummaryDto();
        mockSummary.setTotalDistanceKm(260.0);
        mockSummary.setTotalTravelTimeMinutes(320);
        mockSummary.setSegments(Arrays.asList(
                new RouteSegmentDto("Bengaluru", "Coorg", 240.0, 300, ""),
                new RouteSegmentDto("Coorg", "Mysore", 118.0, 160, "")
        ));
        when(routeService.calculateCompleteRoute(anyString(), anyList(), anyString()))
                .thenReturn(mockSummary);
        when(destinationRepository.save(any(Destination.class))).thenAnswer(i -> i.getArgument(0));

        // Editor can reorder
        TripResponseDto res = tripService.reorderDestinations(10L, Arrays.asList(102L, 101L), editor);
        assertThat(res).isNotNull();

        // Viewer cannot modify trip (throws 403 Forbidden)
        assertThatThrownBy(() -> tripService.reorderDestinations(10L, Arrays.asList(102L, 101L), viewer))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Destination Reordering: Correctly updates sequence orders and segment metrics")
    void testDestinationReorderingUpdatesSegmentMetrics() {
        RouteSummaryDto mockSummary = new RouteSummaryDto();
        mockSummary.setTotalDistanceKm(358.0);
        mockSummary.setTotalTravelTimeMinutes(460);
        mockSummary.setSegments(Arrays.asList(
                new RouteSegmentDto("Bengaluru", "Coorg", 240.0, 300, ""),
                new RouteSegmentDto("Coorg", "Mysore", 118.0, 160, "")
        ));
        when(routeService.calculateCompleteRoute(anyString(), anyList(), anyString()))
                .thenReturn(mockSummary);
        when(destinationRepository.save(any(Destination.class))).thenAnswer(i -> i.getArgument(0));

        tripService.reorderDestinations(10L, Arrays.asList(102L, 101L), owner);

        // Verify dest2 is now first stop (order 0) with distance from origin
        assertThat(dest2.getSequenceOrder()).isEqualTo(0);
        assertThat(dest2.getDistanceFromPrevious()).isEqualTo(240.0);

        // Verify dest1 is now second stop (order 1) with distance from dest2
        assertThat(dest1.getSequenceOrder()).isEqualTo(1);
        assertThat(dest1.getDistanceFromPrevious()).isEqualTo(118.0);
    }

    @Test
    @DisplayName("Hotel update: Allows updating accommodation for a destination later")
    void testUpdateDestinationHotel() {
        tripService.updateDestinationHotel(10L, 101L, "Lalitha Mahal Palace", owner);

        assertThat(dest1.getHotelName()).isEqualTo("Lalitha Mahal Palace");
        verify(destinationRepository).save(dest1);
    }

    @Test
    @DisplayName("Packing Checklist: Adapts to regional climate (hill station warm clothes, etc.)")
    void testPackingChecklistGeneratesClimateAdaptedItems() {
        PackingChecklistDto checklist = tripService.getPackingChecklist(10L, owner);

        assertThat(checklist).isNotNull();
        assertThat(checklist.getCategories()).isNotEmpty();

        // Since destinations include "Coorg" (Hill Station), must include warm fleece/jacket
        boolean hasWarmJacket = checklist.getCategories().stream()
                .flatMap(c -> c.getItems().stream())
                .anyMatch(i -> i.getName().toLowerCase().contains("warm jacket") || i.getName().toLowerCase().contains("sweater"));

        assertThat(hasWarmJacket).isTrue();

        // Must include motion sickness medication for ghat roads
        boolean hasMotionMeds = checklist.getCategories().stream()
                .flatMap(c -> c.getItems().stream())
                .anyMatch(i -> i.getName().toLowerCase().contains("motion sickness"));

        assertThat(hasMotionMeds).isTrue();
    }

    @Test
    @DisplayName("Places along route: Discovers candidate spots within detour tolerance")
    void testGetPlacesAlongRouteCalculatesDetours() {
        when(googleMapsService.calculateRouteSegment(eq("Bengaluru"), eq("Mysore"), anyString()))
                .thenReturn(new GoogleMapsService.RouteCalculationResult(143.5, 180));
        when(googleMapsService.calculateRouteSegment(eq("Mysore"), eq("Coorg"), anyString()))
                .thenReturn(new GoogleMapsService.RouteCalculationResult(118.0, 160));

        // Candidate spot along Bengaluru -> Mysore
        SightseeingPlaceDto ranganathittu = new SightseeingPlaceDto();
        ranganathittu.setName("Ranganathittu Bird Sanctuary");
        ranganathittu.setCategory("Nature & Wildlife");
        ranganathittu.setRating(4.6);
        ranganathittu.setLatitude(12.4244);
        ranganathittu.setLongitude(76.6947);

        when(recommendationService.getRecommendedPlaces(eq("Bengaluru"), any(), anyString(), any()))
                .thenReturn(Collections.emptyList());
        when(recommendationService.getRecommendedPlaces(eq("Mysore"), any(), anyString(), any()))
                .thenReturn(Collections.singletonList(ranganathittu));

        // Legs with stop: Bengaluru -> Ranganathittu: 135km, Ranganathittu -> Mysore: 16km = 151km (baseline 143.5km -> detour 7.5km)
        when(googleMapsService.calculateRouteSegment(eq("Bengaluru"), eq("Ranganathittu Bird Sanctuary"), anyString()))
                .thenReturn(new GoogleMapsService.RouteCalculationResult(135.0, 170));
        when(googleMapsService.calculateRouteSegment(eq("Ranganathittu Bird Sanctuary"), eq("Mysore"), anyString()))
                .thenReturn(new GoogleMapsService.RouteCalculationResult(16.0, 25));

        List<PlaceAlongRouteDto> stops = tripService.getPlacesAlongRoute(10L, 25.0, owner);

        assertThat(stops).isNotEmpty();
        PlaceAlongRouteDto candidate = stops.get(0);
        assertThat(candidate.getName()).isEqualTo("Ranganathittu Bird Sanctuary");
        assertThat(candidate.getDetourDistanceKm()).isLessThanOrEqualTo(25.0);
        assertThat(candidate.getFormattedDetour()).contains("km");
    }
}
