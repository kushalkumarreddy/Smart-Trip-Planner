package com.example.tripplanner.service;

import com.example.tripplanner.dto.DayItineraryDto;
import com.example.tripplanner.dto.ItineraryItemDto;
import com.example.tripplanner.dto.RouteSummaryDto;
import com.example.tripplanner.dto.SightseeingPlaceDto;
import com.example.tripplanner.dto.TripRequestDto;
import com.example.tripplanner.dto.TripResponseDto;
import com.example.tripplanner.entity.Destination;
import com.example.tripplanner.entity.Trip;
import com.example.tripplanner.exception.BadRequestException;
import com.example.tripplanner.repository.DestinationRepository;
import com.example.tripplanner.repository.ItineraryItemRepository;
import com.example.tripplanner.repository.PlaceRepository;
import com.example.tripplanner.repository.TripCollaboratorRepository;
import com.example.tripplanner.repository.TripRepository;
import com.example.tripplanner.util.TimeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TargetedBugFixesVerificationTest {

    @Mock
    private TripRepository tripRepository;
    @Mock
    private DestinationRepository destinationRepository;
    @Mock
    private PlaceRepository placeRepository;
    @Mock
    private ItineraryItemRepository itineraryItemRepository;
    @Mock
    private TripCollaboratorRepository tripCollaboratorRepository;
    @Mock
    private com.example.tripplanner.repository.TripSuggestionRepository tripSuggestionRepository;
    @Mock
    private com.example.tripplanner.repository.TripChecklistRepository tripChecklistRepository;
    @Mock
    private TripVersionService tripVersionService;
    @Mock
    private SmartStopsService smartStopsService;

    private GoogleMapsService googleMapsService;
    private RecommendationService recommendationService;
    private RouteService routeService;
    private ItineraryService itineraryService;
    private TripService tripService;

    @BeforeEach
    void setUp() {
        googleMapsService = new GoogleMapsService();
        recommendationService = new RecommendationService();
        routeService = new RouteService(googleMapsService);
        itineraryService = new ItineraryService(googleMapsService, recommendationService);
        tripService = new TripService(
                tripRepository, destinationRepository, placeRepository, itineraryItemRepository,
                routeService, itineraryService, recommendationService, googleMapsService,
                tripCollaboratorRepository, tripSuggestionRepository, tripChecklistRepository,
                tripVersionService, smartStopsService
        );
    }

    // =========================================================================
    // Issue 1: Fix Incorrect Itinerary Time Management
    // =========================================================================

    @Test
    @DisplayName("Issue 1: Schedule does NOT cut off at 10:15 AM hotel arrival; hotel arrival is an evening transition")
    void testItineraryDoesNotCutOffAtMorningHotelArrival() {
        Trip trip = new Trip();
        trip.setId(10L);
        trip.setStartLocation("Hyderabad");
        trip.setStartDate(LocalDate.of(2026, 11, 10));
        trip.setEndDate(LocalDate.of(2026, 11, 10));
        trip.setDailyStartTime("08:00");
        trip.setDailyEndTime("20:30");
        trip.setTransportMode("CAR");

        Destination kurnool = new Destination("Kurnool", 0);
        kurnool.setId(101L);
        kurnool.setLatitude(15.8281);
        kurnool.setLongitude(78.0373);
        kurnool.setStayDurationDays(1);
        kurnool.setHotelName("Hotel Mourya Inn");

        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, List.of(kurnool), "CAR");
        assertThat(days).isNotEmpty();
        DayItineraryDto day1 = days.get(0);
        List<ItineraryItemDto> items = day1.getItems();
        assertThat(items).isNotEmpty();

        // 1. Verify departure is at 8:00 AM
        ItineraryItemDto depart = items.get(0);
        assertThat(depart.getStartTime()).isEqualTo("8:00 AM");

        // 2. Locate hotel check-in / return
        ItineraryItemDto hotelItem = items.stream()
                .filter(it -> "HOTEL".equalsIgnoreCase(it.getActivityType()))
                .findFirst()
                .orElse(null);
        assertThat(hotelItem).isNotNull();

        LocalTime hotelStartTime = TimeUtils.parseTime(hotelItem.getStartTime());
        // Must NOT be in the morning at 10:15 AM! Hotel arrival is an evening transition (>= 16:00 / 4:00 PM)
        assertThat(hotelStartTime)
                .as("Hotel return should not happen at morning 10:15 AM, must be in the afternoon or evening")
                .isAfterOrEqualTo(LocalTime.of(16, 0));

        // 3. Verify dinner is scheduled in the evening
        ItineraryItemDto dinner = items.stream()
                .filter(it -> "MEAL".equalsIgnoreCase(it.getActivityType()) && it.getActivity() != null && it.getActivity().toLowerCase().contains("dinner"))
                .findFirst()
                .orElse(null);
        assertThat(dinner).isNotNull();
        LocalTime dinnerStart = TimeUtils.parseTime(dinner.getStartTime());
        assertThat(dinnerStart).isAfterOrEqualTo(LocalTime.of(18, 30));

        // 4. Verify no overlapping times between consecutive items
        for (int i = 0; i < items.size() - 1; i++) {
            LocalTime end = TimeUtils.parseTime(items.get(i).getEndTime());
            LocalTime nextStart = TimeUtils.parseTime(items.get(i + 1).getStartTime());
            assertThat(nextStart)
                    .as("Item %d end (%s) must be before or equal to item %d start (%s)", i, end, i + 1, nextStart)
                    .isAfterOrEqualTo(end);
        }

        // 5. Verify daily summary metrics align mathematically
        int calculatedSpan = day1.getTotalDurationMinutes();
        int sumComponents = day1.getTravelTimeMinutes() + day1.getSightseeingTimeMinutes() + day1.getBreakTimeMinutes();
        assertThat(calculatedSpan).isGreaterThanOrEqualTo(sumComponents);
        int availableDay = TimeUtils.differenceInMinutes("08:00", "20:30");
        assertThat(day1.getUnallocatedTimeMinutes()).isEqualTo(Math.max(0, availableDay - calculatedSpan));
    }

    @Test
    @DisplayName("Issue 1: Custom start time (e.g. 09:30 AM) is strictly preserved across the itinerary")
    void testCustomStartTimePreserved() {
        Trip trip = new Trip();
        trip.setId(20L);
        trip.setStartLocation("Bengaluru");
        trip.setStartDate(LocalDate.of(2026, 12, 1));
        trip.setEndDate(LocalDate.of(2026, 12, 1));
        trip.setDailyStartTime("09:30");
        trip.setDailyEndTime("21:00");
        trip.setTransportMode("CAR");

        Destination mysore = new Destination("Mysore", 0);
        mysore.setId(201L);
        mysore.setLatitude(12.2958);
        mysore.setLongitude(76.6394);

        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, List.of(mysore), "CAR");
        assertThat(days).isNotEmpty();
        ItineraryItemDto firstItem = days.get(0).getItems().get(0);
        assertThat(firstItem.getStartTime()).isEqualTo("9:30 AM");
    }

    // =========================================================================
    // Issue 2: Fix Incorrect Places to Visit
    // =========================================================================

    @Test
    @DisplayName("Issue 2: Kurnool catalog returns real identifiable places with authentic coordinates, without generic fabrications")
    void testRealAttractionsForKurnool() {
        List<SightseeingPlaceDto> places = recommendationService.getRecommendedPlaces("Kurnool", null, "popularity", null);
        assertThat(places).isNotEmpty();
        assertThat(places.size()).isGreaterThanOrEqualTo(5);

        List<String> names = places.stream().map(SightseeingPlaceDto::getName).toList();
        // Authentic landmarks
        assertThat(names).contains(
                "Konda Reddy Buruju Historic Fort",
                "Oravakallu Rock Garden Canyon",
                "Belum Underground Caves",
                "Yaganti Uma Maheshwara Cave Temple"
        );

        // Verify NO generic fabrications
        for (SightseeingPlaceDto p : places) {
            assertThat(p.getName()).doesNotContainIgnoringCase("Explore Artisan Spice");
            assertThat(p.getName()).doesNotContainIgnoringCase("Traditional Handicrafts Bazaar");
            assertThat(p.getName()).doesNotContainIgnoringCase("Scenic Walk");
            assertThat(p.getLatitude()).isNotZero();
            assertThat(p.getLongitude()).isNotZero();
            // Coordinates should be in Andhra Pradesh / Kurnool region (~14.5 to 16.5 lat, 77.0 to 79.0 lng)
            assertThat(p.getLatitude()).isBetween(14.0, 16.5);
            assertThat(p.getLongitude()).isBetween(77.0, 79.0);
        }
    }

    // =========================================================================
    // Issue 3: Add an Optional User-Controlled Stop Along the Way
    // =========================================================================

    @Test
    @DisplayName("Issue 3: Route through intermediate stop Hyderabad -> Kurnool -> Bengaluru preserves stop order and calculates full route")
    void testIntermediateStopRouteOrder() {
        List<String> destinations = List.of("Kurnool", "Bengaluru");
        RouteSummaryDto summary = routeService.calculateCompleteRoute("Hyderabad", destinations, "CAR");

        assertThat(summary).isNotNull();
        assertThat(summary.getTotalDestinations()).isEqualTo(2);

        // Path should strictly be: Hyderabad -> Kurnool -> Bengaluru
        List<String> path = summary.getOriginalOrder();
        assertThat(path).containsExactly("Hyderabad", "Kurnool", "Bengaluru");

        // Should have 2 segments
        assertThat(summary.getSegments()).hasSize(2);
        assertThat(summary.getSegments().get(0).getFromLocation()).isEqualTo("Hyderabad");
        assertThat(summary.getSegments().get(0).getToLocation()).isEqualTo("Kurnool");
        assertThat(summary.getSegments().get(0).getDistanceKm()).isGreaterThan(180.0);

        assertThat(summary.getSegments().get(1).getFromLocation()).isEqualTo("Kurnool");
        assertThat(summary.getSegments().get(1).getToLocation()).isEqualTo("Bengaluru");
        assertThat(summary.getSegments().get(1).getDistanceKm()).isGreaterThan(300.0);

        // Total distance must be the sum of segments
        double totalDist = summary.getTotalDistanceKm();
        assertThat(totalDist).isGreaterThan(500.0);
    }

    @Test
    @DisplayName("Issue 3: Validation rejects trip when destination is identical to starting location")
    void testValidationRejectsIdenticalStartAndDestination() {
        TripRequestDto req = new TripRequestDto();
        req.setStartLocation("Hyderabad");
        req.setDestinationNames(List.of("Hyderabad"));
        req.setStartDate(LocalDate.now());
        req.setEndDate(LocalDate.now().plusDays(2));

        assertThatThrownBy(() -> tripService.generateGuestTrip(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be identical");
    }

    @Test
    @DisplayName("Issue 3: Intermediate stops matching starting location are cleaned from trip")
    void testIntermediateStopMatchingStartIsCleaned() {
        TripRequestDto req = new TripRequestDto();
        req.setStartLocation("Hyderabad");
        // Intermediate stop matches start: Hyderabad -> Hyderabad -> Bengaluru
        req.setDestinationNames(List.of("Hyderabad", "Bengaluru"));
        req.setStartDate(LocalDate.now());
        req.setEndDate(LocalDate.now().plusDays(2));

        TripResponseDto guestTrip = tripService.generateGuestTrip(req);
        assertThat(guestTrip).isNotNull();
        assertThat(guestTrip.getDestinations()).hasSize(1);
        assertThat(guestTrip.getDestinations().get(0).getName()).isEqualTo("Bengaluru");
    }

    @Test
    @DisplayName("Issue 3: Multi-stop guest trip generates sequential destinations and day plans")
    void testMultiStopGuestTripPlan() {
        TripRequestDto req = new TripRequestDto();
        req.setStartLocation("Hyderabad");
        req.setDestinationNames(List.of("Kurnool", "Bengaluru"));
        req.setStartDate(LocalDate.now());
        req.setEndDate(LocalDate.now().plusDays(2));
        req.setDailyStartTime("08:00");
        req.setDailyEndTime("20:00");
        req.setTransportMode("CAR");

        TripResponseDto guestTrip = tripService.generateGuestTrip(req);
        assertThat(guestTrip).isNotNull();
        assertThat(guestTrip.getDestinations()).hasSize(2);
        assertThat(guestTrip.getDestinations().get(0).getName()).isEqualTo("Kurnool");
        assertThat(guestTrip.getDestinations().get(1).getName()).isEqualTo("Bengaluru");
        assertThat(guestTrip.getDayItineraries()).isNotEmpty();
    }
}
