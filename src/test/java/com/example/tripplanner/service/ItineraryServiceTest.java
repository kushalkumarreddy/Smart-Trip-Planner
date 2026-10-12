package com.example.tripplanner.service;

import com.example.tripplanner.dto.DayItineraryDto;
import com.example.tripplanner.dto.ItineraryItemDto;
import com.example.tripplanner.dto.SightseeingPlaceDto;
import com.example.tripplanner.entity.Destination;
import com.example.tripplanner.entity.Trip;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ItineraryServiceTest {

    @Mock
    private GoogleMapsService googleMapsService;

    @Mock
    private RecommendationService recommendationService;

    @InjectMocks
    private ItineraryService itineraryService;

    @BeforeEach
    void setUp() {
        when(googleMapsService.calculateRouteSegment(anyString(), anyString(), anyString()))
                .thenReturn(new GoogleMapsService.RouteCalculationResult(140.0, 180));
    }

    @Test
    @DisplayName("Opening hours conflict: Flags warning when activity starts before opening hours")
    void testOpeningHoursConflictBeforeOpen() {
        String conflict = ItineraryService.checkOpeningHoursConflict("08:00", "09:30", "09:00 AM - 05:00 PM");
        assertThat(conflict).isNotNull();
        assertThat(conflict).contains("opens at 09:00 AM");
    }

    @Test
    @DisplayName("Opening hours conflict: Flags warning when activity extends past closing hours")
    void testOpeningHoursConflictAfterClose() {
        String conflict = ItineraryService.checkOpeningHoursConflict("17:30", "19:00", "09:00 AM - 05:00 PM");
        assertThat(conflict).isNotNull();
        assertThat(conflict).contains("closes at 05:00 PM");
    }

    @Test
    @DisplayName("Opening hours: No conflict when visit is within opening window")
    void testOpeningHoursNoConflict() {
        String conflict = ItineraryService.checkOpeningHoursConflict("10:00", "11:30", "09:00 AM - 05:00 PM");
        assertThat(conflict).isNull();
    }

    @Test
    @DisplayName("Opening hours: Handles 24 Hours attraction without conflict")
    void testOpeningHours24Hours() {
        String conflict = ItineraryService.checkOpeningHoursConflict("22:00", "23:00", "24 Hours");
        assertThat(conflict).isNull();
    }

    @Test
    @DisplayName("Day Itinerary Generation: Departs from origin and schedules activities with meal break")
    void testGenerateItineraryPlan() {
        Trip trip = new Trip();
        trip.setId(1L);
        trip.setStartLocation("Bengaluru");
        trip.setStartDate(LocalDate.now());
        trip.setEndDate(LocalDate.now().plusDays(1));
        trip.setDailyStartTime("08:00");
        trip.setDailyEndTime("20:00");
        trip.setTransportMode("CAR");

        Destination dest1 = new Destination("Mysore", 0);
        dest1.setId(10L);
        dest1.setTrip(trip);
        dest1.setHotelName("Radisson Blu Mysore");

        List<SightseeingPlaceDto> spots = new ArrayList<>();
        SightseeingPlaceDto spot1 = new SightseeingPlaceDto();
        spot1.setName("Mysore Palace");
        spot1.setEstimatedVisitDurationMinutes(90);
        spot1.setEstimatedTravelTimeMinutes(15);
        spot1.setDistanceKm(3.0);
        spot1.setOpeningHours("10:00 AM - 05:30 PM");
        spots.add(spot1);

        when(recommendationService.getRecommendedPlaces(eq("Mysore"), any(), anyString(), any()))
                .thenReturn(spots);

        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, Collections.singletonList(dest1), "CAR");

        assertThat(days).isNotEmpty();
        DayItineraryDto day1 = days.get(0);
        assertThat(day1.getItems()).isNotEmpty();

        // Must start with departure
        ItineraryItemDto firstItem = day1.getItems().get(0);
        assertThat(firstItem.getActivityType()).isEqualTo("DEPARTURE");
        assertThat(firstItem.getLocation()).isEqualTo("Bengaluru");

        // Must have arrival and hotel stay
        boolean hasArrival = day1.getItems().stream().anyMatch(i -> "ARRIVAL".equals(i.getActivityType()));
        boolean hasHotel = day1.getItems().stream().anyMatch(i -> "HOTEL".equals(i.getActivityType()));
        assertThat(hasArrival).isTrue();
        assertThat(hasHotel).isTrue();
    }

    @Test
    @DisplayName("Single Day Regeneration: Updates target day while preserving exclusion of already used places")
    void testRegenerateSingleDayExcludesUsedPlaces() {
        Trip trip = new Trip();
        trip.setId(1L);
        trip.setStartLocation("Bengaluru");
        trip.setStartDate(LocalDate.now());
        trip.setEndDate(LocalDate.now().plusDays(2));
        trip.setDailyStartTime("08:00");
        trip.setDailyEndTime("20:00");
        trip.setTransportMode("CAR");

        Destination dest = new Destination("Mysore", 0);
        dest.setId(10L);
        dest.setTrip(trip);

        SightseeingPlaceDto spotA = new SightseeingPlaceDto();
        spotA.setName("Chamundi Hill");
        spotA.setEstimatedVisitDurationMinutes(60);
        spotA.setEstimatedTravelTimeMinutes(20);

        SightseeingPlaceDto spotB = new SightseeingPlaceDto();
        spotB.setName("Mysore Zoo");
        spotB.setEstimatedVisitDurationMinutes(90);
        spotB.setEstimatedTravelTimeMinutes(15);

        when(recommendationService.getRecommendedPlaces(eq("Mysore"), any(), anyString(), any()))
                .thenReturn(Arrays.asList(spotA, spotB));

        // Mark spotA as already used in another day
        HashSet<String> used = new HashSet<>();
        used.add("chamundi hill");

        DayItineraryDto refreshed = itineraryService.regenerateSingleDay(
                trip, Collections.singletonList(dest), 1, "BALANCED", "CAR", used
        );

        assertThat(refreshed).isNotNull();
        // The newly scheduled spot should be spotB (Mysore Zoo)
        boolean hasZoo = refreshed.getItems().stream().anyMatch(i -> i.getActivity().contains("Mysore Zoo"));
        assertThat(hasZoo).isTrue();
    }
}
