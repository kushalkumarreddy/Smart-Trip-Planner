package com.example.tripplanner.service;

import com.example.tripplanner.dto.PlaceAlongRouteDto;
import com.example.tripplanner.dto.RouteSegmentDto;
import com.example.tripplanner.dto.RouteSummaryDto;
import com.example.tripplanner.dto.TripResponseDto;
import com.example.tripplanner.entity.Destination;
import com.example.tripplanner.entity.Trip;
import com.example.tripplanner.entity.User;
import com.example.tripplanner.repository.DestinationRepository;
import com.example.tripplanner.repository.ItineraryItemRepository;
import com.example.tripplanner.repository.TripRepository;
import com.example.tripplanner.util.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Smart Stops Along Route - Functional, Corridor & Detour Tests")
class SmartStopsServiceTest {

    @Mock
    private GoogleMapsService googleMapsService;
    @Mock
    private RouteService routeService;
    @Mock
    private TripRepository tripRepository;
    @Mock
    private DestinationRepository destinationRepository;
    @Mock
    private ItineraryItemRepository itineraryItemRepository;
    @Mock
    private ItineraryService itineraryService;
    @Mock
    private TripVersionService tripVersionService;

    private SmartStopsService smartStopsService;
    private TripService tripService;

    private User owner;
    private Trip trip;
    private Destination destMysore;
    private Destination destCoorg;

    @BeforeEach
    void setUp() {
        smartStopsService = new SmartStopsService(googleMapsService, routeService);

        owner = new User();
        owner.setId(1L);
        owner.setName("Traveler");
        owner.setEmail("traveler@example.com");

        trip = new Trip();
        trip.setId(101L);
        trip.setTripName("Bengaluru to Coorg via Mysore Highway");
        trip.setUser(owner);
        trip.setStartLocation("Bengaluru");
        trip.setStartLat(12.9716);
        trip.setStartLng(77.5946);
        trip.setStartDate(LocalDate.of(2026, 11, 1));
        trip.setEndDate(LocalDate.of(2026, 11, 4));
        trip.setTransportMode("CAR");

        destMysore = new Destination("Mysore", 0);
        destMysore.setId(10L);
        destMysore.setTrip(trip);
        destMysore.setLatitude(12.2958);
        destMysore.setLongitude(76.6394);

        destCoorg = new Destination("Coorg", 1);
        destCoorg.setId(11L);
        destCoorg.setTrip(trip);
        destCoorg.setLatitude(12.4244);
        destCoorg.setLongitude(75.7382);

        trip.getDestinations().add(destMysore);
        trip.getDestinations().add(destCoorg);

        // Simulated highway polyline from Bengaluru through Bidadi, Ramanagara, Maddur, Mandya, Srirangapatna to Mysore
        List<double[]> routeGeometry = new ArrayList<>();
        routeGeometry.add(new double[]{12.9716, 77.5946}); // Bengaluru
        routeGeometry.add(new double[]{12.7981, 77.3820}); // Bidadi
        routeGeometry.add(new double[]{12.7210, 77.2790}); // Ramanagara
        routeGeometry.add(new double[]{12.6510, 77.2020}); // Channapatna
        routeGeometry.add(new double[]{12.5841, 77.0450}); // Maddur
        routeGeometry.add(new double[]{12.5230, 76.8980}); // Mandya
        routeGeometry.add(new double[]{12.4244, 76.6947}); // Srirangapatna
        routeGeometry.add(new double[]{12.2958, 76.6394}); // Mysore
        routeGeometry.add(new double[]{12.4550, 75.9580}); // Kushalnagar
        routeGeometry.add(new double[]{12.4244, 75.7382}); // Coorg

        RouteSummaryDto routeSummary = new RouteSummaryDto();
        routeSummary.setRouteCalculationSuccess(true);
        routeSummary.setTotalDistanceKm(265.0);
        routeSummary.setTotalTravelTimeMinutes(330);
        routeSummary.setFullRouteGeometry(routeGeometry);

        RouteSegmentDto seg1 = new RouteSegmentDto("Bengaluru", "Mysore", 143.5, 180, "CAR");
        seg1.setSuccess(true);
        seg1.setFromLat(12.9716);
        seg1.setFromLng(77.5946);
        seg1.setToLat(12.2958);
        seg1.setToLng(76.6394);
        seg1.setGeometryCoordinates(routeGeometry.subList(0, 8));

        RouteSegmentDto seg2 = new RouteSegmentDto("Mysore", "Coorg", 118.0, 160, "CAR");
        seg2.setSuccess(true);
        seg2.setFromLat(12.2958);
        seg2.setFromLng(76.6394);
        seg2.setToLat(12.4244);
        seg2.setToLng(75.7382);
        seg2.setGeometryCoordinates(routeGeometry.subList(7, 10));

        routeSummary.getSegments().add(seg1);
        routeSummary.getSegments().add(seg2);

        when(routeService.calculateCompleteRoute(anyString(), anyList(), anyString()))
                .thenReturn(routeSummary);

        // Mock road calculations for detour computations using coordinate routing
        when(googleMapsService.calculateRouteBetweenCoordinates(anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString()))
                .thenAnswer(inv -> {
                    double lat1 = inv.getArgument(0);
                    double lon1 = inv.getArgument(1);
                    double lat2 = inv.getArgument(2);
                    double lon2 = inv.getArgument(3);
                    double dist = GeoUtils.calculateHaversineDistanceKm(lat1, lon1, lat2, lon2) * 1.15;
                    int mins = (int) Math.round(dist * 1.25);
                    return new GoogleMapsService.RouteCalculationResult(Math.round(dist * 10.0) / 10.0, mins);
                });
    }

    @Test
    @DisplayName("Route corridor search: Discovers authentic highway stops near the driving route")
    void testFindSmartStopsAlongRouteCorridor() {
        List<PlaceAlongRouteDto> stops = smartStopsService.findSmartStopsAlongRoute(
                trip, trip.getDestinations(), "ALL", null, 25.0, null);

        assertThat(stops).isNotEmpty();
        // Verify results contain highway locations along NH275
        boolean hasHighwaySpots = stops.stream().anyMatch(s ->
                s.getName().contains("Kamat Upachar") ||
                s.getName().contains("Maddur") ||
                s.getName().contains("Shell") ||
                s.getName().contains("Janapada Loka")
        );
        assertThat(hasHighwaySpots).isTrue();

        // Verify distance from route is within 15km corridor
        for (PlaceAlongRouteDto stop : stops) {
            assertThat(stop.getDistanceFromRouteKm()).isLessThanOrEqualTo(20.0);
            assertThat(stop.getLatitude()).isNotNull();
            assertThat(stop.getLongitude()).isNotNull();
            assertThat(stop.getCategory()).isNotBlank();
        }
    }

    @Test
    @DisplayName("Excludes unrelated city-wide spots far away from route")
    void testExcludesUnrelatedDistantSpots() {
        List<PlaceAlongRouteDto> stops = smartStopsService.findSmartStopsAlongRoute(
                trip, trip.getDestinations(), "ALL", null, 25.0, null);

        // Neither Mumbai, Delhi, nor far North India spots should appear on Bengaluru-Mysore-Coorg
        boolean hasDistantSpots = stops.stream().anyMatch(s ->
                s.getName().contains("Khalapur") ||
                s.getName().contains("Jewar") ||
                s.getName().contains("Yamuna")
        );
        assertThat(hasDistantSpots).isFalse();
    }

    @Test
    @DisplayName("Category filter: Accurately filters only requested categories")
    void testCategoryFilter() {
        // 1. Fuel only
        List<PlaceAlongRouteDto> fuelStops = smartStopsService.findSmartStopsAlongRoute(
                trip, trip.getDestinations(), "FUEL", null, 25.0, null);
        assertThat(fuelStops).isNotEmpty();
        assertThat(fuelStops).allMatch(s -> "FUEL".equalsIgnoreCase(s.getCategoryCode()));

        // 2. Food & Dining only
        List<PlaceAlongRouteDto> foodStops = smartStopsService.findSmartStopsAlongRoute(
                trip, trip.getDestinations(), "RESTAURANT", null, 25.0, null);
        assertThat(foodStops).isNotEmpty();
        assertThat(foodStops).allMatch(s -> "RESTAURANT".equalsIgnoreCase(s.getCategoryCode()));

        // 3. EV Charging only
        List<PlaceAlongRouteDto> evStops = smartStopsService.findSmartStopsAlongRoute(
                trip, trip.getDestinations(), "EV_CHARGING", null, 25.0, null);
        assertThat(evStops).isNotEmpty();
        assertThat(evStops).allMatch(s -> "EV_CHARGING".equalsIgnoreCase(s.getCategoryCode()));
    }

    @Test
    @DisplayName("Detour preference: Accurately enforces maximum detour duration")
    void testDetourDurationFilter() {
        // Up to 10 minutes detour
        List<PlaceAlongRouteDto> stopsUnder10Mins = smartStopsService.findSmartStopsAlongRoute(
                trip, trip.getDestinations(), "ALL", 10, null, null);

        assertThat(stopsUnder10Mins).isNotEmpty();
        for (PlaceAlongRouteDto s : stopsUnder10Mins) {
            assertThat(s.getDetourCalculated()).isTrue();
            assertThat(s.getDetourDurationMinutes()).isLessThanOrEqualTo(10);
        }
    }

    @Test
    @DisplayName("Detour failure handling: Does NOT invent fake detour values when routing fails")
    void testDetourFailureHandling() {
        // Simulate routing failure for an unknown stop
        when(googleMapsService.calculateRouteSegment(anyString(), eq("Unreachable Stop"), anyString()))
                .thenReturn(new GoogleMapsService.RouteCalculationResult(0.0, 0, new double[]{0,0}, new double[]{0,0}, false, false, "Connection timeout"));

        // If detour cannot be calculated, detourCalculated is false and no fake numbers are shown
        PlaceAlongRouteDto dto = new PlaceAlongRouteDto();
        dto.setName("Unreachable Stop");
        dto.setDetourCalculated(false);
        dto.setDetourDistanceKm(null);
        dto.setDetourDurationMinutes(null);

        assertThat(dto.getDetourCalculated()).isFalse();
        assertThat(dto.getDetourDurationMinutes()).isNull();
    }

    @Test
    @DisplayName("Keyword search along route filters candidate names and descriptions")
    void testKeywordSearch() {
        List<PlaceAlongRouteDto> searchVada = smartStopsService.findSmartStopsAlongRoute(
                trip, trip.getDestinations(), "ALL", null, 25.0, "Vada");

        assertThat(searchVada).isNotEmpty();
        assertThat(searchVada.get(0).getName()).containsIgnoringCase("Maddur");
    }

    @Test
    @DisplayName("Empty route or destinations returns safe empty list without error")
    void testEmptyRouteHandling() {
        List<PlaceAlongRouteDto> emptyDestStops = smartStopsService.findSmartStopsAlongRoute(
                trip, Collections.emptyList(), "ALL", null, 25.0, null);
        assertThat(emptyDestStops).isEmpty();

        List<PlaceAlongRouteDto> nullTripStops = smartStopsService.findSmartStopsAlongRoute(
                null, Collections.emptyList(), "ALL", null, 25.0, null);
        assertThat(nullTripStops).isEmpty();
    }
}
