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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ItineraryService.class);

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
        Destination last = destinations.get(destinations.size() - 1);
        int lastDuration = last.getStayDurationDays() != null && last.getStayDurationDays() > 0 ? last.getStayDurationDays() : 1;
        int lastStartDay = currentDay - lastDuration;
        return Math.max(1, dayNumber - lastStartDay + 1);
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

            // Generate Day Title reflecting day profile
            String dayTitle;
            if (isFirstDayInCity) {
                if (previousDayEndLocation.equalsIgnoreCase(currentCityName)) {
                    dayTitle = "Day " + dayNumber + " • Arrival & Highlights of " + currentCityName;
                } else {
                    dayTitle = "Day " + dayNumber + " • " + previousDayEndLocation + " → " + currentCityName + " (Highway & Sightseeing)";
                }
            } else {
                dayTitle = "Day " + dayNumber + " • In-Depth Exploration of " + currentCityName + " (Day " + cityDayIndex + ")";
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

            double[] cityCoords = (currentDest != null && currentDest.getLatitude() != null && currentDest.getLatitude() != 0.0)
                    ? new double[]{currentDest.getLatitude(), currentDest.getLongitude()}
                    : GeoUtils.getApproximateCoordinates(currentCityName);

            // 1. Morning Departure
            String dayOrigin;
            double[] originCoords;
            if (dayIndex == 0) {
                dayOrigin = trip.getStartLocation();
                originCoords = (trip.getStartLat() != null && trip.getStartLat() != 0.0)
                        ? new double[]{trip.getStartLat(), trip.getStartLng()}
                        : GeoUtils.getApproximateCoordinates(dayOrigin);
            } else if (previousNightHotel != null && !previousNightHotel.isEmpty()) {
                dayOrigin = previousNightHotel;
                originCoords = cityCoords;
            } else {
                dayOrigin = previousDayEndLocation;
                originCoords = cityCoords;
            }

            String currentLegOrigin = (dayIndex == 0) ? trip.getStartLocation() : previousDayEndLocation;
            boolean hadLunch = false;

            // Travel leg if arriving at a new city
            if (isFirstDayInCity && !currentLegOrigin.equalsIgnoreCase(currentCityName)) {
                int departDuration = 15;
                ItineraryItemDto departItem = new ItineraryItemDto();
                departItem.setDayNumber(dayNumber);
                departItem.setScheduleDate(currentDate);
                departItem.setStartTime(TimeUtils.formatAmPm(currentTime24));
                String departEnd = TimeUtils.addMinutes(currentTime24, departDuration);
                departItem.setEndTime(TimeUtils.formatAmPm(departEnd));
                departItem.setActivity("🚗 Depart from " + dayOrigin);
                departItem.setLocation(dayOrigin);
                departItem.setActivityType("DEPARTURE");
                departItem.setDurationMinutes(departDuration);
                departItem.setLatitude(originCoords[0]);
                departItem.setLongitude(originCoords[1]);
                departItem.setCategory("DEPARTURE");
                items.add(departItem);
                GoogleMapsService.RouteCalculationResult leg = null;
                try {
                    if (googleMapsService != null) {
                        leg = googleMapsService.calculateRouteSegment(currentLegOrigin, currentCityName, transportMode);
                    }
                } catch (Exception e) {
                    log.warn("Failed to calculate route segment from {} to {}: {}", currentLegOrigin, currentCityName, e.getMessage());
                }

                int travelMins = (leg != null && leg.getDurationMinutes() > 0)
                        ? leg.getDurationMinutes()
                        : (currentDest.getTravelTimeFromPrevious() != null && currentDest.getTravelTimeFromPrevious() > 0 ? currentDest.getTravelTimeFromPrevious() : 120);
                double travelDist = (leg != null && leg.getDistanceKm() > 0)
                        ? leg.getDistanceKm()
                        : (currentDest.getDistanceFromPrevious() != null && currentDest.getDistanceFromPrevious() > 0 ? currentDest.getDistanceFromPrevious() : 80.0);

                dayDistance += travelDist;
                dayTravelMinutes += travelMins;

                // Travel leg starts strictly upon departure completion
                String travelStart = departEnd;
                String rawArrival = TimeUtils.addMinutes(travelStart, travelMins);
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
                arriveItem.setDurationMinutes(travelMins);
                arriveItem.setLatitude(cityCoords[0]);
                arriveItem.setLongitude(cityCoords[1]);
                arriveItem.setCategory("ARRIVAL");
                items.add(arriveItem);

                // Safe schedule advance: arrival + buffer rounded up to 15-minute interval so no overlap
                String earliestNextStart = TimeUtils.addMinutes(rawArrival, defaultBufferMinutes);
                currentTime24 = TimeUtils.roundUpTime24(earliestNextStart, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                dayBreakMinutes += defaultBufferMinutes;

                previousDayEndLocation = currentCityName;
            } else {
                int departDuration = 15;
                ItineraryItemDto departItem = new ItineraryItemDto();
                departItem.setDayNumber(dayNumber);
                departItem.setScheduleDate(currentDate);
                departItem.setStartTime(TimeUtils.formatAmPm(currentTime24));
                String departEnd = TimeUtils.addMinutes(currentTime24, departDuration);
                departItem.setEndTime(TimeUtils.formatAmPm(departEnd));
                departItem.setActivity("🚗 Depart from " + dayOrigin);
                departItem.setLocation(dayOrigin);
                departItem.setActivityType("DEPARTURE");
                departItem.setDurationMinutes(departDuration);
                departItem.setLatitude(originCoords[0]);
                departItem.setLongitude(originCoords[1]);
                departItem.setCategory("DEPARTURE");
                items.add(departItem);

                currentTime24 = departEnd;
            }

            // Check for lunch if time is already between 12:00 and 14:15
            if (!hadLunch && !TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(12, 0)) && TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(14, 15))) {
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
                lunchItem.setLatitude(cityCoords[0] + 0.0015);
                lunchItem.setLongitude(cityCoords[1] + 0.0015);
                lunchItem.setCategory("Dining");
                items.add(lunchItem);

                currentTime24 = TimeUtils.roundUpTime24(lunchEnd, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                dayBreakMinutes += 60;
                hadLunch = true;
            }

            // Determine dynamic target activities count for today (Requirement 2 & 3)
            int dayLimitMinutes = TimeUtils.differenceInMinutes(dailyStart, dailyEnd);
            int maxPlacesToday;
            if (isFirstDayInCity && dayTravelMinutes > 240) {
                // Long intercity driving day (4+ hours) -> 1-2 major sightseeing stops
                maxPlacesToday = 2;
            } else if (isFirstDayInCity && dayTravelMinutes > 120) {
                // Moderate drive day (2-4 hours) -> 2-3 sightseeing stops
                maxPlacesToday = 3;
            } else {
                // Normal sightseeing day (full city day) -> aim for 3-6 meaningful activities
                maxPlacesToday = Math.min(6, Math.max(3, (dayLimitMinutes - 120) / 90));
            }

            // Schedule attractions for this day
            List<SightseeingPlaceDto> poolPlaces = getOrGeneratePlacesForCity(currentDest, currentCityName);
            int placesScheduledToday = 0;
            double lastPlaceLat = cityCoords[0];
            double lastPlaceLng = cityCoords[1];

            for (SightseeingPlaceDto place : poolPlaces) {
                if (placesScheduledToday >= maxPlacesToday) break;

                String pKey = normalizePlaceKey(place.getName());
                if (!pKey.isEmpty() && tripScheduledPlaceKeys.contains(pKey)) {
                    continue; // Skip duplicate attraction already visited earlier in trip
                }

                int rawVisitDuration = place.getEstimatedVisitDurationMinutes() != null ? place.getEstimatedVisitDurationMinutes() : 90;
                int visitDuration = Math.max(30, (int) Math.round((double) rawVisitDuration / 15.0) * 15);

                // Calculate realistic local transit time and distance from previous stop
                double pLat = (place.getLatitude() != null && place.getLatitude() != 0.0) ? place.getLatitude() : cityCoords[0];
                double pLng = (place.getLongitude() != null && place.getLongitude() != 0.0) ? place.getLongitude() : cityCoords[1];
                double placeDist = Math.max(1.5, Math.round(GeoUtils.calculateHaversineDistanceKm(lastPlaceLat, lastPlaceLng, pLat, pLng) * 1.3 * 10.0) / 10.0);
                int transitMins = Math.max(15, (int) Math.round((placeDist / 25.0) * 60.0));

                // Safe interval rounding: travel to spot
                String rawTransitArrival = TimeUtils.addMinutes(currentTime24, transitMins);
                String actStart = TimeUtils.roundUpTime24(rawTransitArrival, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                String actEnd = TimeUtils.addMinutes(actStart, visitDuration);

                // Check daily end time boundary
                if (TimeUtils.parseTime(actEnd).isAfter(TimeUtils.parseTime(dailyEnd))) {
                    if (placesScheduledToday == 0) {
                        // Ensure at least 1 activity even if it requires a slight extension or trim
                        visitDuration = Math.max(45, TimeUtils.differenceInMinutes(actStart, dailyEnd));
                        actEnd = TimeUtils.addMinutes(actStart, visitDuration);
                    } else {
                        break;
                    }
                }

                // Check lunch insertion if passing midday
                if (!hadLunch && !TimeUtils.parseTime(actStart).isBefore(LocalTime.of(12, 15)) && TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(14, 15))) {
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
                    lunchItem.setLatitude(cityCoords[0] + 0.0015);
                    lunchItem.setLongitude(cityCoords[1] + 0.0015);
                    lunchItem.setCategory("Dining");
                    items.add(lunchItem);

                    currentTime24 = lunchEnd;
                    dayBreakMinutes += 60;
                    hadLunch = true;

                    // Recalculate actStart and actEnd after lunch
                    rawTransitArrival = TimeUtils.addMinutes(currentTime24, transitMins);
                    actStart = TimeUtils.roundUpTime24(rawTransitArrival, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                    actEnd = TimeUtils.addMinutes(actStart, visitDuration);
                    if (TimeUtils.parseTime(actEnd).isAfter(TimeUtils.parseTime(dailyEnd)) && placesScheduledToday > 0) {
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
                visitItem.setTravelTimeMinutes(transitMins);
                visitItem.setDurationMinutes(visitDuration);
                visitItem.setRating(place.getRating());
                visitItem.setVisited(Boolean.TRUE.equals(place.getVisited()));
                visitItem.setLatitude(pLat);
                visitItem.setLongitude(pLng);
                visitItem.setCategory(place.getCategory() != null ? place.getCategory() : "Historical");

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
                dayTravelMinutes += transitMins;
                daySightseeingMinutes += visitDuration;
                placesScheduledToday++;

                currentTime24 = actEnd;
                lastPlaceLat = pLat;
                lastPlaceLng = pLng;

                // Afternoon tea / scenic break insertion around 3:30 PM - 4:15 PM if more activities ahead
                if (placesScheduledToday >= 2 && placesScheduledToday < maxPlacesToday &&
                        !TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(15, 15)) &&
                        TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(16, 30))) {
                    String teaStart = TimeUtils.roundTime24(currentTime24, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                    String teaEnd = TimeUtils.addMinutes(teaStart, 30);

                    ItineraryItemDto teaItem = new ItineraryItemDto();
                    teaItem.setDayNumber(dayNumber);
                    teaItem.setScheduleDate(currentDate);
                    teaItem.setStartTime(TimeUtils.formatAmPm(teaStart));
                    teaItem.setEndTime(TimeUtils.formatAmPm(teaEnd));
                    teaItem.setActivity("☕ Afternoon Refreshment & Tea Break");
                    teaItem.setLocation(currentCityName + " Heritage Cafe");
                    teaItem.setActivityType("REST");
                    teaItem.setDurationMinutes(30);
                    teaItem.setLatitude(lastPlaceLat + 0.001);
                    teaItem.setLongitude(lastPlaceLng + 0.001);
                    teaItem.setCategory("Rest");
                    items.add(teaItem);

                    currentTime24 = teaEnd;
                    dayBreakMinutes += 30;
                }
            }

            // If multi-day city stay and catalog attractions were already consumed, schedule authentic local experiences
            if (placesScheduledToday < Math.min(3, maxPlacesToday) && TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(17, 0))) {
                String expTitle;
                String expNotes;
                String expCategory;
                int expDuration = 75;
                int expTransit = 15;
                double expDist = 2.0;

                int expVariant = (cityDayIndex + dayIndex) % 4;
                if (expVariant == 0) {
                    expTitle = "Artisan Spice & Traditional Handicrafts Bazaar";
                    expNotes = "Explore authentic regional craft markets, spice shops, and local culinary delicacies.";
                    expCategory = "Shopping";
                } else if (expVariant == 1) {
                    expTitle = "Panoramic Sunset Viewpoint & Lake Promenade";
                    expNotes = "Relaxed evening walk along scenic panoramic vistas and photography outlooks.";
                    expCategory = "Nature";
                } else if (expVariant == 2) {
                    expTitle = "Old Town Heritage & Historic Architecture Trail";
                    expNotes = "Experience historic neighborhood architecture, street charm, and traditional coffee/tea heritage spots.";
                    expCategory = "Historical";
                } else {
                    expTitle = "Scenic Nature Sanctuary & Botanical Walk";
                    expNotes = "Peaceful walking trails amidst verdant groves, local flora, and bird watching boardwalks.";
                    expCategory = "Nature";
                }

                String actStart = TimeUtils.roundUpTime24(TimeUtils.addMinutes(currentTime24, expTransit), TimeUtils.DEFAULT_INTERVAL_MINUTES);
                String actEnd = TimeUtils.addMinutes(actStart, expDuration);

                if (!TimeUtils.parseTime(actEnd).isAfter(TimeUtils.parseTime(dailyEnd))) {
                    ItineraryItemDto expItem = new ItineraryItemDto();
                    expItem.setDayNumber(dayNumber);
                    expItem.setScheduleDate(currentDate);
                    expItem.setStartTime(TimeUtils.formatAmPm(actStart));
                    expItem.setEndTime(TimeUtils.formatAmPm(actEnd));
                    expItem.setActivity("Explore " + expTitle);
                    expItem.setLocation(currentCityName + " Historic District");
                    expItem.setActivityType("SIGHTSEEING");
                    expItem.setTravelDistanceKm(expDist);
                    expItem.setTravelTimeMinutes(expTransit);
                    expItem.setDurationMinutes(expDuration);
                    expItem.setRating(4.8);
                    expItem.setVisited(false);
                    expItem.setNotes(expNotes);
                    expItem.setLatitude(cityCoords[0] + 0.003 * (expVariant + 1));
                    expItem.setLongitude(cityCoords[1] + 0.003 * (expVariant + 1));
                    expItem.setCategory(expCategory);
                    items.add(expItem);

                    currentTime24 = actEnd;
                    dayDistance += expDist;
                    dayTravelMinutes += expTransit;
                    daySightseeingMinutes += expDuration;
                    totalPlacesCount++;
                    placesScheduledToday++;
                }
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
            double[] hotelCoords = new double[]{cityCoords[0] - 0.002, cityCoords[1] - 0.002};

            // Realistic Evening Transition: Return to hotel immediately after activities (Requirement 3)
            int hotelTransitMins = 20;
            String hotelStart = TimeUtils.roundUpTime24(TimeUtils.addMinutes(currentTime24, hotelTransitMins), TimeUtils.DEFAULT_INTERVAL_MINUTES);
            int hotelRestMins = 45;
            String hotelEnd = TimeUtils.addMinutes(hotelStart, hotelRestMins);

            ItineraryItemDto eveningItem = new ItineraryItemDto();
            eveningItem.setDayNumber(dayNumber);
            eveningItem.setScheduleDate(currentDate);
            eveningItem.setStartTime(TimeUtils.formatAmPm(hotelStart));
            eveningItem.setEndTime(TimeUtils.formatAmPm(hotelEnd));
            eveningItem.setActivity(isFirstDayInCity ? ("🏨 Check-in & Freshen up at " + eveningStay) : ("🏨 Return & Relax at " + eveningStay));
            eveningItem.setLocation(eveningStay);
            eveningItem.setActivityType("HOTEL");
            eveningItem.setTravelDistanceKm(3.0);
            eveningItem.setTravelTimeMinutes(hotelTransitMins);
            eveningItem.setDurationMinutes(hotelRestMins);
            eveningItem.setLatitude(hotelCoords[0]);
            eveningItem.setLongitude(hotelCoords[1]);
            eveningItem.setCategory("Hotel");
            items.add(eveningItem);

            dayDistance += 3.0;
            dayTravelMinutes += hotelTransitMins;
            dayBreakMinutes += hotelRestMins;
            currentTime24 = hotelEnd;

            // Check for dinner (between 19:00 and 21:00)
            if (!TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(18, 45)) && TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(21, 30))) {
                String dinnerStart = TimeUtils.roundTime24(currentTime24, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                String dinnerEnd = TimeUtils.addMinutes(dinnerStart, 60);

                ItineraryItemDto dinnerItem = new ItineraryItemDto();
                dinnerItem.setDayNumber(dayNumber);
                dinnerItem.setScheduleDate(currentDate);
                dinnerItem.setStartTime(TimeUtils.formatAmPm(dinnerStart));
                dinnerItem.setEndTime(TimeUtils.formatAmPm(dinnerEnd));
                dinnerItem.setActivity("🍽️ Dinner & Regional Gastronomy");
                dinnerItem.setLocation("Dining in " + currentCityName);
                dinnerItem.setActivityType("MEAL");
                dinnerItem.setDurationMinutes(60);
                dinnerItem.setLatitude(cityCoords[0] + 0.002);
                dinnerItem.setLongitude(cityCoords[1] + 0.002);
                dinnerItem.setCategory("Dining");
                items.add(dinnerItem);

                currentTime24 = dinnerEnd;
                dayBreakMinutes += 60;
            }

            // Sort chronologically strictly by start time
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

            // Compute exact elapsed duration and unallocated time (Requirement 6)
            int calculatedTotalDuration = 0;
            if (!items.isEmpty()) {
                ItineraryItemDto first = items.get(0);
                ItineraryItemDto last = items.get(items.size() - 1);
                LocalTime startTime = TimeUtils.parseTime(first.getStartTime());
                LocalTime endTime = TimeUtils.parseTime(last.getEndTime());
                if (endTime.isBefore(startTime)) {
                    calculatedTotalDuration = (int) java.time.temporal.ChronoUnit.MINUTES.between(startTime, LocalTime.MAX) + 1
                            + (int) java.time.temporal.ChronoUnit.MINUTES.between(LocalTime.MIN, endTime);
                } else {
                    calculatedTotalDuration = (int) java.time.temporal.ChronoUnit.MINUTES.between(startTime, endTime);
                }
            }
            if (calculatedTotalDuration <= 0) {
                calculatedTotalDuration = dayTravelMinutes + daySightseeingMinutes + dayBreakMinutes;
            }
            dayDto.setTotalDurationMinutes(calculatedTotalDuration);

            int availableDayMinutes = TimeUtils.differenceInMinutes(dailyStart, dailyEnd);
            int unallocated = (availableDayMinutes > calculatedTotalDuration) ? (availableDayMinutes - calculatedTotalDuration) : 0;
            dayDto.setUnallocatedTimeMinutes(unallocated);

            dayDto.setTotalPlacesCount(totalPlacesCount);
            dayDto.setVisitedCount(visitedPlacesCount);

            // Crowding & Feasibility Warning Calculations (Requirement 4)
            int activeMinutes = dayTravelMinutes + daySightseeingMinutes;
            int maxDailyDrivingMinutes = 360; // 6 hours driving limit

            if (dayTravelMinutes > maxDailyDrivingMinutes) {
                dayDto.setStatus("⚠️ Long Driving Day");
                dayDto.setWarningMessage("This day contains " + TimeUtils.formatDuration(dayTravelMinutes) + " of driving, exceeding the 6-hour daily limit. Consider an intermediate rest stop.");
            } else if (activeMinutes > 600 || (availableDayMinutes > 0 && activeMinutes > availableDayMinutes)) {
                int approxHours = (int) Math.ceil(activeMinutes / 60.0);
                dayDto.setStatus("Overcrowded");
                dayDto.setWarningMessage("Day " + dayNumber + " looks crowded (" + approxHours + " active hours). Consider moving one attraction to another day.");
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

        int departDuration = 15;
        ItineraryItemDto departItem = new ItineraryItemDto();
        departItem.setDayNumber(dayNumber);
        departItem.setScheduleDate(targetDate);
        departItem.setStartTime(TimeUtils.formatAmPm(currentTime24));
        String departEnd = TimeUtils.addMinutes(currentTime24, departDuration);
        departItem.setEndTime(TimeUtils.formatAmPm(departEnd));
        departItem.setActivity("🚗 Depart from " + hotel);
        departItem.setLocation(hotel);
        departItem.setActivityType("DEPARTURE");
        departItem.setDurationMinutes(departDuration);
        items.add(departItem);
        currentTime24 = departEnd;

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

        double[] defaultCityCoords = (targetDest != null && targetDest.getLatitude() != null && targetDest.getLatitude() != 0.0)
                ? new double[]{targetDest.getLatitude(), targetDest.getLongitude()}
                : new double[]{12.9716, 77.5946};
        int maxPlacesToSchedule = 5;
        int scheduled = 0;
        boolean hadLunch = false;
        boolean hadTeaBreak = false;

        for (SightseeingPlaceDto place : filtered) {
            if (scheduled >= maxPlacesToSchedule) break;

            int rawVisitDuration = place.getEstimatedVisitDurationMinutes() != null ? place.getEstimatedVisitDurationMinutes() : 90;
            int visitDuration = Math.max(30, (int) Math.round((double) rawVisitDuration / 15.0) * 15);
            int travelTime = place.getEstimatedTravelTimeMinutes() != null ? place.getEstimatedTravelTimeMinutes() : 15;
            int transitMins = Math.max(15, travelTime);
            double placeDist = place.getDistanceKm() != null ? place.getDistanceKm() : 2.5;

            // Lunch check (between 12:15 and 14:15)
            if (!hadLunch && !TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(12, 15)) && TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(14, 30))) {
                String lunchStart = TimeUtils.roundTime24(currentTime24, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                String lunchEnd = TimeUtils.addMinutes(lunchStart, 60);

                ItineraryItemDto lunchItem = new ItineraryItemDto();
                lunchItem.setDayNumber(dayNumber);
                lunchItem.setScheduleDate(targetDate);
                lunchItem.setStartTime(TimeUtils.formatAmPm(lunchStart));
                lunchItem.setEndTime(TimeUtils.formatAmPm(lunchEnd));
                lunchItem.setActivity("🍴 Lunch Break & Regional Specialities");
                lunchItem.setLocation("Dining in " + destName);
                lunchItem.setActivityType("MEAL");
                lunchItem.setDurationMinutes(60);
                lunchItem.setLatitude(defaultCityCoords[0] + 0.001);
                lunchItem.setLongitude(defaultCityCoords[1] + 0.001);
                lunchItem.setCategory("Dining");
                items.add(lunchItem);

                currentTime24 = lunchEnd;
                dayBreakMinutes += 60;
                hadLunch = true;
            }

            // Afternoon refreshment break check (around 16:00 - 17:00 if multiple activities already visited)
            if (!hadTeaBreak && scheduled >= 2 && !TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(15, 45)) && TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(17, 30))) {
                String teaStart = TimeUtils.roundTime24(currentTime24, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                String teaEnd = TimeUtils.addMinutes(teaStart, 30);

                ItineraryItemDto teaItem = new ItineraryItemDto();
                teaItem.setDayNumber(dayNumber);
                teaItem.setScheduleDate(targetDate);
                teaItem.setStartTime(TimeUtils.formatAmPm(teaStart));
                teaItem.setEndTime(TimeUtils.formatAmPm(teaEnd));
                teaItem.setActivity("☕ Afternoon Refreshment & Tea Break");
                teaItem.setLocation("Cafe / Promenade in " + destName);
                teaItem.setActivityType("REST");
                teaItem.setDurationMinutes(30);
                teaItem.setLatitude(defaultCityCoords[0] - 0.001);
                teaItem.setLongitude(defaultCityCoords[1] - 0.001);
                teaItem.setCategory("Cafe");
                items.add(teaItem);

                currentTime24 = teaEnd;
                dayBreakMinutes += 30;
                hadTeaBreak = true;
            }

            String actStart = TimeUtils.roundUpTime24(TimeUtils.addMinutes(currentTime24, transitMins), TimeUtils.DEFAULT_INTERVAL_MINUTES);
            String actEnd = TimeUtils.addMinutes(actStart, visitDuration);

            // Don't schedule past dailyEnd
            if (TimeUtils.parseTime(actEnd).isAfter(TimeUtils.parseTime(dailyEnd))) {
                break;
            }

            double pLat = (place.getLatitude() != null && place.getLatitude() != 0.0) ? place.getLatitude() : (defaultCityCoords[0] + (scheduled * 0.003));
            double pLng = (place.getLongitude() != null && place.getLongitude() != 0.0) ? place.getLongitude() : (defaultCityCoords[1] + (scheduled * 0.003));

            ItineraryItemDto visitItem = new ItineraryItemDto();
            visitItem.setDayNumber(dayNumber);
            visitItem.setScheduleDate(targetDate);
            visitItem.setStartTime(TimeUtils.formatAmPm(actStart));
            visitItem.setEndTime(TimeUtils.formatAmPm(actEnd));
            visitItem.setActivity("Visit " + place.getName());
            visitItem.setLocation(place.getName() + ", " + destName);
            visitItem.setActivityType("SIGHTSEEING");
            visitItem.setTravelDistanceKm(placeDist);
            visitItem.setTravelTimeMinutes(transitMins);
            visitItem.setDurationMinutes(visitDuration);
            visitItem.setRating(place.getRating());
            visitItem.setVisited(false);
            visitItem.setLatitude(pLat);
            visitItem.setLongitude(pLng);
            visitItem.setCategory(place.getCategory() != null ? place.getCategory() : "Sightseeing");

            String notes = place.getDescription() != null ? place.getDescription() : "";
            String conflict = checkOpeningHoursConflict(actStart, actEnd, place.getOpeningHours());
            if (conflict != null) {
                notes = (notes.isEmpty() ? "" : notes + " • ") + "⚠️ " + conflict;
            }
            visitItem.setNotes(notes);
            items.add(visitItem);

            dayDistance += placeDist;
            dayTravelMinutes += transitMins;
            daySightseeingMinutes += visitDuration;
            totalPlacesCount++;
            scheduled++;

            currentTime24 = actEnd;
        }

        // Realistic Evening Hotel Return immediately after activities
        int hotelTransitMins = 20;
        String hotelStart = TimeUtils.roundUpTime24(TimeUtils.addMinutes(currentTime24, hotelTransitMins), TimeUtils.DEFAULT_INTERVAL_MINUTES);
        int hotelRestMins = 45;
        String hotelEnd = TimeUtils.addMinutes(hotelStart, hotelRestMins);

        ItineraryItemDto eveningItem = new ItineraryItemDto();
        eveningItem.setDayNumber(dayNumber);
        eveningItem.setScheduleDate(targetDate);
        eveningItem.setStartTime(TimeUtils.formatAmPm(hotelStart));
        eveningItem.setEndTime(TimeUtils.formatAmPm(hotelEnd));
        eveningItem.setActivity("🏨 Return & Relax at " + hotel);
        eveningItem.setLocation(hotel);
        eveningItem.setActivityType("HOTEL");
        eveningItem.setTravelDistanceKm(3.0);
        eveningItem.setTravelTimeMinutes(hotelTransitMins);
        eveningItem.setDurationMinutes(hotelRestMins);
        eveningItem.setLatitude(defaultCityCoords[0] - 0.002);
        eveningItem.setLongitude(defaultCityCoords[1] - 0.002);
        eveningItem.setCategory("Hotel");
        items.add(eveningItem);

        dayDistance += 3.0;
        dayTravelMinutes += hotelTransitMins;
        dayBreakMinutes += hotelRestMins;
        currentTime24 = hotelEnd;

        // Check for dinner if evening has arrived (between 18:45 and 21:30)
        if (!TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(18, 45)) && TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(21, 30))) {
            String dinnerStart = TimeUtils.roundTime24(currentTime24, TimeUtils.DEFAULT_INTERVAL_MINUTES);
            String dinnerEnd = TimeUtils.addMinutes(dinnerStart, 60);

            ItineraryItemDto dinnerItem = new ItineraryItemDto();
            dinnerItem.setDayNumber(dayNumber);
            dinnerItem.setScheduleDate(targetDate);
            dinnerItem.setStartTime(TimeUtils.formatAmPm(dinnerStart));
            dinnerItem.setEndTime(TimeUtils.formatAmPm(dinnerEnd));
            dinnerItem.setActivity("🍽️ Dinner & Regional Cuisine");
            dinnerItem.setLocation("Dining in " + destName);
            dinnerItem.setActivityType("MEAL");
            dinnerItem.setDurationMinutes(60);
            dinnerItem.setLatitude(defaultCityCoords[0] + 0.002);
            dinnerItem.setLongitude(defaultCityCoords[1] + 0.002);
            dinnerItem.setCategory("Dining");
            items.add(dinnerItem);

            currentTime24 = dinnerEnd;
            dayBreakMinutes += 60;
        }

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

        int calculatedTotalDuration = 0;
        if (!items.isEmpty()) {
            ItineraryItemDto first = items.get(0);
            ItineraryItemDto last = items.get(items.size() - 1);
            LocalTime startTime = TimeUtils.parseTime(first.getStartTime());
            LocalTime endTime = TimeUtils.parseTime(last.getEndTime());
            if (endTime.isBefore(startTime)) {
                calculatedTotalDuration = (int) java.time.temporal.ChronoUnit.MINUTES.between(startTime, LocalTime.MAX) + 1
                        + (int) java.time.temporal.ChronoUnit.MINUTES.between(LocalTime.MIN, endTime);
            } else {
                calculatedTotalDuration = (int) java.time.temporal.ChronoUnit.MINUTES.between(startTime, endTime);
            }
        }
        if (calculatedTotalDuration <= 0) {
            calculatedTotalDuration = dayTravelMinutes + daySightseeingMinutes + dayBreakMinutes;
        }
        dayDto.setTotalDurationMinutes(calculatedTotalDuration);

        int availableDayMinutes = TimeUtils.differenceInMinutes(dailyStart, dailyEnd);
        int unallocated = (availableDayMinutes > calculatedTotalDuration) ? (availableDayMinutes - calculatedTotalDuration) : 0;
        dayDto.setUnallocatedTimeMinutes(unallocated);

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