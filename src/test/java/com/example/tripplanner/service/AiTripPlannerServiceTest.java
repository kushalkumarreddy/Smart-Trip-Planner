package com.example.tripplanner.service;

import com.example.tripplanner.dto.AiTripPromptDto;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AI Travel Assistant Natural-Language Processing & Validation Tests")
class AiTripPlannerServiceTest {

    @Mock
    private RouteService routeService;

    @InjectMocks
    private AiTripPlannerService aiTripPlannerService;

    @BeforeEach
    void setUp() {
        RouteSummaryDto mockRouteSummary = new RouteSummaryDto();
        mockRouteSummary.setTotalDistanceKm(350.0);
        mockRouteSummary.setTotalTravelTimeMinutes(420);
        mockRouteSummary.setRouteCalculationSuccess(true);
        when(routeService.calculateCompleteRoute(anyString(), anyList(), anyString()))
                .thenReturn(mockRouteSummary);
    }

    @Test
    @DisplayName("Natural-language request extracts full parameters without unnecessary forms")
    void testExtractTripParametersFromNaturalLanguage() {
        String prompt = "Plan a 5-day family road trip from Bengaluru to Coorg and Wayanad. " +
                "Include waterfalls, scenic viewpoints, relaxed driving hours, suitable lunch breaks, and comfortable overnight stays.";

        AiTripPromptDto.Response result = aiTripPlannerService.parseAndGenerateTrip(prompt);

        assertThat(result).isNotNull();
        assertThat(result.getRequiresFollowUp()).isFalse();
        assertThat(result.getFollowUpQuestions()).isEmpty();

        // Extracted location parameters
        assertThat(result.getStartLocation()).containsIgnoringCase("Bengaluru");
        assertThat(result.getDestinationNames()).contains("Coorg", "Wayanad");

        // Trip parameters
        assertThat(result.getTotalDays()).isEqualTo(5);
        assertThat(result.getTransportMode()).isEqualTo("CAR");
        assertThat(result.getTravelStyle()).isEqualTo("RELAXED");

        // Preferences & accommodation
        assertThat(result.getPreferredInterests()).contains("Waterfalls", "Viewpoints");
        assertThat(result.getAccommodationPreference()).containsIgnoringCase("Comfortable overnight stays");
        assertThat(result.getAccessibilityNotes()).containsIgnoringCase("Family-friendly");

        // Route validation
        assertThat(result.getRouteValidationSuccess()).isTrue();
    }

    @Test
    @DisplayName("Missing essential starting location prompts concise follow-up questions instead of guessing")
    void testMissingStartingLocationProducesFollowUp() {
        String vaguePrompt = "Plan a 3-day road trip to Ooty and Kodaikanal with friends.";

        AiTripPromptDto.Response result = aiTripPlannerService.parseAndGenerateTrip(vaguePrompt);

        assertThat(result).isNotNull();
        assertThat(result.getRequiresFollowUp()).isTrue();
        assertThat(result.getFollowUpQuestions()).isNotEmpty();
        assertThat(result.getFollowUpQuestions().get(0)).containsIgnoringCase("starting location");
    }

    @Test
    @DisplayName("Missing destination stops prompts concise follow-up question")
    void testMissingDestinationsProducesFollowUp() {
        String vaguePrompt = "I am starting from Mumbai and want a 4-day scenic trip.";

        AiTripPromptDto.Response result = aiTripPlannerService.parseAndGenerateTrip(vaguePrompt);

        assertThat(result).isNotNull();
        assertThat(result.getRequiresFollowUp()).isTrue();
        assertThat(result.getFollowUpQuestions()).isNotEmpty();
        assertThat(result.getFollowUpQuestions().get(0)).containsIgnoringCase("destinations");
    }

    @Test
    @DisplayName("Route calculation failure triggers validation error instead of inventing fake distances")
    void testRouteValidationFailureHandledGracefully() {
        RouteSummaryDto failedRoute = new RouteSummaryDto();
        failedRoute.setRouteCalculationSuccess(false);
        when(routeService.calculateCompleteRoute(anyString(), anyList(), anyString()))
                .thenReturn(failedRoute);

        String prompt = "Plan a 3-day trip from Delhi to Agra and Jaipur by car.";
        AiTripPromptDto.Response result = aiTripPlannerService.parseAndGenerateTrip(prompt);

        assertThat(result.getRouteValidationSuccess()).isFalse();
        assertThat(result.getValidationErrorMessage()).contains("routing provider could not compute");
    }
}
