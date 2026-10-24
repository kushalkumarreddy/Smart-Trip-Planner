package com.example.tripplanner.service;

import com.example.tripplanner.dto.*;
import com.example.tripplanner.entity.*;
import com.example.tripplanner.exception.BadRequestException;
import com.example.tripplanner.exception.ResourceNotFoundException;
import com.example.tripplanner.repository.DestinationRepository;
import com.example.tripplanner.repository.ItineraryItemRepository;
import com.example.tripplanner.repository.TripRepository;
import com.example.tripplanner.repository.TripVersionRepository;
import com.example.tripplanner.util.TimeUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TripVersionService {

    private static final Logger logger = LoggerFactory.getLogger(TripVersionService.class);
    private static final int MAX_VERSIONS_PER_TRIP = 30;
    private static final DateTimeFormatter DISPLAY_DT_FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy • hh:mm a");

    private final TripRepository tripRepository;
    private final DestinationRepository destinationRepository;
    private final ItineraryItemRepository itineraryItemRepository;
    private final TripVersionRepository tripVersionRepository;
    private final RouteService routeService;
    private final ItineraryService itineraryService;
    private final TripService tripService;
    private final ObjectMapper objectMapper;

    public TripVersionService(TripRepository tripRepository,
                              DestinationRepository destinationRepository,
                              ItineraryItemRepository itineraryItemRepository,
                              TripVersionRepository tripVersionRepository,
                              RouteService routeService,
                              ItineraryService itineraryService,
                              @Lazy TripService tripService) {
        this.tripRepository = tripRepository;
        this.destinationRepository = destinationRepository;
        this.itineraryItemRepository = itineraryItemRepository;
        this.tripVersionRepository = tripVersionRepository;
        this.routeService = routeService;
        this.itineraryService = itineraryService;
        this.tripService = tripService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Creates and persists a consistent trip version snapshot.
     */
    @Transactional
    public TripVersion createSnapshot(Trip trip, String description, String source, User user) {
        if (trip == null || trip.getId() == null) {
            return null;
        }

        try {
            // Build the complete snapshot DTO
            TripSnapshotDto snapshot = buildSnapshotDto(trip);

            int nextVersionNum = tripVersionRepository.findMaxVersionNumberByTripId(trip.getId()) + 1;
            snapshot.setVersionNumber(nextVersionNum);

            String json = objectMapper.writeValueAsString(snapshot);

            String creatorName = (user != null && user.getName() != null) ? user.getName() : "System / Trip Planner";
            String creatorEmail = (user != null) ? user.getEmail() : null;

            TripVersion version = new TripVersion(
                    trip,
                    nextVersionNum,
                    (description != null && !description.trim().isEmpty()) ? description.trim() : "Trip state update",
                    (source != null && !source.trim().isEmpty()) ? source.trim() : "USER_ACTION",
                    creatorName,
                    creatorEmail,
                    json
            );

            TripVersion saved = tripVersionRepository.save(version);
            logger.info("Created trip version v{} for trip id {}", nextVersionNum, trip.getId());

            // Enforce retention policy
            enforceRetentionPolicy(trip);

            return saved;
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize trip snapshot for trip id {}: {}", trip.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Lists all saved versions for a trip, sorted newest first.
     */
    @Transactional(readOnly = true)
    public List<TripVersionSummaryDto> getTripVersions(Long tripId, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        tripService.checkTripAccess(trip, user, "VIEWER");

        List<TripVersion> versions = tripVersionRepository.findByTripOrderByVersionNumberDesc(trip);
        Integer maxVersionNum = tripVersionRepository.findMaxVersionNumberByTripId(tripId);

        List<TripVersionSummaryDto> dtos = new ArrayList<>();
        for (TripVersion v : versions) {
            TripVersionSummaryDto dto = new TripVersionSummaryDto();
            dto.setId(v.getId());
            dto.setTripId(tripId);
            dto.setVersionNumber(v.getVersionNumber());
            dto.setChangeDescription(v.getChangeDescription());
            dto.setChangeSource(v.getChangeSource());
            dto.setCreatedByName(v.getCreatedByName());
            dto.setCreatedByEmail(v.getCreatedByEmail());
            dto.setCreatedAt(v.getCreatedAt());
            dto.setFormattedCreatedAt(v.getCreatedAt() != null ? v.getCreatedAt().format(DISPLAY_DT_FMT) : "");
            dto.setIsCurrent(Objects.equals(v.getVersionNumber(), maxVersionNum));

            try {
                TripSnapshotDto snap = objectMapper.readValue(v.getSnapshotData(), TripSnapshotDto.class);
                dto.setDestinationsCount(snap.getDestinations() != null ? snap.getDestinations().size() : 0);
                dto.setTotalDistanceKm(snap.getTotalDistance() != null ? snap.getTotalDistance() : 0.0);
                dto.setTotalTravelTimeMinutes(snap.getTotalTravelTimeMinutes() != null ? snap.getTotalTravelTimeMinutes() : 0);
                dto.setFormattedTravelTime(TimeUtils.formatDuration(dto.getTotalTravelTimeMinutes()));

                if (snap.getDestinations() != null && !snap.getDestinations().isEmpty()) {
                    List<String> destNames = snap.getDestinations().stream()
                            .map(TripSnapshotDto.DestinationSnapshotDto::getName)
                            .collect(Collectors.toList());
                    dto.setDestinationsSummary(snap.getStartLocation() + " → " + String.join(" → ", destNames));
                } else {
                    dto.setDestinationsSummary(snap.getStartLocation());
                }
            } catch (Exception e) {
                dto.setDestinationsSummary("Snapshot details unavailable");
            }

            dtos.add(dto);
        }

        return dtos;
    }

    /**
     * Gets a single version's details and full snapshot.
     */
    @Transactional(readOnly = true)
    public TripVersionDetailDto getVersionDetail(Long tripId, Long versionId, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        tripService.checkTripAccess(trip, user, "VIEWER");

        TripVersion version = tripVersionRepository.findByIdAndTripId(versionId, tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Version not found or does not belong to this trip"));

        TripVersionDetailDto dto = new TripVersionDetailDto();
        dto.setId(version.getId());
        dto.setTripId(tripId);
        dto.setVersionNumber(version.getVersionNumber());
        dto.setChangeDescription(version.getChangeDescription());
        dto.setChangeSource(version.getChangeSource());
        dto.setCreatedByName(version.getCreatedByName());
        dto.setCreatedByEmail(version.getCreatedByEmail());
        dto.setCreatedAt(version.getCreatedAt());
        dto.setFormattedCreatedAt(version.getCreatedAt() != null ? version.getCreatedAt().format(DISPLAY_DT_FMT) : "");

        try {
            TripSnapshotDto snap = objectMapper.readValue(version.getSnapshotData(), TripSnapshotDto.class);
            dto.setSnapshot(snap);
        } catch (Exception e) {
            throw new BadRequestException("Could not deserialize version snapshot data: " + e.getMessage());
        }

        return dto;
    }

    /**
     * Compares two saved versions of a trip and produces a structured, readable diff.
     */
    @Transactional(readOnly = true)
    public TripVersionComparisonDto compareVersions(Long tripId, Long v1Id, Long v2Id, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        tripService.checkTripAccess(trip, user, "VIEWER");

        TripVersion verA = tripVersionRepository.findByIdAndTripId(v1Id, tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Version " + v1Id + " does not belong to this trip"));
        TripVersion verB = tripVersionRepository.findByIdAndTripId(v2Id, tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Version " + v2Id + " does not belong to this trip"));

        TripSnapshotDto snapA;
        TripSnapshotDto snapB;
        try {
            snapA = objectMapper.readValue(verA.getSnapshotData(), TripSnapshotDto.class);
            snapB = objectMapper.readValue(verB.getSnapshotData(), TripSnapshotDto.class);
        } catch (Exception e) {
            throw new BadRequestException("Failed to deserialize version snapshots for comparison: " + e.getMessage());
        }

        TripVersionComparisonDto comparison = new TripVersionComparisonDto();
        comparison.setTripId(tripId);

        // Summaries
        TripVersionSummaryDto summaryA = createVersionSummary(verA, snapA);
        TripVersionSummaryDto summaryB = createVersionSummary(verB, snapB);
        comparison.setVersionA(summaryA);
        comparison.setVersionB(summaryB);

        // 1. Destination Changes
        List<String> destA = snapA.getDestinations().stream()
                .map(TripSnapshotDto.DestinationSnapshotDto::getName)
                .collect(Collectors.toList());
        List<String> destB = snapB.getDestinations().stream()
                .map(TripSnapshotDto.DestinationSnapshotDto::getName)
                .collect(Collectors.toList());

        for (String d : destB) {
            if (!destA.contains(d)) {
                comparison.getDestinationChanges().add("Added stop: " + d);
            }
        }
        for (String d : destA) {
            if (!destB.contains(d)) {
                comparison.getDestinationChanges().add("Removed stop: " + d);
            }
        }
        if (destA.equals(destB)) {
            // Sequence order is identical
        } else if (new HashSet<>(destA).equals(new HashSet<>(destB))) {
            comparison.getDestinationChanges().add("Destination sequence reordered: " +
                    String.join(" → ", destA) + " ➔ " + String.join(" → ", destB));
        }

        // 2. Stay Duration Changes
        Map<String, TripSnapshotDto.DestinationSnapshotDto> mapA = snapA.getDestinations().stream()
                .collect(Collectors.toMap(d -> d.getName().toLowerCase().trim(), d -> d, (x, y) -> x));
        for (TripSnapshotDto.DestinationSnapshotDto bDest : snapB.getDestinations()) {
            TripSnapshotDto.DestinationSnapshotDto aDest = mapA.get(bDest.getName().toLowerCase().trim());
            if (aDest != null) {
                int daysA = aDest.getStayDurationDays() != null ? aDest.getStayDurationDays() : 1;
                int daysB = bDest.getStayDurationDays() != null ? bDest.getStayDurationDays() : 1;
                int nightsA = aDest.getStayDurationNights() != null ? aDest.getStayDurationNights() : 0;
                int nightsB = bDest.getStayDurationNights() != null ? bDest.getStayDurationNights() : 0;

                if (daysA != daysB || nightsA != nightsB) {
                    comparison.getStayDurationChanges().add(String.format("%s stay changed: %dD/%dN ➔ %dD/%dN",
                            bDest.getName(), daysA, nightsA, daysB, nightsB));
                }

                // Accommodation changes
                String hA = aDest.getHotelName() != null ? aDest.getHotelName() : "None";
                String hB = bDest.getHotelName() != null ? bDest.getHotelName() : "None";
                if (!hA.equalsIgnoreCase(hB)) {
                    comparison.getAccommodationChanges().add(String.format("%s hotel changed: '%s' ➔ '%s'",
                            bDest.getName(), hA, hB));
                }
            }
        }

        // 3. Travel Date Changes
        if (!Objects.equals(snapA.getStartDate(), snapB.getStartDate()) ||
                !Objects.equals(snapA.getEndDate(), snapB.getEndDate())) {
            comparison.getTravelDateChanges().add(String.format("Trip dates changed: %s to %s ➔ %s to %s",
                    snapA.getStartDate(), snapA.getEndDate(), snapB.getStartDate(), snapB.getEndDate()));
        }

        // 4. Route Preferences
        if (!Objects.equals(snapA.getTransportMode(), snapB.getTransportMode())) {
            comparison.getRoutePreferenceChanges().add(String.format("Transport mode changed: %s ➔ %s",
                    snapA.getTransportMode(), snapB.getTransportMode()));
        }
        if (!Objects.equals(snapA.getTravelStyle(), snapB.getTravelStyle())) {
            comparison.getRoutePreferenceChanges().add(String.format("Travel pace changed: %s ➔ %s",
                    snapA.getTravelStyle(), snapB.getTravelStyle()));
        }

        // 5. Activity Count & Content Changes
        int actCountA = snapA.getItineraries() != null ? snapA.getItineraries().size() : 0;
        int actCountB = snapB.getItineraries() != null ? snapB.getItineraries().size() : 0;
        if (actCountA != actCountB) {
            comparison.getActivityChanges().add(String.format("Total scheduled activities changed: %d ➔ %d (%+d activities)",
                    actCountA, actCountB, actCountB - actCountA));
        }

        // Distance & Time difference
        double distA = snapA.getTotalDistance() != null ? snapA.getTotalDistance() : 0.0;
        double distB = snapB.getTotalDistance() != null ? snapB.getTotalDistance() : 0.0;
        comparison.setDistanceDiffKm(Math.round((distB - distA) * 10.0) / 10.0);

        int timeA = snapA.getTotalTravelTimeMinutes() != null ? snapA.getTotalTravelTimeMinutes() : 0;
        int timeB = snapB.getTotalTravelTimeMinutes() != null ? snapB.getTotalTravelTimeMinutes() : 0;
        comparison.setTravelTimeDiffMinutes(timeB - timeA);

        boolean isIdentical = comparison.getDestinationChanges().isEmpty() &&
                comparison.getStayDurationChanges().isEmpty() &&
                comparison.getTravelDateChanges().isEmpty() &&
                comparison.getAccommodationChanges().isEmpty() &&
                comparison.getRoutePreferenceChanges().isEmpty() &&
                comparison.getActivityChanges().isEmpty();

        comparison.setIdentical(isIdentical);

        StringBuilder sb = new StringBuilder();
        if (isIdentical) {
            sb.append("Both versions have identical itineraries and destinations.");
        } else {
            sb.append(String.format("Version %d vs Version %d: ", verA.getVersionNumber(), verB.getVersionNumber()));
            if (comparison.getDistanceDiffKm() != 0) {
                sb.append(String.format("%+.1f km road distance, ", comparison.getDistanceDiffKm()));
            }
            if (comparison.getTravelTimeDiffMinutes() != 0) {
                sb.append(String.format("%+d min driving time. ", comparison.getTravelTimeDiffMinutes()));
            }
        }
        comparison.setSummaryText(sb.toString().trim());

        return comparison;
    }

    /**
     * Safely restores an older saved trip snapshot.
     * Preserves current state as a new version before restoring,
     * restores destinations and itineraries, and recalculates routes.
     */
    @Transactional
    public TripResponseDto restoreVersion(Long tripId, Long versionId, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        // Require EDITOR or OWNER permission
        tripService.checkTripAccess(trip, user, "EDITOR");

        TripVersion versionToRestore = tripVersionRepository.findByIdAndTripId(versionId, tripId)
                .orElseThrow(() -> new BadRequestException("Target version does not exist or does not belong to this trip."));

        TripSnapshotDto targetSnapshot;
        try {
            targetSnapshot = objectMapper.readValue(versionToRestore.getSnapshotData(), TripSnapshotDto.class);
        } catch (Exception e) {
            throw new BadRequestException("Corrupt version snapshot: cannot deserialize data: " + e.getMessage());
        }

        if (targetSnapshot.getStartLocation() == null || targetSnapshot.getDestinations() == null) {
            throw new BadRequestException("Invalid snapshot data: missing starting location or destinations.");
        }

        // 1. Preserve current trip state as a new version before restoring (Safe recovery)
        createSnapshot(trip,
                "Automatic backup before restoring Version " + versionToRestore.getVersionNumber(),
                "PRE_RESTORE_BACKUP",
                user);

        // 2. Restore Trip entity core fields
        trip.setStartLocation(targetSnapshot.getStartLocation());
        trip.setStartLat(targetSnapshot.getStartLat());
        trip.setStartLng(targetSnapshot.getStartLng());
        trip.setStartDate(targetSnapshot.getStartDate());
        trip.setEndDate(targetSnapshot.getEndDate());
        trip.setTransportMode(targetSnapshot.getTransportMode() != null ? targetSnapshot.getTransportMode() : "CAR");
        trip.setTravelStyle(targetSnapshot.getTravelStyle() != null ? targetSnapshot.getTravelStyle() : "BALANCED");
        trip.setTravelersCount(targetSnapshot.getTravelersCount() != null ? targetSnapshot.getTravelersCount() : 1);
        trip.setDailyStartTime(targetSnapshot.getDailyStartTime());
        trip.setDailyEndTime(targetSnapshot.getDailyEndTime());
        trip.setHotelLocation(targetSnapshot.getHotelLocation());
        trip.setHotelLat(targetSnapshot.getHotelLat());
        trip.setHotelLng(targetSnapshot.getHotelLng());
        trip.setBudgetRange(targetSnapshot.getBudgetRange());
        trip.setPreferredInterests(targetSnapshot.getPreferredInterests());

        // 3. Delete existing destinations and recreate from snapshot
        destinationRepository.deleteByTripId(trip.getId());
        List<Destination> restoredDestinations = new ArrayList<>();

        int seq = 0;
        for (TripSnapshotDto.DestinationSnapshotDto dSnap : targetSnapshot.getDestinations()) {
            Destination d = new Destination();
            d.setTrip(trip);
            d.setName(dSnap.getName());
            d.setLatitude(dSnap.getLatitude());
            d.setLongitude(dSnap.getLongitude());
            d.setSequenceOrder(seq++);
            d.setDistanceFromPrevious(dSnap.getDistanceFromPrevious());
            d.setTravelTimeFromPrevious(dSnap.getTravelTimeFromPrevious());
            d.setVisited(dSnap.getVisited());
            d.setNotes(dSnap.getNotes());

            // Hotel info
            d.setHotelName(dSnap.getHotelName());
            d.setHotelAddress(dSnap.getHotelAddress());
            d.setHotelLat(dSnap.getHotelLat());
            d.setHotelLng(dSnap.getHotelLng());
            d.setCheckInDate(dSnap.getCheckInDate());
            d.setCheckOutDate(dSnap.getCheckOutDate());
            d.setCheckInTime(dSnap.getCheckInTime());
            d.setCheckOutTime(dSnap.getCheckOutTime());
            d.setRoomsCount(dSnap.getRoomsCount());
            d.setGuestsCount(dSnap.getGuestsCount());
            d.setBookingReference(dSnap.getBookingReference());
            d.setHotelNotes(dSnap.getHotelNotes());

            // Stay duration
            d.setStayDurationDays(dSnap.getStayDurationDays());
            d.setStayDurationNights(dSnap.getStayDurationNights());
            d.setArrivalDate(dSnap.getArrivalDate());
            d.setDepartureDate(dSnap.getDepartureDate());
            d.setArrivalTime(dSnap.getArrivalTime());
            d.setDepartureTime(dSnap.getDepartureTime());

            // Places
            if (dSnap.getPlaces() != null) {
                for (TripSnapshotDto.PlaceSnapshotDto pSnap : dSnap.getPlaces()) {
                    Place p = new Place();
                    p.setDestination(d);
                    p.setName(pSnap.getName());
                    p.setCategory(pSnap.getCategory());
                    p.setRating(pSnap.getRating());
                    p.setReviewCount(pSnap.getReviewCount());
                    p.setLatitude(pSnap.getLatitude());
                    p.setLongitude(pSnap.getLongitude());
                    p.setDistanceKm(pSnap.getDistanceKm());
                    p.setEstimatedVisitDurationMinutes(pSnap.getEstimatedVisitDurationMinutes());
                    p.setEstimatedTravelTimeMinutes(pSnap.getEstimatedTravelTimeMinutes());
                    p.setDescription(pSnap.getDescription());
                    p.setOpeningHours(pSnap.getOpeningHours());
                    p.setPhotoUrl(pSnap.getPhotoUrl());
                    p.setGooglePlaceId(pSnap.getGooglePlaceId());
                    p.setPriority(pSnap.getPriority());
                    p.setVisited(pSnap.getVisited());
                    p.setAddedToItinerary(pSnap.getAddedToItinerary());
                    d.getPlaces().add(p);
                }
            }

            destinationRepository.save(d);
            restoredDestinations.add(d);
        }

        // 4. Delete existing itineraries and recreate from snapshot
        itineraryItemRepository.deleteByTripId(trip.getId());
        if (targetSnapshot.getItineraries() != null && !targetSnapshot.getItineraries().isEmpty()) {
            for (TripSnapshotDto.ItinerarySnapshotDto iSnap : targetSnapshot.getItineraries()) {
                ItineraryItem item = new ItineraryItem();
                item.setTrip(trip);
                item.setDayNumber(iSnap.getDayNumber());
                item.setScheduleDate(iSnap.getScheduleDate());
                item.setStartTime(iSnap.getStartTime());
                item.setEndTime(iSnap.getEndTime());
                item.setActivity(iSnap.getActivity());
                item.setLocation(iSnap.getLocation());
                item.setActivityType(iSnap.getActivityType());
                item.setTravelDistanceKm(iSnap.getTravelDistanceKm());
                item.setTravelTimeMinutes(iSnap.getTravelTimeMinutes());
                item.setDurationMinutes(iSnap.getDurationMinutes());
                item.setRating(iSnap.getRating());
                item.setVisited(iSnap.getVisited());
                item.setLocked(iSnap.getLocked());
                item.setNotes(iSnap.getNotes());
                itineraryItemRepository.save(item);
            }
        } else {
            // Re-generate if snapshot had empty itinerary
            List<DayItineraryDto> days = itineraryService.generateItineraryPlan(trip, restoredDestinations, trip.getTransportMode());
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
        }

        // 5. Recalculate route metrics with live routing provider
        List<String> destNames = restoredDestinations.stream().map(Destination::getName).collect(Collectors.toList());
        RouteSummaryDto freshRoute = routeService.calculateCompleteRoute(trip.getStartLocation(), destNames, trip.getTransportMode());

        trip.setTotalDistance(freshRoute.getTotalDistanceKm());
        trip.setTotalTravelTimeMinutes(freshRoute.getTotalTravelTimeMinutes());
        tripRepository.save(trip);

        logger.info("Successfully restored trip id {} to version {}", trip.getId(), versionToRestore.getVersionNumber());

        // 6. Return fresh updated DTO
        return tripService.getTripDtoById(trip.getId(), user);
    }

    private void enforceRetentionPolicy(Trip trip) {
        long count = tripVersionRepository.countByTrip(trip);
        if (count > MAX_VERSIONS_PER_TRIP) {
            List<TripVersion> oldestFirst = tripVersionRepository.findByTripOrderByVersionNumberAsc(trip);
            int toDelete = (int) (count - MAX_VERSIONS_PER_TRIP);
            List<TripVersion> pruned = new ArrayList<>();
            for (int i = 0; i < toDelete && i < oldestFirst.size(); i++) {
                pruned.add(oldestFirst.get(i));
            }
            if (!pruned.isEmpty()) {
                tripVersionRepository.deleteAll(pruned);
            }
            logger.info("Pruned {} old versions for trip id {} under retention policy (max {})", toDelete, trip.getId(), MAX_VERSIONS_PER_TRIP);
        }
    }

    private TripSnapshotDto buildSnapshotDto(Trip trip) {
        TripSnapshotDto dto = new TripSnapshotDto();
        dto.setTripId(trip.getId());
        dto.setTripName(trip.getTripName());
        dto.setStartLocation(trip.getStartLocation());
        dto.setStartLat(trip.getStartLat());
        dto.setStartLng(trip.getStartLng());
        dto.setStartDate(trip.getStartDate());
        dto.setEndDate(trip.getEndDate());
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
        dto.setTotalDistance(trip.getTotalDistance());
        dto.setTotalTravelTimeMinutes(trip.getTotalTravelTimeMinutes());
        dto.setSnapshotTimestamp(java.time.LocalDateTime.now().toString());

        // Destinations & Places
        List<Destination> destinations = destinationRepository.findByTripOrderBySequenceOrderAsc(trip);
        if ((destinations == null || destinations.isEmpty()) && trip.getDestinations() != null) {
            destinations = trip.getDestinations();
        }
        for (Destination d : destinations) {
            TripSnapshotDto.DestinationSnapshotDto dDto = new TripSnapshotDto.DestinationSnapshotDto();
            dDto.setName(d.getName());
            dDto.setLatitude(d.getLatitude());
            dDto.setLongitude(d.getLongitude());
            dDto.setSequenceOrder(d.getSequenceOrder());
            dDto.setDistanceFromPrevious(d.getDistanceFromPrevious());
            dDto.setTravelTimeFromPrevious(d.getTravelTimeFromPrevious());
            dDto.setVisited(d.getVisited());
            dDto.setNotes(d.getNotes());

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

            dDto.setStayDurationDays(d.getStayDurationDays());
            dDto.setStayDurationNights(d.getStayDurationNights());
            dDto.setArrivalDate(d.getArrivalDate());
            dDto.setDepartureDate(d.getDepartureDate());
            dDto.setArrivalTime(d.getArrivalTime());
            dDto.setDepartureTime(d.getDepartureTime());

            if (d.getPlaces() != null) {
                for (Place p : d.getPlaces()) {
                    TripSnapshotDto.PlaceSnapshotDto pDto = new TripSnapshotDto.PlaceSnapshotDto();
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
                    pDto.setPhotoUrl(p.getPhotoUrl());
                    pDto.setGooglePlaceId(p.getGooglePlaceId());
                    pDto.setPriority(p.getPriority());
                    pDto.setVisited(p.getVisited());
                    pDto.setAddedToItinerary(p.getAddedToItinerary());
                    dDto.getPlaces().add(pDto);
                }
            }
            dto.getDestinations().add(dDto);
        }

        // Itineraries
        List<ItineraryItem> items = itineraryItemRepository.findByTripIdOrderByDayNumberAscStartTimeAsc(trip.getId());
        for (ItineraryItem it : items) {
            TripSnapshotDto.ItinerarySnapshotDto iDto = new TripSnapshotDto.ItinerarySnapshotDto();
            iDto.setDayNumber(it.getDayNumber());
            iDto.setScheduleDate(it.getScheduleDate());
            iDto.setStartTime(it.getStartTime());
            iDto.setEndTime(it.getEndTime());
            iDto.setActivity(it.getActivity());
            iDto.setLocation(it.getLocation());
            iDto.setActivityType(it.getActivityType());
            iDto.setTravelDistanceKm(it.getTravelDistanceKm());
            iDto.setTravelTimeMinutes(it.getTravelTimeMinutes());
            iDto.setDurationMinutes(it.getDurationMinutes());
            iDto.setRating(it.getRating());
            iDto.setVisited(it.getVisited());
            iDto.setLocked(it.getLocked());
            iDto.setNotes(it.getNotes());
            if (it.getPlace() != null) {
                iDto.setPlaceName(it.getPlace().getName());
            }
            dto.getItineraries().add(iDto);
        }

        return dto;
    }

    private TripVersionSummaryDto createVersionSummary(TripVersion ver, TripSnapshotDto snap) {
        TripVersionSummaryDto summary = new TripVersionSummaryDto();
        summary.setId(ver.getId());
        summary.setTripId(ver.getTrip().getId());
        summary.setVersionNumber(ver.getVersionNumber());
        summary.setChangeDescription(ver.getChangeDescription());
        summary.setChangeSource(ver.getChangeSource());
        summary.setCreatedByName(ver.getCreatedByName());
        summary.setCreatedByEmail(ver.getCreatedByEmail());
        summary.setCreatedAt(ver.getCreatedAt());
        summary.setFormattedCreatedAt(ver.getCreatedAt() != null ? ver.getCreatedAt().format(DISPLAY_DT_FMT) : "");
        summary.setDestinationsCount(snap.getDestinations() != null ? snap.getDestinations().size() : 0);
        summary.setTotalDistanceKm(snap.getTotalDistance() != null ? snap.getTotalDistance() : 0.0);
        summary.setTotalTravelTimeMinutes(snap.getTotalTravelTimeMinutes() != null ? snap.getTotalTravelTimeMinutes() : 0);
        summary.setFormattedTravelTime(TimeUtils.formatDuration(summary.getTotalTravelTimeMinutes()));

        if (snap.getDestinations() != null && !snap.getDestinations().isEmpty()) {
            List<String> names = snap.getDestinations().stream()
                    .map(TripSnapshotDto.DestinationSnapshotDto::getName)
                    .collect(Collectors.toList());
            summary.setDestinationsSummary(snap.getStartLocation() + " → " + String.join(" → ", names));
        } else {
            summary.setDestinationsSummary(snap.getStartLocation());
        }
        return summary;
    }
}
