package com.example.tripplanner.controller;

import com.example.tripplanner.dto.*;
import com.example.tripplanner.entity.User;
import com.example.tripplanner.service.AiTripPlannerService;
import com.example.tripplanner.service.RouteService;
import com.example.tripplanner.service.TripService;
import com.example.tripplanner.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trips")
public class TripApiController {

    private final TripService tripService;
    private final UserService userService;
    private final AiTripPlannerService aiTripPlannerService;
    private final RouteService routeService;

    public TripApiController(TripService tripService,
                             UserService userService,
                             AiTripPlannerService aiTripPlannerService,
                             RouteService routeService) {
        this.tripService = tripService;
        this.userService = userService;
        this.aiTripPlannerService = aiTripPlannerService;
        this.routeService = routeService;
    }

    @PostMapping
    public ResponseEntity<TripResponseDto> createTrip(@Valid @RequestBody TripRequestDto request,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto created = tripService.createTrip(request, user);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<TripResponseDto>> getUserTrips(@AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        List<TripResponseDto> trips = tripService.getUserTrips(user);
        return ResponseEntity.ok(trips);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripResponseDto> getTripById(@PathVariable("id") Long id,
                                                       @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto trip = tripService.getTripDtoById(id, user);
        return ResponseEntity.ok(trip);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteTrip(@PathVariable("id") Long id,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        tripService.deleteTrip(id, user);
        return ResponseEntity.ok(Map.of("message", "Trip deleted successfully"));
    }

    /**
     * Smart Route Optimization - Applies optimal TSP route order
     */
    @PostMapping("/{id}/optimize")
    public ResponseEntity<TripResponseDto> optimizeTripRoute(@PathVariable("id") Long id,
                                                             @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto optimized = tripService.applyOptimizedRoute(id, user);
        return ResponseEntity.ok(optimized);
    }

    /**
     * Preview Route Optimization without saving (used on Create Trip form)
     */
    @PostMapping("/preview-optimize")
    public ResponseEntity<RouteSummaryDto> previewRouteOptimization(@RequestBody Map<String, Object> body) {
        String startLocation = (String) body.get("startLocation");
        @SuppressWarnings("unchecked")
        List<String> destinations = (List<String>) body.get("destinations");
        String transportMode = (String) body.getOrDefault("transportMode", "CAR");

        RouteSummaryDto summary = routeService.calculateCompleteRoute(startLocation, destinations, transportMode);
        return ResponseEntity.ok(summary);
    }

    /**
     * Manual Destination Reordering (User can swipe/move destinations up & down) or apply optimized route
     */
    @PostMapping("/{id}/reorder")
    public ResponseEntity<TripResponseDto> reorderDestinations(@PathVariable("id") Long id,
                                                               @RequestBody Map<String, Object> body,
                                                               @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        if (body != null && Boolean.TRUE.equals(body.get("applyOptimizedOrder"))) {
            TripResponseDto optimized = tripService.applyOptimizedRoute(id, user);
            return ResponseEntity.ok(optimized);
        }

        List<Long> orderedIds = new ArrayList<>();
        if (body != null && body.containsKey("orderedDestinationIds")) {
            Object raw = body.get("orderedDestinationIds");
            if (raw instanceof List<?>) {
                for (Object item : (List<?>) raw) {
                    if (item instanceof Number) {
                        orderedIds.add(((Number) item).longValue());
                    } else if (item != null) {
                        try {
                            orderedIds.add(Long.valueOf(item.toString()));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
        TripResponseDto updated = tripService.reorderDestinations(id, orderedIds, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * Single Day Regeneration (Requirement 4)
     */
    @PostMapping({ "/{id}/days/{dayNumber}/regenerate", "/{id}/regenerate-day/{dayNumber}" })
    public ResponseEntity<TripResponseDto> regenerateDay(@PathVariable("id") Long id,
                                                         @PathVariable("dayNumber") Integer dayNumber,
                                                         @RequestBody(required = false) Map<String, String> body,
                                                         @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        String preference = (body != null) ? body.getOrDefault("preference", "BALANCED") : "BALANCED";
        TripResponseDto updated = tripService.regenerateSingleDay(id, dayNumber, preference, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * AI-Powered Trip Planner - Natural language trip parsing & structured extraction (Requirement 4)
     */
    @PostMapping("/ai-plan")
    public ResponseEntity<AiTripPromptDto.Response> aiPlanTrip(@RequestBody AiTripPromptDto.Request request) {
        AiTripPromptDto.Response response = aiTripPlannerService.parseAndGenerateTrip(request.getPrompt());
        return ResponseEntity.ok(response);
    }

    /**
     * Add Sightseeing Place to Itinerary
     */
    @PostMapping("/{id}/destinations/{destinationId}/places")
    public ResponseEntity<TripResponseDto> addPlaceToTrip(@PathVariable("id") Long id,
                                                          @PathVariable("destinationId") Long destinationId,
                                                          @RequestBody SightseeingPlaceDto placeDto,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto updated = tripService.addSightseeingPlaceToTrip(id, destinationId, placeDto, user);
        return ResponseEntity.ok(updated);
    }

    // Collaborative Planning Endpoints (Requirement 6)
    @PostMapping("/{id}/collaborators")
    public ResponseEntity<TripCollaboratorDto> addCollaborator(@PathVariable("id") Long id,
                                                               @RequestBody Map<String, String> body,
                                                               @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        String name = body.getOrDefault("name", "Trip Collaborator");
        String email = body.getOrDefault("email", "");
        String role = body.getOrDefault("role", "EDITOR");
        TripCollaboratorDto collab = tripService.addCollaborator(id, name, email, role, user);
        return ResponseEntity.ok(collab);
    }

    @DeleteMapping("/{id}/collaborators/{collaboratorId}")
    public ResponseEntity<Map<String, String>> removeCollaborator(@PathVariable("id") Long id,
                                                                  @PathVariable("collaboratorId") Long collaboratorId,
                                                                  @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        tripService.removeCollaborator(id, collaboratorId, user);
        return ResponseEntity.ok(Map.of("message", "Collaborator removed successfully"));
    }

    @PostMapping("/{id}/suggestions")
    public ResponseEntity<TripSuggestionDto> addSuggestion(@PathVariable("id") Long id,
                                                           @RequestBody Map<String, String> body,
                                                           @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        String placeName = body.get("placeName");
        String category = body.getOrDefault("category", "Sightseeing");
        String notes = body.getOrDefault("notes", "");
        String suggestedByName = body.getOrDefault("suggestedByName", user != null ? user.getName() : "Traveler");

        TripSuggestionDto dto = tripService.addSuggestion(id, placeName, category, notes, suggestedByName, user);
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{id}/suggestions/{suggestionId}/vote")
    public ResponseEntity<TripSuggestionDto> voteSuggestion(@PathVariable("id") Long id,
                                                            @PathVariable("suggestionId") Long suggestionId,
                                                            @RequestParam(value = "voterName", required = false) String voterNameParam,
                                                            @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        String voterName = voterNameParam != null ? voterNameParam : (user != null ? user.getName() : "Traveler");
        TripSuggestionDto updated = tripService.voteSuggestion(id, suggestionId, voterName, user);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/{id}/suggestions/{suggestionId}/accept")
    public ResponseEntity<TripResponseDto> acceptSuggestion(@PathVariable("id") Long id,
                                                            @PathVariable("suggestionId") Long suggestionId,
                                                            @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto updated = tripService.acceptSuggestion(id, suggestionId, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * Update Accommodation / Hotel for a specific destination later (Requirement 10)
     */
    @PostMapping("/{id}/destinations/{destinationId}/hotel")
    public ResponseEntity<TripResponseDto> updateDestinationHotel(@PathVariable("id") Long id,
                                                                 @PathVariable("destinationId") Long destinationId,
                                                                 @RequestBody Map<String, String> body,
                                                                 @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        String hotelName = body.get("hotelName");
        TripResponseDto updated = tripService.updateDestinationHotel(id, destinationId, hotelName, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * Map-based Accommodation selection & details (Requirement 4)
     */
    @RequestMapping(value = "/{id}/destinations/{destinationId}/accommodation", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<TripResponseDto> updateDestinationAccommodation(@PathVariable("id") Long id,
                                                                         @PathVariable("destinationId") Long destinationId,
                                                                         @RequestBody AccommodationStayDto stayDto,
                                                                         @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto updated = tripService.updateDestinationAccommodation(id, destinationId, stayDto, user);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}/destinations/{destinationId}/accommodation")
    public ResponseEntity<TripResponseDto> removeDestinationAccommodation(@PathVariable("id") Long id,
                                                                         @PathVariable("destinationId") Long destinationId,
                                                                         @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto updated = tripService.removeDestinationAccommodation(id, destinationId, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * Lock or Unlock Activity (Requirement 3 & 5)
     */
    @PostMapping("/{id}/itinerary-items/{itemId}/lock")
    public ResponseEntity<Map<String, Object>> toggleActivityLock(@PathVariable("id") Long id,
                                                                 @PathVariable("itemId") Long itemId,
                                                                 @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        Map<String, Object> result = tripService.toggleActivityLock(id, itemId, user);
        return ResponseEntity.ok(result);
    }

    /**
     * Edit / Replace Activity
     */
    @PutMapping("/{id}/itinerary-items/{itemId}")
    public ResponseEntity<TripResponseDto> replaceActivity(@PathVariable("id") Long id,
                                                           @PathVariable("itemId") Long itemId,
                                                           @RequestBody ItineraryItemDto itemDto,
                                                           @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto updated = tripService.replaceActivity(id, itemId, itemDto, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete Activity
     */
    @DeleteMapping("/{id}/itinerary-items/{itemId}")
    public ResponseEntity<TripResponseDto> deleteActivity(@PathVariable("id") Long id,
                                                          @PathVariable("itemId") Long itemId,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto updated = tripService.deleteItineraryItem(id, itemId, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * Add Activity to Day
     */
    @PostMapping("/{id}/days/{dayNumber}/itinerary-items")
    public ResponseEntity<TripResponseDto> addActivityToDay(@PathVariable("id") Long id,
                                                            @PathVariable("dayNumber") Integer dayNumber,
                                                            @RequestBody ItineraryItemDto itemDto,
                                                            @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto updated = tripService.addActivityToDay(id, dayNumber, itemDto, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * Add Destination to Trip
     */
    @PostMapping("/{id}/destinations")
    public ResponseEntity<TripResponseDto> addDestination(@PathVariable("id") Long id,
                                                          @RequestBody Map<String, String> body,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        String destName = body.getOrDefault("name", body.get("destinationName"));
        TripResponseDto updated = tripService.addDestinationToTrip(id, destName, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * Remove Destination from Trip
     */
    @DeleteMapping("/{id}/destinations/{destinationId}")
    public ResponseEntity<TripResponseDto> removeDestination(@PathVariable("id") Long id,
                                                             @PathVariable("destinationId") Long destinationId,
                                                             @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto updated = tripService.removeDestinationFromTrip(id, destinationId, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * Apply Route Alternative (Requirement 5)
     */
    @PostMapping("/{id}/apply-alternative")
    public ResponseEntity<TripResponseDto> applyRouteAlternative(@PathVariable("id") Long id,
                                                                 @RequestBody Map<String, Object> body,
                                                                 @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        @SuppressWarnings("unchecked")
        List<String> ordered = (List<String>) body.get("orderedDestinations");
        TripResponseDto updated = tripService.applyRouteAlternative(id, ordered, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * Discover Places Along Route within max detour km
     */
    @GetMapping("/{id}/places-along-route")
    public ResponseEntity<List<PlaceAlongRouteDto>> getPlacesAlongRoute(@PathVariable("id") Long id,
                                                                        @RequestParam(value = "maxDetourKm", required = false, defaultValue = "25.0") Double maxDetourKm,
                                                                        @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        List<PlaceAlongRouteDto> places = tripService.getPlacesAlongRoute(id, maxDetourKm, user);
        return ResponseEntity.ok(places);
    }

    /**
     * Add a discovered stop along the route directly into trip destinations
     */
    @PostMapping("/{id}/places-along-route")
    public ResponseEntity<TripResponseDto> addPlaceAlongRouteAsDestination(@PathVariable("id") Long id,
                                                                           @RequestBody PlaceAlongRouteDto placeDto,
                                                                           @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto updated = tripService.addPlaceAlongRouteAsDestination(id, placeDto, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * Generate customized packing checklist based on destinations, duration, and climate
     */
    @GetMapping("/{id}/packing-checklist")
    public ResponseEntity<PackingChecklistDto> getPackingChecklist(@PathVariable("id") Long id,
                                                                  @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        PackingChecklistDto checklist = tripService.getPackingChecklist(id, user);
        return ResponseEntity.ok(checklist);
    }

    private User resolveCurrentUser(UserDetails userDetails) {
        if (userDetails != null) {
            return userService.getUserByEmail(userDetails.getUsername());
        }
        return userService.getOrCreateDemoUser();
    }
}
