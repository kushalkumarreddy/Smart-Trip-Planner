package com.example.tripplanner.controller;

import com.example.tripplanner.dto.*;
import com.example.tripplanner.entity.User;
import com.example.tripplanner.service.AiTripPlannerService;
import com.example.tripplanner.service.RouteService;
import com.example.tripplanner.service.TripService;
import com.example.tripplanner.service.UserService;
import com.example.tripplanner.exception.BadRequestException;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * Guest Trip Generation without requiring authentication (Requirement 3)
     */
    @PostMapping(value = {"/guest-plan", "/guest"})
    public ResponseEntity<TripResponseDto> createGuestTrip(@Valid @RequestBody TripRequestDto request,
                                                           HttpSession session) {
        TripResponseDto guestTrip = tripService.generateGuestTrip(request);
        session.setAttribute("GUEST_TRIP", guestTrip);
        session.setAttribute("GUEST_REQUEST", request);
        return new ResponseEntity<>(guestTrip, HttpStatus.OK);
    }

    /**
     * Authenticated endpoint to permanently save a guest trip to the user's account
     */
    @PostMapping("/save-guest")
    public ResponseEntity<TripResponseDto> saveGuestTrip(@RequestBody(required = false) TripRequestDto request,
                                                         @AuthenticationPrincipal UserDetails userDetails,
                                                         HttpSession session) {
        User user = resolveCurrentUser(userDetails);
        TripRequestDto payload = request;
        if (payload == null) {
            payload = (TripRequestDto) session.getAttribute("GUEST_REQUEST");
        }
        if (payload == null) {
            throw new com.example.tripplanner.exception.BadRequestException("No guest trip data found to save. Please create or configure your trip first.");
        }
        TripResponseDto saved = tripService.createTrip(payload, user);
        session.removeAttribute("GUEST_TRIP");
        session.removeAttribute("GUEST_REQUEST");
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    /**
     * Guest Smart Stops Discovery
     */
    @GetMapping("/guest/places-along-route")
    public ResponseEntity<List<PlaceAlongRouteDto>> getGuestPlacesAlongRoute(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "maxDetourMinutes", required = false) Integer maxDetourMinutes,
            @RequestParam(value = "maxDetourKm", required = false) Double maxDetourKm,
            @RequestParam(value = "query", required = false) String query,
            HttpSession session) {
        TripResponseDto guestTrip = (TripResponseDto) session.getAttribute("GUEST_TRIP");
        if (guestTrip == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<PlaceAlongRouteDto> places = tripService.getGuestPlacesAlongRoute(
                guestTrip, category, maxDetourMinutes, maxDetourKm, query);
        return ResponseEntity.ok(places);
    }

    /**
     * Add a discovered stop along the route directly into guest trip destinations
     */
    @PostMapping(value = {"/guest/places-along-route", "/guest/add-place-along-route"})
    public ResponseEntity<TripResponseDto> addGuestPlaceAlongRoute(
            @RequestBody PlaceAlongRouteDto placeDto,
            HttpSession session) {
        TripResponseDto guestTrip = (TripResponseDto) session.getAttribute("GUEST_TRIP");
        if (guestTrip == null) {
            throw new com.example.tripplanner.exception.BadRequestException("No active guest trip session found.");
        }
        TripResponseDto updated = tripService.addGuestPlaceAlongRoute(guestTrip, placeDto);
        session.setAttribute("GUEST_TRIP", updated);
        return ResponseEntity.ok(updated);
    }

    /**
     * Guest Destination Reordering
     */
    @PostMapping("/guest/reorder")
    public ResponseEntity<TripResponseDto> reorderGuestDestinations(
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        TripResponseDto guestTrip = (TripResponseDto) session.getAttribute("GUEST_TRIP");
        if (guestTrip == null) {
            throw new com.example.tripplanner.exception.BadRequestException("No active guest trip session found.");
        }
        TripResponseDto updated = tripService.reorderGuestDestinations(guestTrip, body);
        session.setAttribute("GUEST_TRIP", updated);
        return ResponseEntity.ok(updated);
    }

    /**
     * Guest Day Itinerary Regeneration
     */
    @PostMapping({"/guest/days/{dayNumber}/regenerate", "/guest/regenerate-day/{dayNumber}"})
    public ResponseEntity<TripResponseDto> regenerateGuestDay(
            @PathVariable("dayNumber") Integer dayNumber,
            @RequestBody(required = false) Map<String, String> body,
            HttpSession session) {
        TripResponseDto guestTrip = (TripResponseDto) session.getAttribute("GUEST_TRIP");
        if (guestTrip == null) {
            throw new com.example.tripplanner.exception.BadRequestException("No active guest trip session found.");
        }
        String preference = (body != null) ? body.getOrDefault("preference", "BALANCED") : "BALANCED";
        TripResponseDto updated = tripService.regenerateGuestDay(guestTrip, dayNumber, preference);
        session.setAttribute("GUEST_TRIP", updated);
        return ResponseEntity.ok(updated);
    }

    /**
     * Rehydrate/Sync guest trip from browser localStorage into server session on refresh
     */
    @PostMapping("/guest/sync")
    public ResponseEntity<Map<String, String>> syncGuestSession(
            @RequestBody TripRequestDto request,
            HttpSession session) {
        if (request != null && request.getStartLocation() != null && request.getDestinationNames() != null && !request.getDestinationNames().isEmpty()) {
            TripResponseDto guestTrip = tripService.generateGuestTrip(request);
            session.setAttribute("GUEST_TRIP", guestTrip);
            session.setAttribute("GUEST_REQUEST", request);
        }
        return ResponseEntity.ok(Map.of("message", "Guest trip synchronized successfully"));
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
     * Accurately calculates outbound distance, return highway distance from final destination
     * back to origin, and combined round-trip metrics.
     */
    @GetMapping("/{id}/round-trip")
    public ResponseEntity<RoundTripDetailsDto> getTripRoundTripDetails(@PathVariable("id") Long id,
                                                                       @AuthenticationPrincipal UserDetails userDetails,
                                                                       HttpSession session) {
        TripResponseDto trip;
        if (id == 0L) {
            trip = (TripResponseDto) session.getAttribute("GUEST_TRIP");
            if (trip == null) {
                return ResponseEntity.notFound().build();
            }
        } else {
            User user = resolveCurrentUser(userDetails);
            trip = tripService.getTripDtoById(id, user);
        }

        List<String> destNames = new ArrayList<>();
        if (trip.getDestinations() != null) {
            for (DestinationDto d : trip.getDestinations()) {
                if (d.getName() != null && !d.getName().trim().isEmpty()) {
                    destNames.add(d.getName().trim());
                }
            }
        }

        RoundTripDetailsDto details = routeService.calculateRoundTripDetails(
                trip.getStartLocation(), destNames, trip.getTransportMode(), trip.getRouteSummary());
        return ResponseEntity.ok(details);
    }

    @GetMapping("/guest/round-trip")
    public ResponseEntity<RoundTripDetailsDto> guestGetTripRoundTripDetails(HttpSession session) {
        return getTripRoundTripDetails(0L, null, session);
    }

    /**
     * Preview Round-Trip Distance calculation for dynamic itinerary forms
     */
    @PostMapping("/preview-round-trip")
    public ResponseEntity<RoundTripDetailsDto> previewRoundTrip(@RequestBody Map<String, Object> body) {
        String startLocation = (String) body.get("startLocation");
        @SuppressWarnings("unchecked")
        List<String> destinations = (List<String>) body.get("destinations");
        String transportMode = (String) body.getOrDefault("transportMode", "CAR");

        RoundTripDetailsDto details = routeService.calculateRoundTripDetails(
                startLocation, destinations, transportMode, null);
        return ResponseEntity.ok(details);
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
    /**
     * Lock / Unlock Activity (supports both authenticated and guest trips)
     */
    @PostMapping("/{id}/itinerary-items/{itemId}/lock")
    public ResponseEntity<Map<String, Object>> toggleActivityLock(@PathVariable("id") Long id,
                                                                 @PathVariable("itemId") Long itemId,
                                                                 @AuthenticationPrincipal UserDetails userDetails,
                                                                 HttpSession session) {
        if (id == 0L) {
            TripResponseDto guestTrip = (TripResponseDto) session.getAttribute("GUEST_TRIP");
            if (guestTrip == null) throw new BadRequestException("No active guest trip session found.");
            Map<String, Object> result = tripService.guestToggleLock(guestTrip, itemId);
            return ResponseEntity.ok(result);
        }
        User user = resolveCurrentUser(userDetails);
        Map<String, Object> result = tripService.toggleActivityLock(id, itemId, user);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/guest/activities/{itemId}/lock")
    public ResponseEntity<Map<String, Object>> guestToggleActivityLock(@PathVariable("itemId") Long itemId,
                                                                       HttpSession session) {
        return toggleActivityLock(0L, itemId, null, session);
    }

    /**
     * Edit / Replace Activity (supports both authenticated and guest trips)
     */
    @PutMapping("/{id}/itinerary-items/{itemId}")
    public ResponseEntity<TripResponseDto> replaceActivity(@PathVariable("id") Long id,
                                                           @PathVariable("itemId") Long itemId,
                                                           @RequestBody ItineraryItemDto itemDto,
                                                           @AuthenticationPrincipal UserDetails userDetails,
                                                           HttpSession session) {
        if (id == 0L) {
            TripResponseDto guestTrip = (TripResponseDto) session.getAttribute("GUEST_TRIP");
            if (guestTrip == null) throw new BadRequestException("No active guest trip session found.");
            TripResponseDto updated = tripService.guestReplaceActivity(guestTrip, itemId, itemDto);
            session.setAttribute("GUEST_TRIP", updated);
            return ResponseEntity.ok(updated);
        }
        User user = resolveCurrentUser(userDetails);
        TripResponseDto updated = tripService.replaceActivity(id, itemId, itemDto, user);
        return ResponseEntity.ok(updated);
    }

    @PutMapping({"/guest/activities/{itemId}", "/guest/itinerary-items/{itemId}"})
    public ResponseEntity<TripResponseDto> guestReplaceActivity(@PathVariable("itemId") Long itemId,
                                                                @RequestBody ItineraryItemDto itemDto,
                                                                HttpSession session) {
        return replaceActivity(0L, itemId, itemDto, null, session);
    }

    /**
     * Delete Activity (supports both authenticated and guest trips)
     */
    @DeleteMapping("/{id}/itinerary-items/{itemId}")
    public ResponseEntity<TripResponseDto> deleteActivity(@PathVariable("id") Long id,
                                                          @PathVariable("itemId") Long itemId,
                                                          @AuthenticationPrincipal UserDetails userDetails,
                                                          HttpSession session) {
        if (id == 0L) {
            TripResponseDto guestTrip = (TripResponseDto) session.getAttribute("GUEST_TRIP");
            if (guestTrip == null) throw new BadRequestException("No active guest trip session found.");
            TripResponseDto updated = tripService.guestDeleteActivity(guestTrip, itemId);
            session.setAttribute("GUEST_TRIP", updated);
            return ResponseEntity.ok(updated);
        }
        User user = resolveCurrentUser(userDetails);
        TripResponseDto updated = tripService.deleteItineraryItem(id, itemId, user);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping({"/guest/activities/{itemId}", "/guest/itinerary-items/{itemId}"})
    public ResponseEntity<TripResponseDto> guestDeleteActivity(@PathVariable("itemId") Long itemId,
                                                               HttpSession session) {
        return deleteActivity(0L, itemId, null, session);
    }

    /**
     * Add Activity to Day (supports both authenticated and guest trips)
     */
    @PostMapping("/{id}/days/{dayNumber}/itinerary-items")
    public ResponseEntity<TripResponseDto> addActivityToDay(@PathVariable("id") Long id,
                                                            @PathVariable("dayNumber") Integer dayNumber,
                                                            @RequestBody ItineraryItemDto itemDto,
                                                            @AuthenticationPrincipal UserDetails userDetails,
                                                            HttpSession session) {
        if (id == 0L) {
            TripResponseDto guestTrip = (TripResponseDto) session.getAttribute("GUEST_TRIP");
            if (guestTrip == null) throw new BadRequestException("No active guest trip session found.");
            TripResponseDto updated = tripService.guestAddActivity(guestTrip, dayNumber, itemDto);
            session.setAttribute("GUEST_TRIP", updated);
            return ResponseEntity.ok(updated);
        }
        User user = resolveCurrentUser(userDetails);
        TripResponseDto updated = tripService.addActivityToDay(id, dayNumber, itemDto, user);
        return ResponseEntity.ok(updated);
    }

    @PostMapping({"/guest/days/{dayNumber}/activities", "/guest/days/{dayNumber}/itinerary-items"})
    public ResponseEntity<TripResponseDto> guestAddActivityToDay(@PathVariable("dayNumber") Integer dayNumber,
                                                                 @RequestBody ItineraryItemDto itemDto,
                                                                 HttpSession session) {
        return addActivityToDay(0L, dayNumber, itemDto, null, session);
    }

    /**
     * Move Activity Direction (UP / DOWN) (supports both authenticated and guest trips)
     */
    @PostMapping("/{id}/days/{dayNumber}/activities/{itemId}/move")
    public ResponseEntity<TripResponseDto> moveActivityDirection(@PathVariable("id") Long id,
                                                                 @PathVariable("dayNumber") Integer dayNumber,
                                                                 @PathVariable("itemId") Long itemId,
                                                                 @RequestParam(value = "direction", defaultValue = "UP") String direction,
                                                                 @AuthenticationPrincipal UserDetails userDetails,
                                                                 HttpSession session) {
        String effectiveDirection = (direction != null && !direction.trim().isEmpty()) ? direction.trim().toUpperCase() : "UP";
        if (id == 0L) {
            TripResponseDto guestTrip = (TripResponseDto) session.getAttribute("GUEST_TRIP");
            if (guestTrip == null) throw new BadRequestException("No active guest trip session found.");
            TripResponseDto updated = tripService.guestMoveActivityDirection(guestTrip, dayNumber, itemId, effectiveDirection);
            session.setAttribute("GUEST_TRIP", updated);
            return ResponseEntity.ok(updated);
        }
        User user = resolveCurrentUser(userDetails);
        TripResponseDto updated = tripService.moveActivityDirection(id, dayNumber, itemId, effectiveDirection, user);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/guest/days/{dayNumber}/activities/{itemId}/move")
    public ResponseEntity<TripResponseDto> guestMoveActivityDirection(@PathVariable("dayNumber") Integer dayNumber,
                                                                      @PathVariable("itemId") Long itemId,
                                                                      @RequestParam(value = "direction", defaultValue = "UP") String direction,
                                                                      HttpSession session) {
        return moveActivityDirection(0L, dayNumber, itemId, direction, null, session);
    }

    /**
     * Reorder Day Activities (supports both authenticated and guest trips)
     */
    @PostMapping("/{id}/days/{dayNumber}/reorder-activities")
    public ResponseEntity<TripResponseDto> reorderDayActivities(@PathVariable("id") Long id,
                                                                @PathVariable("dayNumber") Integer dayNumber,
                                                                @RequestBody(required = false) Object body,
                                                                @AuthenticationPrincipal UserDetails userDetails,
                                                                HttpSession session) {
        List<Long> orderedItemIds = new ArrayList<>();
        if (body instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Number n) orderedItemIds.add(n.longValue());
                else if (o != null) orderedItemIds.add(Long.valueOf(o.toString()));
            }
        } else if (body instanceof Map<?, ?> map && map.containsKey("orderedItemIds")) {
            Object val = map.get("orderedItemIds");
            if (val instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Number n) orderedItemIds.add(n.longValue());
                    else if (o != null) orderedItemIds.add(Long.valueOf(o.toString()));
                }
            }
        }

        if (id == 0L) {
            TripResponseDto guestTrip = (TripResponseDto) session.getAttribute("GUEST_TRIP");
            if (guestTrip == null) throw new BadRequestException("No active guest trip session found.");
            TripResponseDto updated = tripService.guestReorderActivities(guestTrip, dayNumber, orderedItemIds);
            session.setAttribute("GUEST_TRIP", updated);
            return ResponseEntity.ok(updated);
        }
        User user = resolveCurrentUser(userDetails);
        TripResponseDto updated = tripService.reorderDayActivities(id, dayNumber, orderedItemIds, user);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/guest/days/{dayNumber}/reorder-activities")
    public ResponseEntity<TripResponseDto> guestReorderDayActivities(@PathVariable("dayNumber") Integer dayNumber,
                                                                     @RequestBody(required = false) Object body,
                                                                     HttpSession session) {
        return reorderDayActivities(0L, dayNumber, body, null, session);
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
     * Discover Places Along Route within corridor and optional detour limits
     */
    @GetMapping("/{id}/places-along-route")
    public ResponseEntity<List<PlaceAlongRouteDto>> getPlacesAlongRoute(@PathVariable("id") Long id,
                                                                        @RequestParam(value = "category", required = false) String category,
                                                                        @RequestParam(value = "maxDetourMinutes", required = false) Integer maxDetourMinutes,
                                                                        @RequestParam(value = "maxDetourKm", required = false) Double maxDetourKm,
                                                                        @RequestParam(value = "query", required = false) String query,
                                                                        @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        List<PlaceAlongRouteDto> places = tripService.getPlacesAlongRoute(id, category, maxDetourMinutes, maxDetourKm, query, user);
        return ResponseEntity.ok(places);
    }

    /**
     * Add a discovered stop along the route directly into trip destinations
     */
    @PostMapping(value = {"/{id}/places-along-route", "/{id}/add-place-along-route"})
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

    /**
     * Update destination stay duration and optionally recalculate affected schedule
     */
    @RequestMapping(value = "/{id}/destinations/{destinationId}/duration", method = {RequestMethod.PUT, RequestMethod.POST})
    public ResponseEntity<TripResponseDto> updateDestinationStayDuration(@PathVariable("id") Long id,
                                                                         @PathVariable("destinationId") Long destinationId,
                                                                         @RequestBody StayDurationUpdateDto updateDto,
                                                                         @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto updated = tripService.updateDestinationStayDuration(id, destinationId, updateDto, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * Move activity between days (Smart Day Planner manual adjustment)
     */
    @PostMapping("/{id}/move-activity")
    public ResponseEntity<TripResponseDto> moveActivityToDay(@PathVariable("id") Long id,
                                                             @Valid @RequestBody MoveActivityDto moveDto,
                                                             @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto updated = tripService.moveActivityToDay(id, moveDto, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * Dedicated Trip Preparation Checklist - Get all items grouped by category with summary counts
     */
    @GetMapping("/{id}/checklist")
    public ResponseEntity<TripChecklistResponseDto> getTripChecklist(@PathVariable("id") Long id,
                                                                    @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripChecklistResponseDto checklist = tripService.getTripChecklist(id, user);
        return ResponseEntity.ok(checklist);
    }

    /**
     * Add a custom checklist item to trip
     */
    @PostMapping("/{id}/checklist/items")
    public ResponseEntity<TripChecklistResponseDto> addChecklistItem(@PathVariable("id") Long id,
                                                                     @Valid @RequestBody ChecklistItemRequestDto request,
                                                                     @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripChecklistResponseDto updated = tripService.addChecklistItem(id, request, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * Edit a checklist item
     */
    @PutMapping("/{id}/checklist/items/{itemId}")
    public ResponseEntity<TripChecklistResponseDto> updateChecklistItem(@PathVariable("id") Long id,
                                                                        @PathVariable("itemId") Long itemId,
                                                                        @Valid @RequestBody ChecklistItemRequestDto request,
                                                                        @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripChecklistResponseDto updated = tripService.updateChecklistItem(id, itemId, request, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * Toggle checklist item completion status
     */
    @RequestMapping(value = "/{id}/checklist/items/{itemId}/toggle", method = {RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<TripChecklistResponseDto> toggleChecklistItem(@PathVariable("id") Long id,
                                                                        @PathVariable("itemId") Long itemId,
                                                                        @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripChecklistResponseDto updated = tripService.toggleChecklistItem(id, itemId, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete a checklist item
     */
    @DeleteMapping("/{id}/checklist/items/{itemId}")
    public ResponseEntity<TripChecklistResponseDto> deleteChecklistItem(@PathVariable("id") Long id,
                                                                        @PathVariable("itemId") Long itemId,
                                                                        @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripChecklistResponseDto updated = tripService.deleteChecklistItem(id, itemId, user);
        return ResponseEntity.ok(updated);
    }

    /**
     * Reset trip checklist (reset all to unchecked, or reseed defaults)
     */
    @PostMapping("/{id}/checklist/reset")
    public ResponseEntity<TripChecklistResponseDto> resetTripChecklist(@PathVariable("id") Long id,
                                                                       @RequestBody(required = false) Map<String, Boolean> body,
                                                                       @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        boolean reseed = (body != null && Boolean.TRUE.equals(body.get("reseedDefaults")));
        TripChecklistResponseDto updated = tripService.resetTripChecklist(id, reseed, user);
        return ResponseEntity.ok(updated);
    }

    // ==========================================
    // Trip Version History Endpoints
    // ==========================================

    /**
     * Get all version snapshots of a trip (newest first)
     */
    @GetMapping("/{id}/versions")
    public ResponseEntity<List<TripVersionSummaryDto>> getTripVersions(@PathVariable("id") Long id,
                                                                       @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        List<TripVersionSummaryDto> versions = tripService.getTripVersions(id, user);
        return ResponseEntity.ok(versions);
    }

    /**
     * Get detailed snapshot of a specific version
     */
    @GetMapping("/{id}/versions/{versionId}")
    public ResponseEntity<TripVersionDetailDto> getVersionDetail(@PathVariable("id") Long id,
                                                                 @PathVariable("versionId") Long versionId,
                                                                 @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripVersionDetailDto detail = tripService.getVersionDetail(id, versionId, user);
        return ResponseEntity.ok(detail);
    }

    /**
     * Create a manual version snapshot with an optional custom label
     */
    @PostMapping("/{id}/versions")
    public ResponseEntity<TripVersionSummaryDto> createManualVersion(@PathVariable("id") Long id,
                                                                     @RequestBody(required = false) Map<String, String> body,
                                                                     @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        String label = (body != null) ? body.get("label") : null;
        var version = tripService.createManualVersion(id, label, user);
        TripVersionSummaryDto dto = new TripVersionSummaryDto();
        if (version != null) {
            dto.setId(version.getId());
            dto.setTripId(id);
            dto.setVersionNumber(version.getVersionNumber());
            dto.setChangeDescription(version.getChangeDescription());
            dto.setChangeSource(version.getChangeSource());
            dto.setCreatedByName(version.getCreatedByName());
            dto.setCreatedAt(version.getCreatedAt());
        }
        return new ResponseEntity<>(dto, HttpStatus.CREATED);
    }

    /**
     * Compare two saved versions of a trip
     */
    @GetMapping("/{id}/versions/compare")
    public ResponseEntity<TripVersionComparisonDto> compareVersions(@PathVariable("id") Long id,
                                                                    @RequestParam("v1") Long v1,
                                                                    @RequestParam("v2") Long v2,
                                                                    @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripVersionComparisonDto comparison = tripService.compareVersions(id, v1, v2, user);
        return ResponseEntity.ok(comparison);
    }

    /**
     * Safely restore an older trip version
     */
    @PostMapping("/{id}/versions/{versionId}/restore")
    public ResponseEntity<TripResponseDto> restoreVersion(@PathVariable("id") Long id,
                                                          @PathVariable("versionId") Long versionId,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto restored = tripService.restoreVersion(id, versionId, user);
        return ResponseEntity.ok(restored);
    }

    private User resolveCurrentUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required to access trip data.");
        }
        return userService.getUserByEmail(userDetails.getUsername());
    }
}

