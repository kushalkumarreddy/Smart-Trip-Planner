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
import java.util.*;

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
                tripVersionService, smartStopsService);
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
        // Must NOT be in the morning at 10:15 AM! Hotel arrival is an evening
        // transition (>= 16:00 / 4:00 PM)
        assertThat(hotelStartTime)
                .as("Hotel return should not happen at morning 10:15 AM, must be in the afternoon or evening")
                .isAfterOrEqualTo(LocalTime.of(16, 0));

        // 3. Verify dinner is scheduled in the evening
        ItineraryItemDto dinner = items.stream()
                .filter(it -> "MEAL".equalsIgnoreCase(it.getActivityType()) && it.getActivity() != null
                        && it.getActivity().toLowerCase().contains("dinner"))
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
        List<SightseeingPlaceDto> places = recommendationService.getRecommendedPlaces("Kurnool", null, "popularity",
                null);
        assertThat(places).isNotEmpty();
        assertThat(places.size()).isGreaterThanOrEqualTo(5);

        List<String> names = places.stream().map(SightseeingPlaceDto::getName).toList();
        // Authentic landmarks
        assertThat(names).contains(
                "Konda Reddy Buruju Historic Fort",
                "Oravakallu Rock Garden Canyon",
                "Belum Underground Caves",
                "Yaganti Uma Maheshwara Cave Temple");

        // Verify NO generic fabrications
        for (SightseeingPlaceDto p : places) {
            assertThat(p.getName()).doesNotContainIgnoringCase("Explore Artisan Spice");
            assertThat(p.getName()).doesNotContainIgnoringCase("Traditional Handicrafts Bazaar");
            assertThat(p.getName()).doesNotContainIgnoringCase("Scenic Walk");
            assertThat(p.getLatitude()).isNotZero();
            assertThat(p.getLongitude()).isNotZero();
            // Coordinates should be in Andhra Pradesh / Kurnool region (~14.5 to 16.5 lat,
            // 77.0 to 79.0 lng)
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

    // =========================================================================
    // Issue 1: Fix Missing Attraction Recommendations & Pipeline
    // =========================================================================

    @Test
    @DisplayName("Issue 1: Multi-day stay in a destination schedules real attractions on each day without drying up")
    void testMultiDayTripSchedulesDistinctAttractionsAcrossDays() {
        Trip trip = new Trip();
        trip.setId(201L);
        trip.setStartLocation("Bengaluru");
        trip.setStartDate(LocalDate.of(2026, 11, 15));
        trip.setEndDate(LocalDate.of(2026, 11, 17));
        trip.setDailyStartTime("08:00");
        trip.setDailyEndTime("20:30");
        trip.setTransportMode("CAR");

        Destination blr = new Destination("Bengaluru", 0, 3);
        blr.setId(201L);
        blr.setLatitude(12.9716);
        blr.setLongitude(77.5946);
        blr.setDistanceFromPrevious(0.0);
        blr.setTravelTimeFromPrevious(0);

        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, List.of(blr), "CAR");
        assertThat(days).hasSize(3);

        DayItineraryDto day1 = days.get(0);
        DayItineraryDto day2 = days.get(1);
        DayItineraryDto day3 = days.get(2);

        long d1Sightseeing = day1.getItems().stream().filter(i -> "SIGHTSEEING".equalsIgnoreCase(i.getActivityType()))
                .count();
        long d2Sightseeing = day2.getItems().stream().filter(i -> "SIGHTSEEING".equalsIgnoreCase(i.getActivityType()))
                .count();
        long d3Sightseeing = day3.getItems().stream().filter(i -> "SIGHTSEEING".equalsIgnoreCase(i.getActivityType()))
                .count();

        assertThat(d1Sightseeing).as("Day 1 sightseeing count").isGreaterThanOrEqualTo(3L);
        assertThat(d2Sightseeing).as("Day 2 sightseeing count should NOT dry up").isGreaterThanOrEqualTo(3L);
        assertThat(d3Sightseeing).as("Day 3 sightseeing count").isGreaterThanOrEqualTo(2L);

        // Ensure distinct attractions are scheduled across days
        List<String> allSightseeingActivities = new ArrayList<>();
        for (DayItineraryDto day : days) {
            for (ItineraryItemDto it : day.getItems()) {
                if ("SIGHTSEEING".equalsIgnoreCase(it.getActivityType())) {
                    allSightseeingActivities.add(ItineraryService.normalizePlaceKey(it.getActivity()));
                }
            }
        }
        Set<String> uniqueActivities = new HashSet<>(allSightseeingActivities);
        assertThat(uniqueActivities.size())
                .as("Across 3 days, attractions should be distinct without immediate duplicates")
                .isEqualTo(allSightseeingActivities.size());
    }

    @Test
    @DisplayName("Issue 1: Recommends real, identifiable attractions for major cities with valid coordinates and ratings")
    void testAttractionsForMajorCities() {
        // Kolkata
        List<SightseeingPlaceDto> kolkataPlaces = recommendationService.getRecommendedPlaces("Kolkata", null,
                "popularity", null);
        assertThat(kolkataPlaces).isNotEmpty();
        assertThat(kolkataPlaces.size()).isGreaterThanOrEqualTo(6);
        assertThat(kolkataPlaces.stream().anyMatch(p -> p.getName().contains("Victoria Memorial"))).isTrue();
        assertThat(kolkataPlaces.stream().anyMatch(p -> p.getName().contains("Howrah Bridge"))).isTrue();
        assertThat(kolkataPlaces.stream().allMatch(p -> p.getLatitude() != 0.0 && p.getRating() >= 4.0)).isTrue();

        // Pune
        List<SightseeingPlaceDto> punePlaces = recommendationService.getRecommendedPlaces("Pune", null, "popularity",
                null);
        assertThat(punePlaces).isNotEmpty();
        assertThat(punePlaces.stream().anyMatch(p -> p.getName().contains("Shaniwar Wada"))).isTrue();
        assertThat(punePlaces.stream().anyMatch(p -> p.getName().contains("Aga Khan Palace"))).isTrue();

        // Delhi
        List<SightseeingPlaceDto> delhiPlaces = recommendationService.getRecommendedPlaces("Delhi", null, "popularity",
                null);
        assertThat(delhiPlaces).isNotEmpty();
        assertThat(delhiPlaces.size()).isGreaterThanOrEqualTo(7);
        assertThat(delhiPlaces.stream()
                .anyMatch(p -> p.getName().contains("Lotus Temple") || p.getName().contains("Akshardham"))).isTrue();
    }

    // =========================================================================
    // Issue 2: Add Captions Between Activities to Explain Time Gaps
    // =========================================================================

    @Test
    @DisplayName("Issue 2: Timeline transition captions are populated between sequential activities")
    void testTimelineTransitionCaptionsBetweenActivities() {
        Trip trip = new Trip();
        trip.setId(301L);
        trip.setStartLocation("Bengaluru");
        trip.setStartDate(LocalDate.of(2026, 11, 20));
        trip.setEndDate(LocalDate.of(2026, 11, 20));
        trip.setDailyStartTime("08:00");
        trip.setDailyEndTime("20:30");
        trip.setTransportMode("CAR");

        Destination dest = new Destination("Bengaluru", 0, 1);
        dest.setId(301L);
        dest.setLatitude(12.9716);
        dest.setLongitude(77.5946);

        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, List.of(dest), "CAR");
        assertThat(days).isNotEmpty();

        DayItineraryDto day = days.get(0);
        List<ItineraryItemDto> items = day.getItems();
        assertThat(items.size()).isGreaterThanOrEqualTo(4);

        // Every item after the first should have transition details
        for (int i = 1; i < items.size(); i++) {
            ItineraryItemDto item = items.get(i);
            assertThat(item.getTransitionSegments())
                    .as("Item %d (%s) should have transition segments from previous activity", i, item.getActivity())
                    .isNotEmpty();
            assertThat(item.getTransitionCaption())
                    .as("Item %d transition caption string should be populated", i)
                    .isNotEmpty();

            // First segment is typically travel/transit or transfer
            var firstSeg = item.getTransitionSegments().get(0);
            assertThat(firstSeg.getText())
                    .as("Transition segment text should contain travel or transfer details")
                    .matches(".*(Travel|Transfer|Drive|Walk|Public transport|min).*");
        }
    }

    @Test
    @DisplayName("Issue 2: Walking and public transport modes display specific mode captions and icons")
    void testWalkingAndTransitModeCaptions() {
        DayItineraryDto dayDto = new DayItineraryDto(1, LocalDate.now(), "Walking Day");
        ItineraryItemDto it1 = new ItineraryItemDto();
        it1.setStartTime("09:00 AM");
        it1.setEndTime("10:00 AM");
        it1.setActivity("Depart from Hotel");
        it1.setLocation("Central Hotel");
        it1.setActivityType("DEPARTURE");
        it1.setLatitude(12.9716);
        it1.setLongitude(77.5946);

        ItineraryItemDto it2 = new ItineraryItemDto();
        it2.setStartTime("10:20 AM");
        it2.setEndTime("11:30 AM");
        it2.setActivity("Visit Cubbon Park");
        it2.setLocation("Cubbon Park, Bengaluru");
        it2.setActivityType("SIGHTSEEING");
        it2.setLatitude(12.9763);
        it2.setLongitude(77.5929);
        it2.setTravelDistanceKm(1.2);
        it2.setTravelTimeMinutes(15);

        dayDto.setItems(new ArrayList<>(List.of(it1, it2)));

        // Test WALKING mode
        itineraryService.populateDayTransitions(dayDto, "WALKING");
        assertThat(it2.getTransitionSegments()).isNotEmpty();
        var walkSeg = it2.getTransitionSegments().get(0);
        assertThat(walkSeg.getIcon()).isEqualTo("bi-person-walking");
        assertThat(walkSeg.getText()).contains("Walk to next attraction").contains("15 min");

        // Test TRANSIT mode
        itineraryService.populateDayTransitions(dayDto, "TRANSIT");
        var transitSeg = it2.getTransitionSegments().get(0);
        assertThat(transitSeg.getIcon()).isEqualTo("bi-bus-front");
        assertThat(transitSeg.getText()).contains("Public transport").contains("15 min");
    }

    @Test
    @DisplayName("Issue 2: Explains time gaps by generating arrival buffer or free time captions")
    void testArrivalBufferAndFreeTimeCaptions() {
        DayItineraryDto dayDto = new DayItineraryDto(1, LocalDate.now(), "Gap Test");
        ItineraryItemDto it1 = new ItineraryItemDto();
        it1.setStartTime("10:00 AM");
        it1.setEndTime("11:00 AM");
        it1.setActivity("Morning Sightseeing");
        it1.setLatitude(12.9716);
        it1.setLongitude(77.5946);

        // Gap: 11:00 AM to 11:30 AM (30 min gap). Travel takes 15 min. Remaining
        // buffer: 15 min -> Arrival buffer
        ItineraryItemDto it2 = new ItineraryItemDto();
        it2.setStartTime("11:30 AM");
        it2.setEndTime("12:30 PM");
        it2.setActivity("Second Stop");
        it2.setLatitude(12.9800);
        it2.setLongitude(77.6000);
        it2.setTravelDistanceKm(2.5);
        it2.setTravelTimeMinutes(15);

        // Gap: 12:30 PM to 02:30 PM (120 min gap). Travel takes 20 min. Remaining gap:
        // 100 min -> Free time
        ItineraryItemDto it3 = new ItineraryItemDto();
        it3.setStartTime("02:30 PM");
        it3.setEndTime("04:00 PM");
        it3.setActivity("Afternoon Attraction");
        it3.setLatitude(12.9900);
        it3.setLongitude(77.6100);
        it3.setTravelDistanceKm(3.0);
        it3.setTravelTimeMinutes(20);

        dayDto.setItems(new ArrayList<>(List.of(it1, it2, it3)));
        itineraryService.populateDayTransitions(dayDto, "CAR");

        // Check it2 has arrival buffer caption
        assertThat(it2.getTransitionSegments().stream().anyMatch(s -> s.getType().equals("BUFFER"))).isTrue();
        var bufferSeg = it2.getTransitionSegments().stream().filter(s -> s.getType().equals("BUFFER")).findFirst()
                .get();
        assertThat(bufferSeg.getText()).contains("Arrival buffer").contains("15 min");

        // Check it3 has free time caption
        assertThat(it3.getTransitionSegments().stream().anyMatch(s -> s.getType().equals("FREE_TIME"))).isTrue();
        var freeSeg = it3.getTransitionSegments().stream().filter(s -> s.getType().equals("FREE_TIME")).findFirst()
                .get();
        assertThat(freeSeg.getText()).contains("Free time").contains("1 hr 40 min");
    }

    @Test
    @DisplayName("Issue 2: Opening hours conflict flags schedule warning in transition captions")
    void testScheduleConflictCaption() {
        DayItineraryDto dayDto = new DayItineraryDto(1, LocalDate.now(), "Conflict Test");
        ItineraryItemDto it1 = new ItineraryItemDto();
        it1.setStartTime("04:00 PM");
        it1.setEndTime("05:30 PM");
        it1.setActivity("Late Afternoon Tour");
        it1.setLatitude(12.9716);
        it1.setLongitude(77.5946);

        ItineraryItemDto it2 = new ItineraryItemDto();
        it2.setStartTime("05:45 PM");
        it2.setEndTime("07:00 PM");
        it2.setActivity("Historical Monument");
        it2.setNotes("⚠️ Schedule conflict • Place closes at 05:00 PM (closes before arrival)");
        it2.setLatitude(12.9800);
        it2.setLongitude(77.6000);
        it2.setTravelDistanceKm(2.0);
        it2.setTravelTimeMinutes(15);

        dayDto.setItems(new ArrayList<>(List.of(it1, it2)));
        itineraryService.populateDayTransitions(dayDto, "CAR");

        assertThat(it2.getTransitionSegments().stream().anyMatch(s -> s.getType().equals("CONFLICT"))).isTrue();
        var conflictSeg = it2.getTransitionSegments().stream().filter(s -> s.getType().equals("CONFLICT")).findFirst()
                .get();
        assertThat(conflictSeg.getText()).contains("Schedule conflict");
        assertThat(conflictSeg.getIcon()).isEqualTo("bi-exclamation-triangle");
    }

    @Test
    @DisplayName("Issue 2: Travel transition captions format duration in hours and minutes instead of raw minutes")
    void testTravelDurationFormattedInHoursAndMinutes() {
        DayItineraryDto dayDto = new DayItineraryDto(1, LocalDate.now(), "Long Travel Day");
        ItineraryItemDto it1 = new ItineraryItemDto();
        it1.setStartTime("06:00 AM");
        it1.setEndTime("07:00 AM");
        it1.setActivity("Depart from Delhi");
        it1.setLocation("Delhi");
        it1.setLatitude(28.6139);
        it1.setLongitude(77.2090);

        // Long travel leg: 468 minutes (7 hours 48 minutes) and 566.5 km
        ItineraryItemDto it2 = new ItineraryItemDto();
        it2.setStartTime("03:00 PM");
        it2.setEndTime("05:00 PM");
        it2.setActivity("Arrive at Destination");
        it2.setLocation("Jaipur");
        it2.setLatitude(26.9124);
        it2.setLongitude(75.7873);
        it2.setTravelDistanceKm(566.5);
        it2.setTravelTimeMinutes(468);

        dayDto.setItems(new ArrayList<>(List.of(it1, it2)));
        itineraryService.populateDayTransitions(dayDto, "CAR");

        assertThat(it2.getTransitionSegments()).isNotEmpty();
        var travelSeg = it2.getTransitionSegments().get(0);
        assertThat(travelSeg.getText())
                .as("Should format 468 minutes into '7 hr 48 min' with hours and minutes")
                .contains("7 hr 48 min")
                .doesNotContain("468 min");
    }

    // =========================================================================
    // Issue 5: Critical Itinerary Intelligence & Nearby Places Fallback Tests
    // =========================================================================

    @Test
    @DisplayName("Issue 5: Progressive radius discovers surrounding towns and tags proximity tiers accurately")
    void testProgressiveRadiusNearbyPlacesDiscovery() {
        // 1. Within 15 km (In-Destination)
        List<SightseeingPlaceDto> localPlaces = recommendationService.getNearbyPlacesWithProgressiveRadius(
                "Kanyakumari", 15, null, "popularity", null);
        assertThat(localPlaces).isNotEmpty();
        assertThat(localPlaces.stream().anyMatch(p -> p.getName().contains("Vivekananda Rock"))).isTrue();
        assertThat(localPlaces.stream().anyMatch(p -> p.getName().contains("Bhagavathy Amman"))).isTrue();
        assertThat(localPlaces.get(0).getProximityTier()).isIn("IN_DESTINATION", "NEARBY_ATTRACTION");

        // 2. Within 35 km (Nearby attractions & neighboring towns: Suchindram, Nagercoil, Padmanabhapuram)
        List<SightseeingPlaceDto> places35 = recommendationService.getNearbyPlacesWithProgressiveRadius(
                "Kanyakumari", 35, null, "popularity", null);
        assertThat(places35.size()).isGreaterThan(localPlaces.size());
        assertThat(places35.stream().anyMatch(p -> p.getName().contains("Suchindram"))).isTrue();
        assertThat(places35.stream().anyMatch(p -> p.getName().contains("Padmanabhapuram"))).isTrue();

        SightseeingPlaceDto suchindram = places35.stream()
                .filter(p -> p.getName().contains("Suchindram"))
                .findFirst().orElseThrow();
        assertThat(suchindram.getTownOrArea()).isEqualTo("Suchindram");
        assertThat(suchindram.getDistanceFromDestinationKm()).isBetween(5.0, 25.0);
        assertThat(suchindram.getProximityTier()).isEqualTo("NEARBY_ATTRACTION");

        // 3. Within 65 km (Surrounding towns: Thirparappu, Mathur Aqueduct)
        List<SightseeingPlaceDto> places65 = recommendationService.getNearbyPlacesWithProgressiveRadius(
                "Kanyakumari", 65, null, "popularity", null);
        assertThat(places65.stream().anyMatch(p -> p.getName().contains("Thirparappu"))).isTrue();
        assertThat(places65.stream().anyMatch(p -> p.getName().contains("Mathur Aqueduct"))).isTrue();

        SightseeingPlaceDto thirparappu = places65.stream()
                .filter(p -> p.getName().contains("Thirparappu"))
                .findFirst().orElseThrow();
        assertThat(thirparappu.getTownOrArea()).isEqualTo("Thirparappu");
        assertThat(thirparappu.getProximityTier()).isEqualTo("NEARBY_TOWN");

        // 4. Within 100 km (Regional excursions: Kovalam, Poovar, Thiruvananthapuram)
        List<SightseeingPlaceDto> places100 = recommendationService.getNearbyPlacesWithProgressiveRadius(
                "Kanyakumari", 100, null, "popularity", null);
        assertThat(places100.stream().anyMatch(p -> p.getName().contains("Kovalam"))).isTrue();
        assertThat(places100.stream().anyMatch(p -> p.getName().contains("Poovar"))).isTrue();
        assertThat(places100.stream().anyMatch(p -> p.getName().contains("Padmanabhaswamy"))).isTrue();
    }

    @Test
    @DisplayName("Issue 5: Multi-day Kanyakumari itinerary schedules authentic places across all days without generic empty fallbacks")
    void testMultiDayKanyakumariItineraryNeverEmpty() {
        Trip trip = new Trip();
        trip.setId(50L);
        trip.setStartLocation("Kanyakumari");
        trip.setStartDate(LocalDate.of(2026, 12, 1));
        trip.setEndDate(LocalDate.of(2026, 12, 3)); // 3 days
        trip.setDailyStartTime("08:00");
        trip.setDailyEndTime("20:00");
        trip.setTransportMode("CAR");

        Destination kanyakumari = new Destination("Kanyakumari", 0);
        kanyakumari.setId(501L);
        kanyakumari.setStayDurationDays(3);
        kanyakumari.setLatitude(8.0883);
        kanyakumari.setLongitude(77.5385);
        kanyakumari.setHotelName("Sparsa Resort Kanyakumari");

        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, List.of(kanyakumari), "CAR");
        assertThat(days).hasSize(3);

        for (DayItineraryDto day : days) {
            assertThat(day.getWarningMessage())
                    .as("Day %d should not have a warning about no verified sightseeing attractions", day.getDayNumber())
                    .satisfies(w -> assertThat(w == null || !w.contains("No verified sightseeing attractions")).isTrue());

            long sightseeingCount = day.getItems().stream()
                    .filter(it -> "SIGHTSEEING".equalsIgnoreCase(it.getActivityType()))
                    .count();
            assertThat(sightseeingCount)
                    .as("Day %d must have real sightseeing activities scheduled", day.getDayNumber())
                    .isGreaterThanOrEqualTo(2);
        }

        // Verify that nearby attractions like Suchindram or Padmanabhapuram or Thirparappu are scheduled on later days
        boolean hasNearbyStop = days.stream()
                .flatMap(d -> d.getItems().stream())
                .anyMatch(it -> it.getProximityTier() != null && !"IN_DESTINATION".equalsIgnoreCase(it.getProximityTier()));
        assertThat(hasNearbyStop)
                .as("Multi-day itinerary should intelligently incorporate surrounding nearby town attractions")
                .isTrue();
    }

    @Test
    @DisplayName("Issue 5: PlacesApiController /nearby endpoint returns verified places with category filtering")
    void testPlacesApiControllerNearbyEndpoint() {
        com.example.tripplanner.controller.PlacesApiController controller =
                new com.example.tripplanner.controller.PlacesApiController(tripService, googleMapsService, recommendationService, null);

        var response = controller.getNearbyPlaces("Kanyakumari", 50, "Temples", "popularity");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        List<SightseeingPlaceDto> temples = response.getBody();
        assertThat(temples).isNotNull().isNotEmpty();
        assertThat(temples.stream().allMatch(t -> "Temples".equalsIgnoreCase(t.getCategory()))).isTrue();
        assertThat(temples.stream().anyMatch(t -> t.getName().contains("Suchindram Thanumalayan Temple"))).isTrue();
        assertThat(temples.stream().anyMatch(t -> t.getName().contains("Bhagavathy Amman"))).isTrue();
    }
}
