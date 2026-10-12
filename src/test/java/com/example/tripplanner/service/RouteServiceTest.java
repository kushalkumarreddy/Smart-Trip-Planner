package com.example.tripplanner.service;

import com.example.tripplanner.dto.RouteSegmentDto;
import com.example.tripplanner.dto.RouteSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RouteServiceTest {

    @Mock
    private GoogleMapsService googleMapsService;

    @InjectMocks
    private RouteService routeService;

    @BeforeEach
    void setUp() {
        when(googleMapsService.calculateRouteSegment(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String from = invocation.getArgument(0);
                    String to = invocation.getArgument(1);
                    if (from.equals(to)) return new GoogleMapsService.RouteCalculationResult(0.0, 0);
                    if ((from.equals("Bengaluru") && to.equals("Mysore")) || (from.equals("Mysore") && to.equals("Bengaluru"))) {
                        return new GoogleMapsService.RouteCalculationResult(143.5, 180);
                    }
                    if ((from.equals("Mysore") && to.equals("Coorg")) || (from.equals("Coorg") && to.equals("Mysore"))) {
                        return new GoogleMapsService.RouteCalculationResult(118.0, 160);
                    }
                    if ((from.equals("Bengaluru") && to.equals("Coorg")) || (from.equals("Coorg") && to.equals("Bengaluru"))) {
                        return new GoogleMapsService.RouteCalculationResult(250.0, 310);
                    }
                    return new GoogleMapsService.RouteCalculationResult(100.0, 120);
                });
    }

    @Test
    @DisplayName("Route Segment Calculations and Total Distance Consistency: Sum of segments equals total")
    void testCalculateCompleteRouteSegmentConsistency() {
        List<String> destinations = Arrays.asList("Mysore", "Coorg");
        RouteSummaryDto summary = routeService.calculateCompleteRoute("Bengaluru", destinations, "CAR");

        assertThat(summary).isNotNull();
        assertThat(summary.getSegments()).hasSize(2);

        RouteSegmentDto leg1 = summary.getSegments().get(0);
        RouteSegmentDto leg2 = summary.getSegments().get(1);

        assertThat(leg1.getDistanceKm()).isEqualTo(143.5);
        assertThat(leg2.getDistanceKm()).isEqualTo(118.0);

        // Consistency Requirement: TotalDistance == Dist(A, B) + Dist(B, C)
        double expectedTotal = Math.round((leg1.getDistanceKm() + leg2.getDistanceKm()) * 10.0) / 10.0;
        assertThat(summary.getTotalDistanceKm()).isEqualTo(expectedTotal);

        int expectedTravelTime = leg1.getDurationMinutes() + leg2.getDurationMinutes();
        assertThat(summary.getTotalTravelTimeMinutes()).isEqualTo(expectedTravelTime);
    }

    @Test
    @DisplayName("Route Optimization: Substantial detour triggers optimization recommendation with positive savings")
    void testRouteOptimizationAvailableWhenOrderCanBeImproved() {
        // Setup a circuit where user specified suboptimal order: Bengaluru -> Coorg -> Mysore
        // (Bengaluru to Coorg: 250km, Coorg to Mysore: 118km = 368km)
        // Alternative: Bengaluru -> Mysore -> Coorg (143.5km + 118km = 261.5km, saving ~106.5km)
        List<String> suboptimalDestinations = Arrays.asList("Coorg", "Mysore");

        RouteSummaryDto summary = routeService.calculateCompleteRoute("Bengaluru", suboptimalDestinations, "CAR");

        assertThat(summary).isNotNull();
        assertThat(summary.getOptimizationRecommended()).isTrue();
        assertThat(summary.getPotentialSavingsKm()).isGreaterThan(1.5);
        assertThat(summary.getPotentialSavingsMinutes()).isGreaterThan(5);
        assertThat(summary.getRecommendedOrder()).containsExactly("Bengaluru", "Mysore", "Coorg");
    }

    @Test
    @DisplayName("Route Optimization: Optimal route does NOT trigger unnecessary reorder recommendation")
    void testRouteOptimizationNotRecommendedWhenAlreadyOptimal() {
        // Bengaluru -> Mysore -> Coorg is already the optimal progression
        List<String> optimalDestinations = Arrays.asList("Mysore", "Coorg");

        RouteSummaryDto summary = routeService.calculateCompleteRoute("Bengaluru", optimalDestinations, "CAR");

        assertThat(summary).isNotNull();
        assertThat(summary.getOptimizationRecommended()).isFalse();
        assertThat(summary.getPotentialSavingsKm()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Trip with single destination returns zero savings and no reorder")
    void testSingleDestinationTrip() {
        List<String> destinations = Collections.singletonList("Mysore");

        RouteSummaryDto summary = routeService.calculateCompleteRoute("Bengaluru", destinations, "CAR");

        assertThat(summary).isNotNull();
        assertThat(summary.getSegments()).hasSize(1);
        assertThat(summary.getOptimizationRecommended()).isFalse();
        assertThat(summary.getTotalDistanceKm()).isEqualTo(143.5);
    }

    @Test
    @DisplayName("Identical origin and destination returns 0 km without error")
    void testZeroDistanceIdenticalLocations() {
        when(googleMapsService.calculateRouteSegment(eq("Mysore"), eq("Mysore"), anyString()))
                .thenReturn(new GoogleMapsService.RouteCalculationResult(0.0, 0));

        RouteSummaryDto summary = routeService.calculateCompleteRoute("Mysore", Collections.singletonList("Mysore"), "CAR");

        assertThat(summary).isNotNull();
        assertThat(summary.getTotalDistanceKm()).isEqualTo(0.0);
        assertThat(summary.getTotalTravelTimeMinutes()).isEqualTo(0);
    }
}
