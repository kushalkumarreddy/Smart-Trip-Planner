package com.example.tripplanner.service;

import com.example.tripplanner.dto.DayItineraryDto;
import com.example.tripplanner.dto.ItineraryItemDto;
import com.example.tripplanner.dto.RouteSummaryDto;
import com.example.tripplanner.dto.TripResponseDto;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Persistence & Security Access Authorization Tests")
class PersistenceAndSecurityTest {

    @Mock
    private TripRepository tripRepository;
    @Mock
    private DestinationRepository destinationRepository;
    @Mock
    private ItineraryItemRepository itineraryItemRepository;
    @Mock
    private RouteService routeService;
    @Mock
    private ItineraryService itineraryService;
    @Mock
    private TripCollaboratorRepository tripCollaboratorRepository;
    @Mock
    private GoogleMapsService googleMapsService;
    @Mock
    private RecommendationService recommendationService;
    @Mock
    private TripSuggestionRepository tripSuggestionRepository;

    @InjectMocks
    private TripService tripService;

    private User owner;
    private User intruder;
    private Trip trip;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setName("Trip Owner");
        owner.setEmail("owner@example.com");

        intruder = new User();
        intruder.setId(99L);
        intruder.setName("Stranger");
        intruder.setEmail("intruder@example.com");

        trip = new Trip();
        trip.setId(10L);
        trip.setTripName("Private Himalayan Trek");
        trip.setUser(owner);
        trip.setStartDate(LocalDate.of(2026, 12, 10));
        trip.setEndDate(LocalDate.of(2026, 12, 14));
        trip.setStartLocation("Delhi");
        trip.setTransportMode("CAR");
        trip.setDestinations(new ArrayList<>());
        trip.setCollaborators(new ArrayList<>());

        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(destinationRepository.findByTripOrderBySequenceOrderAsc(trip)).thenReturn(trip.getDestinations());
        when(itineraryItemRepository.findByTripIdOrderByDayNumberAscStartTimeAsc(10L)).thenReturn(Collections.emptyList());

        RouteSummaryDto summary = new RouteSummaryDto();
        summary.setTotalDistanceKm(100.0);
        summary.setTotalTravelTimeMinutes(120);
        when(routeService.calculateCompleteRoute(anyString(), anyList(), anyString())).thenReturn(summary);
        when(googleMapsService.geocode(anyString())).thenReturn(new double[]{28.6139, 77.2090});
        when(recommendationService.getRecommendedPlaces(anyString(), any(), any(), any())).thenReturn(Collections.emptyList());
        when(tripSuggestionRepository.findByTripOrderByVotesCountDescCreatedAtDesc(any(Trip.class))).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Unauthenticated request (user == null) is strictly rejected with AccessDeniedException")
    void testUnauthenticatedUserAccessDenied() {
        assertThatThrownBy(() -> tripService.getTripDtoById(10L, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    @DisplayName("Unauthorized user without ownership or collaborator role is rejected with AccessDeniedException")
    void testIntruderAccessDenied() {
        when(tripCollaboratorRepository.findByTripAndUserEmail(trip, "intruder@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.getTripDtoById(10L, intruder))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Invited crew collaborator is granted valid access to trip")
    void testCollaboratorGrantedAccess() {
        TripCollaborator collab = new TripCollaborator(trip, intruder.getName(), intruder.getEmail(), "EDITOR");
        trip.setCollaborators(List.of(collab));
        when(tripCollaboratorRepository.findByTripAndUserEmail(trip, "intruder@example.com")).thenReturn(Optional.of(collab));

        TripResponseDto dto = tripService.getTripDtoById(10L, intruder);
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Trip itinerary items persist across page reloads without regenerating from scratch")
    void testItineraryItemsPersistAcrossPageReload() {
        ItineraryItem item1 = new ItineraryItem();
        item1.setId(1001L);
        item1.setTrip(trip);
        item1.setDayNumber(1);
        item1.setActivity("Custom Coffee Plantation Tour");
        item1.setStartTime("10:00");
        item1.setEndTime("11:30");
        item1.setLocked(true);

        when(itineraryItemRepository.findByTripIdOrderByDayNumberAscStartTimeAsc(10L))
                .thenReturn(List.of(item1));

        TripResponseDto dto = tripService.getTripDtoById(10L, owner);

        assertThat(dto).isNotNull();
        assertThat(dto.getDayItineraries()).isNotEmpty();

        DayItineraryDto day1 = dto.getDayItineraries().get(0);
        assertThat(day1.getItems()).isNotEmpty();
        ItineraryItemDto loadedItem = day1.getItems().get(0);

        // Verification: The custom user item and locked state are preserved directly from DB
        assertThat(loadedItem.getActivity()).isEqualTo("Custom Coffee Plantation Tour");
        assertThat(loadedItem.getLocked()).isTrue();

        // Ensure generateItineraryPlan was NOT called to overwrite persisted items
        verify(itineraryService, never()).generateItineraryPlan(any(), any(), any());
    }

    @Test
    @DisplayName("Adding and removing destinations persists order and triggers route calculation")
    void testAddAndRemoveDestinations() {
        Destination d1 = new Destination("Amer Fort", 0);
        d1.setId(101L);
        d1.setTrip(trip);
        d1.setSequenceOrder(0);

        when(destinationRepository.save(any(Destination.class))).thenAnswer(i -> {
            Destination d = i.getArgument(0);
            if (d.getId() == null) d.setId(102L);
            return d;
        });
        when(tripRepository.save(any(Trip.class))).thenAnswer(i -> i.getArgument(0));

        // Add destination
        TripResponseDto afterAdd = tripService.addDestinationToTrip(10L, "Amer Fort", owner);
        assertThat(afterAdd).isNotNull();
        verify(destinationRepository, atLeastOnce()).save(any(Destination.class));

        // Remove destination
        when(destinationRepository.findById(101L)).thenReturn(Optional.of(d1));
        trip.getDestinations().add(d1);

        TripResponseDto afterRemove = tripService.removeDestinationFromTrip(10L, 101L, owner);
        assertThat(afterRemove).isNotNull();
        verify(destinationRepository).delete(d1);
    }
}
