package com.example.tripplanner.service;

import com.example.tripplanner.dto.*;
import com.example.tripplanner.entity.*;
import com.example.tripplanner.exception.BadRequestException;
import com.example.tripplanner.exception.ResourceNotFoundException;
import com.example.tripplanner.repository.*;
import com.example.tripplanner.util.TimeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final DestinationRepository destinationRepository;
    private final PlaceRepository placeRepository;
    private final ItineraryItemRepository itineraryItemRepository;
    private final RouteService routeService;
    private final ItineraryService itineraryService;
    private final RecommendationService recommendationService;
    private final GoogleMapsService googleMapsService;
    private final TripCollaboratorRepository tripCollaboratorRepository;
    private final TripSuggestionRepository tripSuggestionRepository;

    public TripService(TripRepository tripRepository,
                       DestinationRepository destinationRepository,
                       PlaceRepository placeRepository,
                       ItineraryItemRepository itineraryItemRepository,
                       RouteService routeService,
                       ItineraryService itineraryService,
                       RecommendationService recommendationService,
                       GoogleMapsService googleMapsService,
                       TripCollaboratorRepository tripCollaboratorRepository,
                       TripSuggestionRepository tripSuggestionRepository) {
        this.tripRepository = tripRepository;
        this.destinationRepository = destinationRepository;
        this.placeRepository = placeRepository;
        this.itineraryItemRepository = itineraryItemRepository;
        this.routeService = routeService;
        this.itineraryService = itineraryService;
        this.recommendationService = recommendationService;
        this.googleMapsService = googleMapsService;
        this.tripCollaboratorRepository = tripCollaboratorRepository;
        this.tripSuggestionRepository = tripSuggestionRepository;
    }

    @Transactional
    public TripResponseDto createTrip(TripRequestDto request, User user) {
        // Validation
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BadRequestException("Trip start date cannot be after end date.");
        }
        if (request.getDestinationNames() == null || request.getDestinationNames().isEmpty()) {
            throw new BadRequestException("At least one destination is required.");
        }

        // Clean destinations (remove duplicates preserving order)
        List<String> cleanedDestinations = new ArrayList<>();
        List<String> cleanedHotels = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < request.getDestinationNames().size(); i++) {
            String d = request.getDestinationNames().get(i);
            if (d != null && !d.trim().isEmpty()) {
                String normalized = d.trim().toLowerCase();
                if (!seen.contains(normalized)) {
                    seen.add(normalized);
                    cleanedDestinations.add(d.trim());
                    String hotel = (request.getDestinationHotels() != null && i < request.getDestinationHotels().size())
                            ? request.getDestinationHotels().get(i)
                            : null;
                    cleanedHotels.add(hotel);
                }
            }
        }

        if (cleanedDestinations.isEmpty()) {
            throw new BadRequestException("At least one valid destination is required.");
        }

        // Create Trip Entity
        Trip trip = new Trip();
        trip.setUser(user);

        String autoName = (request.getTripName() != null && !request.getTripName().trim().isEmpty())
                ? request.getTripName().trim()
                : String.format("%s to %s Trip", request.getStartLocation(), cleanedDestinations.get(cleanedDestinations.size() - 1));
        trip.setTripName(autoName);

        trip.setStartLocation(request.getStartLocation().trim());
        double[] startCoords = googleMapsService.geocode(request.getStartLocation());
        trip.setStartLat(startCoords[0]);
        trip.setStartLng(startCoords[1]);

        trip.setStartDate(request.getStartDate());
        trip.setEndDate(request.getEndDate());
        trip.setTransportMode(request.getTransportMode() != null ? request.getTransportMode() : "CAR");
        trip.setTravelStyle(request.getTravelStyle() != null ? request.getTravelStyle() : "BALANCED");
        trip.setTravelersCount(request.getTravelersCount() != null ? request.getTravelersCount() : 1);
        trip.setDailyStartTime(request.getDailyStartTime() != null ? request.getDailyStartTime() : "08:00");
        trip.setDailyEndTime(request.getDailyEndTime() != null ? request.getDailyEndTime() : "20:00");
        trip.setHotelLocation(request.getHotelLocation());
        if (request.getHotelLocation() != null && !request.getHotelLocation().trim().isEmpty()) {
            double[] hotelCoords = googleMapsService.geocode(request.getHotelLocation());
            trip.setHotelLat(hotelCoords[0]);
            trip.setHotelLng(hotelCoords[1]);
        }
        trip.setBudgetRange(request.getBudgetRange());
        if (request.getPreferredInterests() != null && !request.getPreferredInterests().isEmpty()) {
            trip.setPreferredInterests(String.join(",", request.getPreferredInterests()));
        }

        trip.setShareCode(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        Trip savedTrip = tripRepository.save(trip);

        // Add Trip Owner as first collaborator
        TripCollaborator ownerCollab = new TripCollaborator(savedTrip, user.getName(), user.getEmail(), "OWNER");
        tripCollaboratorRepository.save(ownerCollab);

        // Calculate Route Matrix
        RouteSummaryDto routeSummary = routeService.calculateCompleteRoute(
                savedTrip.getStartLocation(),
                cleanedDestinations,
                savedTrip.getTransportMode()
        );

        savedTrip.setTotalDistance(routeSummary.getTotalDistanceKm());
        savedTrip.setTotalTravelTimeMinutes(routeSummary.getTotalTravelTimeMinutes());

        // Create Destination Entities & Seed Initial Top Places
        List<Destination> destinations = new ArrayList<>();
        Set<String> tripSeededPlaces = new HashSet<>();
        for (int i = 0; i < cleanedDestinations.size(); i++) {
            String destName = cleanedDestinations.get(i);
            Destination destination = new Destination(destName, i);
            destination.setTrip(savedTrip);

            // Set destination-specific hotel (Requirement 10)
            if (i < cleanedHotels.size() && cleanedHotels.get(i) != null && !cleanedHotels.get(i).trim().isEmpty()) {
                destination.setHotelName(cleanedHotels.get(i).trim());
            }

            double[] coords = googleMapsService.geocode(destName);
            destination.setLatitude(coords[0]);
            destination.setLongitude(coords[1]);

            if (i < routeSummary.getSegments().size()) {
                RouteSegmentDto seg = routeSummary.getSegments().get(i);
                destination.setDistanceFromPrevious(seg.getDistanceKm());
                destination.setTravelTimeFromPrevious(seg.getDurationMinutes());
            }

            Destination savedDest = destinationRepository.save(destination);

            // Seed top sightseeing recommendations into places table ensuring no duplicates across stops
            List<SightseeingPlaceDto> recommended = recommendationService.getRecommendedPlaces(
                    destName,
                    request.getPreferredInterests(),
                    "popularity",
                    null
            );

            for (SightseeingPlaceDto rec : recommended) {
                String recKey = ItineraryService.normalizePlaceKey(rec.getName());
                if (!recKey.isEmpty() && tripSeededPlaces.contains(recKey)) {
                    continue; // Skip duplicate attraction already seeded in earlier stop of this trip
                }
                if (!recKey.isEmpty()) {
                    tripSeededPlaces.add(recKey);
                }

                Place place = new Place();
                place.setDestination(savedDest);
                place.setName(rec.getName());
                place.setCategory(rec.getCategory());
                place.setRating(rec.getRating());
                place.setReviewCount(rec.getReviewCount());
                place.setLatitude(rec.getLatitude());
                place.setLongitude(rec.getLongitude());
                place.setDistanceKm(rec.getDistanceKm());
                place.setEstimatedVisitDurationMinutes(rec.getEstimatedVisitDurationMinutes());
                place.setEstimatedTravelTimeMinutes(rec.getEstimatedTravelTimeMinutes());
                place.setDescription(rec.getDescription());
                place.setOpeningHours(rec.getOpeningHours());
                place.setPriority(rec.getPriority());
                place.setVisited(false);
                place.setAddedToItinerary(true);
                placeRepository.save(place);
                savedDest.getPlaces().add(place);
            }

            destinations.add(savedDest);
        }

        savedTrip.setDestinations(destinations);

        // Generate Day-by-Day Itinerary Plan
        List<DayItineraryDto> dayItineraries = itineraryService.generateItineraryPlan(
                savedTrip,
                destinations,
                savedTrip.getTransportMode()
        );

        // Persist Itinerary Items to Database
        for (DayItineraryDto day : dayItineraries) {
            for (ItineraryItemDto itemDto : day.getItems()) {
                ItineraryItem item = new ItineraryItem();
                item.setTrip(savedTrip);
                item.setDayNumber(day.getDayNumber());
                item.setScheduleDate(day.getDate());
                item.setStartTime(itemDto.getStartTime());
                item.setEndTime(itemDto.getEndTime());
                item.setActivity(itemDto.getActivity());
                item.setLocation(itemDto.getLocation());
                item.setActivityType(itemDto.getActivityType());
                item.setTravelDistanceKm(itemDto.getTravelDistanceKm());
                item.setTravelTimeMinutes(itemDto.getTravelTimeMinutes());
                item.setDurationMinutes(itemDto.getDurationMinutes());
                item.setRating(itemDto.getRating());
                item.setVisited(itemDto.getVisited());
                item.setNotes(itemDto.getNotes());
                itineraryItemRepository.save(item);
                savedTrip.getItineraries().add(item);
            }
        }

        tripRepository.save(savedTrip);

        return buildTripResponseDto(savedTrip, routeSummary, dayItineraries);
    }

    public void checkTripAccess(Trip trip, User user, String requiredRole) {
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required to access trip.");
        }
        // Trip owner has full access
        if (trip.getUser() != null && trip.getUser().getId().equals(user.getId())) {
            return;
        }

        // Demo user compatibility if created by demo user
        if (trip.getUser() != null && "traveler@example.com".equalsIgnoreCase(trip.getUser().getEmail())
                && "traveler@example.com".equalsIgnoreCase(user.getEmail())) {
            return;
        }

        Optional<TripCollaborator> collab = tripCollaboratorRepository.findByTripAndUserEmail(trip, user.getEmail());
        if (collab.isEmpty()) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to access or modify this trip.");
        }

        String userRole = collab.get().getRole();
        if ("OWNER".equalsIgnoreCase(requiredRole)) {
            if (!"OWNER".equalsIgnoreCase(userRole)) {
                throw new org.springframework.security.access.AccessDeniedException("Only the trip owner can perform this action.");
            }
        } else if ("EDITOR".equalsIgnoreCase(requiredRole)) {
            if (!"OWNER".equalsIgnoreCase(userRole) && !"EDITOR".equalsIgnoreCase(userRole)) {
                throw new org.springframework.security.access.AccessDeniedException("Only editors or owners can modify this trip.");
            }
        }
    }

    @Transactional(readOnly = true)
    public TripResponseDto getTripDtoById(Long tripId, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with ID: " + tripId));
        checkTripAccess(trip, user, "VIEWER");

        List<Destination> destinations = destinationRepository.findByTripOrderBySequenceOrderAsc(trip);

        List<String> destNames = destinations.stream().map(Destination::getName).collect(Collectors.toList());
        RouteSummaryDto routeSummary = routeService.calculateCompleteRoute(trip.getStartLocation(), destNames, trip.getTransportMode());

        List<ItineraryItem> persistedItems = itineraryItemRepository.findByTripIdOrderByDayNumberAscStartTimeAsc(trip.getId());
        List<DayItineraryDto> dayItineraries;
        if (!persistedItems.isEmpty()) {
            dayItineraries = buildDayItinerariesFromEntities(persistedItems, trip, destinations);
        } else {
            dayItineraries = itineraryService.generateItineraryPlan(trip, destinations, trip.getTransportMode());
        }

        return buildTripResponseDto(trip, routeSummary, dayItineraries, destinations);
    }

    @Transactional(readOnly = true)
    public List<TripResponseDto> getUserTrips(User user) {
        List<Trip> trips = tripRepository.findByUserOrderByCreatedAtDesc(user);
        List<TripResponseDto> results = new ArrayList<>();
        for (Trip t : trips) {
            TripResponseDto dto = new TripResponseDto();
            dto.setId(t.getId());
            dto.setTripName(t.getTripName());
            dto.setStartLocation(t.getStartLocation());
            dto.setStartDate(t.getStartDate());
            dto.setEndDate(t.getEndDate());
            long days = Math.max(1, ChronoUnit.DAYS.between(t.getStartDate(), t.getEndDate()) + 1);
            dto.setTotalDays((int) days);
            dto.setTransportMode(t.getTransportMode());
            dto.setTotalDistanceKm(t.getTotalDistance());
            dto.setTotalTravelTimeMinutes(t.getTotalTravelTimeMinutes());
            dto.setFormattedTotalTravelTime(TimeUtils.formatDuration(t.getTotalTravelTimeMinutes()));
            dto.setShareCode(t.getShareCode());

            List<Destination> dests = destinationRepository.findByTripOrderBySequenceOrderAsc(t);
            dto.setDestinationsCount(dests.size());

            int totalSpots = 0;
            int visitedSpots = 0;
            for (Destination d : dests) {
                if (Boolean.TRUE.equals(d.getVisited())) visitedSpots++;
                for (Place p : d.getPlaces()) {
                    totalSpots++;
                    if (Boolean.TRUE.equals(p.getVisited())) visitedSpots++;
                }
            }

            dto.setSightseeingPlacesCount(totalSpots);
            dto.setVisitedPlacesCount(visitedSpots);
            dto.setCompletionPercentage(totalSpots > 0 ? (int) Math.round(((double) visitedSpots / totalSpots) * 100.0) : 0);

            results.add(dto);
        }
        return results;
    }

    @Transactional
    public void deleteTrip(Long tripId, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "OWNER");
        tripCollaboratorRepository.deleteByTripId(trip.getId());
        tripSuggestionRepository.deleteByTripId(trip.getId());
        itineraryItemRepository.deleteByTripId(trip.getId());
        tripRepository.delete(trip);
    }

    @Transactional
    public TripResponseDto applyOptimizedRoute(Long tripId, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with ID: " + tripId));
        checkTripAccess(trip, user, "EDITOR");

        List<Destination> destinations = destinationRepository.findByTripOrderBySequenceOrderAsc(trip);
        List<String> destNames = destinations.stream().map(Destination::getName).collect(Collectors.toList());

        RouteSummaryDto routeSummary = routeService.calculateCompleteRoute(trip.getStartLocation(), destNames, trip.getTransportMode());

        if (routeSummary.getRecommendedOrder() != null && routeSummary.getRecommendedOrder().size() > 1) {
            // Reorder destinations to match recommended order
            List<String> newOrder = routeSummary.getRecommendedOrder().subList(1, routeSummary.getRecommendedOrder().size());
            Map<String, Destination> destMap = destinations.stream()
                    .collect(Collectors.toMap(d -> d.getName().trim().toLowerCase(), d -> d, (a, b) -> a));

            RouteSummaryDto newRouteSummary = routeService.calculateCompleteRoute(trip.getStartLocation(), newOrder, trip.getTransportMode());

            List<Destination> reordered = new ArrayList<>();
            for (int i = 0; i < newOrder.size(); i++) {
                String name = newOrder.get(i).trim().toLowerCase();
                Destination d = destMap.get(name);
                if (d != null) {
                    d.setSequenceOrder(i);
                    if (i < newRouteSummary.getSegments().size()) {
                        RouteSegmentDto seg = newRouteSummary.getSegments().get(i);
                        d.setDistanceFromPrevious(seg.getDistanceKm());
                        d.setTravelTimeFromPrevious(seg.getDurationMinutes());
                    }
                    reordered.add(destinationRepository.save(d));
                }
            }

            trip.setTotalDistance(newRouteSummary.getTotalDistanceKm());
            trip.setTotalTravelTimeMinutes(newRouteSummary.getTotalTravelTimeMinutes());

            // Clear old itinerary items and regenerate
            itineraryItemRepository.deleteByTripId(trip.getId());
            List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, reordered, trip.getTransportMode());
            for (DayItineraryDto day : days) {
                for (ItineraryItemDto itemDto : day.getItems()) {
                    ItineraryItem item = new ItineraryItem();
                    item.setTrip(trip);
                    item.setDayNumber(day.getDayNumber());
                    item.setScheduleDate(day.getDate());
                    item.setStartTime(itemDto.getStartTime());
                    item.setEndTime(itemDto.getEndTime());
                    item.setActivity(itemDto.getActivity());
                    item.setLocation(itemDto.getLocation());
                    item.setActivityType(itemDto.getActivityType());
                    item.setTravelDistanceKm(itemDto.getTravelDistanceKm());
                    item.setTravelTimeMinutes(itemDto.getTravelTimeMinutes());
                    item.setDurationMinutes(itemDto.getDurationMinutes());
                    item.setRating(itemDto.getRating());
                    item.setVisited(itemDto.getVisited());
                    item.setNotes(itemDto.getNotes());
                    itineraryItemRepository.save(item);
                }
            }

            tripRepository.save(trip);
            return buildTripResponseDto(trip, newRouteSummary, days, reordered);
        }

        return getTripDtoById(tripId, user);
    }

    /**
     * Reorders destinations by list of destination IDs (Requirement 8).
     */
    @Transactional
    public TripResponseDto reorderDestinations(Long tripId, List<Long> orderedIds, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "EDITOR");

        List<Destination> currentDests = destinationRepository.findByTripOrderBySequenceOrderAsc(trip);
        Map<Long, Destination> idMap = currentDests.stream().collect(Collectors.toMap(Destination::getId, d -> d));

        List<Destination> reordered = new ArrayList<>();
        for (Long id : orderedIds) {
            Destination d = idMap.get(id);
            if (d != null) {
                reordered.add(d);
            }
        }

        List<String> names = reordered.stream().map(Destination::getName).collect(Collectors.toList());
        RouteSummaryDto routeSummary = routeService.calculateCompleteRoute(trip.getStartLocation(), names, trip.getTransportMode());

        for (int i = 0; i < reordered.size(); i++) {
            Destination d = reordered.get(i);
            d.setSequenceOrder(i);
            if (i < routeSummary.getSegments().size()) {
                RouteSegmentDto seg = routeSummary.getSegments().get(i);
                d.setDistanceFromPrevious(seg.getDistanceKm());
                d.setTravelTimeFromPrevious(seg.getDurationMinutes());
            }
            destinationRepository.save(d);
        }

        trip.setTotalDistance(routeSummary.getTotalDistanceKm());
        trip.setTotalTravelTimeMinutes(routeSummary.getTotalTravelTimeMinutes());

        itineraryItemRepository.deleteByTripId(trip.getId());
        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, reordered, trip.getTransportMode());
        for (DayItineraryDto day : days) {
            for (ItineraryItemDto itemDto : day.getItems()) {
                ItineraryItem item = new ItineraryItem();
                item.setTrip(trip);
                item.setDayNumber(day.getDayNumber());
                item.setScheduleDate(day.getDate());
                item.setStartTime(itemDto.getStartTime());
                item.setEndTime(itemDto.getEndTime());
                item.setActivity(itemDto.getActivity());
                item.setLocation(itemDto.getLocation());
                item.setActivityType(itemDto.getActivityType());
                item.setTravelDistanceKm(itemDto.getTravelDistanceKm());
                item.setTravelTimeMinutes(itemDto.getTravelTimeMinutes());
                item.setDurationMinutes(itemDto.getDurationMinutes());
                item.setRating(itemDto.getRating());
                item.setVisited(itemDto.getVisited());
                item.setNotes(itemDto.getNotes());
                itineraryItemRepository.save(item);
            }
        }

        tripRepository.save(trip);
        return buildTripResponseDto(trip, routeSummary, days, reordered);
    }

    /**
     * Regenerates a single day's schedule without rebuilding the entire trip, preserving locked items and stays (Requirement 4 & 5).
     */
    @Transactional
    public TripResponseDto regenerateSingleDay(Long tripId, Integer dayNumber, String preference, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "EDITOR");

        List<Destination> destinations = destinationRepository.findByTripOrderBySequenceOrderAsc(trip);

        // Fetch existing items for this day
        List<ItineraryItem> currentDayItems = itineraryItemRepository.findByTripIdAndDayNumberOrderByStartTimeAsc(trip.getId(), dayNumber);

        // Identify locked items and stays that must be preserved
        List<ItineraryItem> lockedItems = currentDayItems.stream()
                .filter(it -> Boolean.TRUE.equals(it.getLocked()) || "HOTEL".equalsIgnoreCase(it.getActivityType()))
                .collect(Collectors.toList());

        // Collect places scheduled in other days + locked locations on this day to avoid duplicate recommendations
        List<ItineraryItem> allItems = itineraryItemRepository.findByTripIdOrderByDayNumberAscStartTimeAsc(trip.getId());
        Set<String> alreadyUsed = new HashSet<>();
        for (ItineraryItem it : allItems) {
            if (!it.getDayNumber().equals(dayNumber)) {
                if (it.getActivity() != null) alreadyUsed.add(it.getActivity());
                if (it.getLocation() != null) alreadyUsed.add(it.getLocation());
            }
        }
        for (ItineraryItem locked : lockedItems) {
            if (locked.getActivity() != null) alreadyUsed.add(locked.getActivity());
            if (locked.getLocation() != null) alreadyUsed.add(locked.getLocation());
        }

        // Generate refreshed candidate day
        DayItineraryDto refreshedDay = itineraryService.regenerateSingleDay(
                trip, destinations, dayNumber, preference, trip.getTransportMode(), alreadyUsed
        );

        // Delete ONLY unlocked non-hotel items for this day
        List<ItineraryItem> toDelete = currentDayItems.stream()
                .filter(it -> !Boolean.TRUE.equals(it.getLocked()) && !"HOTEL".equalsIgnoreCase(it.getActivityType()))
                .collect(Collectors.toList());
        itineraryItemRepository.deleteAll(toDelete);

        boolean hasHotelPreserved = lockedItems.stream().anyMatch(it -> "HOTEL".equalsIgnoreCase(it.getActivityType()));

        // Save new items (skipping redundant HOTEL if already preserved)
        for (ItineraryItemDto itemDto : refreshedDay.getItems()) {
            if ("HOTEL".equalsIgnoreCase(itemDto.getActivityType()) && hasHotelPreserved) {
                continue;
            }
            ItineraryItem item = new ItineraryItem();
            item.setTrip(trip);
            item.setDayNumber(dayNumber);
            item.setScheduleDate(refreshedDay.getDate());
            item.setStartTime(itemDto.getStartTime());
            item.setEndTime(itemDto.getEndTime());
            item.setActivity(itemDto.getActivity());
            item.setLocation(itemDto.getLocation());
            item.setActivityType(itemDto.getActivityType());
            item.setTravelDistanceKm(itemDto.getTravelDistanceKm());
            item.setTravelTimeMinutes(itemDto.getTravelTimeMinutes());
            item.setDurationMinutes(itemDto.getDurationMinutes());
            item.setRating(itemDto.getRating());
            item.setVisited(false);
            item.setLocked(false);
            item.setNotes(itemDto.getNotes());
            itineraryItemRepository.save(item);
        }

        return getTripDtoById(tripId, user);
    }

    // Collaboration Methods
    @Transactional
    public TripCollaboratorDto addCollaborator(Long tripId, String name, String email, String role, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "OWNER");
        TripCollaborator collaborator = new TripCollaborator(trip, name, email, role != null ? role : "EDITOR");
        TripCollaborator saved = tripCollaboratorRepository.save(collaborator);
        return new TripCollaboratorDto(saved.getId(), saved.getUserName(), saved.getUserEmail(), saved.getRole(), saved.getJoinedAt());
    }

    @Transactional
    public void removeCollaborator(Long tripId, Long collaboratorId, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "OWNER");
        TripCollaborator c = tripCollaboratorRepository.findById(collaboratorId)
                .orElseThrow(() -> new ResourceNotFoundException("Collaborator not found"));
        tripCollaboratorRepository.delete(c);
    }

    @Transactional
    public TripSuggestionDto addSuggestion(Long tripId, String placeName, String category, String notes, String suggestedByName, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "VIEWER");

        TripSuggestion s = new TripSuggestion();
        s.setTrip(trip);
        s.setPlaceName(placeName);
        s.setCategory(category != null ? category : "Sightseeing");
        s.setNotes(notes);
        s.setSuggestedByName((suggestedByName != null && !suggestedByName.trim().isEmpty()) ? suggestedByName : (user != null ? user.getName() : "Traveler"));
        s.setVotesCount(1);
        s.setVoterNames(s.getSuggestedByName());
        s.setStatus("PENDING");

        TripSuggestion saved = tripSuggestionRepository.save(s);
        return mapToSuggestionDto(saved);
    }

    @Transactional
    public TripSuggestionDto voteSuggestion(Long tripId, Long suggestionId, String voterName, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "VIEWER");

        TripSuggestion s = tripSuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new ResourceNotFoundException("Suggestion not found"));

        String voter = (voterName != null && !voterName.trim().isEmpty()) ? voterName.trim() : (user != null ? user.getName() : "Traveler");
        String currentVoters = s.getVoterNames() != null ? s.getVoterNames() : "";

        if (currentVoters.contains(voter)) {
            // Already voted, remove vote
            int votes = Math.max(1, s.getVotesCount() - 1);
            s.setVotesCount(votes);
            s.setVoterNames(currentVoters.replace(voter, "").replaceAll(",,+", ",").trim());
        } else {
            // Add vote
            s.setVotesCount(s.getVotesCount() + 1);
            s.setVoterNames(currentVoters.isEmpty() ? voter : currentVoters + ", " + voter);
        }

        TripSuggestion saved = tripSuggestionRepository.save(s);
        return mapToSuggestionDto(saved);
    }

    @Transactional
    public TripResponseDto acceptSuggestion(Long tripId, Long suggestionId, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "EDITOR");

        TripSuggestion s = tripSuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new ResourceNotFoundException("Suggestion not found"));

        s.setStatus("ACCEPTED");
        tripSuggestionRepository.save(s);

        // Add as a place to the first destination or matching destination
        List<Destination> destinations = destinationRepository.findByTripOrderBySequenceOrderAsc(trip);
        if (!destinations.isEmpty()) {
            Destination dest = destinations.get(0);
            SightseeingPlaceDto pDto = new SightseeingPlaceDto();
            pDto.setName(s.getPlaceName());
            pDto.setCategory(s.getCategory());
            pDto.setDescription(s.getNotes());
            pDto.setRating(4.6);
            pDto.setReviewCount(500);
            pDto.setDistanceKm(3.0);
            pDto.setEstimatedVisitDurationMinutes(90);
            pDto.setEstimatedTravelTimeMinutes(15);
            addSightseeingPlaceToTrip(tripId, dest.getId(), pDto, user);
        }

        return getTripDtoById(tripId, user);
    }

    @Transactional
    public TripResponseDto addSightseeingPlaceToTrip(Long tripId, Long destinationId, SightseeingPlaceDto placeDto, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "EDITOR");

        Destination destination = destinationRepository.findById(destinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found with ID: " + destinationId));

        Place place = new Place();
        place.setDestination(destination);
        place.setName(placeDto.getName());
        place.setCategory(placeDto.getCategory() != null ? placeDto.getCategory() : "Historical");
        place.setRating(placeDto.getRating() != null ? placeDto.getRating() : 4.5);
        place.setReviewCount(placeDto.getReviewCount() != null ? placeDto.getReviewCount() : 100);
        place.setLatitude(placeDto.getLatitude());
        place.setLongitude(placeDto.getLongitude());
        place.setDistanceKm(placeDto.getDistanceKm() != null ? placeDto.getDistanceKm() : 2.5);
        place.setEstimatedVisitDurationMinutes(placeDto.getEstimatedVisitDurationMinutes() != null ? placeDto.getEstimatedVisitDurationMinutes() : 90);
        place.setEstimatedTravelTimeMinutes(placeDto.getEstimatedTravelTimeMinutes() != null ? placeDto.getEstimatedTravelTimeMinutes() : 15);
        place.setDescription(placeDto.getDescription());
        place.setOpeningHours(placeDto.getOpeningHours() != null ? placeDto.getOpeningHours() : "09:00 AM - 06:00 PM");
        place.setPriority(placeDto.getPriority() != null ? placeDto.getPriority() : "High");
        place.setVisited(false);
        place.setAddedToItinerary(true);
        placeRepository.save(place);

        destination.getPlaces().add(place);

        // Recalculate trip itinerary with updated places
        List<Destination> destinations = destinationRepository.findByTripOrderBySequenceOrderAsc(trip);
        itineraryItemRepository.deleteByTripId(trip.getId());

        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, destinations, trip.getTransportMode());
        for (DayItineraryDto day : days) {
            for (ItineraryItemDto itemDto : day.getItems()) {
                ItineraryItem item = new ItineraryItem();
                item.setTrip(trip);
                item.setDayNumber(day.getDayNumber());
                item.setScheduleDate(day.getDate());
                item.setStartTime(itemDto.getStartTime());
                item.setEndTime(itemDto.getEndTime());
                item.setActivity(itemDto.getActivity());
                item.setLocation(itemDto.getLocation());
                item.setActivityType(itemDto.getActivityType());
                item.setTravelDistanceKm(itemDto.getTravelDistanceKm());
                item.setTravelTimeMinutes(itemDto.getTravelTimeMinutes());
                item.setDurationMinutes(itemDto.getDurationMinutes());
                item.setRating(itemDto.getRating());
                item.setVisited(itemDto.getVisited());
                item.setNotes(itemDto.getNotes());
                itineraryItemRepository.save(item);
            }
        }

        List<String> destNames = destinations.stream().map(Destination::getName).collect(Collectors.toList());
        RouteSummaryDto routeSummary = routeService.calculateCompleteRoute(trip.getStartLocation(), destNames, trip.getTransportMode());

        return buildTripResponseDto(trip, routeSummary, days);
    }

    @Transactional
    public TripResponseDto updateDestinationHotel(Long tripId, Long destinationId, String hotelName, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "EDITOR");

        Destination destination = destinationRepository.findById(destinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found with ID: " + destinationId));
        if (!destination.getTrip().getId().equals(trip.getId())) {
            throw new BadRequestException("Destination does not belong to this trip.");
        }

        destination.setHotelName(hotelName != null && !hotelName.trim().isEmpty() ? hotelName.trim() : null);
        destinationRepository.save(destination);

        List<Destination> destinations = destinationRepository.findByTripOrderBySequenceOrderAsc(trip);
        itineraryItemRepository.deleteByTripId(trip.getId());

        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, destinations, trip.getTransportMode());
        for (DayItineraryDto day : days) {
            for (ItineraryItemDto itemDto : day.getItems()) {
                ItineraryItem item = new ItineraryItem();
                item.setTrip(trip);
                item.setDayNumber(day.getDayNumber());
                item.setScheduleDate(day.getDate());
                item.setStartTime(itemDto.getStartTime());
                item.setEndTime(itemDto.getEndTime());
                item.setActivity(itemDto.getActivity());
                item.setLocation(itemDto.getLocation());
                item.setActivityType(itemDto.getActivityType());
                item.setTravelDistanceKm(itemDto.getTravelDistanceKm());
                item.setTravelTimeMinutes(itemDto.getTravelTimeMinutes());
                item.setDurationMinutes(itemDto.getDurationMinutes());
                item.setRating(itemDto.getRating());
                item.setVisited(itemDto.getVisited());
                item.setNotes(itemDto.getNotes());
                itineraryItemRepository.save(item);
            }
        }

        tripRepository.save(trip);
        return getTripDtoById(tripId, user);
    }

    /**
     * Map-based accommodation selection & detail update (Requirement 4).
     */
    @Transactional
    public TripResponseDto updateDestinationAccommodation(Long tripId, Long destinationId, AccommodationStayDto stayDto, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "EDITOR");

        Destination destination = destinationRepository.findById(destinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found with ID: " + destinationId));
        if (!destination.getTrip().getId().equals(trip.getId())) {
            throw new BadRequestException("Destination does not belong to this trip.");
        }

        if (stayDto.getHotelName() != null && !stayDto.getHotelName().trim().isEmpty()) {
            destination.setHotelName(stayDto.getHotelName().trim());
        }
        destination.setHotelAddress(stayDto.getHotelAddress());
        destination.setHotelLat(stayDto.getHotelLat());
        destination.setHotelLng(stayDto.getHotelLng());
        if ((destination.getHotelLat() == null || destination.getHotelLng() == null) && destination.getHotelAddress() != null && !destination.getHotelAddress().trim().isEmpty()) {
            double[] coords = googleMapsService.geocode(destination.getHotelAddress());
            if (coords != null && coords.length >= 2) {
                destination.setHotelLat(coords[0]);
                destination.setHotelLng(coords[1]);
            }
        }
        destination.setCheckInDate(stayDto.getCheckInDate());
        destination.setCheckOutDate(stayDto.getCheckOutDate());
        destination.setCheckInTime(stayDto.getCheckInTime());
        destination.setCheckOutTime(stayDto.getCheckOutTime());
        destination.setRoomsCount(stayDto.getRoomsCount() != null ? stayDto.getRoomsCount() : 1);
        destination.setGuestsCount(stayDto.getGuestsCount() != null ? stayDto.getGuestsCount() : 2);
        destination.setBookingReference(stayDto.getBookingReference());
        destination.setHotelNotes(stayDto.getHotelNotes());

        destinationRepository.save(destination);

        // Update itinerary hotel items for this day
        int targetDayNumber = destination.getSequenceOrder() + 1;
        List<ItineraryItem> dayItems = itineraryItemRepository.findByTripIdAndDayNumberOrderByStartTimeAsc(trip.getId(), targetDayNumber);
        boolean foundHotelItem = false;
        for (ItineraryItem it : dayItems) {
            if ("HOTEL".equalsIgnoreCase(it.getActivityType())) {
                it.setLocation(destination.getHotelName() != null ? destination.getHotelName() : destination.getName());
                it.setActivity("🏨 Check-in & Stay at " + (destination.getHotelName() != null ? destination.getHotelName() : destination.getName()));
                if (destination.getHotelNotes() != null) {
                    it.setNotes(destination.getHotelNotes());
                }
                itineraryItemRepository.save(it);
                foundHotelItem = true;
                break;
            }
        }
        if (!foundHotelItem && destination.getHotelName() != null) {
            ItineraryItem hotelItem = new ItineraryItem();
            hotelItem.setTrip(trip);
            hotelItem.setDayNumber(targetDayNumber);
            hotelItem.setScheduleDate(trip.getStartDate() != null ? trip.getStartDate().plusDays(targetDayNumber - 1) : LocalDate.now());
            hotelItem.setStartTime("19:30");
            hotelItem.setEndTime("20:30");
            hotelItem.setActivity("🏨 Check-in & Stay at " + destination.getHotelName());
            hotelItem.setLocation(destination.getHotelName());
            hotelItem.setActivityType("HOTEL");
            hotelItem.setDurationMinutes(60);
            hotelItem.setNotes(destination.getHotelNotes());
            itineraryItemRepository.save(hotelItem);
        }

        return getTripDtoById(tripId, user);
    }

    @Transactional
    public TripResponseDto removeDestinationAccommodation(Long tripId, Long destinationId, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "EDITOR");

        Destination destination = destinationRepository.findById(destinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found with ID: " + destinationId));
        if (!destination.getTrip().getId().equals(trip.getId())) {
            throw new BadRequestException("Destination does not belong to this trip.");
        }

        destination.setHotelName(null);
        destination.setHotelAddress(null);
        destination.setHotelLat(null);
        destination.setHotelLng(null);
        destination.setCheckInDate(null);
        destination.setCheckOutDate(null);
        destination.setCheckInTime(null);
        destination.setCheckOutTime(null);
        destination.setRoomsCount(1);
        destination.setGuestsCount(1);
        destination.setBookingReference(null);
        destination.setHotelNotes(null);

        destinationRepository.save(destination);

        // Update itinerary hotel items for this day to generic
        int targetDayNumber = destination.getSequenceOrder() + 1;
        List<ItineraryItem> dayItems = itineraryItemRepository.findByTripIdAndDayNumberOrderByStartTimeAsc(trip.getId(), targetDayNumber);
        for (ItineraryItem it : dayItems) {
            if ("HOTEL".equalsIgnoreCase(it.getActivityType())) {
                it.setLocation("Hotel / Stay in " + destination.getName());
                it.setActivity("🏨 Relax at Stay in " + destination.getName());
                it.setNotes(null);
                itineraryItemRepository.save(it);
                break;
            }
        }

        return getTripDtoById(tripId, user);
    }

    @Transactional
    public Map<String, Object> toggleActivityLock(Long tripId, Long itemId, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "EDITOR");

        ItineraryItem item = itineraryItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary item not found with ID: " + itemId));
        if (!item.getTrip().getId().equals(trip.getId())) {
            throw new BadRequestException("Activity does not belong to this trip.");
        }

        boolean newLockedStatus = !Boolean.TRUE.equals(item.getLocked());
        item.setLocked(newLockedStatus);
        itineraryItemRepository.save(item);

        return Map.of("itemId", item.getId(), "locked", newLockedStatus, "message", newLockedStatus ? "Activity locked" : "Activity unlocked");
    }

    @Transactional
    public TripResponseDto replaceActivity(Long tripId, Long itemId, ItineraryItemDto updatedItem, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "EDITOR");

        ItineraryItem item = itineraryItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary item not found with ID: " + itemId));
        if (!item.getTrip().getId().equals(trip.getId())) {
            throw new BadRequestException("Activity does not belong to this trip.");
        }

        if (updatedItem.getActivity() != null && !updatedItem.getActivity().trim().isEmpty()) {
            item.setActivity(updatedItem.getActivity().trim());
        }
        if (updatedItem.getLocation() != null && !updatedItem.getLocation().trim().isEmpty()) {
            item.setLocation(updatedItem.getLocation().trim());
        }
        if (updatedItem.getStartTime() != null) {
            item.setStartTime(updatedItem.getStartTime());
        }
        if (updatedItem.getEndTime() != null) {
            item.setEndTime(updatedItem.getEndTime());
        }
        if (updatedItem.getActivityType() != null) {
            item.setActivityType(updatedItem.getActivityType());
        }
        if (updatedItem.getDurationMinutes() != null) {
            item.setDurationMinutes(updatedItem.getDurationMinutes());
        }
        if (updatedItem.getTravelDistanceKm() != null) {
            item.setTravelDistanceKm(updatedItem.getTravelDistanceKm());
        }
        if (updatedItem.getTravelTimeMinutes() != null) {
            item.setTravelTimeMinutes(updatedItem.getTravelTimeMinutes());
        }
        if (updatedItem.getRating() != null) {
            item.setRating(updatedItem.getRating());
        }
        if (updatedItem.getNotes() != null) {
            item.setNotes(updatedItem.getNotes());
        }
        if (updatedItem.getLocked() != null) {
            item.setLocked(updatedItem.getLocked());
        }

        itineraryItemRepository.save(item);
        return getTripDtoById(tripId, user);
    }

    @Transactional
    public TripResponseDto deleteItineraryItem(Long tripId, Long itemId, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "EDITOR");

        ItineraryItem item = itineraryItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Itinerary item not found with ID: " + itemId));
        if (!item.getTrip().getId().equals(trip.getId())) {
            throw new BadRequestException("Activity does not belong to this trip.");
        }

        itineraryItemRepository.delete(item);
        return getTripDtoById(tripId, user);
    }

    @Transactional
    public TripResponseDto addActivityToDay(Long tripId, Integer dayNumber, ItineraryItemDto newItem, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "EDITOR");

        ItineraryItem item = new ItineraryItem();
        item.setTrip(trip);
        item.setDayNumber(dayNumber);
        LocalDate itemDate = (trip.getStartDate() != null) ? trip.getStartDate().plusDays(dayNumber - 1) : LocalDate.now();
        item.setScheduleDate(itemDate);
        item.setStartTime(newItem.getStartTime() != null ? newItem.getStartTime() : "10:00");
        item.setEndTime(newItem.getEndTime() != null ? newItem.getEndTime() : "11:30");
        item.setActivity(newItem.getActivity() != null ? newItem.getActivity() : "Sightseeing Activity");
        item.setLocation(newItem.getLocation() != null ? newItem.getLocation() : trip.getStartLocation());
        item.setActivityType(newItem.getActivityType() != null ? newItem.getActivityType() : "SIGHTSEEING");
        item.setTravelDistanceKm(newItem.getTravelDistanceKm() != null ? newItem.getTravelDistanceKm() : 2.0);
        item.setTravelTimeMinutes(newItem.getTravelTimeMinutes() != null ? newItem.getTravelTimeMinutes() : 15);
        item.setDurationMinutes(newItem.getDurationMinutes() != null ? newItem.getDurationMinutes() : 90);
        item.setRating(newItem.getRating() != null ? newItem.getRating() : 4.5);
        item.setVisited(false);
        item.setLocked(newItem.getLocked() != null ? newItem.getLocked() : false);
        item.setNotes(newItem.getNotes());

        itineraryItemRepository.save(item);
        return getTripDtoById(tripId, user);
    }

    @Transactional
    public TripResponseDto addDestinationToTrip(Long tripId, String destinationName, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "EDITOR");

        if (destinationName == null || destinationName.trim().isEmpty()) {
            throw new BadRequestException("Destination name cannot be blank.");
        }

        List<Destination> currentDests = destinationRepository.findByTripOrderBySequenceOrderAsc(trip);
        int newSeq = currentDests.size();

        Destination destination = new Destination(destinationName.trim(), newSeq);
        destination.setTrip(trip);
        double[] coords = googleMapsService.geocode(destinationName.trim());
        destination.setLatitude(coords[0]);
        destination.setLongitude(coords[1]);

        Destination savedDest = destinationRepository.save(destination);
        currentDests.add(savedDest);

        // Seed recommendations
        List<SightseeingPlaceDto> recommended = recommendationService.getRecommendedPlaces(
                destinationName.trim(),
                trip.getPreferredInterests() != null ? Arrays.asList(trip.getPreferredInterests().split(",")) : null,
                "popularity",
                null
        );
        for (SightseeingPlaceDto rec : recommended) {
            Place place = new Place();
            place.setDestination(savedDest);
            place.setName(rec.getName());
            place.setCategory(rec.getCategory());
            place.setRating(rec.getRating());
            place.setReviewCount(rec.getReviewCount());
            place.setLatitude(rec.getLatitude());
            place.setLongitude(rec.getLongitude());
            place.setDistanceKm(rec.getDistanceKm());
            place.setEstimatedVisitDurationMinutes(rec.getEstimatedVisitDurationMinutes());
            place.setEstimatedTravelTimeMinutes(rec.getEstimatedTravelTimeMinutes());
            place.setDescription(rec.getDescription());
            place.setOpeningHours(rec.getOpeningHours());
            place.setPriority(rec.getPriority());
            place.setVisited(false);
            place.setAddedToItinerary(true);
            placeRepository.save(place);
            savedDest.getPlaces().add(place);
        }

        List<String> names = currentDests.stream().map(Destination::getName).collect(Collectors.toList());
        RouteSummaryDto routeSummary = routeService.calculateCompleteRoute(trip.getStartLocation(), names, trip.getTransportMode());

        for (int i = 0; i < currentDests.size(); i++) {
            Destination d = currentDests.get(i);
            d.setSequenceOrder(i);
            if (i < routeSummary.getSegments().size()) {
                RouteSegmentDto seg = routeSummary.getSegments().get(i);
                d.setDistanceFromPrevious(seg.getDistanceKm());
                d.setTravelTimeFromPrevious(seg.getDurationMinutes());
            }
            destinationRepository.save(d);
        }

        trip.setTotalDistance(routeSummary.getTotalDistanceKm());
        trip.setTotalTravelTimeMinutes(routeSummary.getTotalTravelTimeMinutes());

        itineraryItemRepository.deleteByTripId(trip.getId());
        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, currentDests, trip.getTransportMode());
        for (DayItineraryDto day : days) {
            for (ItineraryItemDto itemDto : day.getItems()) {
                ItineraryItem item = new ItineraryItem();
                item.setTrip(trip);
                item.setDayNumber(day.getDayNumber());
                item.setScheduleDate(day.getDate());
                item.setStartTime(itemDto.getStartTime());
                item.setEndTime(itemDto.getEndTime());
                item.setActivity(itemDto.getActivity());
                item.setLocation(itemDto.getLocation());
                item.setActivityType(itemDto.getActivityType());
                item.setTravelDistanceKm(itemDto.getTravelDistanceKm());
                item.setTravelTimeMinutes(itemDto.getTravelTimeMinutes());
                item.setDurationMinutes(itemDto.getDurationMinutes());
                item.setRating(itemDto.getRating());
                item.setVisited(itemDto.getVisited());
                item.setNotes(itemDto.getNotes());
                item.setLocked(itemDto.getLocked());
                itineraryItemRepository.save(item);
            }
        }

        tripRepository.save(trip);
        return buildTripResponseDto(trip, routeSummary, days, currentDests);
    }

    @Transactional
    public TripResponseDto removeDestinationFromTrip(Long tripId, Long destinationId, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "EDITOR");

        List<Destination> currentDests = destinationRepository.findByTripOrderBySequenceOrderAsc(trip);
        if (currentDests.size() <= 1) {
            throw new BadRequestException("A trip must have at least one destination.");
        }

        Destination toRemove = currentDests.stream()
                .filter(d -> Objects.equals(d.getId(), destinationId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found with ID: " + destinationId));

        currentDests.remove(toRemove);
        destinationRepository.delete(toRemove);

        // Re-index remaining destinations
        for (int i = 0; i < currentDests.size(); i++) {
            currentDests.get(i).setSequenceOrder(i);
        }

        List<String> names = currentDests.stream().map(Destination::getName).collect(Collectors.toList());
        RouteSummaryDto routeSummary = routeService.calculateCompleteRoute(trip.getStartLocation(), names, trip.getTransportMode());

        for (int i = 0; i < currentDests.size(); i++) {
            Destination d = currentDests.get(i);
            if (i < routeSummary.getSegments().size()) {
                RouteSegmentDto seg = routeSummary.getSegments().get(i);
                d.setDistanceFromPrevious(seg.getDistanceKm());
                d.setTravelTimeFromPrevious(seg.getDurationMinutes());
            } else {
                d.setDistanceFromPrevious(0.0);
                d.setTravelTimeFromPrevious(0);
            }
            destinationRepository.save(d);
        }

        trip.setTotalDistance(routeSummary.getTotalDistanceKm());
        trip.setTotalTravelTimeMinutes(routeSummary.getTotalTravelTimeMinutes());

        itineraryItemRepository.deleteByTripId(trip.getId());
        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, currentDests, trip.getTransportMode());
        for (DayItineraryDto day : days) {
            for (ItineraryItemDto itemDto : day.getItems()) {
                ItineraryItem item = new ItineraryItem();
                item.setTrip(trip);
                item.setDayNumber(day.getDayNumber());
                item.setScheduleDate(day.getDate());
                item.setStartTime(itemDto.getStartTime());
                item.setEndTime(itemDto.getEndTime());
                item.setActivity(itemDto.getActivity());
                item.setLocation(itemDto.getLocation());
                item.setActivityType(itemDto.getActivityType());
                item.setTravelDistanceKm(itemDto.getTravelDistanceKm());
                item.setTravelTimeMinutes(itemDto.getTravelTimeMinutes());
                item.setDurationMinutes(itemDto.getDurationMinutes());
                item.setRating(itemDto.getRating());
                item.setVisited(itemDto.getVisited());
                item.setNotes(itemDto.getNotes());
                item.setLocked(itemDto.getLocked());
                itineraryItemRepository.save(item);
            }
        }

        tripRepository.save(trip);
        return buildTripResponseDto(trip, routeSummary, days, currentDests);
    }

    @Transactional
    public TripResponseDto applyRouteAlternative(Long tripId, List<String> orderedDestinations, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with ID: " + tripId));
        checkTripAccess(trip, user, "EDITOR");

        if (orderedDestinations == null || orderedDestinations.isEmpty()) {
            throw new BadRequestException("Alternative destination order cannot be empty.");
        }

        List<Destination> destinations = destinationRepository.findByTripOrderBySequenceOrderAsc(trip);
        Map<String, Destination> destMap = destinations.stream()
                .collect(Collectors.toMap(d -> d.getName().trim().toLowerCase(), d -> d, (a, b) -> a));

        List<Destination> reordered = new ArrayList<>();
        for (String destName : orderedDestinations) {
            Destination d = destMap.get(destName.trim().toLowerCase());
            if (d != null && !reordered.contains(d)) {
                reordered.add(d);
            }
        }

        for (Destination d : destinations) {
            if (!reordered.contains(d)) {
                reordered.add(d);
            }
        }

        List<String> names = reordered.stream().map(Destination::getName).collect(Collectors.toList());
        RouteSummaryDto newRouteSummary = routeService.calculateCompleteRoute(trip.getStartLocation(), names, trip.getTransportMode());

        for (int i = 0; i < reordered.size(); i++) {
            Destination d = reordered.get(i);
            d.setSequenceOrder(i);
            if (i < newRouteSummary.getSegments().size()) {
                RouteSegmentDto seg = newRouteSummary.getSegments().get(i);
                d.setDistanceFromPrevious(seg.getDistanceKm());
                d.setTravelTimeFromPrevious(seg.getDurationMinutes());
            }
            destinationRepository.save(d);
        }

        trip.setTotalDistance(newRouteSummary.getTotalDistanceKm());
        trip.setTotalTravelTimeMinutes(newRouteSummary.getTotalTravelTimeMinutes());

        List<ItineraryItem> existingItems = itineraryItemRepository.findByTripIdOrderByDayNumberAscStartTimeAsc(trip.getId());
        List<ItineraryItem> lockedItems = existingItems.stream().filter(it -> Boolean.TRUE.equals(it.getLocked())).collect(Collectors.toList());

        itineraryItemRepository.deleteByTripId(trip.getId());
        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, reordered, trip.getTransportMode());

        for (DayItineraryDto day : days) {
            for (ItineraryItemDto itemDto : day.getItems()) {
                ItineraryItem item = new ItineraryItem();
                item.setTrip(trip);
                item.setDayNumber(day.getDayNumber());
                item.setScheduleDate(day.getDate());
                item.setStartTime(itemDto.getStartTime());
                item.setEndTime(itemDto.getEndTime());
                item.setActivity(itemDto.getActivity());
                item.setLocation(itemDto.getLocation());
                item.setActivityType(itemDto.getActivityType());
                item.setTravelDistanceKm(itemDto.getTravelDistanceKm());
                item.setTravelTimeMinutes(itemDto.getTravelTimeMinutes());
                item.setDurationMinutes(itemDto.getDurationMinutes());
                item.setRating(itemDto.getRating());
                item.setVisited(itemDto.getVisited());
                item.setNotes(itemDto.getNotes());
                item.setLocked(itemDto.getLocked());
                itineraryItemRepository.save(item);
            }
        }

        for (ItineraryItem locked : lockedItems) {
            ItineraryItem restored = new ItineraryItem();
            restored.setTrip(trip);
            restored.setDayNumber(locked.getDayNumber());
            restored.setScheduleDate(locked.getScheduleDate());
            restored.setStartTime(locked.getStartTime());
            restored.setEndTime(locked.getEndTime());
            restored.setActivity(locked.getActivity());
            restored.setLocation(locked.getLocation());
            restored.setActivityType(locked.getActivityType());
            restored.setTravelDistanceKm(locked.getTravelDistanceKm());
            restored.setTravelTimeMinutes(locked.getTravelTimeMinutes());
            restored.setDurationMinutes(locked.getDurationMinutes());
            restored.setRating(locked.getRating());
            restored.setVisited(locked.getVisited());
            restored.setNotes(locked.getNotes());
            restored.setLocked(true);
            itineraryItemRepository.save(restored);
        }

        tripRepository.save(trip);
        return getTripDtoById(tripId, user);
    }

    @Transactional(readOnly = true)
    public List<PlaceAlongRouteDto> getPlacesAlongRoute(Long tripId, Double maxDetourKm, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "VIEWER");

        double limitKm = (maxDetourKm != null && maxDetourKm > 0) ? maxDetourKm : 25.0;

        List<Destination> destinations = destinationRepository.findByTripOrderBySequenceOrderAsc(trip);
        if (destinations.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> routePoints = new ArrayList<>();
        routePoints.add(trip.getStartLocation());
        for (Destination d : destinations) {
            routePoints.add(d.getName());
        }

        List<PlaceAlongRouteDto> results = new ArrayList<>();
        Set<String> alreadyInTrip = new HashSet<>();
        for (String pt : routePoints) {
            alreadyInTrip.add(pt.trim().toLowerCase());
        }

        Set<String> seenCandidateNames = new HashSet<>();
        for (int i = 0; i < routePoints.size() - 1; i++) {
            String from = routePoints.get(i);
            String to = routePoints.get(i + 1);
            String segmentName = from + " → " + to;

            GoogleMapsService.RouteCalculationResult baseline = googleMapsService.calculateRouteSegment(from, to, trip.getTransportMode());
            if (baseline == null) {
                continue;
            }
            double baselineKm = baseline.getDistanceKm();
            int baselineMins = baseline.getDurationMinutes();

            List<SightseeingPlaceDto> candidates = new ArrayList<>();
            candidates.addAll(recommendationService.getRecommendedPlaces(from, null, "rating", null));
            candidates.addAll(recommendationService.getRecommendedPlaces(to, null, "rating", null));

            for (SightseeingPlaceDto cand : candidates) {
                String cName = cand.getName().trim().toLowerCase();
                if (alreadyInTrip.contains(cName) || seenCandidateNames.contains(cName)) {
                    continue;
                }
                seenCandidateNames.add(cName);

                GoogleMapsService.RouteCalculationResult leg1 = googleMapsService.calculateRouteSegment(from, cand.getName(), trip.getTransportMode());
                GoogleMapsService.RouteCalculationResult leg2 = googleMapsService.calculateRouteSegment(cand.getName(), to, trip.getTransportMode());
                if (leg1 == null || leg2 == null) {
                    continue;
                }

                double totalWithStopKm = leg1.getDistanceKm() + leg2.getDistanceKm();
                int totalWithStopMins = leg1.getDurationMinutes() + leg2.getDurationMinutes();

                double extraKm = Math.max(0.0, Math.round((totalWithStopKm - baselineKm) * 10.0) / 10.0);
                int extraMins = Math.max(0, totalWithStopMins - baselineMins);

                if (extraKm <= limitKm) {
                    String formatted = String.format("+%.1f km, +%d min detour", extraKm, extraMins);
                    PlaceAlongRouteDto dto = new PlaceAlongRouteDto(
                            cand.getName(),
                            cand.getCategory() != null ? cand.getCategory() : "Scenic Stop",
                            cand.getRating() != null ? cand.getRating() : 4.5,
                            cand.getReviewCount() != null ? cand.getReviewCount() : 150,
                            cand.getDescription(),
                            cand.getLatitude(),
                            cand.getLongitude(),
                            segmentName,
                            from,
                            to,
                            extraKm,
                            extraMins,
                            formatted,
                            i
                    );
                    results.add(dto);
                }
            }
        }

        results.sort(Comparator.comparingDouble(PlaceAlongRouteDto::getDetourDistanceKm));
        return results.stream().limit(12).collect(Collectors.toList());
    }

    @Transactional
    public TripResponseDto addPlaceAlongRouteAsDestination(Long tripId, PlaceAlongRouteDto placeDto, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "EDITOR");

        List<Destination> destinations = destinationRepository.findByTripOrderBySequenceOrderAsc(trip);
        int insertIdx = (placeDto.getInsertIndex() != null && placeDto.getInsertIndex() >= 0 && placeDto.getInsertIndex() <= destinations.size())
                ? placeDto.getInsertIndex() + 1
                : destinations.size();

        Destination newDest = new Destination(placeDto.getName(), insertIdx);
        newDest.setTrip(trip);
        double[] coords = (placeDto.getLatitude() != null && placeDto.getLongitude() != null)
                ? new double[]{placeDto.getLatitude(), placeDto.getLongitude()}
                : googleMapsService.geocode(placeDto.getName());
        newDest.setLatitude(coords[0]);
        newDest.setLongitude(coords[1]);

        destinations.add(insertIdx, newDest);

        List<String> names = destinations.stream().map(Destination::getName).collect(Collectors.toList());
        RouteSummaryDto routeSummary = routeService.calculateCompleteRoute(trip.getStartLocation(), names, trip.getTransportMode());

        for (int i = 0; i < destinations.size(); i++) {
            Destination d = destinations.get(i);
            d.setSequenceOrder(i);
            if (i < routeSummary.getSegments().size()) {
                RouteSegmentDto seg = routeSummary.getSegments().get(i);
                d.setDistanceFromPrevious(seg.getDistanceKm());
                d.setTravelTimeFromPrevious(seg.getDurationMinutes());
            }
            destinationRepository.save(d);
        }

        trip.setTotalDistance(routeSummary.getTotalDistanceKm());
        trip.setTotalTravelTimeMinutes(routeSummary.getTotalTravelTimeMinutes());

        itineraryItemRepository.deleteByTripId(trip.getId());
        List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, destinations, trip.getTransportMode());
        for (DayItineraryDto day : days) {
            for (ItineraryItemDto itemDto : day.getItems()) {
                ItineraryItem item = new ItineraryItem();
                item.setTrip(trip);
                item.setDayNumber(day.getDayNumber());
                item.setScheduleDate(day.getDate());
                item.setStartTime(itemDto.getStartTime());
                item.setEndTime(itemDto.getEndTime());
                item.setActivity(itemDto.getActivity());
                item.setLocation(itemDto.getLocation());
                item.setActivityType(itemDto.getActivityType());
                item.setTravelDistanceKm(itemDto.getTravelDistanceKm());
                item.setTravelTimeMinutes(itemDto.getTravelTimeMinutes());
                item.setDurationMinutes(itemDto.getDurationMinutes());
                item.setRating(itemDto.getRating());
                item.setVisited(itemDto.getVisited());
                item.setNotes(itemDto.getNotes());
                itineraryItemRepository.save(item);
            }
        }

        tripRepository.save(trip);
        return buildTripResponseDto(trip, routeSummary, days, destinations);
    }

    @Transactional(readOnly = true)
    public PackingChecklistDto getPackingChecklist(Long tripId, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "VIEWER");

        long daysCount = Math.max(1, ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1);
        PackingChecklistDto checklist = new PackingChecklistDto(trip.getId(), trip.getTripName(), (int) daysCount);

        List<Destination> destinations = destinationRepository.findByTripOrderBySequenceOrderAsc(trip);
        String allNames = destinations.stream().map(d -> d.getName().toLowerCase()).collect(Collectors.joining(" "))
                + " " + (trip.getStartLocation() != null ? trip.getStartLocation().toLowerCase() : "");

        // 1. Essential Documents
        List<PackingChecklistDto.ChecklistItem> docs = new ArrayList<>();
        docs.add(new PackingChecklistDto.ChecklistItem("Government Photo ID / Passport", "Required for hotel check-ins and identity checks", true));
        docs.add(new PackingChecklistDto.ChecklistItem("Driver's License & Vehicle Documents", "Essential if self-driving or renting vehicles", true));
        docs.add(new PackingChecklistDto.ChecklistItem("Debit / Credit Cards & Emergency Cash", "Toll booths, local stalls, and areas with spotty network", true));
        docs.add(new PackingChecklistDto.ChecklistItem("Digital & Printed Trip Itinerary", "Convenient offline access during travel", false));
        docs.add(new PackingChecklistDto.ChecklistItem("Medical / Travel Insurance Cards", "Health safety emergency coverage", false));
        checklist.getCategories().add(new PackingChecklistDto.ChecklistCategory("Essential Documents", "📄", docs));

        // 2. Clothing & Apparel
        List<PackingChecklistDto.ChecklistItem> clothing = new ArrayList<>();
        clothing.add(new PackingChecklistDto.ChecklistItem(Math.min(10, daysCount + 2) + " Sets of Comfortable Daily Outfits", "Breathable clothes for daytime sightseeing", true));
        clothing.add(new PackingChecklistDto.ChecklistItem((daysCount + 2) + " Pairs of Innerwear & Socks", "Duration-based hygiene essentials", true));
        clothing.add(new PackingChecklistDto.ChecklistItem("Comfortable Walking / Running Shoes", "Crucial for exploring destinations on foot", true));
        clothing.add(new PackingChecklistDto.ChecklistItem("Slip-on Sandals or Flip-flops", "Easy to take off at temples and relaxing at hotel", false));

        boolean isHillStation = allNames.contains("coorg") || allNames.contains("ooty") || allNames.contains("munnar") ||
                allNames.contains("wayanad") || allNames.contains("manali") || allNames.contains("shimla") ||
                allNames.contains("kodaikanal") || allNames.contains("chikmagalur");
        if (isHillStation) {
            clothing.add(new PackingChecklistDto.ChecklistItem("Warm Jacket / Fleece Sweater", "Misty hill weather and chilly evenings", true));
            clothing.add(new PackingChecklistDto.ChecklistItem("Thermal Innerwear & Beanie", "Cold morning viewpoints and mountain treks", false));
        }

        boolean isBeach = allNames.contains("goa") || allNames.contains("gokarna") || allNames.contains("pondicherry") ||
                allNames.contains("kochi") || allNames.contains("mangalore") || allNames.contains("varkala") || allNames.contains("alappuzha");
        if (isBeach) {
            clothing.add(new PackingChecklistDto.ChecklistItem("Swimwear & Quick-Dry Beach Shorts", "Coastal swimming and beach sports", true));
            clothing.add(new PackingChecklistDto.ChecklistItem("Light UV-Protection Linen Shirts", "Sunny coastal afternoon protection", false));
        }

        boolean isTempleOrHeritage = allNames.contains("mysore") || allNames.contains("hampi") || allNames.contains("madurai") ||
                allNames.contains("varanasi") || allNames.contains("badami");
        if (isTempleOrHeritage) {
            clothing.add(new PackingChecklistDto.ChecklistItem("Traditional / Modest Temple Attire", "Shoulders and knees must be covered at heritage shrines", true));
        }
        checklist.getCategories().add(new PackingChecklistDto.ChecklistCategory("Clothing & Apparel", "👕", clothing));

        // 3. Toiletries & Health Essentials
        List<PackingChecklistDto.ChecklistItem> health = new ArrayList<>();
        health.add(new PackingChecklistDto.ChecklistItem("First-Aid Kit & Band-Aids", "Blister care, minor cuts, and antiseptic wipes", true));
        health.add(new PackingChecklistDto.ChecklistItem("Prescription Medications & Pain Reliever", "Daily medications, Paracetamol, and antacids", true));
        health.add(new PackingChecklistDto.ChecklistItem("Motion Sickness Medication (Avomine)", "Ghat roads and winding hill station hairpin turns", isHillStation));
        health.add(new PackingChecklistDto.ChecklistItem("Broad-Spectrum Sunscreen (SPF 50)", "Protection against harsh UV rays during outdoor exploration", true));
        health.add(new PackingChecklistDto.ChecklistItem("Mosquito / Insect Repellent Spray", "Outdoor evening sightseeing and nature trails", true));
        health.add(new PackingChecklistDto.ChecklistItem("Hand Sanitizer & Pocket Wet Wipes", "Hygiene while dining at highway stops", false));
        checklist.getCategories().add(new PackingChecklistDto.ChecklistCategory("Toiletries & Health", "💊", health));

        // 4. Electronics & Travel Gear
        List<PackingChecklistDto.ChecklistItem> electronics = new ArrayList<>();
        electronics.add(new PackingChecklistDto.ChecklistItem("High-Capacity Power Bank (10,000+ mAh)", "Keeps phone charged for GPS navigation and photos", true));
        electronics.add(new PackingChecklistDto.ChecklistItem("Smartphone Chargers & Car USB Adapter", "Daily mobile recharge while driving", true));
        electronics.add(new PackingChecklistDto.ChecklistItem("Waterproof Phone Pouch or Rain Cover", "Protection during monsoons, waterfalls, and boat rides", isBeach || isHillStation));
        electronics.add(new PackingChecklistDto.ChecklistItem("Camera / Action Cam & Extra Memory Cards", "Capturing scenic landscapes and cultural moments", false));
        electronics.add(new PackingChecklistDto.ChecklistItem("Noise-Cancelling Earphones", "Peaceful travel during long transit segments", false));
        checklist.getCategories().add(new PackingChecklistDto.ChecklistCategory("Electronics & Travel Gear", "⚡", electronics));

        return checklist;
    }

    @Transactional
    public Map<String, Object> togglePlaceVisited(Long placeId, boolean visited) {
        return togglePlaceVisited(placeId, visited, null);
    }

    @Transactional
    public Map<String, Object> togglePlaceVisited(Long placeId, boolean visited, User user) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new ResourceNotFoundException("Place not found with ID: " + placeId));
        Trip trip = place.getDestination().getTrip();
        if (user != null) {
            checkTripAccess(trip, user, "EDITOR");
        }
        place.setVisited(visited);
        placeRepository.save(place);

        Map<String, Object> map = computeProgressMap(trip);
        map.put("visited", visited);
        return map;
    }

    @Transactional
    public Map<String, Object> togglePlaceVisited(Long placeId, User user) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new ResourceNotFoundException("Place not found with ID: " + placeId));
        boolean newStatus = !Boolean.TRUE.equals(place.getVisited());
        return togglePlaceVisited(placeId, newStatus, user);
    }

    @Transactional
    public Map<String, Object> toggleDestinationVisited(Long destinationId, boolean visited) {
        return toggleDestinationVisited(destinationId, visited, null);
    }

    @Transactional
    public Map<String, Object> toggleDestinationVisited(Long destinationId, boolean visited, User user) {
        Destination destination = destinationRepository.findById(destinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found with ID: " + destinationId));
        Trip trip = destination.getTrip();
        if (user != null) {
            checkTripAccess(trip, user, "EDITOR");
        }
        destination.setVisited(visited);
        destinationRepository.save(destination);

        return computeProgressMap(trip);
    }

    private Map<String, Object> computeProgressMap(Trip trip) {
        List<Destination> dests = destinationRepository.findByTripOrderBySequenceOrderAsc(trip);
        int total = 0;
        int visited = 0;

        for (Destination d : dests) {
            total++;
            if (Boolean.TRUE.equals(d.getVisited())) visited++;
            for (Place p : d.getPlaces()) {
                total++;
                if (Boolean.TRUE.equals(p.getVisited())) visited++;
            }
        }

        int percentage = total > 0 ? (int) Math.round(((double) visited / total) * 100.0) : 0;

        Map<String, Object> map = new HashMap<>();
        map.put("totalPlaces", total);
        map.put("visitedPlaces", visited);
        map.put("percentage", percentage);
        map.put("tripId", trip.getId());
        return map;
    }

    private List<DayItineraryDto> buildDayItinerariesFromEntities(List<ItineraryItem> items, Trip trip, List<Destination> destinations) {
        Map<Integer, List<ItineraryItem>> byDay = items.stream()
                .collect(Collectors.groupingBy(ItineraryItem::getDayNumber, TreeMap::new, Collectors.toList()));

        List<DayItineraryDto> dayDtos = new ArrayList<>();
        LocalDate startDate = trip.getStartDate() != null ? trip.getStartDate() : LocalDate.now();
        Set<String> seenTripSightseeingKeys = new HashSet<>();

        for (Map.Entry<Integer, List<ItineraryItem>> entry : byDay.entrySet()) {
            int dayNumber = entry.getKey();
            List<ItineraryItem> dayItems = entry.getValue();
            LocalDate dayDate = startDate.plusDays(dayNumber - 1);

            int destIdx = Math.min(destinations.size() - 1, dayNumber - 1);
            String destName = (destIdx >= 0 && destIdx < destinations.size()) ? destinations.get(destIdx).getName() : trip.getStartLocation();
            String title = "Day " + dayNumber + " • Explore " + destName;

            DayItineraryDto dayDto = new DayItineraryDto(dayNumber, dayDate, title);
            List<ItineraryItemDto> itemDtos = new ArrayList<>();

            double dayDistance = 0.0;
            int dayTravelMinutes = 0;
            int daySightseeingMinutes = 0;
            int dayBreakMinutes = 0;
            int totalPlaces = 0;
            int visitedPlaces = 0;

            for (ItineraryItem it : dayItems) {
                if ("SIGHTSEEING".equalsIgnoreCase(it.getActivityType())) {
                    String pKey = ItineraryService.normalizePlaceKey(it.getActivity() != null ? it.getActivity() : it.getLocation());
                    if (!pKey.isEmpty() && seenTripSightseeingKeys.contains(pKey)) {
                        // Skip repetitive sightseeing visit that duplicates an earlier day
                        continue;
                    }
                    if (!pKey.isEmpty()) {
                        seenTripSightseeingKeys.add(pKey);
                    }
                }
                ItineraryItemDto dto = new ItineraryItemDto();
                dto.setId(it.getId());
                dto.setTripId(trip.getId());
                dto.setDayNumber(it.getDayNumber());
                dto.setScheduleDate(it.getScheduleDate() != null ? it.getScheduleDate() : dayDate);
                dto.setStartTime(it.getStartTime());
                dto.setEndTime(it.getEndTime());
                dto.setActivity(it.getActivity());
                dto.setLocation(it.getLocation());
                dto.setActivityType(it.getActivityType());
                if (it.getPlace() != null) dto.setPlaceId(it.getPlace().getId());
                dto.setTravelDistanceKm(it.getTravelDistanceKm() != null ? it.getTravelDistanceKm() : 0.0);
                dto.setTravelTimeMinutes(it.getTravelTimeMinutes() != null ? it.getTravelTimeMinutes() : 0);
                dto.setDurationMinutes(it.getDurationMinutes() != null ? it.getDurationMinutes() : 60);
                dto.setRating(it.getRating());
                dto.setVisited(it.getVisited() != null && it.getVisited());
                dto.setLocked(it.getLocked() != null && it.getLocked());
                dto.setNotes(it.getNotes());

                itemDtos.add(dto);

                dayDistance += dto.getTravelDistanceKm();
                dayTravelMinutes += dto.getTravelTimeMinutes();
                if ("SIGHTSEEING".equalsIgnoreCase(dto.getActivityType())) {
                    daySightseeingMinutes += dto.getDurationMinutes();
                    totalPlaces++;
                    if (Boolean.TRUE.equals(dto.getVisited())) visitedPlaces++;
                } else if ("MEAL".equalsIgnoreCase(dto.getActivityType()) || "REST".equalsIgnoreCase(dto.getActivityType()) || "HOTEL".equalsIgnoreCase(dto.getActivityType())) {
                    dayBreakMinutes += dto.getDurationMinutes();
                }
            }

            dayDto.setItems(itemDtos);
            dayDto.setTotalDistanceKm(Math.round(dayDistance * 10.0) / 10.0);
            dayDto.setTravelTimeMinutes(dayTravelMinutes);
            dayDto.setSightseeingTimeMinutes(daySightseeingMinutes);
            dayDto.setBreakTimeMinutes(dayBreakMinutes);
            dayDto.setTotalDurationMinutes(dayTravelMinutes + daySightseeingMinutes + dayBreakMinutes);
            dayDto.setTotalPlacesCount(totalPlaces);
            dayDto.setVisitedCount(visitedPlaces);

            if (dayTravelMinutes >= 420) {
                dayDto.setStatus("⚠️ Long Day");
                dayDto.setWarningMessage("This day contains " + TimeUtils.formatDuration(dayTravelMinutes) + " of transit!");
            } else if (dayTravelMinutes + daySightseeingMinutes >= 600) {
                dayDto.setStatus("Overcrowded");
                dayDto.setWarningMessage("Schedule is heavily packed.");
            } else if (dayTravelMinutes < 120 && daySightseeingMinutes < 180) {
                dayDto.setStatus("Relaxed");
            } else {
                dayDto.setStatus("Balanced");
            }

            dayDtos.add(dayDto);
        }

        return dayDtos;
    }

    private TripResponseDto buildTripResponseDto(Trip trip, RouteSummaryDto routeSummary, List<DayItineraryDto> dayItineraries) {
        return buildTripResponseDto(trip, routeSummary, dayItineraries, null);
    }

    private TripResponseDto buildTripResponseDto(Trip trip, RouteSummaryDto routeSummary, List<DayItineraryDto> dayItineraries, List<Destination> orderedDestinations) {
        TripResponseDto dto = new TripResponseDto();
        dto.setId(trip.getId());
        dto.setTripName(trip.getTripName());
        dto.setStartLocation(trip.getStartLocation());
        dto.setStartLat(trip.getStartLat());
        dto.setStartLng(trip.getStartLng());
        if (dto.getStartLat() == null || dto.getStartLng() == null) {
            double[] startC = googleMapsService.geocode(trip.getStartLocation());
            if (startC != null && startC.length >= 2) {
                dto.setStartLat(startC[0]);
                dto.setStartLng(startC[1]);
            } else {
                dto.setStartLat(12.9716);
                dto.setStartLng(77.5946);
            }
        }
        dto.setStartDate(trip.getStartDate());
        dto.setEndDate(trip.getEndDate());

        long daysCount = Math.max(1, ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1);
        dto.setTotalDays((int) daysCount);
        dto.setTransportMode(trip.getTransportMode());
        dto.setTravelStyle(trip.getTravelStyle());
        dto.setTravelersCount(trip.getTravelersCount());
        dto.setDailyStartTime(trip.getDailyStartTime());
        dto.setDailyEndTime(trip.getDailyEndTime());
        dto.setHotelLocation(trip.getHotelLocation());
        dto.setHotelLat(trip.getHotelLat());
        dto.setHotelLng(trip.getHotelLng());
        dto.setBudgetRange(trip.getBudgetRange());
        dto.setPreferredInterests(trip.getPreferredInterests());
        dto.setShareCode(trip.getShareCode());

        if (routeSummary != null) {
            dto.setTotalDistanceKm(routeSummary.getTotalDistanceKm());
            dto.setTotalTravelTimeMinutes(routeSummary.getTotalTravelTimeMinutes());
            dto.setFormattedTotalTravelTime(TimeUtils.formatDuration(routeSummary.getTotalTravelTimeMinutes()));
        } else {
            dto.setTotalDistanceKm(0.0);
            dto.setTotalTravelTimeMinutes(0);
            dto.setFormattedTotalTravelTime("0m");
        }

        // Map Destinations
        List<DestinationDto> destDtos = new ArrayList<>();
        int totalSightseeing = 0;
        int visitedSightseeing = 0;

        List<Destination> destinations = orderedDestinations != null ? orderedDestinations
                : (trip.getDestinations() != null ? trip.getDestinations() : Collections.emptyList());
        for (Destination d : destinations) {
            DestinationDto dDto = new DestinationDto(d.getId(), d.getName(), d.getSequenceOrder());
            dDto.setTripId(trip.getId());
            dDto.setLatitude(d.getLatitude());
            dDto.setLongitude(d.getLongitude());
            if (dDto.getLatitude() == null || dDto.getLongitude() == null || (dDto.getLatitude() == 0.0 && dDto.getLongitude() == 0.0)) {
                double[] destC = googleMapsService.geocode(d.getName());
                if (destC != null && destC.length >= 2) {
                    dDto.setLatitude(destC[0]);
                    dDto.setLongitude(destC[1]);
                } else {
                    dDto.setLatitude(12.9716);
                    dDto.setLongitude(77.5946);
                }
            }
            dDto.setDistanceFromPrevious(d.getDistanceFromPrevious());
            dDto.setTravelTimeFromPrevious(d.getTravelTimeFromPrevious());
            dDto.setHotelName(d.getHotelName());
            dDto.setHotelAddress(d.getHotelAddress());
            dDto.setHotelLat(d.getHotelLat());
            dDto.setHotelLng(d.getHotelLng());
            dDto.setCheckInDate(d.getCheckInDate());
            dDto.setCheckOutDate(d.getCheckOutDate());
            dDto.setCheckInTime(d.getCheckInTime());
            dDto.setCheckOutTime(d.getCheckOutTime());
            dDto.setRoomsCount(d.getRoomsCount());
            dDto.setGuestsCount(d.getGuestsCount());
            dDto.setBookingReference(d.getBookingReference());
            dDto.setHotelNotes(d.getHotelNotes());
            dDto.setVisited(d.getVisited());

            List<SightseeingPlaceDto> placeDtos = new ArrayList<>();
            if (d.getPlaces() != null) {
                for (Place p : d.getPlaces()) {
                    SightseeingPlaceDto pDto = new SightseeingPlaceDto();
                    pDto.setId(p.getId());
                    pDto.setDestinationId(d.getId());
                    pDto.setDestinationName(d.getName());
                    pDto.setName(p.getName());
                    pDto.setCategory(p.getCategory());
                    pDto.setRating(p.getRating());
                    pDto.setReviewCount(p.getReviewCount());
                    pDto.setLatitude(p.getLatitude());
                    pDto.setLongitude(p.getLongitude());
                    pDto.setDistanceKm(p.getDistanceKm());
                    pDto.setEstimatedVisitDurationMinutes(p.getEstimatedVisitDurationMinutes());
                    pDto.setEstimatedTravelTimeMinutes(p.getEstimatedTravelTimeMinutes());
                    pDto.setDescription(p.getDescription());
                    pDto.setOpeningHours(p.getOpeningHours());
                    pDto.setPriority(p.getPriority());
                    pDto.setVisited(p.getVisited());
                    pDto.setAddedToItinerary(p.getAddedToItinerary());

                    placeDtos.add(pDto);
                    totalSightseeing++;
                    if (Boolean.TRUE.equals(p.getVisited())) {
                        visitedSightseeing++;
                    }
                }
            }
            dDto.setPlaces(placeDtos);
            destDtos.add(dDto);
        }

        dto.setDestinations(destDtos);
        dto.setDestinationsCount(destDtos.size());
        dto.setSightseeingPlacesCount(totalSightseeing);
        dto.setVisitedPlacesCount(visitedSightseeing);
        dto.setCompletionPercentage(totalSightseeing > 0 ? (int) Math.round(((double) visitedSightseeing / totalSightseeing) * 100.0) : 0);

        dto.setRouteSummary(routeSummary);
        dto.setDayItineraries(dayItineraries);

        // Fetch Curated Recommendations for primary stop
        if (!destinations.isEmpty()) {
            List<SightseeingPlaceDto> pool = recommendationService.getRecommendedPlaces(
                    destinations.get(0).getName(),
                    trip.getPreferredInterests() != null ? Arrays.asList(trip.getPreferredInterests().split(",")) : null,
                    "popularity",
                    null
            );
            dto.setRecommendedNearbyPlaces(pool);
        }

        // Load Collaborators & Suggestions
        List<TripCollaborator> collabs = tripCollaboratorRepository.findByTripOrderByIdAsc(trip);
        dto.setCollaborators(collabs.stream()
                .map(c -> new TripCollaboratorDto(c.getId(), c.getUserName(), c.getUserEmail(), c.getRole(), c.getJoinedAt()))
                .collect(Collectors.toList()));

        List<TripSuggestion> suggestions = tripSuggestionRepository.findByTripOrderByVotesCountDescCreatedAtDesc(trip);
        dto.setSuggestions(suggestions.stream().map(this::mapToSuggestionDto).collect(Collectors.toList()));

        // Evaluate Global Trip Feasibility & Health Warnings
        List<String> warnings = new ArrayList<>();
        if (destDtos.size() > (daysCount * 3)) {
            dto.setFeasibilityStatus("Difficult");
            warnings.add("⚠️ Trip may be difficult to complete: You have " + destDtos.size() + " destinations planned across only " + daysCount + " days. Consider extending trip duration or spreading destinations.");
        } else if (routeSummary != null && routeSummary.getTotalTravelTimeMinutes() > (daysCount * 360)) {
            dto.setFeasibilityStatus("Busy");
            warnings.add("Notice: Average daily driving time exceeds 6 hours. Expect substantial road travel.");
        } else {
            dto.setFeasibilityStatus("Optimal");
        }

        if (routeSummary != null && Boolean.TRUE.equals(routeSummary.getOptimizationRecommended())) {
            warnings.add("💡 Smart Route Tip: Reordering your destinations can save approximately " + routeSummary.getPotentialSavingsKm() + " km and " + TimeUtils.formatDuration(routeSummary.getPotentialSavingsMinutes()) + "!");
        }

        dto.setWarnings(warnings);
        return dto;
    }

    private TripSuggestionDto mapToSuggestionDto(TripSuggestion s) {
        TripSuggestionDto dto = new TripSuggestionDto();
        dto.setId(s.getId());
        dto.setPlaceName(s.getPlaceName());
        dto.setCategory(s.getCategory());
        dto.setDestinationName(s.getDestinationName());
        dto.setNotes(s.getNotes());
        dto.setSuggestedByName(s.getSuggestedByName());
        dto.setVotesCount(s.getVotesCount());
        dto.setVoterNames(s.getVoterNames());
        dto.setStatus(s.getStatus());
        dto.setCreatedAt(s.getCreatedAt());
        return dto;
    }
}
