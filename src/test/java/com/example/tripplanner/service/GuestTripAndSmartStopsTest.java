package com.example.tripplanner.service;

import com.example.tripplanner.dto.PlaceAlongRouteDto;
import com.example.tripplanner.dto.RouteSegmentDto;
import com.example.tripplanner.dto.RouteSummaryDto;
import com.example.tripplanner.dto.TripRequestDto;
import com.example.tripplanner.dto.TripResponseDto;
import com.example.tripplanner.entity.Destination;
import com.example.tripplanner.entity.Trip;
import com.example.tripplanner.repository.DestinationRepository;
import com.example.tripplanner.repository.ItineraryItemRepository;
import com.example.tripplanner.repository.PlaceRepository;
import com.example.tripplanner.repository.TripCollaboratorRepository;
import com.example.tripplanner.repository.TripRepository;
import com.example.tripplanner.repository.TripSuggestionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GuestTripAndSmartStopsTest {

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
    private GoogleMapsService googleMapsService;
    @Mock
    private RouteService routeService;
    @Mock
    private ItineraryService itineraryService;
    @Mock
    private RecommendationService recommendationService;
    @Mock
    private SmartStopsService smartStopsService;
    @Mock
    private TripVersionService tripVersionService;

    @InjectMocks
    private TripService tripService;

    @Test
    @DisplayName("Guest Trip Planning: Generates full trip without requiring an authenticated user")
    void testGenerateGuestTrip() {
        MockHttpSession session = new MockHttpSession();

        TripRequestDto request = new TripRequestDto();
        request.setTripName("Coorg Gateway Trip");
        request.setStartLocation("Bengaluru");
        request.setDestinationNames(Arrays.asList("Mysore", "Coorg"));
        request.setStartDate(LocalDate.of(2026, 11, 10));
        request.setEndDate(LocalDate.of(2026, 11, 12));
        request.setDailyStartTime("08:00");
        request.setTransportMode("CAR");

        RouteSummaryDto mockRoute = new RouteSummaryDto();
        mockRoute.setRouteCalculationSuccess(true);
        mockRoute.setTotalDistanceKm(250.0);
        mockRoute.setTotalTravelTimeMinutes(300);
        when(routeService.calculateCompleteRoute(anyString(), anyList(), anyString())).thenReturn(mockRoute);
        when(itineraryService.generateItineraryPlan(any(Trip.class), anyList(), anyString())).thenReturn(Collections.emptyList());
        when(googleMapsService.geocode(anyString())).thenReturn(new double[]{12.9716, 77.5946});

        TripResponseDto result = tripService.generateGuestTrip(request);
        session.setAttribute("GUEST_TRIP", result);

        assertThat(result).isNotNull();
        assertThat(result.getIsGuest()).isTrue();
        assertThat(result.getStartLocation()).isEqualTo("Bengaluru");
        assertThat(result.getDestinations()).hasSize(2);
        assertThat(result.getDailyStartTime()).isEqualTo("08:00");
        assertThat(session.getAttribute("GUEST_TRIP")).isNotNull();
    }

    @Test
    @DisplayName("Public Transport Integrity: Transit segment accurately flags missing live transit provider")
    void testPublicTransportIntegrity() {
        RouteSegmentDto segment = new RouteSegmentDto("Bengaluru", "Mysore", 145.0, 180, "PUBLIC_TRANSPORT");
        segment.setVerifiedProvider("OSRM_ROAD_NETWORK");
        segment.setTransitAvailable(false);
        segment.setTransitNotice("Live public transit timetables unavailable for this segment without live transit provider credentials. Use direct transit links below.");

        assertThat(segment.getTransitAvailable()).isFalse();
        assertThat(segment.getTransitNotice()).contains("unavailable");
        assertThat(segment.getTransitNotice()).doesNotContain("Fabricated train 12610");
    }

    @Test
    @DisplayName("Smart Stops Discovery: Verified catalog includes emergency hospital and pharmacy stops")
    void testSmartStopsCatalogIncludesHospitalsAndPharmacies() {
        SmartStopsService service = new SmartStopsService(googleMapsService, routeService);

        Trip trip = new Trip();
        trip.setStartLocation("Bengaluru");
        trip.setTransportMode("CAR");
        Destination dest = new Destination("Mysore", 0);
        dest.setTrip(trip);
        trip.setDestinations(Collections.singletonList(dest));

        RouteSummaryDto routeSummary = new RouteSummaryDto();
        routeSummary.setRouteCalculationSuccess(true);
        // Include geometry coordinates along NH275 corridor near Mandya (12.528, 76.901)
        routeSummary.setFullRouteGeometry(Arrays.asList(
                new double[]{12.9716, 77.5946},
                new double[]{12.7210, 77.2790},
                new double[]{12.5280, 76.9010},
                new double[]{12.3051, 76.6551}
        ));
        when(routeService.calculateCompleteRoute(anyString(), anyList(), anyString())).thenReturn(routeSummary);

        List<PlaceAlongRouteDto> hospitalStops = service.findSmartStopsAlongRoute(
                trip, Collections.singletonList(dest), "HOSPITAL", null, 25.0, null
        );

        assertThat(hospitalStops).isNotEmpty();
        assertThat(hospitalStops.get(0).getCategoryCode()).isEqualTo("HOSPITAL");
        assertThat(hospitalStops.get(0).getCategory()).contains("Hospital");

        List<PlaceAlongRouteDto> pharmacyStops = service.findSmartStopsAlongRoute(
                trip, Collections.singletonList(dest), "PHARMACY", null, 25.0, null
        );
        assertThat(pharmacyStops).isNotEmpty();
        assertThat(pharmacyStops.get(0).getCategoryCode()).isEqualTo("PHARMACY");
    }
}
