package com.example.tripplanner.service;

import com.example.tripplanner.dto.*;
import com.example.tripplanner.entity.*;
import com.example.tripplanner.exception.BadRequestException;
import com.example.tripplanner.exception.ResourceNotFoundException;
import com.example.tripplanner.repository.DestinationRepository;
import com.example.tripplanner.repository.ItineraryItemRepository;
import com.example.tripplanner.repository.PlaceRepository;
import com.example.tripplanner.repository.TripRepository;
import com.example.tripplanner.util.GeoUtils;
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

    public TripService(TripRepository tripRepository,
                       DestinationRepository destinationRepository,
                       PlaceRepository placeRepository,
                       ItineraryItemRepository itineraryItemRepository,
                       RouteService routeService,
                       ItineraryService itineraryService,
                       RecommendationService recommendationService,
                       GoogleMapsService googleMapsService) {
        this.tripRepository = tripRepository;
        this.destinationRepository = destinationRepository;
        this.placeRepository = placeRepository;
        this.itineraryItemRepository = itineraryItemRepository;
        this.routeService = routeService;
        this.itineraryService = itineraryService;
        this.recommendationService = recommendationService;
        this.googleMapsService = googleMapsService;
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
        Set<String> seen = new HashSet<>();
        for (String d : request.getDestinationNames()) {
            if (d != null && !d.trim().isEmpty()) {
                String trimmed = d.trim();
                if (!seen.contains(trimmed.toLowerCase())) {
                    cleanedDestinations.add(trimmed);
                    seen.add(trimmed.toLowerCase());
                }
            }
        }
        if (cleanedDestinations.isEmpty()) {
            throw new BadRequestException("Please enter at least one valid destination.");
        }

        // Initialize Trip Entity
        Trip trip = new Trip();
        trip.setUser(user);
        String tripTitle = (request.getTripName() != null && !request.getTripName().trim().isEmpty())
                ? request.getTripName().trim()
                : (request.getStartLocation() + " to " + String.join(" & ", cleanedDestinations));
        trip.setTripName(tripTitle);
        trip.setStartLocation(request.getStartLocation().trim());

        double[] startCoords = googleMapsService.geocode(trip.getStartLocation());
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

        Trip savedTrip = tripRepository.save(trip);

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

            double[] coords = googleMapsService.geocode(destName);
            destination.setLatitude(coords[0]);
            destination.setLongitude(coords[1]);

            // Match segment stats if available
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

    @Transactional(readOnly = true)
    public TripResponseDto getTripDtoById(Long tripId, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found with ID: " + tripId));

        List<Destination> destinations = destinationRepository.findByTripOrderBySequenceOrderAsc(trip);
        trip.setDestinations(destinations);

        List<String> destNames = destinations.stream().map(Destination::getName).collect(Collectors.toList());
        RouteSummaryDto routeSummary = routeService.calculateCompleteRoute(trip.getStartLocation(), destNames, trip.getTransportMode());

        List<DayItineraryDto> dayItineraries = itineraryService.generateItineraryPlan(trip, destinations, trip.getTransportMode());

        return buildTripResponseDto(trip, routeSummary, dayItineraries);
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

            List<Destination> dests = destinationRepository.findByTripOrderBySequenceOrderAsc(t);
            dto.setDestinationsCount(dests.size());

            int totalSpots = 0;
            int visitedSpots = 0;
            for (Destination d : dests) {
                if (d.getVisited() != null && d.getVisited()) visitedSpots++;
                for (Place p : d.getPlaces()) {
                    totalSpots++;
                    if (p.getVisited() != null && p.getVisited()) visitedSpots++;
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
        Trip trip = tripRepository.findByIdAndUser(tripId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found or unauthorized"));
        tripRepository.delete(trip);
    }

    @Transactional
    public TripResponseDto applyOptimizedRoute(Long tripId, User user) {
        Trip trip = tripRepository.findByIdAndUser(tripId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found or unauthorized"));

        List<Destination> destinations = destinationRepository.findByTripOrderBySequenceOrderAsc(trip);
        List<String> destNames = destinations.stream().map(Destination::getName).collect(Collectors.toList());

        RouteSummaryDto routeSummary = routeService.calculateCompleteRoute(trip.getStartLocation(), destNames, trip.getTransportMode());

        if (routeSummary.getRecommendedOrder() != null && routeSummary.getRecommendedOrder().size() > 1) {
            // Reorder destinations to match recommended order
            List<String> newOrder = routeSummary.getRecommendedOrder().subList(1, routeSummary.getRecommendedOrder().size());
            Map<String, Destination> destMap = destinations.stream()
                    .collect(Collectors.toMap(d -> d.getName().toLowerCase(), d -> d, (a, b) -> a));

            List<Destination> reordered = new ArrayList<>();
            for (int i = 0; i < newOrder.size(); i++) {
                String name = newOrder.get(i);
                Destination d = destMap.get(name.toLowerCase());
                if (d != null) {
                    d.setSequenceOrder(i);
                    reordered.add(destinationRepository.save(d));
                }
            }

            trip.setDestinations(reordered);
            // Recalculate route with new order
            RouteSummaryDto newRouteSummary = routeService.calculateCompleteRoute(trip.getStartLocation(), newOrder, trip.getTransportMode());
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
            return buildTripResponseDto(trip, newRouteSummary, days);
        }

        return getTripDtoById(tripId, user);
    }

    @Transactional
    public TripResponseDto addSightseeingPlaceToTrip(Long tripId, Long destinationId, SightseeingPlaceDto placeDto, User user) {
        Trip trip = tripRepository.findByIdAndUser(tripId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found or unauthorized"));

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
    public Map<String, Object> togglePlaceVisited(Long placeId, boolean visited) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new ResourceNotFoundException("Place not found with ID: " + placeId));
        place.setVisited(visited);
        placeRepository.save(place);

        Trip trip = place.getDestination().getTrip();
        return computeProgressMap(trip);
    }

    @Transactional
    public Map<String, Object> toggleDestinationVisited(Long destinationId, boolean visited) {
        Destination destination = destinationRepository.findById(destinationId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found with ID: " + destinationId));
        destination.setVisited(visited);
        destinationRepository.save(destination);

        Trip trip = destination.getTrip();
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
        TripResponseDto dto = new TripResponseDto();
        dto.setId(trip.getId());
        dto.setTripName(trip.getTripName());
        dto.setStartLocation(trip.getStartLocation());
        dto.setStartLat(trip.getStartLat());
        dto.setStartLng(trip.getStartLng());
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

        dto.setTotalDistanceKm(routeSummary.getTotalDistanceKm());
        dto.setTotalTravelTimeMinutes(routeSummary.getTotalTravelTimeMinutes());
        dto.setFormattedTotalTravelTime(TimeUtils.formatDuration(routeSummary.getTotalTravelTimeMinutes()));

        // Map Destinations
        List<DestinationDto> destDtos = new ArrayList<>();
        int totalSightseeing = 0;
        int visitedSightseeing = 0;

        List<Destination> destinations = trip.getDestinations() != null ? trip.getDestinations() : Collections.emptyList();
        for (Destination d : destinations) {
            DestinationDto dDto = new DestinationDto(d.getId(), d.getName(), d.getSequenceOrder());
            dDto.setTripId(trip.getId());
            dDto.setLatitude(d.getLatitude());
            dDto.setLongitude(d.getLongitude());
            dDto.setDistanceFromPrevious(d.getDistanceFromPrevious());
            dDto.setTravelTimeFromPrevious(d.getTravelTimeFromPrevious());
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

        // Fetch curated recommended places for first destination (or each) for immediate recommendation deck
        if (!destinations.isEmpty()) {
            List<SightseeingPlaceDto> pool = recommendationService.getRecommendedPlaces(
                    destinations.get(0).getName(),
                    trip.getPreferredInterests() != null ? Arrays.asList(trip.getPreferredInterests().split(",")) : null,
                    "popularity",
                    null
            );
            dto.setRecommendedNearbyPlaces(pool);
        }

        // Evaluate Global Trip Feasibility & Health Warnings
        List<String> warnings = new ArrayList<>();
        if (destDtos.size() > (daysCount * 3)) {
            dto.setFeasibilityStatus("Difficult");
            warnings.add("⚠️ Trip may be difficult to complete: You have " + destDtos.size() + " destinations planned across only " + daysCount + " days. Consider extending trip duration or removing lower priority destinations.");
        } else if (routeSummary.getTotalTravelTimeMinutes() > (daysCount * 360)) {
            dto.setFeasibilityStatus("Busy");
            warnings.add("Notice: Average daily driving time exceeds 6 hours. Expect substantial road travel.");
        } else {
            dto.setFeasibilityStatus("Optimal");
        }

        if (routeSummary.getOptimizationRecommended()) {
            warnings.add("💡 Smart Route Tip: Reordering your destinations can save approximately " + routeSummary.getPotentialSavingsKm() + " km and " + TimeUtils.formatDuration(routeSummary.getPotentialSavingsMinutes()) + "!");
        }

        dto.setWarnings(warnings);

        return dto;
    }
}
