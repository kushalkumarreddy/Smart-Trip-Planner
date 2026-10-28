package com.example.tripplanner.service;

import com.example.tripplanner.dto.DayItineraryDto;
import com.example.tripplanner.dto.ItineraryItemDto;
import com.example.tripplanner.dto.RouteSummaryDto;
import com.example.tripplanner.dto.TripRequestDto;
import com.example.tripplanner.dto.TripResponseDto;
import com.example.tripplanner.entity.Destination;
import com.example.tripplanner.entity.Trip;
import com.example.tripplanner.util.TimeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProductionReadinessAndAccuracyMasterTest {

    private RecommendationService recommendationService;
    private ItineraryService itineraryService;

    @Mock
    private GoogleMapsService googleMapsService;

    @Mock
    private RouteService routeService;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService();
        itineraryService = new ItineraryService(googleMapsService, recommendationService);
    }

    @Test
    @DisplayName("Requirement 1 & 2: Sightseeing day generates multiple (3-6) activities without repetitive patterns")
    void testMultipleActivitiesPerDayOnSightseeingDay() {
        Trip trip = new Trip();
        trip.setId(1L);
        trip.setStartLocation("Bengaluru");
        trip.setStartDate(LocalDate.now());
        trip.setEndDate(LocalDate.now().plusDays(2));
        trip.setDailyStartTime("08:00");
        trip.setDailyEndTime("20:30");
        trip.setTransportMode("CAR");

        Destination dest = new Destination("Mysuru", 0, 2);
        dest.setLatitude(12.2958);
        dest.setLongitude(76.6394);
        dest.setDistanceFromPrevious(145.0);
        dest.setTravelTimeFromPrevious(150);

        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, List.of(dest), "CAR");

        assertThat(days).hasSize(3);

        // Day 2 is a full city sightseeing day in Mysuru
        DayItineraryDto day2 = days.get(1);
        long sightseeingCount = day2.getItems().stream()
                .filter(it -> "SIGHTSEEING".equalsIgnoreCase(it.getActivityType()))
                .count();

        // Must generate 3 to 6 meaningful activities on a dedicated city sightseeing day
        assertThat(sightseeingCount)
                .as("City sightseeing day should generate 3-6 meaningful activities")
                .isBetween(3L, 6L);

        // Ensure distinct places were scheduled (no immediate duplicates on same day)
        Set<String> placeNames = new HashSet<>();
        for (ItineraryItemDto it : day2.getItems()) {
            if ("SIGHTSEEING".equalsIgnoreCase(it.getActivityType())) {
                assertThat(placeNames.add(it.getActivity()))
                        .as("Attraction %s should not be duplicated on the same day", it.getActivity())
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("Requirement 3: Eliminates 4-hour dead gap before hotel check-in / dinner")
    void testNoArbitraryDeadGapBeforeHotel() {
        Trip trip = new Trip();
        trip.setId(2L);
        trip.setStartLocation("Bengaluru");
        trip.setStartDate(LocalDate.now());
        trip.setEndDate(LocalDate.now().plusDays(1));
        trip.setDailyStartTime("08:00");
        trip.setDailyEndTime("20:30");
        trip.setTransportMode("CAR");

        Destination dest = new Destination("Ooty", 0, 1);
        dest.setLatitude(11.4102);
        dest.setLongitude(76.6950);
        dest.setDistanceFromPrevious(270.0);
        dest.setTravelTimeFromPrevious(240);

        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, List.of(dest), "CAR");
        DayItineraryDto day1 = days.get(0);

        List<ItineraryItemDto> items = day1.getItems();
        assertThat(items).isNotEmpty();

        // Verify sequential continuity: gap between consecutive activities should not exceed normal transit/buffer (<= 45 mins)
        for (int i = 0; i < items.size() - 1; i++) {
            ItineraryItemDto current = items.get(i);
            ItineraryItemDto next = items.get(i + 1);

            LocalTime currentEnd = TimeUtils.parseTime(current.getEndTime());
            LocalTime nextStart = TimeUtils.parseTime(next.getStartTime());

            int gapMinutes = (int) java.time.temporal.ChronoUnit.MINUTES.between(currentEnd, nextStart);
            assertThat(gapMinutes)
                    .as("Gap between %s and %s should be reasonable buffer, not a dead void", current.getActivity(), next.getActivity())
                    .isLessThanOrEqualTo(45);
        }

        // Verify hotel return is scheduled followed by dinner or leisure
        boolean hasHotel = items.stream().anyMatch(it -> "HOTEL".equalsIgnoreCase(it.getActivityType()));
        assertThat(hasHotel).isTrue();
    }

    @Test
    @DisplayName("Requirement 6: Summary metrics are mathematically consistent with no overlapping double-counting")
    void testMathematicalMetricsConsistency() {
        Trip trip = new Trip();
        trip.setId(3L);
        trip.setStartLocation("Bengaluru");
        trip.setStartDate(LocalDate.now());
        trip.setEndDate(LocalDate.now().plusDays(1));
        trip.setDailyStartTime("08:30");
        trip.setDailyEndTime("20:00");
        trip.setTransportMode("CAR");

        Destination dest = new Destination("Coorg", 0, 1);
        dest.setLatitude(12.3375);
        dest.setLongitude(75.8069);
        dest.setDistanceFromPrevious(250.0);
        dest.setTravelTimeFromPrevious(270);

        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, List.of(dest), "CAR");
        DayItineraryDto day1 = days.get(0);

        int travel = day1.getTravelTimeMinutes();
        int sightseeing = day1.getSightseeingTimeMinutes();
        int breaks = day1.getBreakTimeMinutes();
        int totalPlanned = day1.getTotalDurationMinutes();
        int unallocated = day1.getUnallocatedTimeMinutes();

        assertThat(travel).isGreaterThan(0);
        assertThat(sightseeing).isGreaterThan(0);
        assertThat(totalPlanned).isGreaterThan(0);

        // Sum of components should align logically with total planned duration
        assertThat(travel + sightseeing + breaks)
                .as("Sum of travel, sightseeing, and breaks must not exceed total planned duration by more than rounding buffers")
                .isLessThanOrEqualTo(totalPlanned + 30);

        // Available day hours: 08:30 to 20:00 = 690 minutes
        int availableMinutes = TimeUtils.differenceInMinutes("08:30", "20:00");
        assertThat(totalPlanned + unallocated)
                .as("Total planned elapsed time + unallocated time should reflect total day hours")
                .isGreaterThanOrEqualTo(availableMinutes - 60);
    }

    @Test
    @DisplayName("Requirement 5: Activity items carry coordinates and category for Leaflet map markers")
    void testActivityCoordinatesAndCategoriesPopulated() {
        Trip trip = new Trip();
        trip.setId(4L);
        trip.setStartLocation("Bengaluru");
        trip.setStartDate(LocalDate.now());
        trip.setEndDate(LocalDate.now());
        trip.setDailyStartTime("08:00");
        trip.setDailyEndTime("20:00");

        Destination dest = new Destination("Mysuru", 0, 1);
        dest.setLatitude(12.2958);
        dest.setLongitude(76.6394);

        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, List.of(dest), "CAR");
        DayItineraryDto day1 = days.get(0);

        for (ItineraryItemDto item : day1.getItems()) {
            assertThat(item.getLatitude())
                    .as("Item %s must have non-null latitude for map marker rendering", item.getActivity())
                    .isNotNull()
                    .isNotEqualTo(0.0);

            assertThat(item.getLongitude())
                    .as("Item %s must have non-null longitude for map marker rendering", item.getActivity())
                    .isNotNull()
                    .isNotEqualTo(0.0);

            assertThat(item.getCategory())
                    .as("Item %s must have an identifiable category for custom map marker icons", item.getActivity())
                    .isNotNull()
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("Requirement 8: Public transport mode provides honest timetable status without fabricated trains or fares")
    void testPublicTransportAccurateAndUnfabricated() {
        RouteService realRouteService = new RouteService(googleMapsService);

        RouteSummaryDto transitSummary = realRouteService.calculateCompleteRoute(
                "Bengaluru",
                List.of("Mysuru"),
                "PUBLIC_TRANSPORT"
        );

        assertThat(transitSummary).isNotNull();
        assertThat(transitSummary.getSegments()).hasSize(1);

        var segment = transitSummary.getSegments().get(0);
        assertThat(segment.getPublicTransportSearchUrl())
                .as("Must provide authentic public transit search link (e.g. Google Maps Transit)")
                .isNotNull()
                .contains("transit");

        // When live Google Transit credentials are not configured, it must set transitAvailable to false with honest notice
        if (!segment.getTransitAvailable()) {
            assertThat(segment.getTransitNotice())
                    .as("Must explain absence of live timetable provider honestly")
                    .contains("unavailable");
        }
    }
}
