package com.example.tripplanner.service;

import com.example.tripplanner.dto.DayItineraryDto;
import com.example.tripplanner.dto.ItineraryItemDto;
import com.example.tripplanner.dto.SightseeingPlaceDto;
import com.example.tripplanner.entity.Destination;
import com.example.tripplanner.entity.Trip;
import com.example.tripplanner.util.TimeUtils;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StartTimeAndItinerarySchedulingTest {

    @Mock
    private GoogleMapsService googleMapsService;

    @Mock
    private RecommendationService recommendationService;

    @InjectMocks
    private ItineraryService itineraryService;

    @BeforeEach
    void setUp() {
        when(googleMapsService.calculateRouteSegment(anyString(), anyString(), anyString()))
                .thenReturn(new GoogleMapsService.RouteCalculationResult(140.0, 150));

        SightseeingPlaceDto spot1 = new SightseeingPlaceDto();
        spot1.setName("Mysore Palace");
        spot1.setEstimatedVisitDurationMinutes(90);
        spot1.setEstimatedTravelTimeMinutes(15);
        spot1.setDistanceKm(3.0);
        spot1.setOpeningHours("10:00 AM - 05:30 PM");

        SightseeingPlaceDto spot2 = new SightseeingPlaceDto();
        spot2.setName("Chamundi Hill");
        spot2.setEstimatedVisitDurationMinutes(60);
        spot2.setEstimatedTravelTimeMinutes(20);
        spot2.setDistanceKm(8.0);
        spot2.setOpeningHours("06:00 AM - 09:00 PM");

        when(recommendationService.getRecommendedPlaces(anyString(), any(), anyString(), any()))
                .thenReturn(Arrays.asList(spot1, spot2));
    }

    @Test
    @DisplayName("Default Start Time: New trip defaults to 08:00 AM and begins day at 08:00 AM")
    void testDefaultStartTimeEightAm() {
        Trip trip = new Trip();
        trip.setId(1L);
        trip.setStartLocation("Bengaluru");
        trip.setStartDate(LocalDate.of(2026, 11, 1));
        trip.setEndDate(LocalDate.of(2026, 11, 1));
        trip.setDailyStartTime("08:00");
        trip.setTransportMode("CAR");

        Destination dest1 = new Destination("Mysore", 0);
        dest1.setId(10L);
        dest1.setTrip(trip);
        dest1.setHotelName("Radisson Blu");

        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, Collections.singletonList(dest1), "CAR");
        assertThat(days).isNotEmpty();
        DayItineraryDto day1 = days.get(0);
        assertThat(day1.getItems()).isNotEmpty();

        ItineraryItemDto firstItem = day1.getItems().get(0);
        assertThat(firstItem.getStartTime()).isEqualTo("8:00 AM");
        // Departure should have a 15-minute preparation block, not 0-minute duplicate
        assertThat(firstItem.getEndTime()).isEqualTo("8:15 AM");
    }

    @Test
    @DisplayName("Custom Start Time: Trip starting at 06:30 AM begins at 06:30 AM without resetting to 08:00 AM")
    void testCustomStartTimeSixThirtyAm() {
        Trip trip = new Trip();
        trip.setId(2L);
        trip.setStartLocation("Bengaluru");
        trip.setStartDate(LocalDate.of(2026, 11, 1));
        trip.setEndDate(LocalDate.of(2026, 11, 1));
        trip.setDailyStartTime("06:30");
        trip.setTransportMode("CAR");

        Destination dest1 = new Destination("Mysore", 0);
        dest1.setId(11L);
        dest1.setTrip(trip);
        dest1.setHotelName("Radisson Blu");

        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, Collections.singletonList(dest1), "CAR");
        assertThat(days).isNotEmpty();
        DayItineraryDto day1 = days.get(0);

        ItineraryItemDto firstItem = day1.getItems().get(0);
        assertThat(firstItem.getStartTime()).isEqualTo("6:30 AM");
        assertThat(firstItem.getEndTime()).isEqualTo("6:45 AM");
    }

    @Test
    @DisplayName("Chronological Consistency: No duplicate start times or backwards time shifts in itinerary")
    void testNoDuplicateOrOverlappingTimes() {
        Trip trip = new Trip();
        trip.setId(3L);
        trip.setStartLocation("Bengaluru");
        trip.setStartDate(LocalDate.of(2026, 11, 1));
        trip.setEndDate(LocalDate.of(2026, 11, 2));
        trip.setDailyStartTime("08:00");
        trip.setTransportMode("CAR");

        Destination dest1 = new Destination("Mysore", 0);
        dest1.setId(12L);
        dest1.setTrip(trip);
        dest1.setHotelName("Radisson Blu");

        Destination dest2 = new Destination("Coorg", 1);
        dest2.setId(13L);
        dest2.setTrip(trip);
        dest2.setHotelName("Club Mahindra Coorg");

        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, Arrays.asList(dest1, dest2), "CAR");
        assertThat(days).hasSize(2);

        for (DayItineraryDto day : days) {
            LocalTime prevEnd = null;
            for (ItineraryItemDto item : day.getItems()) {
                LocalTime start = TimeUtils.parseTime(item.getStartTime());
                LocalTime end = TimeUtils.parseTime(item.getEndTime());

                assertThat(start).isNotNull();
                assertThat(end).isNotNull();
                assertThat(start).isBeforeOrEqualTo(end);

                if (prevEnd != null) {
                    // Next activity must start at or after the previous activity ends
                    assertThat(start).isAfterOrEqualTo(prevEnd);
                }
                prevEnd = end;
            }
        }
    }
}
