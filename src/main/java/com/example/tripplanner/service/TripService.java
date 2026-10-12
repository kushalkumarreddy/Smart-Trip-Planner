package com.example.tripplanner.service;

import com.example.tripplanner.dto.*;
import com.example.tripplanner.entity.*;
import com.example.tripplanner.exception.BadRequestException;
import com.example.tripplanner.exception.ResourceNotFoundException;
import com.example.tripplanner.repository.*;
import com.example.tripplanner.util.TimeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

            // Seed top sightseeing recommendations into places table
            List<SightseeingPlaceDto> recommended = recommendationService.getRecommendedPlaces(
                    destName,
                    request.getPreferredInterests(),
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
            return;
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

        List<DayItineraryDto> dayItineraries = itineraryService.generateItineraryPlan(trip, destinations, trip.getTransportMode());

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
     * Regenerates a single day's schedule without rebuilding the entire trip (Requirement 4).
     */
    @Transactional
    public TripResponseDto regenerateSingleDay(Long tripId, Integer dayNumber, String preference, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        checkTripAccess(trip, user, "EDITOR");

        List<Destination> destinations = destinationRepository.findByTripOrderBySequenceOrderAsc(trip);

        // Collect names of places scheduled in OTHER days to avoid duplicates
        List<ItineraryItem> allItems = itineraryItemRepository.findByTripIdOrderByDayNumberAscStartTimeAsc(trip.getId());
        Set<String> alreadyUsed = new HashSet<>();
        for (ItineraryItem it : allItems) {
            if (!it.getDayNumber().equals(dayNumber) && it.getLocation() != null) {
                alreadyUsed.add(it.getLocation().toLowerCase());
            }
        }

        // Generate refreshed day
        DayItineraryDto refreshedDay = itineraryService.regenerateSingleDay(
                trip, destinations, dayNumber, preference, trip.getTransportMode(), alreadyUsed
        );

        // Delete old items for that day only
        itineraryItemRepository.deleteByTripIdAndDayNumber(trip.getId(), dayNumber);

        // Save new items
        for (ItineraryItemDto itemDto : refreshedDay.getItems()) {
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
            dDto.setVisited(d.getVisited());
            dDto.setHotelName(d.getHotelName());

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
