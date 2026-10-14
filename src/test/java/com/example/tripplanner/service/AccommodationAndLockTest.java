package com.example.tripplanner.service;

import com.example.tripplanner.dto.AccommodationStayDto;
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

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Accommodation Stays & Activity Lock Preservation Tests")
class AccommodationAndLockTest {

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
    private Trip trip;
    private Destination destination;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setEmail("traveler@example.com");

        trip = new Trip();
        trip.setId(10L);
        trip.setTripName("Southern India Road Trip");
        trip.setUser(owner);
        trip.setStartDate(LocalDate.of(2026, 11, 1));
        trip.setEndDate(LocalDate.of(2026, 11, 4));
        trip.setStartLocation("Bengaluru");
        trip.setStartLat(12.9716);
        trip.setStartLng(77.5946);
        trip.setTransportMode("CAR");

        destination = new Destination("Coorg", 0);
        destination.setId(101L);
        destination.setTrip(trip);
        destination.setLatitude(12.4244);
        destination.setLongitude(75.7382);
        destination.setSequenceOrder(0);

        trip.setDestinations(new ArrayList<>(List.of(destination)));

        when(tripRepository.findById(10L)).thenReturn(Optional.of(trip));
        when(destinationRepository.findById(101L)).thenReturn(Optional.of(destination));
        when(destinationRepository.findByTripOrderBySequenceOrderAsc(trip)).thenReturn(List.of(destination));
        when(tripRepository.save(any(Trip.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(destinationRepository.save(any(Destination.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RouteSummaryDto summary = new RouteSummaryDto();
        summary.setTotalDistanceKm(250.0);
        summary.setTotalTravelTimeMinutes(300);
        when(routeService.calculateCompleteRoute(anyString(), anyList(), anyString())).thenReturn(summary);
        when(googleMapsService.geocode(anyString())).thenReturn(new double[]{12.9716, 77.5946});
        when(recommendationService.getRecommendedPlaces(anyString(), any(), any(), any())).thenReturn(Collections.emptyList());
        when(itineraryService.generateItineraryPlan(any(), any(), any())).thenReturn(Collections.emptyList());
        when(tripSuggestionRepository.findByTripOrderByVotesCountDescCreatedAtDesc(any(Trip.class))).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("Updating accommodation associates stay details and coordinates with destination stop")
    void testUpdateDestinationAccommodation() {
        AccommodationStayDto stayDto = new AccommodationStayDto();
        stayDto.setHotelName("Evolve Back Resort");
        stayDto.setHotelAddress("Karadigodu Post, Siddapur, Coorg, Karnataka 571253");
        stayDto.setHotelLat(12.3120);
        stayDto.setHotelLng(75.8940);
        stayDto.setCheckInDate(LocalDate.of(2026, 11, 1));
        stayDto.setCheckOutDate(LocalDate.of(2026, 11, 3));
        stayDto.setRoomsCount(2);
        stayDto.setGuestsCount(4);
        stayDto.setBookingReference("RES-998877");
        stayDto.setHotelNotes("Cottage facing plantation");

        TripResponseDto updated = tripService.updateDestinationAccommodation(10L, 101L, stayDto, owner);

        assertThat(updated).isNotNull();
        assertThat(destination.getHotelName()).isEqualTo("Evolve Back Resort");
        assertThat(destination.getHotelAddress()).contains("Karadigodu Post");
        assertThat(destination.getHotelLat()).isEqualTo(12.3120);
        assertThat(destination.getHotelLng()).isEqualTo(75.8940);
        assertThat(destination.getCheckInDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(destination.getRoomsCount()).isEqualTo(2);
        assertThat(destination.getBookingReference()).isEqualTo("RES-998877");

        verify(destinationRepository).save(destination);
    }

    @Test
    @DisplayName("Removing accommodation clears hotel fields while preserving destination stop")
    void testRemoveDestinationAccommodation() {
        destination.setHotelName("Old Hotel");
        destination.setHotelAddress("Old Street");
        destination.setHotelLat(12.3);
        destination.setHotelLng(75.8);

        tripService.removeDestinationAccommodation(10L, 101L, owner);

        assertThat(destination.getHotelName()).isNull();
        assertThat(destination.getHotelAddress()).isNull();
        assertThat(destination.getHotelLat()).isNull();
        assertThat(destination.getHotelLng()).isNull();
        verify(destinationRepository).save(destination);
    }

    @Test
    @DisplayName("Toggling activity lock switches item between locked and unlocked state")
    void testToggleActivityLock() {
        ItineraryItem item = new ItineraryItem();
        item.setId(1001L);
        item.setTrip(trip);
        item.setActivity("Abbey Falls Sightseeing");
        item.setLocked(false);

        when(itineraryItemRepository.findById(1001L)).thenReturn(Optional.of(item));
        when(itineraryItemRepository.save(any(ItineraryItem.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> lockResult = tripService.toggleActivityLock(10L, 1001L, owner);
        assertThat(lockResult.get("locked")).isEqualTo(true);

        Map<String, Object> unlockResult = tripService.toggleActivityLock(10L, 1001L, owner);
        assertThat(unlockResult.get("locked")).isEqualTo(false);
    }

    @Test
    @DisplayName("Single-day regeneration strictly preserves locked activities and hotel stays")
    void testRegenerateSingleDayPreservesLockedItems() {
        // Setup Day 1 with 1 locked sightseeing item, 1 hotel stay, and 1 unlocked flexible sightseeing item
        ItineraryItem lockedSightseeing = new ItineraryItem();
        lockedSightseeing.setId(1001L);
        lockedSightseeing.setTrip(trip);
        lockedSightseeing.setDayNumber(1);
        lockedSightseeing.setActivity("Dubare Elephant Camp (Must Visit)");
        lockedSightseeing.setActivityType("SIGHTSEEING");
        lockedSightseeing.setLocked(true);

        ItineraryItem hotelStay = new ItineraryItem();
        hotelStay.setId(1002L);
        hotelStay.setTrip(trip);
        hotelStay.setDayNumber(1);
        hotelStay.setActivity("Check-in: Evolve Back Resort");
        hotelStay.setActivityType("HOTEL");
        hotelStay.setLocked(false);

        ItineraryItem unlockedItem = new ItineraryItem();
        unlockedItem.setId(1003L);
        unlockedItem.setTrip(trip);
        unlockedItem.setDayNumber(1);
        unlockedItem.setActivity("Local Market Walk");
        unlockedItem.setActivityType("SIGHTSEEING");
        unlockedItem.setLocked(false);

        // Day 2 item (must be completely untouched)
        ItineraryItem day2Item = new ItineraryItem();
        day2Item.setId(1004L);
        day2Item.setTrip(trip);
        day2Item.setDayNumber(2);
        day2Item.setActivity("Tala Kaveri Visit");
        day2Item.setActivityType("SIGHTSEEING");

        when(itineraryItemRepository.findByTripIdOrderByDayNumberAscStartTimeAsc(10L))
                .thenReturn(Arrays.asList(lockedSightseeing, hotelStay, unlockedItem, day2Item));

        when(itineraryItemRepository.findByTripIdAndDayNumberOrderByStartTimeAsc(10L, 1))
                .thenReturn(Arrays.asList(lockedSightseeing, hotelStay, unlockedItem));

        com.example.tripplanner.dto.DayItineraryDto refreshedDay = new com.example.tripplanner.dto.DayItineraryDto();
        refreshedDay.setDayNumber(1);
        refreshedDay.setDate(LocalDate.of(2026, 11, 1));
        refreshedDay.setItems(new ArrayList<>());
        when(itineraryService.regenerateSingleDay(any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(refreshedDay);

        tripService.regenerateSingleDay(10L, 1, "Waterfalls", owner);

        // Verify that unlocked flexible item WAS passed to deleteAll for replacement
        verify(itineraryItemRepository).deleteAll(argThat(iterable -> {
            List<ItineraryItem> list = new ArrayList<>();
            iterable.forEach(list::add);
            return list.contains(unlockedItem) && !list.contains(lockedSightseeing) && !list.contains(hotelStay) && !list.contains(day2Item);
        }));
    }
}
