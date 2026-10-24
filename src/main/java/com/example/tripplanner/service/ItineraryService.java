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

    /**
     * Determines which destination a given day belongs to based on configured stay durations.
     */
    public Destination findDestinationForDay(List<Destination> destinations, int dayNumber) {
        if (destinations == null || destinations.isEmpty()) {
            return null;
        }
        int currentDay = 1;
        for (Destination d : destinations) {
            int duration = d.getStayDurationDays() != null && d.getStayDurationDays() > 0 ? d.getStayDurationDays() : 1;
            if (dayNumber >= currentDay && dayNumber < currentDay + duration) {
                return d;
            }
            currentDay += duration;
        }
        return destinations.get(destinations.size() - 1);
    }

    /**
     * Calculates the city day index (1-based: e.g. Day 1 in Mysuru, Day 2 in Mysuru) for a given day.
     */
    public int getCityDayIndex(List<Destination> destinations, int dayNumber) {
        if (destinations == null || destinations.isEmpty()) {
            return 1;
        }
        int currentDay = 1;
        for (Destination d : destinations) {
            int duration = d.getStayDurationDays() != null && d.getStayDurationDays() > 0 ? d.getStayDurationDays() : 1;
            if (dayNumber >= currentDay && dayNumber < currentDay + duration) {
                return dayNumber - currentDay + 1;
            }
            currentDay += duration;
        }
        return 1;
    }

    /**
     * Generates a realistic, chronological day-by-day itinerary plan using stay durations,
     * route engine data, and verified 15-minute clock intervals.
     */
    public List<DayItineraryDto> generateItineraryPlan(Trip trip, List<Destination> destinations, String transportMode) {
        List<DayItineraryDto> dayItineraries = new ArrayList<>();

        LocalDate startDate = trip.getStartDate() != null ? trip.getStartDate() : LocalDate.now();
        LocalDate configuredEndDate = trip.getEndDate() != null ? trip.getEndDate() : startDate;

        // Sum up configured city stay durations
        int sumStayDays = 0;
        if (destinations != null) {
            for (Destination d : destinations) {
                int days = d.getStayDurationDays() != null && d.getStayDurationDays() > 0 ? d.getStayDurationDays() : 1;
                sumStayDays += days;
            }
        }
        if (sumStayDays == 0) {
            sumStayDays = Math.max(1, destinations != null ? destinations.size() : 1);
        }

        long tripCalendarDays = Math.max(1, ChronoUnit.DAYS.between(startDate, configuredEndDate) + 1);
        // Itinerary covers all planned city stays
        long totalDaysCount = Math.max(sumStayDays, tripCalendarDays);

        String dailyStartRaw = (trip.getDailyStartTime() != null && !trip.getDailyStartTime().isEmpty()) ? trip.getDailyStartTime() : "08:00";
        String dailyEndRaw = (trip.getDailyEndTime() != null && !trip.getDailyEndTime().isEmpty()) ? trip.getDailyEndTime() : "20:30";

        // Align daily start to clean 15-minute clock interval
        String dailyStart = TimeUtils.roundTime24(dailyStartRaw, TimeUtils.DEFAULT_INTERVAL_MINUTES);
        String dailyEnd = TimeUtils.roundTime24(dailyEndRaw, TimeUtils.DEFAULT_INTERVAL_MINUTES);

        // Pre-calculate arrival and departure dates for each destination
        if (destinations != null && !destinations.isEmpty()) {
            int dayOffset = 0;
            for (Destination d : destinations) {
                int stayDays = d.getStayDurationDays() != null && d.getStayDurationDays() > 0 ? d.getStayDurationDays() : 1;
                d.setArrivalDate(startDate.plusDays(dayOffset));
                d.setDepartureDate(startDate.plusDays(dayOffset + stayDays - 1));
                if (d.getStayDurationNights() == null || d.getStayDurationNights() == 0) {
                    d.setStayDurationNights(Math.max(0, stayDays - 1));
                }
                dayOffset += stayDays;
            }
        }

        String previousDayEndLocation = trip.getStartLocation();
        String previousNightHotel = null;

        // Track scheduled place keys across the entire trip to guarantee zero repetitive visits
        Set<String> tripScheduledPlaceKeys = new HashSet<>();

        for (int dayIndex = 0; dayIndex < totalDaysCount; dayIndex++) {
            int dayNumber = dayIndex + 1;
            LocalDate currentDate = startDate.plusDays(dayIndex);

            Destination currentDest = findDestinationForDay(destinations, dayNumber);
            int cityDayIndex = getCityDayIndex(destinations, dayNumber);
            boolean isFirstDayInCity = (cityDayIndex == 1);
            String currentCityName = (currentDest != null) ? currentDest.getName() : previousDayEndLocation;

            // Generate Day Title
            String dayTitle;
            if (isFirstDayInCity) {
                if (previousDayEndLocation.equalsIgnoreCase(currentCityName)) {
                    dayTitle = "Day " + dayNumber + " • Arrival & Highlights of " + currentCityName;
                } else {
                    dayTitle = "Day " + dayNumber + " • " + previousDayEndLocation + " → " + currentCityName;
                }
            } else {
                dayTitle = "Day " + dayNumber + " • Deep Dive in " + currentCityName + " (Day " + cityDayIndex + ")";
            }

            DayItineraryDto dayDto = new DayItineraryDto(dayNumber, currentDate, dayTitle);
            List<ItineraryItemDto> items = new ArrayList<>();

            String currentTime24 = dailyStart;
            double dayDistance = 0.0;
            int dayTravelMinutes = 0;
            int daySightseeingMinutes = 0;
            int dayBreakMinutes = 0;
            int totalPlacesCount = 0;
            int visitedPlacesCount = 0;

            // 1. Morning Departure
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
            departItem.setStartTime(TimeUtils.formatAmPm(currentTime24));
            departItem.setEndTime(TimeUtils.formatAmPm(currentTime24));
            departItem.setActivity("🚗 Depart from " + dayOrigin);
            departItem.setLocation(dayOrigin);
            departItem.setActivityType("DEPARTURE");
            items.add(departItem);

            String currentLegOrigin = (dayIndex == 0) ? trip.getStartLocation() : previousDayEndLocation;
            boolean hadLunch = false;

            // Travel leg if arriving at a new city
            if (isFirstDayInCity && !currentLegOrigin.equalsIgnoreCase(currentCityName)) {
                GoogleMapsService.RouteCalculationResult leg = googleMapsService.calculateRouteSegment(currentLegOrigin, currentCityName, transportMode);
                int travelMins = leg.getDurationMinutes();
                double travelDist = leg.getDistanceKm();

                dayDistance += travelDist;
                dayTravelMinutes += travelMins;

                // Travel start time is current time
                String travelStart = currentTime24;
                String rawArrival = TimeUtils.addMinutes(travelStart, travelMins);
                // Round arrival display cleanly to 15-min interval
                String travelEnd = TimeUtils.roundTime24(rawArrival, TimeUtils.DEFAULT_INTERVAL_MINUTES);

                ItineraryItemDto arriveItem = new ItineraryItemDto();
                arriveItem.setDayNumber(dayNumber);
                arriveItem.setScheduleDate(currentDate);
                arriveItem.setStartTime(TimeUtils.formatAmPm(travelStart));
                arriveItem.setEndTime(TimeUtils.formatAmPm(travelEnd));
                arriveItem.setActivity("Scenic Highway Travel to " + currentCityName);
                arriveItem.setLocation(currentCityName);
                arriveItem.setActivityType("ARRIVAL");
                arriveItem.setTravelDistanceKm(travelDist);
                arriveItem.setTravelTimeMinutes(travelMins);
                items.add(arriveItem);

                // Safe schedule advance: arrival + buffer rounded up to 15-minute interval so no overlap
                String earliestNextStart = TimeUtils.addMinutes(rawArrival, defaultBufferMinutes);
                currentTime24 = TimeUtils.roundUpTime24(earliestNextStart, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                dayBreakMinutes += defaultBufferMinutes;

                previousDayEndLocation = currentCityName;
            }

            // Check for lunch if time is already past 12:00
            if (!hadLunch && TimeUtils.parseTime(currentTime24).isAfter(LocalTime.of(12, 0)) && TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(14, 30))) {
                String lunchStart = TimeUtils.roundTime24(currentTime24, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                String lunchEnd = TimeUtils.addMinutes(lunchStart, 60);

                ItineraryItemDto lunchItem = new ItineraryItemDto();
                lunchItem.setDayNumber(dayNumber);
                lunchItem.setScheduleDate(currentDate);
                lunchItem.setStartTime(TimeUtils.formatAmPm(lunchStart));
                lunchItem.setEndTime(TimeUtils.formatAmPm(lunchEnd));
                lunchItem.setActivity("🍴 Lunch Break & Local Specialities");
                lunchItem.setLocation("Dining in " + currentCityName);
                lunchItem.setActivityType("MEAL");
                lunchItem.setDurationMinutes(60);
                items.add(lunchItem);

                currentTime24 = lunchEnd;
                dayBreakMinutes += 60;
                hadLunch = true;
                currentTime24 = TimeUtils.roundUpTime24(TimeUtils.addMinutes(currentTime24, 15), TimeUtils.DEFAULT_INTERVAL_MINUTES);
            }

            // Schedule attractions for this day
            List<SightseeingPlaceDto> poolPlaces = getOrGeneratePlacesForCity(currentDest, currentCityName);
            int placesScheduledToday = 0;
            int maxPlacesToday = (isFirstDayInCity && dayTravelMinutes > 180) ? 2 : 3;

            for (SightseeingPlaceDto place : poolPlaces) {
                if (placesScheduledToday >= maxPlacesToday) break;

                String pKey = normalizePlaceKey(place.getName());
                if (!pKey.isEmpty() && tripScheduledPlaceKeys.contains(pKey)) {
                    continue; // Strictly skip: already scheduled in earlier days/stops
                }

                int rawVisitDuration = place.getEstimatedVisitDurationMinutes() != null ? place.getEstimatedVisitDurationMinutes() : 90;
                // Round visit duration to nearest 15-minute block (e.g. 60, 75, 90, 105, 120 mins)
                int visitDuration = Math.max(30, (int) Math.round((double) rawVisitDuration / 15.0) * 15);
                int travelTime = place.getEstimatedTravelTimeMinutes() != null ? place.getEstimatedTravelTimeMinutes() : 15;
                double placeDist = place.getDistanceKm() != null ? place.getDistanceKm() : 2.5;

                // Safe interval rounding: travel to spot
                String rawTransitArrival = TimeUtils.addMinutes(currentTime24, travelTime);
                String actStart = TimeUtils.roundUpTime24(rawTransitArrival, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                String actEnd = TimeUtils.addMinutes(actStart, visitDuration);

                // Check daily end time boundary
                if (TimeUtils.parseTime(actEnd).isAfter(TimeUtils.parseTime(dailyEnd))) {
                    break;
                }

                // Check lunch insertion if passing midday
                if (!hadLunch && TimeUtils.parseTime(actStart).isAfter(LocalTime.of(12, 15))) {
                    String lunchStart = TimeUtils.roundTime24(currentTime24, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                    String lunchEnd = TimeUtils.addMinutes(lunchStart, 60);

                    ItineraryItemDto lunchItem = new ItineraryItemDto();
                    lunchItem.setDayNumber(dayNumber);
                    lunchItem.setScheduleDate(currentDate);
                    lunchItem.setStartTime(TimeUtils.formatAmPm(lunchStart));
                    lunchItem.setEndTime(TimeUtils.formatAmPm(lunchEnd));
                    lunchItem.setActivity("🍴 Lunch Break & Local Specialities");
                    lunchItem.setLocation("Dining in " + currentCityName);
                    lunchItem.setActivityType("MEAL");
                    lunchItem.setDurationMinutes(60);
                    items.add(lunchItem);

                    currentTime24 = lunchEnd;
                    dayBreakMinutes += 60;
                    hadLunch = true;

                    // Recalculate actStart and actEnd after lunch
                    rawTransitArrival = TimeUtils.addMinutes(currentTime24, travelTime);
                    actStart = TimeUtils.roundUpTime24(rawTransitArrival, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                    actEnd = TimeUtils.addMinutes(actStart, visitDuration);
                    if (TimeUtils.parseTime(actEnd).isAfter(TimeUtils.parseTime(dailyEnd))) {
                        break;
                    }
                }

                ItineraryItemDto visitItem = new ItineraryItemDto();
                visitItem.setDayNumber(dayNumber);
                visitItem.setScheduleDate(currentDate);
                visitItem.setStartTime(TimeUtils.formatAmPm(actStart));
                visitItem.setEndTime(TimeUtils.formatAmPm(actEnd));
                visitItem.setActivity("Visit " + place.getName());
                visitItem.setLocation(place.getName() + ", " + currentCityName);
                visitItem.setActivityType("SIGHTSEEING");
                visitItem.setPlaceId(place.getId());
                visitItem.setTravelDistanceKm(placeDist);
                visitItem.setTravelTimeMinutes(travelTime);
                visitItem.setDurationMinutes(visitDuration);
                visitItem.setRating(place.getRating());
                visitItem.setVisited(Boolean.TRUE.equals(place.getVisited()));

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
                placesScheduledToday++;

                // Advance currentTime24 after visit with buffer rounded up to clean interval
                String nextSlot = TimeUtils.addMinutes(actEnd, defaultBufferMinutes);
                currentTime24 = TimeUtils.roundUpTime24(nextSlot, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                dayBreakMinutes += defaultBufferMinutes;
            }

            // If multi-day city stay and catalog attractions were already consumed, schedule curated authentic local experiences
            if (placesScheduledToday == 0) {
                String expTitle;
                String expNotes;
                int expDuration = 90;
                int expTransit = 15;
                double expDist = 2.0;

                if (cityDayIndex % 3 == 1) {
                    expTitle = "Local Handicrafts & Artisan Spice Bazaar";
                    expNotes = "Explore authentic regional craft markets, spice shops, and local culinary delicacies.";
                } else if (cityDayIndex % 3 == 2) {
                    expTitle = "Scenic Nature Trail & Viewpoint Walk";
                    expNotes = "Relaxed stroll along panoramic nature paths, valley vistas, and photography points.";
                } else {
                    expTitle = "Cultural Heritage Stroll & Café Trail";
                    expNotes = "Experience historic neighborhood architecture, street charm, and traditional coffee/tea heritage spots.";
                }

                String actStart = TimeUtils.roundUpTime24(TimeUtils.addMinutes(currentTime24, expTransit), TimeUtils.DEFAULT_INTERVAL_MINUTES);
                String actEnd = TimeUtils.addMinutes(actStart, expDuration);

                ItineraryItemDto expItem = new ItineraryItemDto();
                expItem.setDayNumber(dayNumber);
                expItem.setScheduleDate(currentDate);
                expItem.setStartTime(TimeUtils.formatAmPm(actStart));
                expItem.setEndTime(TimeUtils.formatAmPm(actEnd));
                expItem.setActivity("Explore " + expTitle);
                expItem.setLocation(currentCityName + " City & Environs");
                expItem.setActivityType("SIGHTSEEING");
                expItem.setTravelDistanceKm(expDist);
                expItem.setTravelTimeMinutes(expTransit);
                expItem.setDurationMinutes(expDuration);
                expItem.setRating(4.8);
                expItem.setVisited(false);
                expItem.setNotes(expNotes);
                items.add(expItem);

                currentTime24 = actEnd;
                dayDistance += expDist;
                dayTravelMinutes += expTransit;
                daySightseeingMinutes += expDuration;
                totalPlacesCount++;

                currentTime24 = TimeUtils.roundUpTime24(TimeUtils.addMinutes(currentTime24, defaultBufferMinutes), TimeUtils.DEFAULT_INTERVAL_MINUTES);
                dayBreakMinutes += defaultBufferMinutes;
            }

            // Evening Stay: hotel for this destination/city
            String destinationHotel = null;
            if (currentDest != null && currentDest.getHotelName() != null && !currentDest.getHotelName().trim().isEmpty()) {
                destinationHotel = currentDest.getHotelName().trim();
            } else if (trip.getHotelLocation() != null && !trip.getHotelLocation().trim().isEmpty()) {
                destinationHotel = trip.getHotelLocation().trim();
            }

            String eveningStay = (destinationHotel != null) ? destinationHotel : ("Hotel / Resort in " + currentCityName);
            previousNightHotel = eveningStay;

            String eveningStart = TimeUtils.roundTime24(currentTime24, TimeUtils.DEFAULT_INTERVAL_MINUTES);
            String eveningEnd = TimeUtils.addMinutes(eveningStart, 60);

            ItineraryItemDto eveningItem = new ItineraryItemDto();
            eveningItem.setDayNumber(dayNumber);
            eveningItem.setScheduleDate(currentDate);
            eveningItem.setStartTime(TimeUtils.formatAmPm(eveningStart));
            eveningItem.setEndTime(TimeUtils.formatAmPm(eveningEnd));
            eveningItem.setActivity(isFirstDayInCity ? ("🏨 Check-in & Relax at " + eveningStay) : ("🏨 Return & Relax at " + eveningStay));
            eveningItem.setLocation(eveningStay);
            eveningItem.setActivityType("HOTEL");
            eveningItem.setDurationMinutes(60);
            items.add(eveningItem);
            dayBreakMinutes += 60;

            // Compute Day Metrics & Feasibility Warnings
            items.sort((a, b) -> {
                int cmp = TimeUtils.compareItineraryTimes(a.getStartTime(), b.getStartTime());
                if (cmp != 0) return cmp;
                int typeCmp = Integer.compare(TimeUtils.getActivityTypeOrder(a.getActivityType()), TimeUtils.getActivityTypeOrder(b.getActivityType()));
                if (typeCmp != 0) return typeCmp;
                long idA = a.getId() != null ? a.getId() : 0L;
                long idB = b.getId() != null ? b.getId() : 0L;
                return Long.compare(idA, idB);
            });
            dayDto.setItems(items);
            dayDto.setTotalDistanceKm(Math.round(dayDistance * 10.0) / 10.0);
            dayDto.setTravelTimeMinutes(dayTravelMinutes);
            dayDto.setSightseeingTimeMinutes(daySightseeingMinutes);
            dayDto.setBreakTimeMinutes(dayBreakMinutes);
            dayDto.setTotalDurationMinutes(dayTravelMinutes + daySightseeingMinutes + dayBreakMinutes);
            dayDto.setTotalPlacesCount(totalPlacesCount);
            dayDto.setVisitedCount(visitedPlacesCount);

            // Dynamic Crowding & Feasibility Warning Calculations (Requirement 2 & 4)
            int activeMinutes = dayTravelMinutes + daySightseeingMinutes;
            int availableMinutes = TimeUtils.differenceInMinutes(dailyStart, dailyEnd);
            int maxDailyDrivingMinutes = 360; // 6 hours driving limit

            if (dayTravelMinutes > maxDailyDrivingMinutes) {
                dayDto.setStatus("⚠️ Long Driving Day");
                dayDto.setWarningMessage("This day contains " + TimeUtils.formatDuration(dayTravelMinutes) + " of driving, exceeding the 6-hour daily limit. Consider an intermediate rest stop.");
            } else if (activeMinutes > 600 || (availableMinutes > 0 && activeMinutes > availableMinutes)) {
                int approxHours = (int) Math.ceil(activeMinutes / 60.0);
                dayDto.setStatus("Overcrowded");
                dayDto.setWarningMessage("Day " + dayNumber + " looks crowded. Your planned activities require approximately " + approxHours + " hours, excluding unexpected delays. Consider moving one attraction to another day.");
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
     * Regenerates a single day's itinerary without altering other days, preserving locked items,
     * stay durations, and 15-minute clock intervals.
     */
    public DayItineraryDto regenerateSingleDay(Trip trip, List<Destination> destinations, int dayNumber,
                                               String preference, String transportMode,
                                               Set<String> alreadyUsedPlaceNames) {
        LocalDate startDate = trip.getStartDate() != null ? trip.getStartDate() : LocalDate.now();
        LocalDate targetDate = startDate.plusDays(dayNumber - 1);

        String dailyStartRaw = (trip.getDailyStartTime() != null && !trip.getDailyStartTime().isEmpty()) ? trip.getDailyStartTime() : "08:00";
        String dailyEndRaw = (trip.getDailyEndTime() != null && !trip.getDailyEndTime().isEmpty()) ? trip.getDailyEndTime() : "20:30";

        String dailyStart = TimeUtils.roundTime24(dailyStartRaw, TimeUtils.DEFAULT_INTERVAL_MINUTES);
        String dailyEnd = TimeUtils.roundTime24(dailyEndRaw, TimeUtils.DEFAULT_INTERVAL_MINUTES);

        Destination targetDest = findDestinationForDay(destinations, dayNumber);
        int cityDayIndex = getCityDayIndex(destinations, dayNumber);
        String destName = (targetDest != null) ? targetDest.getName() : trip.getStartLocation();

        String dayTitle = "Day " + dayNumber + " • Refreshed Itinerary in " + destName;
        DayItineraryDto dayDto = new DayItineraryDto(dayNumber, targetDate, dayTitle);
        List<ItineraryItemDto> items = new ArrayList<>();

        String currentTime24 = dailyStart;
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
        departItem.setStartTime(TimeUtils.formatAmPm(currentTime24));
        departItem.setEndTime(TimeUtils.formatAmPm(currentTime24));
        departItem.setActivity("🚗 Depart from " + hotel);
        departItem.setLocation(hotel);
        departItem.setActivityType("DEPARTURE");
        items.add(departItem);

        String categoryFilter = (preference != null && !preference.equalsIgnoreCase("ALL") && !preference.equalsIgnoreCase("BALANCED"))
                ? preference
                : null;

        List<SightseeingPlaceDto> candidates = recommendationService.getRecommendedPlaces(destName, null, "rating", categoryFilter);
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

            int rawVisitDuration = place.getEstimatedVisitDurationMinutes() != null ? place.getEstimatedVisitDurationMinutes() : 90;
            int visitDuration = Math.max(30, (int) Math.round((double) rawVisitDuration / 15.0) * 15);
            int travelTime = place.getEstimatedTravelTimeMinutes() != null ? place.getEstimatedTravelTimeMinutes() : 15;
            double placeDist = place.getDistanceKm() != null ? place.getDistanceKm() : 2.5;

            // Lunch check
            if (!hadLunch && TimeUtils.parseTime(currentTime24).isAfter(LocalTime.of(12, 15))) {
                String lunchStart = TimeUtils.roundTime24(currentTime24, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                String lunchEnd = TimeUtils.addMinutes(lunchStart, 60);

                ItineraryItemDto lunchItem = new ItineraryItemDto();
                lunchItem.setDayNumber(dayNumber);
                lunchItem.setScheduleDate(targetDate);
                lunchItem.setStartTime(TimeUtils.formatAmPm(lunchStart));
                lunchItem.setEndTime(TimeUtils.formatAmPm(lunchEnd));
                lunchItem.setActivity("🍴 Lunch Break & Local Specialities");
                lunchItem.setLocation("Dining in " + destName);
                lunchItem.setActivityType("MEAL");
                lunchItem.setDurationMinutes(60);
                items.add(lunchItem);

                currentTime24 = lunchEnd;
                dayBreakMinutes += 60;
                hadLunch = true;
                currentTime24 = TimeUtils.roundUpTime24(TimeUtils.addMinutes(currentTime24, 15), TimeUtils.DEFAULT_INTERVAL_MINUTES);
            }

            String actStart = TimeUtils.roundUpTime24(TimeUtils.addMinutes(currentTime24, travelTime), TimeUtils.DEFAULT_INTERVAL_MINUTES);
            String actEnd = TimeUtils.addMinutes(actStart, visitDuration);

            if (TimeUtils.parseTime(actEnd).isAfter(TimeUtils.parseTime(dailyEnd))) {
                break;
            }

            ItineraryItemDto visitItem = new ItineraryItemDto();
            visitItem.setDayNumber(dayNumber);
            visitItem.setScheduleDate(targetDate);
            visitItem.setStartTime(TimeUtils.formatAmPm(actStart));
            visitItem.setEndTime(TimeUtils.formatAmPm(actEnd));
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

            currentTime24 = TimeUtils.roundUpTime24(TimeUtils.addMinutes(actEnd, defaultBufferMinutes), TimeUtils.DEFAULT_INTERVAL_MINUTES);
            dayBreakMinutes += defaultBufferMinutes;
        }

        // Evening Hotel Return
        String eveningStart = TimeUtils.roundTime24(currentTime24, TimeUtils.DEFAULT_INTERVAL_MINUTES);
        String eveningEnd = TimeUtils.addMinutes(eveningStart, 60);

        ItineraryItemDto eveningItem = new ItineraryItemDto();
        eveningItem.setDayNumber(dayNumber);
        eveningItem.setScheduleDate(targetDate);
        eveningItem.setStartTime(TimeUtils.formatAmPm(eveningStart));
        eveningItem.setEndTime(TimeUtils.formatAmPm(eveningEnd));
        eveningItem.setActivity("🏨 Return & Relax at " + hotel);
        eveningItem.setLocation(hotel);
        eveningItem.setActivityType("HOTEL");
        eveningItem.setDurationMinutes(60);
        items.add(eveningItem);
        dayBreakMinutes += 60;

        items.sort((a, b) -> {
            int cmp = TimeUtils.compareItineraryTimes(a.getStartTime(), b.getStartTime());
            if (cmp != 0) return cmp;
            int typeCmp = Integer.compare(TimeUtils.getActivityTypeOrder(a.getActivityType()), TimeUtils.getActivityTypeOrder(b.getActivityType()));
            if (typeCmp != 0) return typeCmp;
            long idA = a.getId() != null ? a.getId() : 0L;
            long idB = b.getId() != null ? b.getId() : 0L;
            return Long.compare(idA, idB);
        });
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

    private List<SightseeingPlaceDto> getOrGeneratePlacesForCity(Destination dest, String cityName) {
        if (dest != null && dest.getPlaces() != null && !dest.getPlaces().isEmpty()) {
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

        return recommendationService.getRecommendedPlaces(cityName, null, "popularity", null);
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

    public static String normalizePlaceKey(String placeName) {
        if (placeName == null) return "";
        return placeName.trim().toLowerCase()
                .replaceAll("(?i)^(visit|explore|see|tour|check\\s*in|depart|travel\\s*to)\\s+", "")
                .replaceAll("[^a-z0-9]", "");
    }
}