package com.example.tripplanner.service;

import com.example.tripplanner.dto.RouteAlternativeDto;
import com.example.tripplanner.dto.RouteSegmentDto;
import com.example.tripplanner.dto.RouteSummaryDto;
import com.example.tripplanner.util.GeoUtils;
import org.junit.jupiter.api.AfterEach;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Route Accuracy, API Failure Handling, & Multi-Alternative Evaluation Tests")
class RouteAccuracyAndAlternativesTest {

    @Mock
    private GoogleMapsService googleMapsService;

    @InjectMocks
    private RouteService routeService;

    @BeforeEach
    void setUp() {
        GeoUtils.setSimulateRoutingFailure(false);

        when(googleMapsService.calculateRouteSegment(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String from = invocation.getArgument(0);
                    String to = invocation.getArgument(1);

                    if (from.equals(to)) return new GoogleMapsService.RouteCalculationResult(0.0, 0);

                    // Mock accurate realistic road distances between southern Indian destinations
                    if ((from.contains("Bengaluru") && to.contains("Mysore")) || (from.contains("Mysore") && to.contains("Bengaluru"))) {
                        return new GoogleMapsService.RouteCalculationResult(143.5, 180);
                    }
                    if ((from.contains("Mysore") && to.contains("Coorg")) || (from.contains("Coorg") && to.contains("Mysore"))) {
                        return new GoogleMapsService.RouteCalculationResult(118.2, 160);
                    }
                    if ((from.contains("Bengaluru") && to.contains("Coorg")) || (from.contains("Coorg") && to.contains("Bengaluru"))) {
                        return new GoogleMapsService.RouteCalculationResult(252.0, 310);
                    }
                    if ((from.contains("Coorg") && to.contains("Wayanad")) || (from.contains("Wayanad") && to.contains("Coorg"))) {
                        return new GoogleMapsService.RouteCalculationResult(115.0, 175);
                    }
                    return new GoogleMapsService.RouteCalculationResult(85.0, 110);
                });
    }

    @AfterEach
    void tearDown() {
        GeoUtils.setSimulateRoutingFailure(false);
    }

    @Test
    @DisplayName("Total route distance strictly equals the sum of consecutive valid legs")
    void testTotalDistanceEqualsSumOfSegments() {
        List<String> stops = Arrays.asList("Mysore", "Coorg", "Wayanad");
        RouteSummaryDto summary = routeService.calculateCompleteRoute("Bengaluru", stops, "CAR");

        assertThat(summary).isNotNull();
        assertThat(summary.getRouteCalculationSuccess()).isTrue();
        assertThat(summary.getSegments()).hasSize(3);

        double manualSum = 0.0;
        int manualDuration = 0;
        for (RouteSegmentDto seg : summary.getSegments()) {
            manualSum += seg.getDistanceKm();
            manualDuration += seg.getDurationMinutes();
        }
        manualSum = Math.round(manualSum * 10.0) / 10.0;

        assertThat(summary.getTotalDistanceKm()).isEqualTo(manualSum);
        assertThat(summary.getTotalTravelTimeMinutes()).isEqualTo(manualDuration);
    }

    @Test
    @DisplayName("Routing provider failure explicitly sets calculation success to false and flags error")
    void testRoutingProviderFailureHandling() {
        // Mock routing provider failure where calculating leg returns null for a multi-km route
        when(googleMapsService.calculateRouteSegment("Bengaluru", "Coorg", "CAR"))
                .thenReturn(null);

        RouteSummaryDto summary = routeService.calculateCompleteRoute("Bengaluru", List.of("Coorg"), "CAR");

        assertThat(summary).isNotNull();
        assertThat(summary.getRouteCalculationSuccess()).isFalse();
        assertThat(summary.getSegments()).isNotEmpty();
        RouteSegmentDto failedSegment = summary.getSegments().get(0);
        assertThat(failedSegment.getSuccess()).isFalse();
        // Crucial requirement: failed segments must NOT be treated as valid zero-distance routes
        assertThat(failedSegment.getDistanceKm()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Evaluate multi-strategy alternatives computes genuine savings and clear explanations")
    void testEvaluateAlternativeRoutesMultiStrategy() {
        // Suboptimal user order: Bengaluru -> Coorg -> Mysore (causes backtracking)
        List<String> suboptimalDestinations = Arrays.asList("Coorg", "Mysore");

        RouteSummaryDto summary = routeService.calculateCompleteRoute("Bengaluru", suboptimalDestinations, "CAR");

        assertThat(summary).isNotNull();
        assertThat(summary.getAlternatives()).isNotEmpty();
        assertThat(summary.getAlternatives()).extracting(RouteAlternativeDto::getStrategy)
                .contains("ORIGINAL_ROUTE", "SHORTEST_DISTANCE", "BALANCED_RELAXED");

        RouteAlternativeDto shortest = summary.getAlternatives().stream()
                .filter(a -> "SHORTEST_DISTANCE".equals(a.getStrategy()))
                .findFirst().orElseThrow();

        // Suboptimal: Blr -> Coorg (252) + Coorg -> Mysore (118.2) = 370.2 km
        // Optimal: Blr -> Mysore (143.5) + Mysore -> Coorg (118.2) = 261.7 km
        // Savings = 370.2 - 261.7 = 108.5 km
        assertThat(shortest.getSavingsKm()).isGreaterThan(50.0);
        assertThat(shortest.getSavingsMinutes()).isGreaterThan(30);
        assertThat(shortest.getDescription()).contains("driving distance");
        assertThat(shortest.getOrderedLocations()).containsExactly("Bengaluru", "Mysore", "Coorg");
    }

    @Test
    @DisplayName("Reordering destinations recalculates valid consecutive route segments")
    void testChangeDestinationOrderRecalculatesRoute() {
        // Original: Bengaluru -> Mysore -> Coorg
        RouteSummaryDto original = routeService.calculateCompleteRoute("Bengaluru", Arrays.asList("Mysore", "Coorg"), "CAR");
        // Reordered: Bengaluru -> Coorg -> Mysore
        RouteSummaryDto reordered = routeService.calculateCompleteRoute("Bengaluru", Arrays.asList("Coorg", "Mysore"), "CAR");

        assertThat(original.getTotalDistanceKm()).isNotEqualTo(reordered.getTotalDistanceKm());
        assertThat(original.getSegments().get(0).getToLocation()).isEqualTo("Mysore");
        assertThat(reordered.getSegments().get(0).getToLocation()).isEqualTo("Coorg");
    }
}
