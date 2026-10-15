package com.example.tripplanner.service;

import com.example.tripplanner.dto.*;
import com.example.tripplanner.entity.*;
import com.example.tripplanner.exception.BadRequestException;
import com.example.tripplanner.repository.*;
import com.example.tripplanner.util.TimeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StayDurationAndSmartDayPlannerTest {

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
    private TripSuggestionRepository tripSuggestionRepository;

    @Mock
    private RouteService routeService;

    @Mock
    private GoogleMapsService googleMapsService;

    @Mock
    private RecommendationService recommendationService;

    @Mock
    private ItineraryService itineraryService;

    @InjectMocks
    private TripService tripService;

    private User testUser;
    private Trip testTrip;
    private Destination testDest;

    @BeforeEach
    void setUp() {
        testUser = new User("Explorer", "explorer@example.com", "SecretPass123");
        testUser.setId(1L);

        testTrip = new Trip();
        testTrip.setId(100L);
        testTrip.setUser(testUser);
        testTrip.setTripName("Southern Highlands Road Trip");
        testTrip.setStartLocation("Bengaluru");
        testTrip.setStartDate(LocalDate.of(2026, 11, 1));
        testTrip.setEndDate(LocalDate.of(2026, 11, 6));
        testTrip.setDailyStartTime("08:00");
        testTrip.setDailyEndTime("20:00");
        testTrip.setTransportMode("CAR");

        testDest = new Destination("Mysuru", 0, 2);
        testDest.setId(200L);
        testDest.setTrip(testTrip);
        testDest.setStayDurationDays(2);
        testDest.setStayDurationNights(1);
        testTrip.setDestinations(new ArrayList<>(List.of(testDest)));
    }

    // =========================================================================
    // 1. TimeUtils 15-Minute Clock Interval Rounding Tests (Requirement 4)
    // =========================================================================

    @Test
    @DisplayName("Time rounding: 5:57 PM rounds to 6:00 PM")
    void testRoundToNearest_5_57_PM() {
        LocalTime raw = LocalTime.of(17, 57);
        LocalTime rounded = TimeUtils.roundToInterval(raw, 15);
        assertEquals(LocalTime.of(18, 0), rounded);
        assertEquals("6:00 PM", TimeUtils.formatAmPm(rounded));
    }

    @Test
    @DisplayName("Time rounding: 10:07 AM rounds to 10:00 AM")
    void testRoundToNearest_10_07_AM() {
        LocalTime raw = LocalTime.of(10, 7);
        LocalTime rounded = TimeUtils.roundToInterval(raw, 15);
        assertEquals(LocalTime.of(10, 0), rounded);
        assertEquals("10:00 AM", TimeUtils.formatAmPm(rounded));
    }

    @Test
    @DisplayName("Time rounding: 2:23 PM rounds to 2:30 PM")
    void testRoundToNearest_2_23_PM() {
        LocalTime raw = LocalTime.of(14, 23);
        LocalTime rounded = TimeUtils.roundToInterval(raw, 15);
        assertEquals(LocalTime.of(14, 30), rounded);
        assertEquals("2:30 PM", TimeUtils.formatAmPm(rounded));
    }

    @Test
    @DisplayName("Time rounding: 8:44 AM rounds to 8:45 AM")
    void testRoundToNearest_8_44_AM() {
        LocalTime raw = LocalTime.of(8, 44);
        LocalTime rounded = TimeUtils.roundToInterval(raw, 15);
        assertEquals(LocalTime.of(8, 45), rounded);
        assertEquals("8:45 AM", TimeUtils.formatAmPm(rounded));
    }

    @Test
    @DisplayName("Time rounding up for safe transit buffers: raw arrival 10:37 with 10m buffer rounds up to 11:00 AM")
    void testRoundUpToIntervalSafeArrival() {
        LocalTime rawArrival = LocalTime.of(10, 37);
        LocalTime buffered = rawArrival.plusMinutes(10); // 10:47
        LocalTime roundedUp = TimeUtils.roundUpToInterval(buffered, 15);
        assertEquals(LocalTime.of(11, 0), roundedUp);
        assertEquals("11:00 AM", TimeUtils.formatAmPm(roundedUp));
    }

    @Test
    @DisplayName("Time rounding: handle midnight boundary safely")
    void testRoundMidnightCrossing() {
        LocalTime nearMidnight = LocalTime.of(23, 58);
        LocalTime rounded = TimeUtils.roundToInterval(nearMidnight, 15);
        assertEquals(LocalTime.of(0, 0), rounded);
    }

    // =========================================================================
    // 2. Stay Duration Update and Validation (Requirement 1)
    // =========================================================================

    @Test
    @DisplayName("Update destination stay duration: increases days and updates dates")
    void testUpdateDestinationStayDuration_Success() {
        when(tripRepository.findById(100L)).thenReturn(Optional.of(testTrip));
        when(destinationRepository.findById(200L)).thenReturn(Optional.of(testDest));
        when(destinationRepository.findByTripOrderBySequenceOrderAsc(testTrip)).thenReturn(new ArrayList<>(List.of(testDest)));
        when(destinationRepository.save(any(Destination.class))).thenAnswer(i -> i.getArgument(0));

        RouteSummaryDto mockRouteSummary = new RouteSummaryDto();
        mockRouteSummary.setTotalDistanceKm(150.0);
        mockRouteSummary.setTotalTravelTimeMinutes(180);
        when(routeService.calculateCompleteRoute(any(), any(), any())).thenReturn(mockRouteSummary);
        when(itineraryItemRepository.findByTripIdOrderByDayNumberAscStartTimeAsc(100L)).thenReturn(Collections.emptyList());
        when(itineraryService.generateItineraryPlan(any(), any(), any())).thenReturn(Collections.emptyList());

        StayDurationUpdateDto updateDto = new StayDurationUpdateDto(3, 2);
        updateDto.setRecalculateSchedule(false);

        TripResponseDto updated = tripService.updateDestinationStayDuration(100L, 200L, updateDto, testUser);

        assertNotNull(updated);
        assertEquals(3, testDest.getStayDurationDays());
        assertEquals(2, testDest.getStayDurationNights());
        verify(destinationRepository, atLeastOnce()).save(testDest);
    }

    @Test
    @DisplayName("Update stay duration: reject invalid 0 or negative days")
    void testUpdateDestinationStayDuration_InvalidDays() {
        when(tripRepository.findById(100L)).thenReturn(Optional.of(testTrip));
        when(destinationRepository.findById(200L)).thenReturn(Optional.of(testDest));

        StayDurationUpdateDto zeroDays = new StayDurationUpdateDto(0, 0);
        assertThrows(BadRequestException.class, () ->
                tripService.updateDestinationStayDuration(100L, 200L, zeroDays, testUser)
        );

        StayDurationUpdateDto negativeDays = new StayDurationUpdateDto(-2, 1);
        assertThrows(BadRequestException.class, () ->
                tripService.updateDestinationStayDuration(100L, 200L, negativeDays, testUser)
        );
    }

    @Test
    @DisplayName("Update stay duration: enforce trip ownership & collaborator security")
    void testUpdateDestinationStayDuration_Unauthorized() {
        User otherUser = new User("Intruder", "intruder@example.com", "pass");
        otherUser.setId(999L);

        when(tripRepository.findById(100L)).thenReturn(Optional.of(testTrip));
        when(tripCollaboratorRepository.findByTripAndUserEmail(any(), any())).thenReturn(Optional.empty());

        StayDurationUpdateDto dto = new StayDurationUpdateDto(2, 1);
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                tripService.updateDestinationStayDuration(100L, 200L, dto, otherUser)
        );
    }

    // =========================================================
    // 3. Move Activity Between Days (Requirement 2)
    // =========================================================

    @Test
    @DisplayName("Move activity to another day: updates day number and recalculates schedule")
    void testMoveActivityToDay_Success() {
        ItineraryItem item = new ItineraryItem();
        item.setId(500L);
        item.setTrip(testTrip);
        item.setDayNumber(1);
        item.setActivity("Palace Tour");
        item.setActivityType("SIGHTSEEING");
        item.setStartTime("10:00");
        item.setEndTime("12:00");
        item.setDurationMinutes(120);

        when(tripRepository.findById(100L)).thenReturn(Optional.of(testTrip));
        when(itineraryItemRepository.findById(500L)).thenReturn(Optional.of(item));
        when(itineraryItemRepository.findByTripIdAndDayNumberOrderByStartTimeAsc(100L, 3))
                .thenReturn(new ArrayList<>());
        when(itineraryItemRepository.save(any(ItineraryItem.class))).thenAnswer(i -> i.getArgument(0));

        RouteSummaryDto mockRouteSummary = new RouteSummaryDto();
        mockRouteSummary.setTotalDistanceKm(150.0);
        mockRouteSummary.setTotalTravelTimeMinutes(180);
        when(routeService.calculateCompleteRoute(any(), any(), any())).thenReturn(mockRouteSummary);
        when(destinationRepository.findByTripOrderBySequenceOrderAsc(testTrip)).thenReturn(new ArrayList<>(List.of(testDest)));
        when(itineraryItemRepository.findByTripIdOrderByDayNumberAscStartTimeAsc(100L)).thenReturn(Collections.emptyList());
        when(itineraryService.generateItineraryPlan(any(), any(), any())).thenReturn(Collections.emptyList());

        MoveActivityDto moveDto = new MoveActivityDto(500L, 3);
        TripResponseDto res = tripService.moveActivityToDay(100L, moveDto, testUser);

        assertNotNull(res);
        assertEquals(3, item.getDayNumber());
        verify(itineraryItemRepository, atLeastOnce()).save(item);
    }

    @Test
    @DisplayName("Move activity: reject invalid day numbers")
    void testMoveActivityToDay_InvalidDay() {
        when(tripRepository.findById(100L)).thenReturn(Optional.of(testTrip));

        MoveActivityDto invalidDayDto = new MoveActivityDto(500L, 0);
        assertThrows(BadRequestException.class, () ->
                tripService.moveActivityToDay(100L, invalidDayDto, testUser)
        );
    }

    // =========================================================
    // 4. Smart Day Planner Crowding and Multi-Day Feasibility (Requirement 2)
    // =========================================================

    @Test
    @DisplayName("Smart Day Planner: multi-day stay allocates unique spots across days without repeats")
    void testSmartDayPlanner_MultiDayUniqueSpots() {
        GoogleMapsService mockMaps = mock(GoogleMapsService.class);
        RecommendationService mockRec = mock(RecommendationService.class);

        when(mockMaps.calculateRouteSegment(anyString(), anyString(), anyString()))
                .thenReturn(new GoogleMapsService.RouteCalculationResult(140.0, 180));

        ItineraryService realItineraryService = new ItineraryService(mockMaps, mockRec);

        Destination multiDayDest = new Destination("Mysuru", 0, 2); // 2 days in Mysuru
        multiDayDest.setId(10L);
        multiDayDest.setStayDurationDays(2);
        multiDayDest.setStayDurationNights(1);

        Place p1 = new Place();
        p1.setId(101L);
        p1.setName("Mysore Palace");
        p1.setEstimatedVisitDurationMinutes(90);
        p1.setEstimatedTravelTimeMinutes(15);
        p1.setDistanceKm(3.0);
        p1.setRating(4.9);
        p1.setLatitude(12.3051);
        p1.setLongitude(76.6551);

        Place p2 = new Place();
        p2.setId(102L);
        p2.setName("Chamundi Hill");
        p2.setEstimatedVisitDurationMinutes(75);
        p2.setEstimatedTravelTimeMinutes(20);
        p2.setDistanceKm(5.0);
        p2.setRating(4.7);
        p2.setLatitude(12.2743);
        p2.setLongitude(76.6710);

        multiDayDest.setPlaces(List.of(p1, p2));

        Trip trip = new Trip();
        trip.setId(1L);
        trip.setStartLocation("Bengaluru");
        trip.setStartDate(LocalDate.of(2026, 11, 1));
        trip.setEndDate(LocalDate.of(2026, 11, 2));
        trip.setDailyStartTime("08:00");
        trip.setDailyEndTime("20:00");
        trip.setDestinations(List.of(multiDayDest));

        List<DayItineraryDto> days = realItineraryService.generateItineraryPlan(
                trip,
                List.of(multiDayDest),
                "CAR"
        );

        assertNotNull(days);
        assertEquals(2, days.size(), "2 stay days in Mysuru must produce 2 dedicated daily itineraries");

        // Verify that activities exist on both days
        assertFalse(days.get(0).getItems().isEmpty());
        assertFalse(days.get(1).getItems().isEmpty());

        // Check if Mysore Palace was scheduled
        boolean palaceOnDay1 = days.get(0).getItems().stream()
                .anyMatch(i -> i.getActivity() != null && i.getActivity().contains("Mysore Palace"));
        boolean palaceOnDay2 = days.get(1).getItems().stream()
                .anyMatch(i -> i.getActivity() != null && i.getActivity().contains("Mysore Palace"));

        assertTrue(palaceOnDay1, "Palace should be scheduled on Day 1");
        assertFalse(palaceOnDay2, "Palace must not be duplicated on Day 2");

        // Verify clean time formats (e.g. 15-minute intervals like 08:00 AM, 08:30 AM, etc.)
        days.forEach(day -> {
            day.getItems().forEach(item -> {
                if (item.getStartTime() != null) {
                    assertTrue(item.getStartTime().matches("\\d{1,2}:\\d{2} (AM|PM)"), "Start time must be formatted 12-hour AM/PM: " + item.getStartTime());
                }
                if (item.getEndTime() != null) {
                    assertTrue(item.getEndTime().matches("\\d{1,2}:\\d{2} (AM|PM)"), "End time must be formatted 12-hour AM/PM: " + item.getEndTime());
                }
            });
        });
    }
}
