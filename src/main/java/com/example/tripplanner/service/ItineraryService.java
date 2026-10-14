package com.example.tripplanner.service;

import com.example.tripplanner.dto.DayItineraryDto;
import com.example.tripplanner.dto.ItineraryItemDto;
import com.example.tripplanner.dto.SightseeingPlaceDto;
import com.example.tripplanner.entity.Destination;
import com.example.tripplanner.entity.ItineraryItem;
import com.example.tripplanner.entity.Place;
import com.example.tripplanner.entity.Trip;
import com.example.tripplanner.util.GeoUtils;
import com.example.tripplanner.util.TimeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ItineraryService {

    @Value("${tripplanner.default.buffer-minutes:20}")
    private int defaultBufferMinutes;

    private final GoogleMapsService googleMapsService;
    private final RecommendationService recommendationService;

    public ItineraryService(GoogleMapsService googleMapsService, RecommendationService recommendationService) {
        this.googleMapsService = googleMapsService;
        this.recommendationService = recommendationService;
    }

    public List<DayItineraryDto> generateItineraryPlan(Trip trip, List<Destination> destinations, String transportMode) {
        List<DayItineraryDto> dayItineraries = new ArrayList<>();

        LocalDate startDate = trip.getStartDate() != null ? trip.getStartDate() : LocalDate.now();
        LocalDate endDate = trip.getEndDate() != null ? trip.getEndDate() : startDate.plusDays(Math.max(1, destinations.size() - 1));
        long daysCount = Math.max(1, ChronoUnit.DAYS.between(startDate, endDate) + 1);

        String dailyStart = (trip.getDailyStartTime() != null && !trip.getDailyStartTime().isEmpty()) ? trip.getDailyStartTime() : "08:00";
        String dailyEnd = (trip.getDailyEndTime() != null && !trip.getDailyEndTime().isEmpty()) ? trip.getDailyEndTime() : "20:30";

        // Distribute destinations across days
        int destCount = destinations.size();
        List<List<Destination>> destinationsByDay = new ArrayList<>();
        for (int i = 0; i < daysCount; i++) {
            destinationsByDay.add(new ArrayList<>());
        }

        if (daysCount >= destCount) {
            // Generous time: 1 destination per day or every few days
            for (int i = 0; i < destCount; i++) {
                int targetDay = Math.min((int) daysCount - 1, i);
                destinationsByDay.get(targetDay).add(destinations.get(i));
            }
        } else {
            // More destinations than days: distribute evenly
            for (int i = 0; i < destCount; i++) {
                int assignedDay = (int) Math.floor((double) i * daysCount / destCount);
                if (assignedDay >= daysCount) assignedDay = (int) daysCount - 1;
                destinationsByDay.get(assignedDay).add(destinations.get(i));
            }
        }

        String previousDayEndLocation = trip.getStartLocation();
        String previousNightHotel = null;

        // Track all scheduled place keys across the entire multi-day trip to guarantee ZERO repetitive visits
        Set<String> tripScheduledPlaceKeys = new HashSet<>();

        for (int dayIndex = 0; dayIndex < daysCount; dayIndex++) {
            int dayNumber = dayIndex + 1;
            LocalDate currentDate = startDate.plusDays(dayIndex);
            List<Destination> dayDests = destinationsByDay.get(dayIndex);

            String dayTitle;
            if (dayDests.isEmpty()) {
                dayTitle = "Day " + dayNumber + " • Leisure & Local Exploration in " + previousDayEndLocation;
            } else if (dayDests.size() == 1) {
                dayTitle = "Day " + dayNumber + " • " + previousDayEndLocation + " → " + dayDests.get(0).getName();
            } else {
                dayTitle = "Day " + dayNumber + " • " + previousDayEndLocation + " → " + dayDests.get(0).getName() + " & " + dayDests.get(dayDests.size() - 1).getName();
            }

            DayItineraryDto dayDto = new DayItineraryDto(dayNumber, currentDate, dayTitle);
            List<ItineraryItemDto> items = new ArrayList<>();

            String currentTime = dailyStart;
            double dayDistance = 0.0;
            int dayTravelMinutes = 0;
            int daySightseeingMinutes = 0;
            int dayBreakMinutes = 0;
            int totalPlacesCount = 0;
            int visitedPlacesCount = 0;

            // 1. Morning Departure: Depart from previous night's hotel stay or initial origin
            String dayOrigin;
            if (dayIndex == 0) {
                dayOrigin = trip.getStartLocation();
            } else if (previousNightHotel != null && !previousNightHotel.isEmpty()) {
                dayOrigin = previousNightHotel;
            } else {
                dayOrigin = previousDayEndLocation;
            }

            ItineraryItemDto departItem = new ItineraryItemDto();
            departItem.setDayNumber(dayNumber);
            departItem.setScheduleDate(currentDate);
            departItem.setStartTime(currentTime);
            departItem.setEndTime(currentTime);
            departItem.setActivity("🚗 Depart from " + dayOrigin);
            departItem.setLocation(dayOrigin);
            departItem.setActivityType("DEPARTURE");
            items.add(departItem);

            String currentLegOrigin = (dayIndex == 0) ? trip.getStartLocation() : previousDayEndLocation;
            boolean hadLunch = false;
            Destination currentStopDest = null;

            // Process destinations for this day
            if (dayDests.isEmpty()) {
                // Leisure / sight day in previous destination without repeating earlier visits
                List<SightseeingPlaceDto> leisurePlaces = recommendationService.getRecommendedPlaces(previousDayEndLocation, null, "rating", null);
                int added = 0;
                for (SightseeingPlaceDto p : leisurePlaces) {
                    if (added >= 3) break;
                    String pKey = normalizePlaceKey(p.getName());
                    if (!pKey.isEmpty() && tripScheduledPlaceKeys.contains(pKey)) {
                        continue; // Strictly skip: traveler already visited this attraction on an earlier day
                    }

                    int transitMins = p.getEstimatedTravelTimeMinutes() != null ? p.getEstimatedTravelTimeMinutes() : 15;
                    double distKm = p.getDistanceKm() != null ? p.getDistanceKm() : 3.0;
                    currentTime = TimeUtils.addMinutes(currentTime, defaultBufferMinutes);

                    String visitStart = TimeUtils.addMinutes(currentTime, transitMins);
                    int dur = p.getEstimatedVisitDurationMinutes() != null ? p.getEstimatedVisitDurationMinutes() : 90;
                    String visitEnd = TimeUtils.addMinutes(visitStart, dur);

                    ItineraryItemDto spotItem = new ItineraryItemDto();
                    spotItem.setDayNumber(dayNumber);
                    spotItem.setScheduleDate(currentDate);
                    spotItem.setStartTime(visitStart);
                    spotItem.setEndTime(visitEnd);
                    spotItem.setActivity("Explore " + p.getName());
                    spotItem.setLocation(p.getName() + " (" + previousDayEndLocation + ")");
                    spotItem.setActivityType("SIGHTSEEING");
                    spotItem.setTravelDistanceKm(distKm);
                    spotItem.setTravelTimeMinutes(transitMins);
                    spotItem.setDurationMinutes(dur);
                    spotItem.setRating(p.getRating());
                    spotItem.setVisited(false);
                    spotItem.setNotes(p.getDescription());
                    items.add(spotItem);

                    tripScheduledPlaceKeys.add(pKey);

                    currentTime = visitEnd;
                    dayDistance += distKm;
                    dayTravelMinutes += transitMins;
                    daySightseeingMinutes += dur;
                    totalPlacesCount++;
                    added++;
                }

                // If all catalog attractions for this destination were already visited on previous days,
                // schedule authentic local exploration experiences rather than repeating the same tourist spots
                if (added == 0) {
                    String experienceTitle;
                    String experienceNotes;
                    int expDuration = 90;
                    int expTransit = 15;
                    double expDist = 2.0;

                    if (dayNumber % 3 == 1) {
                        experienceTitle = "Local Handicrafts & Artisan Spice Bazaar";
                        experienceNotes = "Explore authentic regional craft markets, spice shops, and local culinary delicacies.";
                    } else if (dayNumber % 3 == 2) {
                        experienceTitle = "Scenic Nature Trail & Viewpoint Walk";
                        experienceNotes = "Relaxed stroll along panoramic nature paths, valley vistas, and photography points.";
                    } else {
                        experienceTitle = "Cultural Heritage Stroll & Café Trail";
                        experienceNotes = "Experience historic neighborhood architecture, street charm, and traditional coffee/tea heritage spots.";
                    }

                    currentTime = TimeUtils.addMinutes(currentTime, defaultBufferMinutes);
                    String visitStart = TimeUtils.addMinutes(currentTime, expTransit);
                    String visitEnd = TimeUtils.addMinutes(visitStart, expDuration);

                    ItineraryItemDto expItem = new ItineraryItemDto();
                    expItem.setDayNumber(dayNumber);
                    expItem.setScheduleDate(currentDate);
                    expItem.setStartTime(visitStart);
                    expItem.setEndTime(visitEnd);
                    expItem.setActivity("Explore " + experienceTitle);
                    expItem.setLocation(previousDayEndLocation + " City & Environs");
                    expItem.setActivityType("SIGHTSEEING");
                    expItem.setTravelDistanceKm(expDist);
                    expItem.setTravelTimeMinutes(expTransit);
                    expItem.setDurationMinutes(expDuration);
                    expItem.setRating(4.8);
                    expItem.setVisited(false);
                    expItem.setNotes(experienceNotes);
                    items.add(expItem);

                    currentTime = visitEnd;
                    dayDistance += expDist;
                    dayTravelMinutes += expTransit;
                    daySightseeingMinutes += expDuration;
                    totalPlacesCount++;
                }
            } else {
                for (Destination dest : dayDests) {
                    currentStopDest = dest;

                    // Travel from current point to this destination (skip zero-travel leg if already at destination)
                    boolean alreadyAtDestination = currentLegOrigin != null && currentLegOrigin.equalsIgnoreCase(dest.getName());
                    if (!alreadyAtDestination) {
                        GoogleMapsService.RouteCalculationResult leg = googleMapsService.calculateRouteSegment(currentLegOrigin, dest.getName(), transportMode);
                        dayDistance += leg.getDistanceKm();
                        dayTravelMinutes += leg.getDurationMinutes();

                        String arriveTime = TimeUtils.addMinutes(currentTime, leg.getDurationMinutes());

                        ItineraryItemDto arriveItem = new ItineraryItemDto();
                        arriveItem.setDayNumber(dayNumber);
                        arriveItem.setScheduleDate(currentDate);
                        arriveItem.setStartTime(currentTime);
                        arriveItem.setEndTime(arriveTime);
                        arriveItem.setActivity("Scenic Travel to " + dest.getName());
                        arriveItem.setLocation(dest.getName());
                        arriveItem.setActivityType("ARRIVAL");
                        arriveItem.setTravelDistanceKm(leg.getDistanceKm());
                        arriveItem.setTravelTimeMinutes(leg.getDurationMinutes());
                        items.add(arriveItem);

                        currentTime = arriveTime;
                        currentLegOrigin = dest.getName();
                        previousDayEndLocation = dest.getName();

                        // Buffer after travel
                        currentTime = TimeUtils.addMinutes(currentTime, defaultBufferMinutes);
                        dayBreakMinutes += defaultBufferMinutes;
                    }

                    // Lunch check: Around 12:00 - 14:00
                    if (!hadLunch && TimeUtils.parseTime(currentTime).isAfter(TimeUtils.parseTime("12:00"))) {
                        String lunchStart = currentTime;
                        String lunchEnd = TimeUtils.addMinutes(lunchStart, 60);
                        ItineraryItemDto lunchItem = new ItineraryItemDto();
                        lunchItem.setDayNumber(dayNumber);
                        lunchItem.setScheduleDate(currentDate);
                        lunchItem.setStartTime(lunchStart);
                        lunchItem.setEndTime(lunchEnd);
                        lunchItem.setActivity("🍴 Lunch Break & Local Cuisine");
                        lunchItem.setLocation("Local dining in " + dest.getName());
                        lunchItem.setActivityType("MEAL");
                        lunchItem.setDurationMinutes(60);
                        items.add(lunchItem);

                        currentTime = lunchEnd;
                        dayBreakMinutes += 60;
                        hadLunch = true;
                        currentTime = TimeUtils.addMinutes(currentTime, 15);
                    }

                    // Fetch places for this destination ensuring NO duplicate visits across stops/days
                    List<SightseeingPlaceDto> destPlaces = getOrGeneratePlacesForDestination(dest);
                    int placesScheduled = 0;

                    for (SightseeingPlaceDto place : destPlaces) {
                        if (placesScheduled >= 3) break;
                        String pKey = normalizePlaceKey(place.getName());
                        if (!pKey.isEmpty() && tripScheduledPlaceKeys.contains(pKey)) {
                            continue; // Strictly skip: already scheduled on an earlier day or stop
                        }

                        int visitDuration = place.getEstimatedVisitDurationMinutes() != null ? place.getEstimatedVisitDurationMinutes() : 90;
                        int travelTime = place.getEstimatedTravelTimeMinutes() != null ? place.getEstimatedTravelTimeMinutes() : 15;
                        double placeDist = place.getDistanceKm() != null ? place.getDistanceKm() : 2.5;

                        String actStart = TimeUtils.addMinutes(currentTime, travelTime);
                        String actEnd = TimeUtils.addMinutes(actStart, visitDuration);

                        if (TimeUtils.parseTime(actEnd).isAfter(TimeUtils.parseTime(dailyEnd))) {
                            break;
                        }

                        ItineraryItemDto visitItem = new ItineraryItemDto();
                        visitItem.setDayNumber(dayNumber);
                        visitItem.setScheduleDate(currentDate);
                        visitItem.setStartTime(actStart);
                        visitItem.setEndTime(actEnd);
                        visitItem.setActivity("Visit " + place.getName());
                        visitItem.setLocation(place.getName() + ", " + dest.getName());
                        visitItem.setActivityType("SIGHTSEEING");
                        visitItem.setPlaceId(place.getId());
                        visitItem.setTravelDistanceKm(placeDist);
                        visitItem.setTravelTimeMinutes(travelTime);
                        visitItem.setDurationMinutes(visitDuration);
                        visitItem.setRating(place.getRating());
                        visitItem.setVisited(place.getVisited() != null && place.getVisited());

                        String notes = place.getDescription() != null ? place.getDescription() : "";
                        String conflict = checkOpeningHoursConflict(actStart, actEnd, place.getOpeningHours());
                        if (conflict != null) {
                            notes = (notes.isEmpty() ? "" : notes + " • ") + "⚠️ " + conflict;
                        }
                        visitItem.setNotes(notes);
                        items.add(visitItem);

                        tripScheduledPlaceKeys.add(pKey);

                        if (visitItem.getVisited()) {
                            visitedPlacesCount++;
                        }
                        totalPlacesCount++;

                        dayDistance += placeDist;
                        dayTravelMinutes += travelTime;
                        daySightseeingMinutes += visitDuration;
                        placesScheduled++;

                        currentTime = TimeUtils.addMinutes(actEnd, defaultBufferMinutes);
                        dayBreakMinutes += defaultBufferMinutes;
                    }

                    // If fewer than 2 places were scheduled because earlier days consumed top attractions,
                    // search additional catalog places for this destination to provide fresh visits
                    if (placesScheduled < 2) {
                        List<SightseeingPlaceDto> morePlaces = recommendationService.getRecommendedPlaces(dest.getName(), null, "rating", null);
                        for (SightseeingPlaceDto altPlace : morePlaces) {
                            if (placesScheduled >= 3) break;
                            String altKey = normalizePlaceKey(altPlace.getName());
                            if (!altKey.isEmpty() && tripScheduledPlaceKeys.contains(altKey)) {
                                continue;
                            }

                            int visitDuration = altPlace.getEstimatedVisitDurationMinutes() != null ? altPlace.getEstimatedVisitDurationMinutes() : 90;
                            int travelTime = altPlace.getEstimatedTravelTimeMinutes() != null ? altPlace.getEstimatedTravelTimeMinutes() : 15;
                            double placeDist = altPlace.getDistanceKm() != null ? altPlace.getDistanceKm() : 2.5;

                            String actStart = TimeUtils.addMinutes(currentTime, travelTime);
                            String actEnd = TimeUtils.addMinutes(actStart, visitDuration);

                            if (TimeUtils.parseTime(actEnd).isAfter(TimeUtils.parseTime(dailyEnd))) {
                                break;
                            }

                            ItineraryItemDto visitItem = new ItineraryItemDto();
                            visitItem.setDayNumber(dayNumber);
                            visitItem.setScheduleDate(currentDate);
                            visitItem.setStartTime(actStart);
                            visitItem.setEndTime(actEnd);
                            visitItem.setActivity("Visit " + altPlace.getName());
                            visitItem.setLocation(altPlace.getName() + ", " + dest.getName());
                            visitItem.setActivityType("SIGHTSEEING");
                            visitItem.setPlaceId(altPlace.getId());
                            visitItem.setTravelDistanceKm(placeDist);
                            visitItem.setTravelTimeMinutes(travelTime);
                            visitItem.setDurationMinutes(visitDuration);
                            visitItem.setRating(altPlace.getRating());
                            visitItem.setVisited(altPlace.getVisited() != null && altPlace.getVisited());

                            String notes = altPlace.getDescription() != null ? altPlace.getDescription() : "";
                            String conflict = checkOpeningHoursConflict(actStart, actEnd, altPlace.getOpeningHours());
                            if (conflict != null) {
                                notes = (notes.isEmpty() ? "" : notes + " • ") + "⚠️ " + conflict;
                            }
                            visitItem.setNotes(notes);
                            items.add(visitItem);

                            tripScheduledPlaceKeys.add(altKey);

                            if (visitItem.getVisited()) {
                                visitedPlacesCount++;
                            }
                            totalPlacesCount++;

                            dayDistance += placeDist;
                            dayTravelMinutes += travelTime;
                            daySightseeingMinutes += visitDuration;
                            placesScheduled++;

                            currentTime = TimeUtils.addMinutes(actEnd, defaultBufferMinutes);
                            dayBreakMinutes += defaultBufferMinutes;
                        }
                    }
                }
            }

            // Evening Stay: Separate hotel based on this destination/stop (Requirement 10)
            String destinationHotel = null;
            if (currentStopDest != null && currentStopDest.getHotelName() != null && !currentStopDest.getHotelName().trim().isEmpty()) {
                destinationHotel = currentStopDest.getHotelName().trim();
            } else if (trip.getHotelLocation() != null && !trip.getHotelLocation().trim().isEmpty() && destinations.size() == 1) {
                destinationHotel = trip.getHotelLocation().trim();
            }

            String eveningStay = (destinationHotel != null) ? destinationHotel : ("Hotel / Resort in " + previousDayEndLocation);
            previousNightHotel = eveningStay;

            ItineraryItemDto eveningItem = new ItineraryItemDto();
            eveningItem.setDayNumber(dayNumber);
            eveningItem.setScheduleDate(currentDate);
            eveningItem.setStartTime(currentTime);
            eveningItem.setEndTime(TimeUtils.addMinutes(currentTime, 60));
            eveningItem.setActivity("🏨 Check-in & Relax at " + eveningStay);
            eveningItem.setLocation(eveningStay);
            eveningItem.setActivityType("HOTEL");
            eveningItem.setDurationMinutes(60);
            items.add(eveningItem);
            dayBreakMinutes += 60;

            // Compute Day Metrics & Fatigue Health Warnings
            dayDto.setItems(items);
            dayDto.setTotalDistanceKm(Math.round(dayDistance * 10.0) / 10.0);
            dayDto.setTravelTimeMinutes(dayTravelMinutes);
            dayDto.setSightseeingTimeMinutes(daySightseeingMinutes);
            dayDto.setBreakTimeMinutes(dayBreakMinutes);
            dayDto.setTotalDurationMinutes(dayTravelMinutes + daySightseeingMinutes + dayBreakMinutes);
            dayDto.setTotalPlacesCount(totalPlacesCount);
            dayDto.setVisitedCount(visitedPlacesCount);

            // Health Status & Warnings
            if (dayTravelMinutes >= 420) {
                dayDto.setStatus("⚠️ Long Day");
                dayDto.setWarningMessage("This day contains " + TimeUtils.formatDuration(dayTravelMinutes) + " of transit! Consider spreading destinations across another day or resting.");
            } else if (dayTravelMinutes + daySightseeingMinutes >= 600) {
                dayDto.setStatus("Overcrowded");
                dayDto.setWarningMessage("Schedule is heavily packed (" + TimeUtils.formatDuration(dayTravelMinutes + daySightseeingMinutes) + " active time). You may experience rush between spots.");
            } else if (dayTravelMinutes < 120 && daySightseeingMinutes < 180) {
                dayDto.setStatus("Relaxed");
            } else {
                dayDto.setStatus("Balanced");
            }

            dayItineraries.add(dayDto);
        }

        return dayItineraries;
    }

    /**
     * Regenerates a single day's itinerary without altering other days (Requirement 4).
     */
    public DayItineraryDto regenerateSingleDay(Trip trip, List<Destination> destinations, int dayNumber,
                                               String preference, String transportMode,
                                               Set<String> alreadyUsedPlaceNames) {
        LocalDate startDate = trip.getStartDate() != null ? trip.getStartDate() : LocalDate.now();
        LocalDate targetDate = startDate.plusDays(dayNumber - 1);

        String dailyStart = (trip.getDailyStartTime() != null && !trip.getDailyStartTime().isEmpty()) ? trip.getDailyStartTime() : "08:00";
        String dailyEnd = (trip.getDailyEndTime() != null && !trip.getDailyEndTime().isEmpty()) ? trip.getDailyEndTime() : "20:30";

        // Determine destination for this day
        int destIndex = Math.min(destinations.size() - 1, dayNumber - 1);
        Destination targetDest = (destIndex >= 0 && destIndex < destinations.size()) ? destinations.get(destIndex) : null;
        String destName = (targetDest != null) ? targetDest.getName() : trip.getStartLocation();

        String dayTitle = "Day " + dayNumber + " • Refreshed Itinerary in " + destName;
        DayItineraryDto dayDto = new DayItineraryDto(dayNumber, targetDate, dayTitle);
        List<ItineraryItemDto> items = new ArrayList<>();

        String currentTime = dailyStart;
        double dayDistance = 0.0;
        int dayTravelMinutes = 0;
        int daySightseeingMinutes = 0;
        int dayBreakMinutes = 0;
        int totalPlacesCount = 0;
        int visitedPlacesCount = 0;

        // Morning Departure from hotel
        String hotel = (targetDest != null && targetDest.getHotelName() != null && !targetDest.getHotelName().isEmpty())
                ? targetDest.getHotelName()
                : ("Hotel / Stay in " + destName);

        ItineraryItemDto departItem = new ItineraryItemDto();
        departItem.setDayNumber(dayNumber);
        departItem.setScheduleDate(targetDate);
        departItem.setStartTime(currentTime);
        departItem.setEndTime(currentTime);
        departItem.setActivity("🚗 Depart from " + hotel);
        departItem.setLocation(hotel);
        departItem.setActivityType("DEPARTURE");
        items.add(departItem);

        // Fetch fresh candidate places matching the user's preference
        String categoryFilter = (preference != null && !preference.equalsIgnoreCase("ALL") && !preference.equalsIgnoreCase("BALANCED"))
                ? preference
                : null;

        List<SightseeingPlaceDto> candidates = recommendationService.getRecommendedPlaces(destName, null, "rating", categoryFilter);
        // Shuffle or filter out places already in other days
        List<SightseeingPlaceDto> filtered = new ArrayList<>();
        for (SightseeingPlaceDto c : candidates) {
            String cKey = normalizePlaceKey(c.getName());
            boolean isUsed = false;
            if (alreadyUsedPlaceNames != null) {
                for (String used : alreadyUsedPlaceNames) {
                    if (!cKey.isEmpty() && normalizePlaceKey(used).equals(cKey)) {
                        isUsed = true;
                        break;
                    }
                }
            }
            if (!isUsed) {
                filtered.add(c);
            }
        }
        if (filtered.isEmpty()) {
            List<SightseeingPlaceDto> allCandidates = recommendationService.getRecommendedPlaces(destName, null, "popularity", null);
            for (SightseeingPlaceDto c : allCandidates) {
                String cKey = normalizePlaceKey(c.getName());
                boolean isUsed = false;
                if (alreadyUsedPlaceNames != null) {
                    for (String used : alreadyUsedPlaceNames) {
                        if (!cKey.isEmpty() && normalizePlaceKey(used).equals(cKey)) {
                            isUsed = true;
                            break;
                        }
                    }
                }
                if (!isUsed) {
                    filtered.add(c);
                }
            }
        }
        Collections.shuffle(filtered);

        int scheduled = 0;
        boolean hadLunch = false;

        for (SightseeingPlaceDto place : filtered) {
            if (scheduled >= 3) break;

            int visitDuration = place.getEstimatedVisitDurationMinutes() != null ? place.getEstimatedVisitDurationMinutes() : 90;
            int travelTime = place.getEstimatedTravelTimeMinutes() != null ? place.getEstimatedTravelTimeMinutes() : 15;
            double placeDist = place.getDistanceKm() != null ? place.getDistanceKm() : 2.5;

            // Lunch insertion if around noon
            if (!hadLunch && TimeUtils.parseTime(currentTime).isAfter(TimeUtils.parseTime("12:30"))) {
                String lunchStart = currentTime;
                String lunchEnd = TimeUtils.addMinutes(lunchStart, 60);
                ItineraryItemDto lunchItem = new ItineraryItemDto();
                lunchItem.setDayNumber(dayNumber);
                lunchItem.setScheduleDate(targetDate);
                lunchItem.setStartTime(lunchStart);
                lunchItem.setEndTime(lunchEnd);
                lunchItem.setActivity("🍴 Lunch Break & Local Specialities");
                lunchItem.setLocation("Dining in " + destName);
                lunchItem.setActivityType("MEAL");
                lunchItem.setDurationMinutes(60);
                items.add(lunchItem);

                currentTime = lunchEnd;
                dayBreakMinutes += 60;
                hadLunch = true;
                currentTime = TimeUtils.addMinutes(currentTime, 15);
            }

            String actStart = TimeUtils.addMinutes(currentTime, travelTime);
            String actEnd = TimeUtils.addMinutes(actStart, visitDuration);

            if (TimeUtils.parseTime(actEnd).isAfter(TimeUtils.parseTime(dailyEnd))) {
                break;
            }

            ItineraryItemDto visitItem = new ItineraryItemDto();
            visitItem.setDayNumber(dayNumber);
            visitItem.setScheduleDate(targetDate);
            visitItem.setStartTime(actStart);
            visitItem.setEndTime(actEnd);
            visitItem.setActivity("Visit " + place.getName());
            visitItem.setLocation(place.getName() + ", " + destName);
            visitItem.setActivityType("SIGHTSEEING");
            visitItem.setTravelDistanceKm(placeDist);
            visitItem.setTravelTimeMinutes(travelTime);
            visitItem.setDurationMinutes(visitDuration);
            visitItem.setRating(place.getRating());
            visitItem.setVisited(false);

            String notes = place.getDescription() != null ? place.getDescription() : "";
            String conflict = checkOpeningHoursConflict(actStart, actEnd, place.getOpeningHours());
            if (conflict != null) {
                notes = (notes.isEmpty() ? "" : notes + " • ") + "⚠️ " + conflict;
            }
            visitItem.setNotes(notes);
            items.add(visitItem);

            dayDistance += placeDist;
            dayTravelMinutes += travelTime;
            daySightseeingMinutes += visitDuration;
            totalPlacesCount++;
            scheduled++;

            currentTime = TimeUtils.addMinutes(actEnd, defaultBufferMinutes);
            dayBreakMinutes += defaultBufferMinutes;
        }

        // Evening Hotel Check-in
        ItineraryItemDto eveningItem = new ItineraryItemDto();
        eveningItem.setDayNumber(dayNumber);
        eveningItem.setScheduleDate(targetDate);
        eveningItem.setStartTime(currentTime);
        eveningItem.setEndTime(TimeUtils.addMinutes(currentTime, 60));
        eveningItem.setActivity("🏨 Return & Relax at " + hotel);
        eveningItem.setLocation(hotel);
        eveningItem.setActivityType("HOTEL");
        eveningItem.setDurationMinutes(60);
        items.add(eveningItem);
        dayBreakMinutes += 60;

        dayDto.setItems(items);
        dayDto.setTotalDistanceKm(Math.round(dayDistance * 10.0) / 10.0);
        dayDto.setTravelTimeMinutes(dayTravelMinutes);
        dayDto.setSightseeingTimeMinutes(daySightseeingMinutes);
        dayDto.setBreakTimeMinutes(dayBreakMinutes);
        dayDto.setTotalDurationMinutes(dayTravelMinutes + daySightseeingMinutes + dayBreakMinutes);
        dayDto.setTotalPlacesCount(totalPlacesCount);
        dayDto.setVisitedCount(visitedPlacesCount);
        dayDto.setStatus("Balanced");

        return dayDto;
    }

    private List<SightseeingPlaceDto> getOrGeneratePlacesForDestination(Destination dest) {
        if (dest.getPlaces() != null && !dest.getPlaces().isEmpty()) {
            List<SightseeingPlaceDto> dtos = new ArrayList<>();
            for (Place p : dest.getPlaces()) {
                SightseeingPlaceDto dto = new SightseeingPlaceDto();
                dto.setId(p.getId());
                dto.setName(p.getName());
                dto.setDestinationName(dest.getName());
                dto.setCategory(p.getCategory());
                dto.setRating(p.getRating());
                dto.setReviewCount(p.getReviewCount());
                dto.setDistanceKm(p.getDistanceKm());
                dto.setEstimatedVisitDurationMinutes(p.getEstimatedVisitDurationMinutes());
                dto.setEstimatedTravelTimeMinutes(p.getEstimatedTravelTimeMinutes());
                dto.setDescription(p.getDescription());
                dto.setOpeningHours(p.getOpeningHours());
                dto.setLatitude(p.getLatitude());
                dto.setLongitude(p.getLongitude());
                dto.setPriority(p.getPriority());
                dto.setVisited(p.getVisited());
                dto.setAddedToItinerary(p.getAddedToItinerary());
                dtos.add(dto);
            }
            return dtos;
        }

        return recommendationService.getRecommendedPlaces(dest.getName(), null, "popularity", null);
    }

    public static String checkOpeningHoursConflict(String visitStartTime, String visitEndTime, String openingHours) {
        if (openingHours == null || openingHours.trim().isEmpty() ||
                openingHours.equalsIgnoreCase("24 Hours") ||
                openingHours.toLowerCase().contains("24") ||
                openingHours.toLowerCase().contains("all day")) {
            return null;
        }
        try {
            String[] parts = openingHours.split("-");
            if (parts.length != 2) return null;

            LocalTime openTime = parseTime12(parts[0].trim());
            LocalTime closeTime = parseTime12(parts[1].trim());
            if (openTime == null || closeTime == null) return null;

            LocalTime start = TimeUtils.parseTime(visitStartTime);
            LocalTime end = TimeUtils.parseTime(visitEndTime);

            if (start.isBefore(openTime)) {
                return "Attraction opens at " + parts[0].trim() + " (visit scheduled from " + TimeUtils.formatAmPm(visitStartTime) + ")";
            }
            if (end.isAfter(closeTime)) {
                return "Attraction closes at " + parts[1].trim() + " (visit extends until " + TimeUtils.formatAmPm(visitEndTime) + ")";
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static LocalTime parseTime12(String raw) {
        try {
            String clean = raw.trim().toUpperCase().replaceAll("\\s*(AM|PM)", " $1");
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
            return LocalTime.parse(clean, dtf);
        } catch (Exception e) {
            try {
                DateTimeFormatter dtf2 = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
                return LocalTime.parse(raw.trim().toUpperCase(), dtf2);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    /**
     * Normalizes place/attraction names for accurate duplicate detection across days and route stops.
     * Strips common itinerary activity prefixes ("Visit", "Explore", "Tour") and punctuation.
     */
    public static String normalizePlaceKey(String placeName) {
        if (placeName == null) return "";
        return placeName.trim().toLowerCase()
                .replaceAll("(?i)^(visit|explore|see|tour|check\\s*in|depart|travel\\s*to)\\s+", "")
                .replaceAll("[^a-z0-9]", "");
    }
}
