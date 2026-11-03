package com.example.tripplanner.service;

import com.example.tripplanner.dto.DayItineraryDto;
import com.example.tripplanner.dto.ItineraryItemDto;
import com.example.tripplanner.dto.SightseeingPlaceDto;
import com.example.tripplanner.dto.TimelineTransitionDto;
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

    public static class TransitStopInfo {
        private final String name;
        private final String location;
        private final String activityType; // "REST" or "MEAL"
        private final String category;
        private final double lat;
        private final double lng;
        private final int durationMinutes;
        private final double distFromPrevKm;
        private final int transitMinutes;

        public TransitStopInfo(String name, String location, String activityType, String category,
                               double lat, double lng, int durationMinutes, double distFromPrevKm, int transitMinutes) {
            this.name = name;
            this.location = location;
            this.activityType = activityType;
            this.category = category;
            this.lat = lat;
            this.lng = lng;
            this.durationMinutes = durationMinutes;
            this.distFromPrevKm = distFromPrevKm;
            this.transitMinutes = transitMinutes;
        }

        public String getName() { return name; }
        public String getLocation() { return location; }
        public String getActivityType() { return activityType; }
        public String getCategory() { return category; }
        public double getLat() { return lat; }
        public double getLng() { return lng; }
        public int getDurationMinutes() { return durationMinutes; }
        public double getDistFromPrevKm() { return distFromPrevKm; }
        public int getTransitMinutes() { return transitMinutes; }
    }

    public static List<TransitStopInfo> resolveHighwayTransitStops(String origin, String destination, double totalDistKm, int totalTransitMins) {
        List<TransitStopInfo> stops = new ArrayList<>();
        String normOrigin = (origin != null) ? origin.toLowerCase().trim() : "";
        String normDest = (destination != null) ? destination.toLowerCase().trim() : "";

        // 1. Hyderabad -> Bengaluru / Bangalore (~570 km, ~8-9 hrs via NH44)
        if (normOrigin.contains("hyderabad") && (normDest.contains("bengaluru") || normDest.contains("bangalore"))) {
            stops.add(new TransitStopInfo("☕ Morning Refreshment & Highway Break", "Kurnool NH44 Food Plaza", "REST", "Rest", 15.8281, 78.0373, 30, 200.0, 165));
            stops.add(new TransitStopInfo("🍴 Highway Lunch Break & Fuel Stop", "Anantapur NH44 Food Court", "MEAL", "Dining", 14.6819, 77.6006, 60, 140.0, 110));
            return stops;
        }
        if ((normOrigin.contains("bengaluru") || normOrigin.contains("bangalore")) && normDest.contains("hyderabad")) {
            stops.add(new TransitStopInfo("☕ Morning Refreshment & Highway Break", "Anantapur NH44 Food Plaza", "REST", "Rest", 14.6819, 77.6006, 30, 215.0, 175));
            stops.add(new TransitStopInfo("🍴 Highway Lunch Break & Fuel Stop", "Kurnool NH44 Food Court", "MEAL", "Dining", 15.8281, 78.0373, 60, 140.0, 110));
            return stops;
        }

        // 2. Hyderabad -> Nagpur (NH44 North Transit)
        if (normOrigin.contains("hyderabad") && normDest.contains("nagpur")) {
            stops.add(new TransitStopInfo("☕ Morning Highway Tea Break", "Kamareddy NH44 Court", "REST", "Rest", 18.3242, 78.3392, 30, 120.0, 100));
            stops.add(new TransitStopInfo("🍴 Highway Lunch Break & Fuel Stop", "Adilabad Bypass Plaza", "MEAL", "Dining", 19.6641, 78.5320, 60, 185.0, 150));
            return stops;
        }

        // 3. Nagpur -> Gwalior / Jhansi (NH44 Central Transit)
        if (normOrigin.contains("nagpur") && (normDest.contains("gwalior") || normDest.contains("jhansi"))) {
            stops.add(new TransitStopInfo("☕ Morning Highway Break", "Sagar Highway Plaza", "REST", "Rest", 23.8388, 78.7378, 30, 220.0, 180));
            stops.add(new TransitStopInfo("🍴 Highway Lunch Break", "Jhansi Bypass Food Plaza", "MEAL", "Dining", 25.4484, 78.5685, 60, 180.0, 150));
            return stops;
        }

        // 4. Gwalior -> Delhi (Yamuna / NH44 Transit)
        if (normOrigin.contains("gwalior") && normDest.contains("delhi")) {
            stops.add(new TransitStopInfo("☕ Highway Tea Break", "Mathura Expressway Plaza", "REST", "Rest", 27.4924, 77.6737, 30, 170.0, 140));
            stops.add(new TransitStopInfo("🍴 Highway Lunch & Gateway Plaza", "Faridabad Gateway Plaza", "MEAL", "Dining", 28.4089, 77.3178, 60, 120.0, 100));
            return stops;
        }

        // 5. Hyderabad -> Goa (~650 km via Raichur / Hubballi)
        if (normOrigin.contains("hyderabad") && normDest.contains("goa")) {
            stops.add(new TransitStopInfo("☕ Morning Highway Tea Break", "Raichur Bypass Food Plaza", "REST", "Rest", 16.2076, 77.3463, 30, 210.0, 170));
            stops.add(new TransitStopInfo("🍴 Highway Lunch Break & Rest Stop", "Hubballi Highway Food Court", "MEAL", "Dining", 15.3647, 75.1240, 60, 220.0, 180));
            return stops;
        }

        // 6. Hyderabad -> Pune / Mumbai (~560 - 710 km via NH65)
        if (normOrigin.contains("hyderabad") && (normDest.contains("pune") || normDest.contains("mumbai"))) {
            stops.add(new TransitStopInfo("☕ Morning Highway Refreshment", "Humnabad NH65 Plaza", "REST", "Rest", 17.7667, 77.1333, 30, 160.0, 140));
            stops.add(new TransitStopInfo("🍴 Highway Lunch Break", "Solapur NH65 Food Plaza", "MEAL", "Dining", 17.6599, 75.9064, 60, 150.0, 130));
            return stops;
        }

        // 7. Mumbai / Pune -> Goa (~450 - 580 km via NH48)
        if ((normOrigin.contains("mumbai") || normOrigin.contains("pune")) && normDest.contains("goa")) {
            stops.add(new TransitStopInfo("☕ Morning Highway Break", "Satara Highway Plaza", "REST", "Rest", 17.6805, 74.0183, 30, 160.0, 140));
            stops.add(new TransitStopInfo("🍴 Highway Lunch Break", "Kolhapur NH48 Food Court", "MEAL", "Dining", 16.7050, 74.2433, 60, 120.0, 105));
            return stops;
        }

        // 8. Delhi -> Jaipur (~280 km via NH48)
        if (normOrigin.contains("delhi") && normDest.contains("jaipur")) {
            stops.add(new TransitStopInfo("☕ Highway Refreshment Break", "Neemrana Highway Food Court", "REST", "Rest", 27.9892, 76.3860, 30, 125.0, 110));
            return stops;
        }

        // 9. Generic long highway drive (> 330 mins)
        if (totalTransitMins > 330 && totalDistKm > 200.0) {
            double[] oCoord = GeoUtils.getApproximateCoordinates(origin);
            double[] dCoord = GeoUtils.getApproximateCoordinates(destination);

            double lat1 = oCoord[0] + (dCoord[0] - oCoord[0]) * 0.35;
            double lon1 = oCoord[1] + (dCoord[1] - oCoord[1]) * 0.35;
            double dist1 = Math.round(totalDistKm * 0.35 * 10.0) / 10.0;
            int mins1 = (int) Math.round(totalTransitMins * 0.35);
            stops.add(new TransitStopInfo("☕ Morning Highway Refreshment & Break", "Highway Rest Stop Plaza", "REST", "Rest", lat1, lon1, 30, dist1, mins1));

            double lat2 = oCoord[0] + (dCoord[0] - oCoord[0]) * 0.68;
            double lon2 = oCoord[1] + (dCoord[1] - oCoord[1]) * 0.68;
            double dist2 = Math.round(totalDistKm * 0.33 * 10.0) / 10.0;
            int mins2 = (int) Math.round(totalTransitMins * 0.33);
            stops.add(new TransitStopInfo("🍴 Highway Lunch Break & Fuel Stop", "Highway Food Court & Fuel Plaza", "MEAL", "Dining", lat2, lon2, 60, dist2, mins2));
        }

        return stops;
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

            // Check for extreme multi-day road corridor (e.g. Hyderabad -> Delhi, 1580 km)
            boolean isHydToDelhiCorridor = (trip.getStartLocation() != null && trip.getStartLocation().toLowerCase().contains("hyderabad"))
                    && ((currentDest != null && currentDest.getName() != null && currentDest.getName().toLowerCase().contains("delhi"))
                        || (currentCityName.toLowerCase().contains("delhi")));

            if (isHydToDelhiCorridor) {
                if (dayIndex == 0) {
                    currentCityName = "Nagpur";
                    isFirstDayInCity = true;
                } else if (dayIndex == 1) {
                    currentCityName = "Gwalior";
                    isFirstDayInCity = true;
                } else if (dayIndex == 2) {
                    currentCityName = "Delhi";
                    isFirstDayInCity = true;
                } else {
                    currentCityName = "Delhi";
                    isFirstDayInCity = false;
                    cityDayIndex = (int) dayIndex - 1;
                }
            }

            // Generate Day Title reflecting day profile
            String dayTitle;
            if (isHydToDelhiCorridor) {
                if (dayIndex == 0) {
                    dayTitle = "Day 1 • Hyderabad → Nagpur (NH44 North Transit Leg)";
                } else if (dayIndex == 1) {
                    dayTitle = "Day 2 • Nagpur → Gwalior (Central India Highway Transit)";
                } else if (dayIndex == 2) {
                    dayTitle = "Day 3 • Gwalior → Delhi (Arrival & Evening Exploration)";
                } else {
                    dayTitle = "Day " + dayNumber + " • In-Depth Exploration of Delhi (Day " + cityDayIndex + ")";
                }
            } else if (isFirstDayInCity) {
                if (previousDayEndLocation.equalsIgnoreCase(currentCityName)) {
                    dayTitle = "Day " + dayNumber + " • Arrival & Highlights of " + currentCityName;
                } else {
                    dayTitle = "Day " + dayNumber + " • " + previousDayEndLocation + " → " + currentCityName + " (Highway & Sightseeing)";
                }
            } else {
                dayTitle = "Day " + dayNumber + " • In-Depth Exploration of " + currentCityName + " (Day " + cityDayIndex + ")";
            }

            DayItineraryDto dayDto = new DayItineraryDto(dayNumber, currentDate, dayTitle);
            dayDto.setDestinationName(currentCityName);
            List<ItineraryItemDto> items = new ArrayList<>();

            String currentTime24 = dailyStart;
            double dayDistance = 0.0;
            int dayTravelMinutes = 0;
            int daySightseeingMinutes = 0;
            int dayBreakMinutes = 0;
            int totalPlacesCount = 0;
            int visitedPlacesCount = 0;

            double[] cityCoords = (currentDest != null && currentDest.getLatitude() != null && currentDest.getLatitude() != 0.0 && !isHydToDelhiCorridor)
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
            Double firstStopTravelDist = null;
            Integer firstStopTravelMins = null;
            boolean isHighwayTransitDay = false;

            if (isFirstDayInCity && !currentLegOrigin.equalsIgnoreCase(currentCityName)) {
                int departDuration = 15;
                ItineraryItemDto departItem = new ItineraryItemDto();
                departItem.setDayNumber(dayNumber);
                departItem.setScheduleDate(currentDate);
                departItem.setStartTime(TimeUtils.formatAmPm(currentTime24));
                String departEnd = TimeUtils.addMinutes(currentTime24, departDuration);
                departItem.setEndTime(TimeUtils.formatAmPm(departEnd));
                departItem.setActivity("Depart from " + dayOrigin);
                departItem.setLocation(dayOrigin);
                departItem.setActivityType("DEPARTURE");
                departItem.setDurationMinutes(departDuration);
                departItem.setLatitude(originCoords[0]);
                departItem.setLongitude(originCoords[1]);
                departItem.setCategory("DEPARTURE");
                items.add(departItem);
                currentTime24 = departEnd;

                GoogleMapsService.RouteCalculationResult leg = null;
                try {
                    if (googleMapsService != null) {
                        leg = googleMapsService.calculateRouteSegment(currentLegOrigin, currentCityName, transportMode);
                    }
                } catch (Exception e) {
                    log.warn("Failed to calculate route segment from {} to {}: {}", currentLegOrigin, currentCityName, e.getMessage());
                }

                int travelMins;
                double travelDist;
                if (leg != null && leg.getDurationMinutes() > 0) {
                    travelMins = leg.getDurationMinutes();
                    travelDist = leg.getDistanceKm();
                } else if (currentDest != null && currentDest.getTravelTimeFromPrevious() != null && currentDest.getTravelTimeFromPrevious() > 0 && !isHydToDelhiCorridor) {
                    travelMins = currentDest.getTravelTimeFromPrevious();
                    travelDist = (currentDest.getDistanceFromPrevious() != null && currentDest.getDistanceFromPrevious() > 0) ? currentDest.getDistanceFromPrevious() : 80.0;
                } else {
                    GeoUtils.RoadRouteResult roadLeg = GeoUtils.calculateRealRoadRoute(originCoords[0], originCoords[1], cityCoords[0], cityCoords[1], transportMode);
                    travelDist = Math.max(0.5, roadLeg.getDistanceKm());
                    travelMins = Math.max(5, roadLeg.getDurationMinutes());
                }

                // If travel takes more than 330 minutes (> 5.5 hours), this is a dedicated highway transit day
                if (travelMins > 330) {
                    isHighwayTransitDay = true;
                    List<TransitStopInfo> transitStops = resolveHighwayTransitStops(currentLegOrigin, currentCityName, travelDist, travelMins);
                    double accumulatedDist = 0.0;
                    int accumulatedMins = 0;

                    for (TransitStopInfo stop : transitStops) {
                        accumulatedDist += stop.getDistFromPrevKm();
                        accumulatedMins += stop.getTransitMinutes();

                        String rawStopArrival = TimeUtils.addMinutes(currentTime24, stop.getTransitMinutes());
                        String stopStart = TimeUtils.roundUpTime24(rawStopArrival, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                        String stopEnd = TimeUtils.addMinutes(stopStart, stop.getDurationMinutes());

                        ItineraryItemDto stopItem = new ItineraryItemDto();
                        stopItem.setDayNumber(dayNumber);
                        stopItem.setScheduleDate(currentDate);
                        stopItem.setStartTime(TimeUtils.formatAmPm(stopStart));
                        stopItem.setEndTime(TimeUtils.formatAmPm(stopEnd));
                        stopItem.setActivity(stop.getName());
                        stopItem.setLocation(stop.getLocation());
                        stopItem.setActivityType(stop.getActivityType());
                        stopItem.setCategory(stop.getCategory());
                        stopItem.setDurationMinutes(stop.getDurationMinutes());
                        stopItem.setTravelDistanceKm(stop.getDistFromPrevKm());
                        stopItem.setTravelTimeMinutes(stop.getTransitMinutes());
                        stopItem.setLatitude(stop.getLat());
                        stopItem.setLongitude(stop.getLng());
                        items.add(stopItem);

                        currentTime24 = stopEnd;
                        dayDistance += stop.getDistFromPrevKm();
                        dayTravelMinutes += stop.getTransitMinutes();
                        dayBreakMinutes += stop.getDurationMinutes();

                        if ("MEAL".equalsIgnoreCase(stop.getActivityType())) {
                            hadLunch = true;
                        }
                    }

                    // Final highway leg to destination hotel
                    double finalLegDist = Math.max(10.0, Math.round((travelDist - accumulatedDist) * 10.0) / 10.0);
                    int finalLegMins = Math.max(15, travelMins - accumulatedMins);

                    String destinationHotel = (currentDest != null && currentDest.getHotelName() != null && !currentDest.getHotelName().trim().isEmpty() && !isHydToDelhiCorridor)
                            ? currentDest.getHotelName().trim() : ("Hotel / Stay in " + currentCityName);
                    previousNightHotel = destinationHotel;
                    double[] hotelCoords = new double[]{cityCoords[0] - 0.002, cityCoords[1] - 0.002};

                    String rawHotelArrival = TimeUtils.addMinutes(currentTime24, finalLegMins);
                    String hotelStart = TimeUtils.roundUpTime24(rawHotelArrival, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                    int hotelRestMins = 60;
                    String hotelEnd = TimeUtils.addMinutes(hotelStart, hotelRestMins);

                    ItineraryItemDto hotelItem = new ItineraryItemDto();
                    hotelItem.setDayNumber(dayNumber);
                    hotelItem.setScheduleDate(currentDate);
                    hotelItem.setStartTime(TimeUtils.formatAmPm(hotelStart));
                    hotelItem.setEndTime(TimeUtils.formatAmPm(hotelEnd));
                    hotelItem.setActivity("🏨 Check-in & Evening Unwind at " + destinationHotel);
                    hotelItem.setLocation(destinationHotel);
                    hotelItem.setActivityType("HOTEL");
                    hotelItem.setCategory("Hotel");
                    hotelItem.setDurationMinutes(hotelRestMins);
                    hotelItem.setTravelDistanceKm(finalLegDist);
                    hotelItem.setTravelTimeMinutes(finalLegMins);
                    hotelItem.setLatitude(hotelCoords[0]);
                    hotelItem.setLongitude(hotelCoords[1]);
                    items.add(hotelItem);

                    currentTime24 = hotelEnd;
                    dayDistance += finalLegDist;
                    dayTravelMinutes += finalLegMins;
                    dayBreakMinutes += hotelRestMins;

                    // Evening dinner in destination
                    String dinnerStart = TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(19, 15))
                            ? "19:15"
                            : TimeUtils.roundUpTime24(currentTime24, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                    String dinnerEnd = TimeUtils.addMinutes(dinnerStart, 60);

                    ItineraryItemDto dinnerItem = new ItineraryItemDto();
                    dinnerItem.setDayNumber(dayNumber);
                    dinnerItem.setScheduleDate(currentDate);
                    dinnerItem.setStartTime(TimeUtils.formatAmPm(dinnerStart));
                    dinnerItem.setEndTime(TimeUtils.formatAmPm(dinnerEnd));
                    dinnerItem.setActivity("🍽️ Dinner & Regional Gastronomy");
                    dinnerItem.setLocation("Dining in " + currentCityName);
                    dinnerItem.setActivityType("MEAL");
                    dinnerItem.setCategory("Dining");
                    dinnerItem.setDurationMinutes(60);
                    dinnerItem.setLatitude(cityCoords[0] + 0.002);
                    dinnerItem.setLongitude(cityCoords[1] + 0.002);
                    items.add(dinnerItem);

                    currentTime24 = dinnerEnd;
                    dayBreakMinutes += 60;

                    previousDayEndLocation = currentCityName;
                } else {
                    // Medium/short journey (<= 330 mins, e.g. Bengaluru -> Mysuru)
                    dayDistance += travelDist;
                    dayTravelMinutes += travelMins;

                    String travelStart = departEnd;
                    String rawArrival = TimeUtils.addMinutes(travelStart, travelMins);
                    String earliestNextStart = TimeUtils.addMinutes(rawArrival, defaultBufferMinutes);
                    currentTime24 = TimeUtils.roundUpTime24(earliestNextStart, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                    dayBreakMinutes += defaultBufferMinutes;

                    firstStopTravelDist = travelDist;
                    firstStopTravelMins = travelMins;

                    previousDayEndLocation = currentCityName;
                }
            } else {
                int departDuration = 15;
                ItineraryItemDto departItem = new ItineraryItemDto();
                departItem.setDayNumber(dayNumber);
                departItem.setScheduleDate(currentDate);
                departItem.setStartTime(TimeUtils.formatAmPm(currentTime24));
                String departEnd = TimeUtils.addMinutes(currentTime24, departDuration);
                departItem.setEndTime(TimeUtils.formatAmPm(departEnd));
                departItem.setActivity("Depart from " + dayOrigin);
                departItem.setLocation(dayOrigin);
                departItem.setActivityType("DEPARTURE");
                departItem.setDurationMinutes(departDuration);
                departItem.setLatitude(originCoords[0]);
                departItem.setLongitude(originCoords[1]);
                departItem.setCategory("DEPARTURE");
                items.add(departItem);
                currentTime24 = departEnd;
            }

            if (!isHighwayTransitDay) {

            // Check for lunch if time is already between 12:00 and 14:15
            if (!hadLunch && !TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(12, 0)) && TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(14, 15))) {
                String lunchStart = TimeUtils.roundUpTime24(currentTime24, TimeUtils.DEFAULT_INTERVAL_MINUTES);
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
                if (firstStopTravelMins != null && firstStopTravelMins > 0) {
                    lunchItem.setTravelTimeMinutes(firstStopTravelMins);
                    lunchItem.setTravelDistanceKm(firstStopTravelDist);
                    firstStopTravelMins = null;
                    firstStopTravelDist = null;
                }
                items.add(lunchItem);

                currentTime24 = TimeUtils.roundUpTime24(lunchEnd, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                dayBreakMinutes += 60;
                hadLunch = true;
            }

            // Determine dynamic target activities count for today based on daylight and travel commitments
            int dayLimitMinutes = TimeUtils.differenceInMinutes(dailyStart, dailyEnd);
            int nonSightseeingCommitments = dayTravelMinutes + 120 + 45 + 15 + 45; // driving + meals(120) + hotel(45) + depart(15) + free leisure(45)
            int netSightseeingMinutes = Math.max(0, dayLimitMinutes - nonSightseeingCommitments);
            int maxPlacesToday = Math.max(1, netSightseeingMinutes / 115);

            if (isFirstDayInCity && dayTravelMinutes > 240) {
                // Long intercity driving day (4+ hours) -> 1-2 major sightseeing stops
                maxPlacesToday = Math.min(2, Math.max(1, maxPlacesToday));
            } else if (isFirstDayInCity && dayTravelMinutes > 90) {
                // Moderate/significant drive day (1.5-4 hours) -> 2 sightseeing stops
                maxPlacesToday = Math.min(2, Math.max(1, maxPlacesToday));
            } else {
                // Normal sightseeing day (full city day) -> 2-3 comfortable activities
                maxPlacesToday = Math.min(3, Math.max(2, maxPlacesToday));
            }

            // Schedule attractions for this day
            List<SightseeingPlaceDto> poolPlaces = getOrGeneratePlacesForCity(currentDest, currentCityName);
            List<SightseeingPlaceDto> availableCandidates = new ArrayList<>();
            for (SightseeingPlaceDto p : poolPlaces) {
                String pKey = normalizePlaceKey(p.getName());
                if (!pKey.isEmpty() && !tripScheduledPlaceKeys.contains(pKey)) {
                    availableCandidates.add(p);
                }
            }

            // If available candidates are fewer than 4 or on later days in the city (Day 2+), incorporate surrounding/nearby attractions
            if ((availableCandidates.size() < 4 || cityDayIndex >= 2) && recommendationService != null) {
                List<String> preferredInterests = (trip != null && trip.getPreferredInterests() != null && !trip.getPreferredInterests().isEmpty())
                        ? Arrays.asList(trip.getPreferredInterests().split(",")) : Collections.emptyList();
                List<SightseeingPlaceDto> more = recommendationService.getRecommendedPlaces(currentCityName, preferredInterests, "popularity", null);
                if (more != null) {
                    for (SightseeingPlaceDto m : more) {
                        String mKey = normalizePlaceKey(m.getName());
                        if (!mKey.isEmpty() && !tripScheduledPlaceKeys.contains(mKey)) {
                            boolean exists = availableCandidates.stream().anyMatch(c -> normalizePlaceKey(c.getName()).equals(mKey));
                            if (!exists) {
                                availableCandidates.add(m);
                            }
                        }
                    }
                }
                // Expand search to nearby towns within 65 km so later days feature surrounding region gems
                if (availableCandidates.size() < 6 || cityDayIndex >= 2) {
                    List<SightseeingPlaceDto> nearbyTowns = recommendationService.getNearbyPlacesWithProgressiveRadius(
                            currentCityName, 65, null, "popularity", preferredInterests);
                    if (nearbyTowns != null) {
                        for (SightseeingPlaceDto m : nearbyTowns) {
                            String mKey = normalizePlaceKey(m.getName());
                            if (!mKey.isEmpty() && !tripScheduledPlaceKeys.contains(mKey)) {
                                boolean exists = availableCandidates.stream().anyMatch(c -> normalizePlaceKey(c.getName()).equals(mKey));
                                if (!exists) {
                                    if (cityDayIndex >= 2) {
                                        availableCandidates.add(0, m);
                                    } else {
                                        availableCandidates.add(m);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            int placesScheduledToday = 0;
            double lastPlaceLat = cityCoords[0];
            double lastPlaceLng = cityCoords[1];

            while (placesScheduledToday < maxPlacesToday && !availableCandidates.isEmpty()) {
                // Check lunch insertion if passing midday (between 12:00 and 14:15)
                if (!hadLunch && !TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(12, 0)) && TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(14, 15))) {
                    double diningLat = cityCoords[0] + 0.0015;
                    double diningLng = cityCoords[1] + 0.0015;
                    int mealTransitMins;
                    double mealDist;
                    if (firstStopTravelMins != null && firstStopTravelMins > 0) {
                        mealTransitMins = firstStopTravelMins;
                        mealDist = firstStopTravelDist;
                        firstStopTravelMins = null;
                        firstStopTravelDist = null;
                    } else {
                        GeoUtils.RoadRouteResult mealLeg = GeoUtils.calculateRealRoadRoute(lastPlaceLat, lastPlaceLng, diningLat, diningLng, transportMode);
                        mealDist = Math.max(0.5, mealLeg.getDistanceKm());
                        mealTransitMins = Math.max(10, mealLeg.getDurationMinutes());
                    }

                    String rawArrival = TimeUtils.addMinutes(currentTime24, mealTransitMins);
                    String lunchStart = TimeUtils.roundUpTime24(rawArrival, TimeUtils.DEFAULT_INTERVAL_MINUTES);
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
                    lunchItem.setLatitude(diningLat);
                    lunchItem.setLongitude(diningLng);
                    lunchItem.setCategory("Dining");
                    lunchItem.setTravelTimeMinutes(mealTransitMins);
                    lunchItem.setTravelDistanceKm(mealDist);
                    items.add(lunchItem);

                    currentTime24 = TimeUtils.roundUpTime24(lunchEnd, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                    dayDistance += mealDist;
                    dayTravelMinutes += mealTransitMins;
                    dayBreakMinutes += 60;
                    lastPlaceLat = diningLat;
                    lastPlaceLng = diningLng;
                    hadLunch = true;
                }

                // Select next best attraction geographically to avoid backtracking, respecting opening hours
                SightseeingPlaceDto place = selectNextBestAttraction(availableCandidates, lastPlaceLat, lastPlaceLng, currentTime24, dailyEnd, tripScheduledPlaceKeys);
                if (place == null) {
                    // If no attraction fits right now and lunch hasn't been taken yet, try having lunch first
                    if (!hadLunch && TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(13, 30))) {
                        double diningLat = cityCoords[0] + 0.0015;
                        double diningLng = cityCoords[1] + 0.0015;
                        int mealTransitMins;
                        double mealDist;
                        if (firstStopTravelMins != null && firstStopTravelMins > 0) {
                            mealTransitMins = firstStopTravelMins;
                            mealDist = firstStopTravelDist;
                            firstStopTravelMins = null;
                            firstStopTravelDist = null;
                        } else {
                            GeoUtils.RoadRouteResult mealLeg = GeoUtils.calculateRealRoadRoute(lastPlaceLat, lastPlaceLng, diningLat, diningLng, transportMode);
                            mealDist = Math.max(0.5, mealLeg.getDistanceKm());
                            mealTransitMins = Math.max(10, mealLeg.getDurationMinutes());
                        }

                        String rawArrival = TimeUtils.addMinutes(currentTime24, mealTransitMins);
                        String lunchStart = TimeUtils.roundUpTime24(rawArrival, TimeUtils.DEFAULT_INTERVAL_MINUTES);
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
                        lunchItem.setLatitude(diningLat);
                        lunchItem.setLongitude(diningLng);
                        lunchItem.setCategory("Dining");
                        lunchItem.setTravelTimeMinutes(mealTransitMins);
                        lunchItem.setTravelDistanceKm(mealDist);
                        items.add(lunchItem);

                        currentTime24 = TimeUtils.roundUpTime24(lunchEnd, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                        dayDistance += mealDist;
                        dayTravelMinutes += mealTransitMins;
                        dayBreakMinutes += 60;
                        lastPlaceLat = diningLat;
                        lastPlaceLng = diningLng;
                        hadLunch = true;

                        place = selectNextBestAttraction(availableCandidates, lastPlaceLat, lastPlaceLng, currentTime24, dailyEnd, tripScheduledPlaceKeys);
                    }
                    if (place == null) {
                        break; // No more suitable candidate places fit into available hours today
                    }
                }

                int rawVisitDuration = place.getEstimatedVisitDurationMinutes() != null ? place.getEstimatedVisitDurationMinutes() : 90;
                int visitDuration = Math.max(30, (int) Math.round((double) rawVisitDuration / 15.0) * 15);

                double pLat = (place.getLatitude() != null && place.getLatitude() != 0.0) ? place.getLatitude() : cityCoords[0];
                double pLng = (place.getLongitude() != null && place.getLongitude() != 0.0) ? place.getLongitude() : cityCoords[1];

                double placeDist;
                int transitMins;
                if (googleMapsService != null && (lastPlaceLat != cityCoords[0] || lastPlaceLng != cityCoords[1] || pLat != cityCoords[0] || pLng != cityCoords[1])) {
                    GoogleMapsService.RouteCalculationResult leg = googleMapsService.calculateRouteBetweenCoordinates(lastPlaceLat, lastPlaceLng, pLat, pLng, transportMode);
                    if (leg != null && leg.isSuccess()) {
                        placeDist = Math.max(1.0, leg.getDistanceKm());
                        transitMins = Math.max(10, leg.getDurationMinutes());
                    } else {
                        placeDist = Math.max(1.5, Math.round(GeoUtils.calculateHaversineDistanceKm(lastPlaceLat, lastPlaceLng, pLat, pLng) * 1.3 * 10.0) / 10.0);
                        transitMins = Math.max(15, (int) Math.round((placeDist / 25.0) * 60.0));
                    }
                } else {
                    placeDist = Math.max(1.5, Math.round(GeoUtils.calculateHaversineDistanceKm(lastPlaceLat, lastPlaceLng, pLat, pLng) * 1.3 * 10.0) / 10.0);
                    transitMins = Math.max(15, (int) Math.round((placeDist / 25.0) * 60.0));
                }

                String rawTransitArrival = TimeUtils.addMinutes(currentTime24, transitMins);
                String actStart = TimeUtils.roundUpTime24(rawTransitArrival, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                String actEnd = TimeUtils.addMinutes(actStart, visitDuration);

                // If activity extends beyond daily end boundary
                if (TimeUtils.parseTime(actEnd).isAfter(TimeUtils.parseTime(dailyEnd))) {
                    if (placesScheduledToday == 0) {
                        visitDuration = Math.max(45, TimeUtils.differenceInMinutes(actStart, dailyEnd));
                        actEnd = TimeUtils.addMinutes(actStart, visitDuration);
                    } else {
                        availableCandidates.remove(place);
                        continue;
                    }
                }

                ItineraryItemDto visitItem = new ItineraryItemDto();
                visitItem.setDayNumber(dayNumber);
                visitItem.setScheduleDate(currentDate);
                visitItem.setStartTime(TimeUtils.formatAmPm(actStart));
                visitItem.setEndTime(TimeUtils.formatAmPm(actEnd));
                visitItem.setActivity("Visit " + place.getName());

                String town = (place.getTownOrArea() != null && !place.getTownOrArea().trim().isEmpty())
                        ? place.getTownOrArea().trim() : currentCityName;
                visitItem.setTownOrArea(town);
                visitItem.setProximityTier(place.getProximityTier() != null ? place.getProximityTier() : "IN_DESTINATION");

                if (town != null && !town.equalsIgnoreCase(currentCityName)) {
                    visitItem.setLocation(place.getName() + " (" + town + ")");
                } else {
                    visitItem.setLocation(place.getName() + ", " + currentCityName);
                }

                visitItem.setActivityType("SIGHTSEEING");
                visitItem.setPlaceId(place.getId());
                if (firstStopTravelMins != null && firstStopTravelMins > 0) {
                    visitItem.setTravelDistanceKm(firstStopTravelDist);
                    visitItem.setTravelTimeMinutes(firstStopTravelMins);
                    firstStopTravelMins = null;
                    firstStopTravelDist = null;
                } else {
                    visitItem.setTravelDistanceKm(placeDist);
                    visitItem.setTravelTimeMinutes(transitMins);
                }
                visitItem.setDurationMinutes(visitDuration);
                visitItem.setRating(place.getRating());
                visitItem.setVisited(Boolean.TRUE.equals(place.getVisited()));
                visitItem.setLatitude(pLat);
                visitItem.setLongitude(pLng);
                visitItem.setCategory(place.getCategory() != null ? place.getCategory() : "Historical");

                String notes = place.getDescription() != null ? place.getDescription() : "";
                if (place.getProximityTier() != null && !"IN_DESTINATION".equalsIgnoreCase(place.getProximityTier())
                        && place.getDistanceFromDestinationKm() != null && place.getDistanceFromDestinationKm() > 1.0) {
                    int driveMins = place.getEstimatedTravelTimeMinutes() != null ? place.getEstimatedTravelTimeMinutes() : transitMins;
                    String distanceInfo = String.format(Locale.US, "📍 Nearby attraction in %s • %.1f km from %s (~%s drive)",
                            town, place.getDistanceFromDestinationKm(), currentCityName,
                            TimeUtils.formatDuration(driveMins));
                    notes = notes.isEmpty() ? distanceInfo : (distanceInfo + " • " + notes);
                }
                String conflict = checkOpeningHoursConflict(actStart, actEnd, place.getOpeningHours());
                if (conflict != null) {
                    notes = (notes.isEmpty() ? "" : notes + " • ") + "⚠️ " + conflict;
                }
                visitItem.setNotes(notes);
                items.add(visitItem);

                tripScheduledPlaceKeys.add(normalizePlaceKey(place.getName()));
                availableCandidates.remove(place);

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

            // If activities ended before lunchtime and lunch hasn't been scheduled yet, insert lunch
            if (!hadLunch && TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(14, 0))) {
                double diningLat = cityCoords[0] + 0.0015;
                double diningLng = cityCoords[1] + 0.0015;
                int mealTransitMins;
                double mealDist;
                if (firstStopTravelMins != null && firstStopTravelMins > 0) {
                    mealTransitMins = firstStopTravelMins;
                    mealDist = firstStopTravelDist;
                    firstStopTravelMins = null;
                    firstStopTravelDist = null;
                } else {
                    GeoUtils.RoadRouteResult mealLeg = GeoUtils.calculateRealRoadRoute(lastPlaceLat, lastPlaceLng, diningLat, diningLng, transportMode);
                    mealDist = Math.max(0.5, mealLeg.getDistanceKm());
                    mealTransitMins = Math.max(10, mealLeg.getDurationMinutes());
                }

                String rawArrival = TimeUtils.addMinutes(currentTime24, mealTransitMins);
                String lunchStart = TimeUtils.parseTime(rawArrival).isBefore(LocalTime.of(12, 0)) ? "12:00" : TimeUtils.roundUpTime24(rawArrival, TimeUtils.DEFAULT_INTERVAL_MINUTES);
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
                lunchItem.setLatitude(diningLat);
                lunchItem.setLongitude(diningLng);
                lunchItem.setCategory("Dining");
                lunchItem.setTravelTimeMinutes(mealTransitMins);
                lunchItem.setTravelDistanceKm(mealDist);
                items.add(lunchItem);

                currentTime24 = lunchEnd;
                dayDistance += mealDist;
                dayTravelMinutes += mealTransitMins;
                dayBreakMinutes += 60;
                lastPlaceLat = diningLat;
                lastPlaceLng = diningLng;
                hadLunch = true;
            }

            // Realistic Evening Transition: Return to hotel immediately after activities (never morning 10:15 AM!)
            int hotelTransitMins = 20;
            String rawHotelStart = TimeUtils.addMinutes(currentTime24, hotelTransitMins);
            String hotelStart = TimeUtils.parseTime(rawHotelStart).isBefore(LocalTime.of(16, 30))
                    ? "16:30"
                    : TimeUtils.roundUpTime24(rawHotelStart, TimeUtils.DEFAULT_INTERVAL_MINUTES);
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
            if (firstStopTravelMins != null && firstStopTravelMins > 0) {
                eveningItem.setTravelDistanceKm(firstStopTravelDist);
                eveningItem.setTravelTimeMinutes(firstStopTravelMins);
                firstStopTravelMins = null;
                firstStopTravelDist = null;
            } else {
                eveningItem.setTravelDistanceKm(3.0);
                eveningItem.setTravelTimeMinutes(hotelTransitMins);
            }
            eveningItem.setDurationMinutes(hotelRestMins);
            eveningItem.setLatitude(hotelCoords[0]);
            eveningItem.setLongitude(hotelCoords[1]);
            eveningItem.setCategory("Hotel");
            items.add(eveningItem);

            dayDistance += 3.0;
            dayTravelMinutes += hotelTransitMins;
            dayBreakMinutes += hotelRestMins;
            currentTime24 = hotelEnd;

            // Evening dinner (scheduled around 19:00 - 20:30)
            String dinnerStart = TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(19, 0))
                    ? "19:00"
                    : TimeUtils.roundUpTime24(currentTime24, TimeUtils.DEFAULT_INTERVAL_MINUTES);
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
            long seqId = 1L;
            for (ItineraryItemDto it : items) {
                if (it.getId() == null) {
                    it.setId((long) dayNumber * 1000L + seqId++);
                }
            }
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

            if (isHighwayTransitDay) {
                dayDto.setStatus("Highway Transit");
                dayDto.setWarningMessage(null);
            } else if (dayTravelMinutes > maxDailyDrivingMinutes) {
                boolean hasTransitStops = items.stream().anyMatch(it -> "REST".equalsIgnoreCase(it.getActivityType()) || ("MEAL".equalsIgnoreCase(it.getActivityType()) && it.getActivity() != null && it.getActivity().toLowerCase().contains("highway")));
                if (hasTransitStops) {
                    dayDto.setStatus("Highway Transit");
                    dayDto.setWarningMessage(null);
                } else {
                    dayDto.setStatus("⚠️ Long Driving Day");
                    dayDto.setWarningMessage("This day contains " + TimeUtils.formatDuration(dayTravelMinutes) + " of driving. An intermediate rest break is recommended.");
                }
            } else if (activeMinutes > 600 || (availableDayMinutes > 0 && activeMinutes > availableDayMinutes)) {
                dayDto.setStatus("Busy");
                dayDto.setWarningMessage(null);
            } else if (dayTravelMinutes < 120 && daySightseeingMinutes < 180) {
                dayDto.setStatus("Relaxed");
                dayDto.setWarningMessage(null);
            } else {
                dayDto.setStatus("Balanced");
                dayDto.setWarningMessage(null);
            }

            if (dayIndex == 0 && totalDaysCount < 3 && isHydToDelhiCorridor) {
                dayDto.setWarningMessage("⚠️ Insufficient Road Trip Duration: Driving from Hyderabad to Delhi requires at least 3 travel days (1,580 km via NH44). In " + totalDaysCount + " day(s), daylight driving safely reaches the Nagpur transit hub. Consider extending trip dates to at least 3-4 days or selecting Flight mode.");
            }

            if (totalPlacesCount == 0 && !isHighwayTransitDay && (dayDto.getWarningMessage() == null || dayDto.getWarningMessage().isEmpty())) {
                dayDto.setStatus("No attractions discovered");
                dayDto.setWarningMessage("No verified sightseeing attractions could be discovered for " + currentCityName + ". You can customize or add activities using the button below.");
            }

            populateDayTransitions(dayDto, transportMode);

            dayItineraries.add(dayDto);
        }

        // Auto-balance entire multi-day trip so no day starts overloaded
        if (dayItineraries.size() > 1) {
            autoBalanceTripDays(trip, dayItineraries, transportMode);
        }

        return dayItineraries;
    }

    /**
     * Automatically balances a multi-day trip's schedule:
     * - Identifies overloaded days (total duration > 720 min or status Overcrowded/Overloaded).
     * - Redistributes flexible, unlocked sightseeing attractions to recipient days in the same city (or adjacent day) with capacity.
     * - Preserves locked activities.
     * - Recalculates affected day schedules so timing flows realistically without overlap or conflict.
     */
    public boolean autoBalanceTripDays(Trip trip, List<DayItineraryDto> days, String transportMode) {
        if (days == null || days.size() <= 1) {
            return false;
        }

        boolean anyRebalanced = false;
        int maxPasses = 5;

        for (int pass = 0; pass < maxPasses; pass++) {
            boolean passChanged = false;
            for (int i = 0; i < days.size(); i++) {
                DayItineraryDto sourceDay = days.get(i);
                boolean isOverloaded = "Overloaded".equalsIgnoreCase(sourceDay.getFeasibilityState()) ||
                        (sourceDay.getTotalDurationMinutes() != null && sourceDay.getTotalDurationMinutes() > 720) ||
                        (sourceDay.getUnallocatedTimeMinutes() != null && sourceDay.getUnallocatedTimeMinutes() < 30);

                if (!isOverloaded) {
                    continue;
                }

                List<ItineraryItemDto> sourceItems = sourceDay.getItems();
                if (sourceItems == null || sourceItems.size() <= 3) {
                    continue;
                }

                // Find candidate flexible activity to shift (prefer last unlocked SIGHTSEEING item)
                ItineraryItemDto candidateItem = null;
                for (int k = sourceItems.size() - 1; k >= 0; k--) {
                    ItineraryItemDto item = sourceItems.get(k);
                    if ("SIGHTSEEING".equalsIgnoreCase(item.getActivityType()) && !Boolean.TRUE.equals(item.getLocked())) {
                        candidateItem = item;
                        break;
                    }
                }

                if (candidateItem == null) {
                    continue;
                }

                // Look for recipient day with capacity (first prefer same city, then adjacent day)
                DayItineraryDto targetDay = null;
                for (int j = 0; j < days.size(); j++) {
                    if (i == j) continue;
                    DayItineraryDto candidateTarget = days.get(j);
                    boolean sameCity = sourceDay.getDestinationName() != null &&
                            sourceDay.getDestinationName().equalsIgnoreCase(candidateTarget.getDestinationName());
                    boolean hasCapacity = !"Overloaded".equalsIgnoreCase(candidateTarget.getFeasibilityState()) &&
                            (candidateTarget.getTotalDurationMinutes() == null || candidateTarget.getTotalDurationMinutes() < 600) &&
                            (candidateTarget.getPlannedStopsCount() < 4);

                    if (sameCity && hasCapacity) {
                        targetDay = candidateTarget;
                        break;
                    } else if (hasCapacity && targetDay == null && Math.abs(i - j) == 1) {
                        targetDay = candidateTarget;
                    }
                }

                if (targetDay != null) {
                    sourceItems.remove(candidateItem);
                    candidateItem.setDayNumber(targetDay.getDayNumber());
                    candidateItem.setScheduleDate(targetDay.getDate());

                    List<ItineraryItemDto> targetItems = new ArrayList<>(targetDay.getItems());
                    // Insert before evening hotel / dinner
                    int insertIndex = Math.max(1, targetItems.size() - 2);
                    targetItems.add(insertIndex, candidateItem);

                    // Recalculate both days
                    recalculateDaySchedule(trip, sourceDay, sourceItems, transportMode);
                    recalculateDaySchedule(trip, targetDay, targetItems, transportMode);

                    passChanged = true;
                    anyRebalanced = true;
                    break;
                }
            }
            if (!passChanged) {
                break;
            }
        }

        if (anyRebalanced) {
            for (DayItineraryDto d : days) {
                if (!"Overloaded".equalsIgnoreCase(d.getFeasibilityState())) {
                    d.setStatus("Balanced");
                    d.setWarningMessage(null);
                }
            }
        }

        return anyRebalanced;
    }

    public DayItineraryDto recalculateDaySchedule(Trip trip, DayItineraryDto dayDto, List<ItineraryItemDto> rawItems, String transportMode) {
        String cityName = dayDto.getDestinationName() != null ? dayDto.getDestinationName() : "Destination";
        double[] cityCoords = GeoUtils.getApproximateCoordinates(cityName);
        String dayOrigin = (dayDto.getDayNumber() == 1 && trip != null && trip.getStartLocation() != null)
                ? trip.getStartLocation() : ("Hotel in " + cityName);
        double[] originCoords = (dayDto.getDayNumber() == 1 && trip != null && trip.getStartLat() != null && trip.getStartLat() != 0.0)
                ? new double[]{trip.getStartLat(), trip.getStartLng()} : cityCoords;
        String hotelStay = "Hotel in " + cityName;
        double[] hotelCoords = new double[]{cityCoords[0] - 0.002, cityCoords[1] - 0.002};
        LocalDate dayDate = dayDto.getDate() != null ? dayDto.getDate() : LocalDate.now();

        Destination dest = null;
        if (trip != null && trip.getDestinations() != null) {
            dest = trip.getDestinations().stream()
                    .filter(d -> d.getName() != null && d.getName().equalsIgnoreCase(cityName))
                    .findFirst()
                    .orElse(null);
            if (dest == null && !trip.getDestinations().isEmpty()) {
                int destIdx = Math.min(dayDto.getDayNumber() - 1, trip.getDestinations().size() - 1);
                if (destIdx >= 0) dest = trip.getDestinations().get(destIdx);
            }
        }

        DayItineraryDto result = recalculateDaySchedule(trip, dest, dayDto.getDayNumber(), dayDate, rawItems,
                dayOrigin, originCoords, hotelStay, hotelCoords, transportMode);

        dayDto.setItems(result.getItems());
        dayDto.setTotalDistanceKm(result.getTotalDistanceKm());
        dayDto.setTravelTimeMinutes(result.getTravelTimeMinutes());
        dayDto.setSightseeingTimeMinutes(result.getSightseeingTimeMinutes());
        dayDto.setBreakTimeMinutes(result.getBreakTimeMinutes());
        dayDto.setTotalDurationMinutes(result.getTotalDurationMinutes());
        dayDto.setUnallocatedTimeMinutes(result.getUnallocatedTimeMinutes());
        dayDto.setStatus(result.getStatus());
        dayDto.setWarningMessage(result.getWarningMessage());
        dayDto.setTotalPlacesCount(result.getTotalPlacesCount());
        dayDto.setVisitedCount(result.getVisitedCount());
        return dayDto;
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
        dayDto.setDestinationName(destName);
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
        if (filtered.size() < 5 && recommendationService != null) {
            List<SightseeingPlaceDto> nearbyTowns = recommendationService.getNearbyPlacesWithProgressiveRadius(
                    destName, 65, categoryFilter, "popularity", null);
            if (nearbyTowns != null) {
                for (SightseeingPlaceDto c : nearbyTowns) {
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
                        boolean already = filtered.stream().anyMatch(f -> normalizePlaceKey(f.getName()).equals(cKey));
                        if (!already) {
                            filtered.add(c);
                        }
                    }
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

            String town = (place.getTownOrArea() != null && !place.getTownOrArea().trim().isEmpty())
                    ? place.getTownOrArea().trim() : destName;
            visitItem.setTownOrArea(town);
            visitItem.setProximityTier(place.getProximityTier() != null ? place.getProximityTier() : "IN_DESTINATION");

            if (town != null && !town.equalsIgnoreCase(destName)) {
                visitItem.setLocation(place.getName() + " (" + town + ")");
            } else {
                visitItem.setLocation(place.getName() + ", " + destName);
            }

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
            if (place.getProximityTier() != null && !"IN_DESTINATION".equalsIgnoreCase(place.getProximityTier())
                    && place.getDistanceFromDestinationKm() != null && place.getDistanceFromDestinationKm() > 1.0) {
                int driveMins = place.getEstimatedTravelTimeMinutes() != null ? place.getEstimatedTravelTimeMinutes() : transitMins;
                String distanceInfo = String.format(Locale.US, "📍 Nearby attraction in %s • %.1f km from %s (~%s drive)",
                        town, place.getDistanceFromDestinationKm(), destName,
                        TimeUtils.formatDuration(driveMins));
                notes = notes.isEmpty() ? distanceInfo : (distanceInfo + " • " + notes);
            }
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

        // If activities ended before lunchtime and lunch hasn't been scheduled yet, insert lunch
        if (!hadLunch && TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(14, 0))) {
            String lunchStart = TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(12, 0)) ? "12:00" : TimeUtils.roundTime24(currentTime24, TimeUtils.DEFAULT_INTERVAL_MINUTES);
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

        // Realistic Evening Hotel Return immediately after activities (never morning 10:15 AM!)
        int hotelTransitMins = 20;
        String rawHotelStart = TimeUtils.addMinutes(currentTime24, hotelTransitMins);
        String hotelStart = TimeUtils.parseTime(rawHotelStart).isBefore(LocalTime.of(16, 30))
                ? "16:30"
                : TimeUtils.roundUpTime24(rawHotelStart, TimeUtils.DEFAULT_INTERVAL_MINUTES);
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

        // Evening dinner (scheduled around 19:00 - 20:30)
        String dinnerStart = TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(19, 0))
                ? "19:00"
                : TimeUtils.roundTime24(currentTime24, TimeUtils.DEFAULT_INTERVAL_MINUTES);
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

        items.sort((a, b) -> {
            int cmp = TimeUtils.compareItineraryTimes(a.getStartTime(), b.getStartTime());
            if (cmp != 0) return cmp;
            int typeCmp = Integer.compare(TimeUtils.getActivityTypeOrder(a.getActivityType()), TimeUtils.getActivityTypeOrder(b.getActivityType()));
            if (typeCmp != 0) return typeCmp;
            long idA = a.getId() != null ? a.getId() : 0L;
            long idB = b.getId() != null ? b.getId() : 0L;
            return Long.compare(idA, idB);
        });
        long seqRegenId = 1L;
        for (ItineraryItemDto it : items) {
            if (it.getId() == null) {
                it.setId((long) dayNumber * 1000L + seqRegenId++);
            }
        }
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
        List<SightseeingPlaceDto> dtos = new ArrayList<>();
        if (dest != null && dest.getPlaces() != null && !dest.getPlaces().isEmpty()) {
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
        }

        // If dest has fewer than 16 places, supplement with other verified catalog places for this city
        if (dtos.size() < 16 && recommendationService != null) {
            List<SightseeingPlaceDto> more = recommendationService.getRecommendedPlaces(cityName, null, "popularity", null);
            if (more != null) {
                for (SightseeingPlaceDto m : more) {
                    String mKey = normalizePlaceKey(m.getName());
                    boolean exists = dtos.stream().anyMatch(d -> normalizePlaceKey(d.getName()).equals(mKey));
                    if (!exists) {
                        dtos.add(m);
                    }
                }
            }
        }
        // If still fewer than 16 places, supplement with nearby surrounding places
        if (dtos.size() < 16 && recommendationService != null) {
            List<SightseeingPlaceDto> nearby = recommendationService.getNearbyPlacesWithProgressiveRadius(cityName, 65, null, "popularity", null);
            if (nearby != null) {
                for (SightseeingPlaceDto m : nearby) {
                    String mKey = normalizePlaceKey(m.getName());
                    boolean exists = dtos.stream().anyMatch(d -> normalizePlaceKey(d.getName()).equals(mKey));
                    if (!exists) {
                        dtos.add(m);
                    }
                }
            }
        }

        return dtos;
    }

    private SightseeingPlaceDto selectNextBestAttraction(List<SightseeingPlaceDto> candidates,
                                                        double currentLat, double currentLng,
                                                        String currentTime24, String dailyEnd,
                                                        Set<String> scheduledKeys) {
        if (candidates == null || candidates.isEmpty()) return null;

        SightseeingPlaceDto best = null;
        double bestScore = Double.MAX_VALUE;

        for (SightseeingPlaceDto candidate : candidates) {
            String pKey = normalizePlaceKey(candidate.getName());
            if (!pKey.isEmpty() && scheduledKeys != null && scheduledKeys.contains(pKey)) {
                continue;
            }

            double pLat = (candidate.getLatitude() != null && candidate.getLatitude() != 0.0)
                    ? candidate.getLatitude() : currentLat;
            double pLng = (candidate.getLongitude() != null && candidate.getLongitude() != 0.0)
                    ? candidate.getLongitude() : currentLng;

            double dist = GeoUtils.calculateHaversineDistanceKm(currentLat, currentLng, pLat, pLng);
            int estTransit = Math.max(10, (int) Math.round((dist / 25.0) * 60.0));
            int visitDur = candidate.getEstimatedVisitDurationMinutes() != null
                    ? candidate.getEstimatedVisitDurationMinutes() : 90;
            String actStart = TimeUtils.roundUpTime24(TimeUtils.addMinutes(currentTime24, estTransit), TimeUtils.DEFAULT_INTERVAL_MINUTES);
            String actEnd = TimeUtils.addMinutes(actStart, visitDur);

            // If activity starts after daily end time, skip
            if (dailyEnd != null && TimeUtils.parseTime(actStart).isAfter(TimeUtils.parseTime(dailyEnd))) {
                continue;
            }

            // Proximity score with opening hours check
            double score = dist;
            if (candidate.getProximityTier() != null && !"IN_DESTINATION".equalsIgnoreCase(candidate.getProximityTier())) {
                score = dist * 0.05; // Allow regional gems to be scheduled on multi-day itineraries
            }
            if (dailyEnd != null && TimeUtils.parseTime(actEnd).isAfter(TimeUtils.parseTime(dailyEnd))) {
                score += 25.0; // Penalize places extending beyond daily boundary
            }
            String conflict = checkOpeningHoursConflict(actStart, actEnd, candidate.getOpeningHours());
            if (conflict != null && conflict.contains("opens at")) {
                score += 50.0; // Penalize closed places so open attractions are picked first
            }

            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
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
     * Authoritative day schedule recalculation engine.
     * Takes an updated or reordered list of day activities, computes sequential transit times
     * and distances from preceding locations, strictly prevents overlaps and voids, validates
     * opening hours, adjusts hotel returns, and recalculates consistent summary metrics.
     */
    public DayItineraryDto recalculateDaySchedule(Trip trip, Destination currentDest, int dayNumber,
                                                  LocalDate dayDate, List<ItineraryItemDto> rawItems,
                                                  String dayOrigin, double[] originCoords,
                                                  String hotelStay, double[] hotelCoords,
                                                  String transportMode) {
        String dailyStartRaw = (trip != null && trip.getDailyStartTime() != null && !trip.getDailyStartTime().isEmpty())
                ? trip.getDailyStartTime() : "08:00";
        String dailyEndRaw = (trip != null && trip.getDailyEndTime() != null && !trip.getDailyEndTime().isEmpty())
                ? trip.getDailyEndTime() : "20:30";

        String dailyStart = TimeUtils.roundTime24(dailyStartRaw, TimeUtils.DEFAULT_INTERVAL_MINUTES);
        String dailyEnd = TimeUtils.roundTime24(dailyEndRaw, TimeUtils.DEFAULT_INTERVAL_MINUTES);

        String cityName = (currentDest != null && currentDest.getName() != null)
                ? currentDest.getName()
                : (trip != null && trip.getStartLocation() != null ? trip.getStartLocation() : "Destination");

        double[] cityCoords = (currentDest != null && currentDest.getLatitude() != null && currentDest.getLatitude() != 0.0)
                ? new double[]{currentDest.getLatitude(), currentDest.getLongitude()}
                : GeoUtils.getApproximateCoordinates(cityName);

        if (originCoords == null || originCoords.length < 2 || (originCoords[0] == 0.0 && originCoords[1] == 0.0)) {
            originCoords = cityCoords;
        }
        if (hotelCoords == null || hotelCoords.length < 2 || (hotelCoords[0] == 0.0 && hotelCoords[1] == 0.0)) {
            hotelCoords = new double[]{cityCoords[0] - 0.002, cityCoords[1] - 0.002};
        }

        String dayTitle = "Day " + dayNumber + " • " + cityName + " Schedule";
        DayItineraryDto dayDto = new DayItineraryDto(dayNumber, dayDate, dayTitle);
        dayDto.setDestinationName(cityName);

        List<ItineraryItemDto> recalculated = new ArrayList<>();
        String currentTime24 = dailyStart;
        double dayDistance = 0.0;
        int dayTravelMinutes = 0;
        int daySightseeingMinutes = 0;
        int dayBreakMinutes = 0;
        int totalPlacesCount = 0;
        int visitedPlacesCount = 0;

        // Separate items by type
        ItineraryItemDto existingDepart = null;
        ItineraryItemDto existingTransit = null;
        List<ItineraryItemDto> daytimeItems = new ArrayList<>();
        ItineraryItemDto existingHotel = null;
        ItineraryItemDto existingDinner = null;

        if (rawItems != null) {
            for (ItineraryItemDto item : rawItems) {
                String type = item.getActivityType() != null ? item.getActivityType().toUpperCase() : "SIGHTSEEING";
                if ("DEPARTURE".equals(type)) {
                    if (existingDepart == null) existingDepart = item;
                } else if ("ARRIVAL".equals(type) || "TRANSIT".equals(type)) {
                    if (existingTransit == null) existingTransit = item;
                } else if ("HOTEL".equals(type)) {
                    if (existingHotel == null) existingHotel = item;
                } else if ("MEAL".equals(type) && item.getActivity() != null && item.getActivity().toLowerCase().contains("dinner")) {
                    if (existingDinner == null) existingDinner = item;
                } else {
                    daytimeItems.add(item);
                }
            }
        }

        // 1. Morning Departure
        ItineraryItemDto departItem = (existingDepart != null) ? existingDepart : new ItineraryItemDto();
        int departDuration = (departItem.getDurationMinutes() != null && departItem.getDurationMinutes() > 0)
                ? departItem.getDurationMinutes() : 15;
        departItem.setDayNumber(dayNumber);
        departItem.setScheduleDate(dayDate);
        departItem.setStartTime(TimeUtils.formatAmPm(currentTime24));
        String departEnd = TimeUtils.addMinutes(currentTime24, departDuration);
        departItem.setEndTime(TimeUtils.formatAmPm(departEnd));
        if (departItem.getActivity() == null || departItem.getActivity().isEmpty()) {
            departItem.setActivity("🚗 Depart from " + (dayOrigin != null ? dayOrigin : "Hotel"));
        }
        if (departItem.getLocation() == null || departItem.getLocation().isEmpty()) {
            departItem.setLocation(dayOrigin != null ? dayOrigin : cityName);
        }
        departItem.setActivityType("DEPARTURE");
        departItem.setDurationMinutes(departDuration);
        departItem.setLatitude(originCoords[0]);
        departItem.setLongitude(originCoords[1]);
        departItem.setCategory("DEPARTURE");
        recalculated.add(departItem);
        currentTime24 = departEnd;

        // 2. Intercity Transit Leg (if present)
        Double firstStopTravelDist = null;
        Integer firstStopTravelMins = null;
        boolean isIntercityLeg = existingTransit != null
                || (dayOrigin != null && !dayOrigin.trim().equalsIgnoreCase(cityName.trim()))
                || (originCoords != null && cityCoords != null && GeoUtils.calculateHaversineDistanceKm(originCoords[0], originCoords[1], cityCoords[0], cityCoords[1]) > 25.0);

        if (isIntercityLeg) {
            int travelMins = 0;
            double travelDist = 0.0;

            if (existingTransit != null && existingTransit.getTravelTimeMinutes() != null && existingTransit.getTravelTimeMinutes() > 0) {
                travelMins = existingTransit.getTravelTimeMinutes();
                travelDist = (existingTransit.getTravelDistanceKm() != null && existingTransit.getTravelDistanceKm() > 0)
                        ? existingTransit.getTravelDistanceKm() : 80.0;
            } else if (currentDest != null && currentDest.getTravelTimeFromPrevious() != null && currentDest.getTravelTimeFromPrevious() > 0) {
                travelMins = currentDest.getTravelTimeFromPrevious();
                travelDist = (currentDest.getDistanceFromPrevious() != null && currentDest.getDistanceFromPrevious() > 0)
                        ? currentDest.getDistanceFromPrevious() : 80.0;
            } else {
                if (googleMapsService != null) {
                    try {
                        GoogleMapsService.RouteCalculationResult leg = googleMapsService.calculateRouteSegment(dayOrigin, cityName, transportMode);
                        if (leg != null && leg.isSuccess() && leg.getDurationMinutes() > 0) {
                            travelMins = leg.getDurationMinutes();
                            travelDist = leg.getDistanceKm();
                        }
                    } catch (Exception ignored) {}
                }
                if (travelMins <= 0) {
                    GeoUtils.RoadRouteResult roadLeg = GeoUtils.calculateRealRoadRoute(originCoords[0], originCoords[1], cityCoords[0], cityCoords[1], transportMode);
                    travelDist = Math.max(0.5, roadLeg.getDistanceKm());
                    travelMins = Math.max(5, roadLeg.getDurationMinutes());
                }
            }

            dayDistance += travelDist;
            dayTravelMinutes += travelMins;
            String rawArrival = TimeUtils.addMinutes(currentTime24, travelMins);
            String earliestNext = TimeUtils.addMinutes(rawArrival, defaultBufferMinutes);
            currentTime24 = TimeUtils.roundUpTime24(earliestNext, TimeUtils.DEFAULT_INTERVAL_MINUTES);
            dayBreakMinutes += defaultBufferMinutes;

            firstStopTravelDist = travelDist;
            firstStopTravelMins = travelMins;
        }

        // 3. Daytime Activities in the provided order
        double lastPlaceLat = cityCoords[0];
        double lastPlaceLng = cityCoords[1];
        boolean hadLunch = false;

        for (ItineraryItemDto item : daytimeItems) {
            String actType = item.getActivityType() != null ? item.getActivityType().toUpperCase() : "SIGHTSEEING";

            if ("MEAL".equals(actType)) {
                int mealDuration = item.getDurationMinutes() != null && item.getDurationMinutes() > 0 ? item.getDurationMinutes() : 60;
                double diningLat = (item.getLatitude() != null && item.getLatitude() != 0.0) ? item.getLatitude() : (lastPlaceLat + 0.001);
                double diningLng = (item.getLongitude() != null && item.getLongitude() != 0.0) ? item.getLongitude() : (lastPlaceLng + 0.001);
                int mealTransitMins;
                double mealDist;
                if (firstStopTravelMins != null && firstStopTravelMins > 0) {
                    mealTransitMins = firstStopTravelMins;
                    mealDist = firstStopTravelDist;
                    firstStopTravelMins = null;
                    firstStopTravelDist = null;
                } else {
                    GeoUtils.RoadRouteResult mealLeg = GeoUtils.calculateRealRoadRoute(lastPlaceLat, lastPlaceLng, diningLat, diningLng, transportMode);
                    mealDist = Math.max(0.5, mealLeg.getDistanceKm());
                    mealTransitMins = Math.max(10, mealLeg.getDurationMinutes());
                }

                String rawMealArrival = TimeUtils.addMinutes(currentTime24, mealTransitMins);
                String mealStart = TimeUtils.roundUpTime24(rawMealArrival, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                String mealEnd = TimeUtils.addMinutes(mealStart, mealDuration);
                item.setDayNumber(dayNumber);
                item.setScheduleDate(dayDate);
                item.setStartTime(TimeUtils.formatAmPm(mealStart));
                item.setEndTime(TimeUtils.formatAmPm(mealEnd));
                item.setDurationMinutes(mealDuration);
                item.setLatitude(diningLat);
                item.setLongitude(diningLng);
                item.setTravelDistanceKm(mealDist);
                item.setTravelTimeMinutes(mealTransitMins);
                if (item.getCategory() == null || item.getCategory().isEmpty()) item.setCategory("Dining");
                recalculated.add(item);
                dayDistance += mealDist;
                dayTravelMinutes += mealTransitMins;
                dayBreakMinutes += mealDuration;
                currentTime24 = mealEnd;
                lastPlaceLat = diningLat;
                lastPlaceLng = diningLng;
                hadLunch = true;
                continue;
            }

            if ("REST".equals(actType)) {
                int restDuration = item.getDurationMinutes() != null && item.getDurationMinutes() > 0 ? item.getDurationMinutes() : 30;
                int restTransitMins = 0;
                double restDist = 0.0;
                if (firstStopTravelMins != null && firstStopTravelMins > 0) {
                    restTransitMins = firstStopTravelMins;
                    restDist = firstStopTravelDist;
                    firstStopTravelMins = null;
                    firstStopTravelDist = null;
                }
                String rawRestArrival = TimeUtils.addMinutes(currentTime24, restTransitMins);
                String restStart = TimeUtils.roundUpTime24(rawRestArrival, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                String restEnd = TimeUtils.addMinutes(restStart, restDuration);
                item.setDayNumber(dayNumber);
                item.setScheduleDate(dayDate);
                item.setStartTime(TimeUtils.formatAmPm(restStart));
                item.setEndTime(TimeUtils.formatAmPm(restEnd));
                item.setDurationMinutes(restDuration);
                item.setLatitude(lastPlaceLat);
                item.setLongitude(lastPlaceLng);
                item.setTravelDistanceKm(restDist);
                item.setTravelTimeMinutes(restTransitMins);
                if (item.getCategory() == null || item.getCategory().isEmpty()) item.setCategory("Rest");
                recalculated.add(item);
                dayDistance += restDist;
                dayTravelMinutes += restTransitMins;
                dayBreakMinutes += restDuration;
                currentTime24 = restEnd;
                continue;
            }

            // Normal Sightseeing / Experience Activity
            double pLat = (item.getLatitude() != null && item.getLatitude() != 0.0) ? item.getLatitude() : cityCoords[0];
            double pLng = (item.getLongitude() != null && item.getLongitude() != 0.0) ? item.getLongitude() : cityCoords[1];

            double placeDist;
            int transitMins;
            if (googleMapsService != null && (lastPlaceLat != cityCoords[0] || lastPlaceLng != cityCoords[1] || pLat != cityCoords[0] || pLng != cityCoords[1])) {
                GoogleMapsService.RouteCalculationResult leg = googleMapsService.calculateRouteBetweenCoordinates(lastPlaceLat, lastPlaceLng, pLat, pLng, transportMode);
                if (leg != null && leg.isSuccess()) {
                    placeDist = Math.max(1.0, leg.getDistanceKm());
                    transitMins = Math.max(10, leg.getDurationMinutes());
                } else {
                    placeDist = Math.max(1.5, Math.round(GeoUtils.calculateHaversineDistanceKm(lastPlaceLat, lastPlaceLng, pLat, pLng) * 1.3 * 10.0) / 10.0);
                    transitMins = Math.max(15, (int) Math.round((placeDist / 25.0) * 60.0));
                }
            } else {
                placeDist = Math.max(1.5, Math.round(GeoUtils.calculateHaversineDistanceKm(lastPlaceLat, lastPlaceLng, pLat, pLng) * 1.3 * 10.0) / 10.0);
                transitMins = Math.max(15, (int) Math.round((placeDist / 25.0) * 60.0));
            }

            // Lunch insertion if passing 12:15 PM and haven't had lunch yet
            if (!hadLunch && !TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(12, 15)) && TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(14, 15))) {
                String lunchStart = TimeUtils.roundUpTime24(currentTime24, TimeUtils.DEFAULT_INTERVAL_MINUTES);
                String lunchEnd = TimeUtils.addMinutes(lunchStart, 60);

                ItineraryItemDto lunchItem = new ItineraryItemDto();
                lunchItem.setDayNumber(dayNumber);
                lunchItem.setScheduleDate(dayDate);
                lunchItem.setStartTime(TimeUtils.formatAmPm(lunchStart));
                lunchItem.setEndTime(TimeUtils.formatAmPm(lunchEnd));
                lunchItem.setActivity("🍴 Lunch Break & Local Specialities");
                lunchItem.setLocation("Dining in " + cityName);
                lunchItem.setActivityType("MEAL");
                lunchItem.setDurationMinutes(60);
                lunchItem.setLatitude(cityCoords[0] + 0.0015);
                lunchItem.setLongitude(cityCoords[1] + 0.0015);
                lunchItem.setCategory("Dining");
                if (firstStopTravelMins != null && firstStopTravelMins > 0) {
                    lunchItem.setTravelDistanceKm(firstStopTravelDist);
                    lunchItem.setTravelTimeMinutes(firstStopTravelMins);
                    firstStopTravelMins = null;
                    firstStopTravelDist = null;
                }
                recalculated.add(lunchItem);

                currentTime24 = lunchEnd;
                dayBreakMinutes += 60;
                hadLunch = true;
            }

            int rawVisitDuration = item.getDurationMinutes() != null && item.getDurationMinutes() > 0 ? item.getDurationMinutes() : 90;
            int visitDuration = Math.max(30, (int) Math.round((double) rawVisitDuration / 15.0) * 15);

            String rawTransitArrival = TimeUtils.addMinutes(currentTime24, transitMins);
            String actStart = TimeUtils.roundUpTime24(rawTransitArrival, TimeUtils.DEFAULT_INTERVAL_MINUTES);
            String actEnd = TimeUtils.addMinutes(actStart, visitDuration);

            item.setDayNumber(dayNumber);
            item.setScheduleDate(dayDate);
            item.setStartTime(TimeUtils.formatAmPm(actStart));
            item.setEndTime(TimeUtils.formatAmPm(actEnd));
            if (firstStopTravelMins != null && firstStopTravelMins > 0) {
                item.setTravelDistanceKm(firstStopTravelDist);
                item.setTravelTimeMinutes(firstStopTravelMins);
                firstStopTravelMins = null;
                firstStopTravelDist = null;
            } else {
                item.setTravelDistanceKm(placeDist);
                item.setTravelTimeMinutes(transitMins);
            }
            item.setDurationMinutes(visitDuration);
            item.setLatitude(pLat);
            item.setLongitude(pLng);
            if (item.getCategory() == null || item.getCategory().isEmpty()) {
                item.setCategory("Historical");
            }
            recalculated.add(item);

            dayDistance += placeDist;
            dayTravelMinutes += transitMins;
            daySightseeingMinutes += visitDuration;
            totalPlacesCount++;
            if (Boolean.TRUE.equals(item.getVisited())) visitedPlacesCount++;

            currentTime24 = actEnd;
            lastPlaceLat = pLat;
            lastPlaceLng = pLng;
        }

        // 4. Return to Hotel / Evening Stay (never morning 10:15 AM!)
        int hotelTransitMins = 20;
        String rawHotelStart = TimeUtils.addMinutes(currentTime24, hotelTransitMins);
        String hotelStart = TimeUtils.parseTime(rawHotelStart).isBefore(LocalTime.of(16, 30))
                ? "16:30"
                : TimeUtils.roundUpTime24(rawHotelStart, TimeUtils.DEFAULT_INTERVAL_MINUTES);
        int hotelRestMins = 45;
        String hotelEnd = TimeUtils.addMinutes(hotelStart, hotelRestMins);

        ItineraryItemDto hotelItem = (existingHotel != null) ? existingHotel : new ItineraryItemDto();
        hotelItem.setDayNumber(dayNumber);
        hotelItem.setScheduleDate(dayDate);
        hotelItem.setStartTime(TimeUtils.formatAmPm(hotelStart));
        hotelItem.setEndTime(TimeUtils.formatAmPm(hotelEnd));
        String stayName = hotelStay != null ? hotelStay : ("Hotel in " + cityName);
        if (hotelItem.getActivity() == null || hotelItem.getActivity().isEmpty()) {
            hotelItem.setActivity("🏨 Return & Relax at " + stayName);
        }
        hotelItem.setLocation(stayName);
        hotelItem.setActivityType("HOTEL");
        if (firstStopTravelMins != null && firstStopTravelMins > 0) {
            hotelItem.setTravelDistanceKm(firstStopTravelDist);
            hotelItem.setTravelTimeMinutes(firstStopTravelMins);
            firstStopTravelMins = null;
            firstStopTravelDist = null;
        } else {
            hotelItem.setTravelDistanceKm(3.0);
            hotelItem.setTravelTimeMinutes(hotelTransitMins);
        }
        hotelItem.setDurationMinutes(hotelRestMins);
        hotelItem.setLatitude(hotelCoords[0]);
        hotelItem.setLongitude(hotelCoords[1]);
        hotelItem.setCategory("Hotel");
        recalculated.add(hotelItem);

        dayDistance += 3.0;
        dayTravelMinutes += hotelTransitMins;
        dayBreakMinutes += hotelRestMins;
        currentTime24 = hotelEnd;

        // 5. Dinner (recalculated and adjusted if present on this day)
        if (existingDinner != null) {
            String dinnerStart = TimeUtils.parseTime(currentTime24).isBefore(LocalTime.of(19, 0))
                    ? "19:00"
                    : TimeUtils.roundUpTime24(currentTime24, TimeUtils.DEFAULT_INTERVAL_MINUTES);
            String dinnerEnd = TimeUtils.addMinutes(dinnerStart, 60);

            existingDinner.setDayNumber(dayNumber);
            existingDinner.setScheduleDate(dayDate);
            existingDinner.setStartTime(TimeUtils.formatAmPm(dinnerStart));
            existingDinner.setEndTime(TimeUtils.formatAmPm(dinnerEnd));
            if (existingDinner.getActivity() == null || existingDinner.getActivity().isEmpty()) {
                existingDinner.setActivity("🍽️ Dinner & Regional Gastronomy");
            }
            if (existingDinner.getLocation() == null || existingDinner.getLocation().isEmpty()) {
                existingDinner.setLocation("Dining in " + cityName);
            }
            existingDinner.setActivityType("MEAL");
            existingDinner.setDurationMinutes(60);
            existingDinner.setLatitude(cityCoords[0] + 0.002);
            existingDinner.setLongitude(cityCoords[1] + 0.002);
            existingDinner.setCategory("Dining");
            recalculated.add(existingDinner);

            currentTime24 = dinnerEnd;
            dayBreakMinutes += 60;
        }

        // Sort chronologically strictly by start time
        recalculated.sort((a, b) -> {
            int cmp = TimeUtils.compareItineraryTimes(a.getStartTime(), b.getStartTime());
            if (cmp != 0) return cmp;
            int typeCmp = Integer.compare(TimeUtils.getActivityTypeOrder(a.getActivityType()), TimeUtils.getActivityTypeOrder(b.getActivityType()));
            if (typeCmp != 0) return typeCmp;
            long idA = a.getId() != null ? a.getId() : 0L;
            long idB = b.getId() != null ? b.getId() : 0L;
            return Long.compare(idA, idB);
        });

        long seqRecalcId = 1L;
        for (ItineraryItemDto it : recalculated) {
            if (it.getId() == null) {
                it.setId((long) dayNumber * 1000L + seqRecalcId++);
            }
        }

        dayDto.setItems(recalculated);
        dayDto.setTotalDistanceKm(Math.round(dayDistance * 10.0) / 10.0);
        dayDto.setTravelTimeMinutes(dayTravelMinutes);
        dayDto.setSightseeingTimeMinutes(daySightseeingMinutes);
        dayDto.setBreakTimeMinutes(dayBreakMinutes);

        int calculatedTotalDuration = 0;
        if (!recalculated.isEmpty()) {
            ItineraryItemDto first = recalculated.get(0);
            ItineraryItemDto last = recalculated.get(recalculated.size() - 1);
            LocalTime st = TimeUtils.parseTime(first.getStartTime());
            LocalTime et = TimeUtils.parseTime(last.getEndTime());
            if (et.isBefore(st)) {
                calculatedTotalDuration = (int) ChronoUnit.MINUTES.between(st, LocalTime.MAX) + 1 + (int) ChronoUnit.MINUTES.between(LocalTime.MIN, et);
            } else {
                calculatedTotalDuration = (int) ChronoUnit.MINUTES.between(st, et);
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

        int activeMinutes = dayTravelMinutes + daySightseeingMinutes;
        boolean hasTransitStops = recalculated.stream().anyMatch(it -> "REST".equalsIgnoreCase(it.getActivityType()) || ("MEAL".equalsIgnoreCase(it.getActivityType()) && it.getActivity() != null && it.getActivity().toLowerCase().contains("highway")));
        if (hasTransitStops) {
            dayDto.setStatus("Highway Transit");
            dayDto.setWarningMessage(null);
        } else if (dayTravelMinutes > 360) {
            dayDto.setStatus("⚠️ Long Driving Day");
            dayDto.setWarningMessage("This day contains " + TimeUtils.formatDuration(dayTravelMinutes) + " of driving. An intermediate rest stop is recommended.");
        } else if (activeMinutes > 600 || (availableDayMinutes > 0 && activeMinutes > availableDayMinutes)) {
            dayDto.setStatus("Busy");
            dayDto.setWarningMessage(null);
        } else if (dayTravelMinutes < 120 && daySightseeingMinutes < 180) {
            dayDto.setStatus("Relaxed");
            dayDto.setWarningMessage(null);
        } else {
            dayDto.setStatus("Balanced");
            dayDto.setWarningMessage(null);
        }

        if (totalPlacesCount == 0 && (dayDto.getWarningMessage() == null || dayDto.getWarningMessage().isEmpty())) {
            dayDto.setStatus("No attractions discovered");
            dayDto.setWarningMessage("No verified sightseeing attractions could be discovered for " + cityName + ". You can customize or add activities using the button below.");
        }

        populateDayTransitions(dayDto, transportMode);

        return dayDto;
    }

    public void populateDayTransitions(DayItineraryDto dayDto, String transportMode) {
        if (dayDto == null || dayDto.getItems() == null || dayDto.getItems().size() <= 1) {
            return;
        }

        // Automatic schedule correction (Requirement 1):
        // Automatically ripple activities forward if arrival is later than scheduled start
        autoCorrectDaySchedule(dayDto, transportMode);

        List<ItineraryItemDto> items = dayDto.getItems();
        List<double[]> dayGeometry = new ArrayList<>();

        for (int i = 1; i < items.size(); i++) {
            ItineraryItemDto prev = items.get(i - 1);
            ItineraryItemDto curr = items.get(i);

            List<TimelineTransitionDto> segments = new ArrayList<>();

            // 1. Determine Distance and Transit Duration
            double lat1 = (prev.getLatitude() != null && prev.getLatitude() != 0.0) ? prev.getLatitude() : 0.0;
            double lon1 = (prev.getLongitude() != null && prev.getLongitude() != 0.0) ? prev.getLongitude() : 0.0;
            double lat2 = (curr.getLatitude() != null && curr.getLatitude() != 0.0) ? curr.getLatitude() : 0.0;
            double lon2 = (curr.getLongitude() != null && curr.getLongitude() != 0.0) ? curr.getLongitude() : 0.0;

            Double distKm = curr.getTravelDistanceKm();
            Integer travelMins = curr.getTravelTimeMinutes();

            if ((distKm == null || distKm <= 0.0 || travelMins == null || travelMins <= 0) &&
                    lat1 != 0.0 && lon1 != 0.0 && lat2 != 0.0 && lon2 != 0.0) {
                if (googleMapsService != null) {
                    GoogleMapsService.RouteCalculationResult leg = googleMapsService.calculateRouteBetweenCoordinates(lat1, lon1, lat2, lon2, transportMode);
                    if (leg != null && leg.isSuccess()) {
                        distKm = Math.max(0.5, leg.getDistanceKm());
                        travelMins = Math.max(5, leg.getDurationMinutes());
                    }
                }
                if (distKm == null || distKm <= 0.0) {
                    GeoUtils.RoadRouteResult roadLeg = GeoUtils.calculateRealRoadRoute(lat1, lon1, lat2, lon2, transportMode);
                    distKm = Math.max(0.5, roadLeg.getDistanceKm());
                    travelMins = Math.max(5, roadLeg.getDurationMinutes());
                }
                curr.setTravelDistanceKm(distKm);
                curr.setTravelTimeMinutes(travelMins);
            }

            if (distKm == null) distKm = 0.0;
            if (travelMins == null) travelMins = 0;

            // Collect realistic road network geometry waypoints for map rendering
            if (lat1 != 0.0 && lon1 != 0.0 && lat2 != 0.0 && lon2 != 0.0) {
                GeoUtils.RoadRouteResult roadLeg = GeoUtils.calculateRealRoadRoute(lat1, lon1, lat2, lon2, transportMode);
                if (roadLeg != null && roadLeg.getGeometryCoordinates() != null && !roadLeg.getGeometryCoordinates().isEmpty()) {
                    for (double[] pt : roadLeg.getGeometryCoordinates()) {
                        if (dayGeometry.isEmpty() ||
                                Math.abs(dayGeometry.get(dayGeometry.size() - 1)[0] - pt[0]) > 0.00001 ||
                                Math.abs(dayGeometry.get(dayGeometry.size() - 1)[1] - pt[1]) > 0.00001) {
                            dayGeometry.add(pt);
                        }
                    }
                } else {
                    if (dayGeometry.isEmpty()) dayGeometry.add(new double[]{lat1, lon1});
                    dayGeometry.add(new double[]{lat2, lon2});
                }
            }

            // 2. Determine Transport Mode and Format
            String modeNorm = (transportMode != null) ? transportMode.trim().toUpperCase() : "CAR";
            String currType = curr.getActivityType() != null ? curr.getActivityType().toUpperCase() : "SIGHTSEEING";

            String travelIcon;
            String travelText;
            String travelType;

            String formattedDist = (distKm >= 1.0)
                    ? String.format(Locale.US, "%.1f km", distKm)
                    : String.format(Locale.US, "%d m", Math.round(distKm * 1000));
            String formattedTravelDuration = TimeUtils.formatDuration(travelMins);

            if ("WALKING".equals(modeNorm)) {
                travelIcon = "bi-person-walking";
                travelType = "WALK";
                travelText = "Walk to next attraction · " + formattedTravelDuration + (distKm > 0.05 ? " · " + formattedDist : "");
            } else if ("TRANSIT".equals(modeNorm) || "PUBLIC_TRANSPORT".equals(modeNorm)) {
                travelIcon = "bi-bus-front";
                travelType = "TRANSIT";
                travelText = "Public transport · " + formattedTravelDuration + (distKm > 0.05 ? " · " + formattedDist : "");
            } else if ("BICYCLE".equals(modeNorm)) {
                travelIcon = "bi-bicycle";
                travelType = "BIKE";
                travelText = "Bike to next stop · " + formattedTravelDuration + (distKm > 0.05 ? " · " + formattedDist : "");
            } else if ("HOTEL".equals(currType) || (curr.getActivity() != null && curr.getActivity().toLowerCase().contains("hotel"))) {
                travelIcon = "bi-building";
                travelType = "HOTEL_TRANSFER";
                travelText = "Transfer to hotel · " + formattedTravelDuration + (distKm > 0.05 ? " · " + formattedDist : "");
            } else {
                travelIcon = "bi-car-front";
                travelType = "TRAVEL";
                travelText = "Travel to next stop · " + formattedTravelDuration + (distKm > 0.05 ? " · " + formattedDist : "");
            }

            // Calculate Departure, Expected Arrival, Next Activity Start, and Buffer/Conflict
            LocalTime depTime = TimeUtils.parseTime(prev.getEndTime());
            LocalTime nextStartTime = TimeUtils.parseTime(curr.getStartTime());
            LocalTime arrivalTime = depTime.plusMinutes(travelMins);

            String depTimeFormatted = TimeUtils.formatAmPm(depTime);
            String arrivalTimeFormatted = TimeUtils.formatAmPm(arrivalTime);
            String nextStartFormatted = TimeUtils.formatAmPm(nextStartTime);

            int bufferMins = 0;
            boolean hasTimingConflict = false;
            String conflictMsg = null;

            if (nextStartTime.isAfter(arrivalTime)) {
                bufferMins = (int) ChronoUnit.MINUTES.between(arrivalTime, nextStartTime);
            } else if (nextStartTime.isBefore(arrivalTime)) {
                int lateMins = (int) ChronoUnit.MINUTES.between(nextStartTime, arrivalTime);
                hasTimingConflict = true;
                conflictMsg = "Arrival (" + arrivalTimeFormatted + ") is " + TimeUtils.formatDuration(lateMins) + " after scheduled start (" + nextStartFormatted + ")";
            }

            // Add dedicated travel connector segment
            if (travelMins > 0 || distKm > 0.05 || i > 0) {
                TimelineTransitionDto travelSeg = new TimelineTransitionDto(travelIcon, travelText, travelType, distKm, travelMins);
                travelSeg.setFromLocation(prev.getLocation() != null ? prev.getLocation() : prev.getActivity());
                travelSeg.setToLocation(curr.getLocation() != null ? curr.getLocation() : curr.getActivity());
                travelSeg.setDepartureTime(depTimeFormatted);
                travelSeg.setExpectedArrivalTime(arrivalTimeFormatted);
                travelSeg.setNextActivityStartTime(nextStartFormatted);
                travelSeg.setBufferMinutes(bufferMins);
                travelSeg.setMode(modeNorm);
                travelSeg.setModeIcon(travelIcon);
                travelSeg.setModeLabel(
                        "WALK".equals(travelType) ? "Walk to next attraction" :
                        "TRANSIT".equals(travelType) ? "Public transport" :
                        "BIKE".equals(travelType) ? "Bicycle ride" :
                        "HOTEL_TRANSFER".equals(travelType) ? "Transfer to accommodation" :
                        "Travel to next destination"
                );
                if (bufferMins > 0) {
                    travelSeg.setBufferLabel(TimeUtils.formatDuration(bufferMins) + " buffer before next activity");
                } else if (hasTimingConflict) {
                    travelSeg.setBufferLabel(conflictMsg);
                    travelSeg.setHasConflict(true);
                    travelSeg.setConflictMessage(conflictMsg);
                } else {
                    travelSeg.setBufferLabel("Direct arrival at next activity");
                }
                segments.add(travelSeg);
            }

            // 3. Time Gap Analysis (Arrival buffer vs Free time)
            if (bufferMins >= 10 && bufferMins <= 25) {
                TimelineTransitionDto bufferSeg = new TimelineTransitionDto(
                        "bi-clock",
                        "Arrival buffer · " + TimeUtils.formatDuration(bufferMins),
                        "BUFFER",
                        0.0,
                        bufferMins
                );
                segments.add(bufferSeg);
            } else if (bufferMins > 25) {
                TimelineTransitionDto freeSeg = new TimelineTransitionDto(
                        "bi-sun",
                        "Free time · " + TimeUtils.formatDuration(bufferMins),
                        "FREE_TIME",
                        0.0,
                        bufferMins
                );
                segments.add(freeSeg);
            }

            // 4. Schedule Conflict Check (Opening hours & Timing conflict)
            if (hasTimingConflict) {
                TimelineTransitionDto timingConflictSeg = new TimelineTransitionDto(
                        "bi-exclamation-triangle",
                        "Schedule conflict · " + conflictMsg,
                        "CONFLICT",
                        0.0,
                        0
                );
                segments.add(timingConflictSeg);
            }
            if (curr.getNotes() != null && (curr.getNotes().contains("⚠️ Closed") || curr.getNotes().contains("⚠️ Schedule conflict") || curr.getNotes().contains("closes before arrival"))) {
                TimelineTransitionDto conflictSeg = new TimelineTransitionDto(
                        "bi-exclamation-triangle",
                        "Schedule conflict · Next attraction closes before arrival",
                        "CONFLICT",
                        0.0,
                        0
                );
                segments.add(conflictSeg);
            }

            curr.setTransitionSegments(segments);
            if (!segments.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (TimelineTransitionDto s : segments) {
                    if (sb.length() > 0) sb.append(" | ");
                    sb.append(s.getText());
                }
                curr.setTransitionCaption(sb.toString());
            }
        }

        if (!dayGeometry.isEmpty()) {
            dayDto.setRouteGeometry(dayGeometry);
        }
    }

    /**
     * Automatic schedule correction (Requirement 1):
     * 1. Calculates actual travel time between consecutive stops.
     * 2. Calculates expected arrival time = previous activity end time + travel duration.
     * 3. If arrival is later than the next activity's scheduled start, automatically moves that activity forward.
     * 4. Recalculates its end time preserving sightseeing duration, meal duration (45-60 min), and rest breaks.
     * 5. Adjusts subsequent activities chronologically until all activities are strictly valid with zero overlaps.
     */
    public void autoCorrectDaySchedule(DayItineraryDto dayDto, String transportMode) {
        if (dayDto == null || dayDto.getItems() == null || dayDto.getItems().size() <= 1) {
            return;
        }

        List<ItineraryItemDto> items = dayDto.getItems();

        // 1. Ensure travel durations and distances are calculated between sequential items
        for (int i = 1; i < items.size(); i++) {
            ItineraryItemDto prev = items.get(i - 1);
            ItineraryItemDto curr = items.get(i);

            double lat1 = (prev.getLatitude() != null && prev.getLatitude() != 0.0) ? prev.getLatitude() : 0.0;
            double lon1 = (prev.getLongitude() != null && prev.getLongitude() != 0.0) ? prev.getLongitude() : 0.0;
            double lat2 = (curr.getLatitude() != null && curr.getLatitude() != 0.0) ? curr.getLatitude() : 0.0;
            double lon2 = (curr.getLongitude() != null && curr.getLongitude() != 0.0) ? curr.getLongitude() : 0.0;

            Integer travelMins = curr.getTravelTimeMinutes();
            Double distKm = curr.getTravelDistanceKm();
            if ((travelMins == null || travelMins <= 0 || distKm == null || distKm <= 0.0) && lat1 != 0.0 && lon1 != 0.0 && lat2 != 0.0 && lon2 != 0.0) {
                if (googleMapsService != null) {
                    GoogleMapsService.RouteCalculationResult leg = googleMapsService.calculateRouteBetweenCoordinates(lat1, lon1, lat2, lon2, transportMode);
                    if (leg != null && leg.isSuccess()) {
                        distKm = Math.max(0.5, leg.getDistanceKm());
                        travelMins = Math.max(5, leg.getDurationMinutes());
                    }
                }
                if (distKm == null || distKm <= 0.0) {
                    GeoUtils.RoadRouteResult roadLeg = GeoUtils.calculateRealRoadRoute(lat1, lon1, lat2, lon2, transportMode);
                    distKm = Math.max(0.5, roadLeg.getDistanceKm());
                    travelMins = Math.max(5, roadLeg.getDurationMinutes());
                }
                curr.setTravelDistanceKm(distKm);
                curr.setTravelTimeMinutes(travelMins);
            }
        }

        // 2. Cascade forward any overlapping items
        boolean changed = false;
        for (int i = 1; i < items.size(); i++) {
            ItineraryItemDto prev = items.get(i - 1);
            ItineraryItemDto curr = items.get(i);

            int travelMins = (curr.getTravelTimeMinutes() != null && curr.getTravelTimeMinutes() > 0)
                    ? curr.getTravelTimeMinutes() : 0;

            LocalTime depTime = TimeUtils.parseTime(prev.getEndTime());
            LocalTime expectedArrival = depTime.plusMinutes(travelMins);
            LocalTime currStart = TimeUtils.parseTime(curr.getStartTime());

            if (currStart.isBefore(expectedArrival)) {
                // If user explicitly locked with a rigid manual lock, preserve it
                if (Boolean.TRUE.equals(curr.getLocked()) && curr.getNotes() != null && curr.getNotes().contains("Manually locked")) {
                    continue;
                }

                LocalTime newStart = expectedArrival;
                int duration = (curr.getDurationMinutes() != null && curr.getDurationMinutes() > 0)
                        ? curr.getDurationMinutes()
                        : ("MEAL".equalsIgnoreCase(curr.getActivityType()) ? 60 : 45);

                LocalTime newEnd = newStart.plusMinutes(duration);

                curr.setStartTime(TimeUtils.formatAmPm(newStart));
                curr.setEndTime(TimeUtils.formatAmPm(newEnd));
                changed = true;
            }
        }

        if (changed && !items.isEmpty()) {
            ItineraryItemDto first = items.get(0);
            ItineraryItemDto last = items.get(items.size() - 1);
            LocalTime st = TimeUtils.parseTime(first.getStartTime());
            LocalTime et = TimeUtils.parseTime(last.getEndTime());
            int totalActive;
            if (et.isBefore(st)) {
                totalActive = (int) ChronoUnit.MINUTES.between(st, LocalTime.MAX) + 1 + (int) ChronoUnit.MINUTES.between(LocalTime.MIN, et);
            } else {
                totalActive = (int) ChronoUnit.MINUTES.between(st, et);
            }
            dayDto.setTotalDurationMinutes(totalActive);
            int unallocated = Math.max(0, 720 - totalActive);
            dayDto.setUnallocatedTimeMinutes(unallocated);
        }
    }

    /**
     * Dedicated Return Journey Generator (Requirement 3):
     * Generates a separate, geographically validated return itinerary from the final destination back to origin.
     * Incorporates road routes, optional intermediate return stops, meal breaks, and fuel/rest stops.
     */
    public DayItineraryDto generateReturnItinerary(String returnOrigin, String returnDestination,
                                                  List<String> intermediateStops,
                                                  String departureTimeStr,
                                                  LocalDate returnDate,
                                                  String transportMode) {
        if (returnOrigin == null || returnDestination == null) {
            return null;
        }

        LocalDate effDate = returnDate != null ? returnDate : LocalDate.now().plusDays(2);
        DayItineraryDto returnDay = new DayItineraryDto(1, effDate, "Return Journey: " + returnOrigin + " to " + returnDestination);
        returnDay.setDestinationName(returnDestination);
        List<ItineraryItemDto> items = new ArrayList<>();

        double[] originCoords = GeoUtils.getApproximateCoordinates(returnOrigin);
        if (originCoords == null && googleMapsService != null) {
            originCoords = googleMapsService.geocode(returnOrigin);
        }
        if (originCoords == null) originCoords = new double[]{17.3850, 78.4867};

        double[] destCoords = GeoUtils.getApproximateCoordinates(returnDestination);
        if (destCoords == null && googleMapsService != null) {
            destCoords = googleMapsService.geocode(returnDestination);
        }
        if (destCoords == null) destCoords = new double[]{12.9716, 77.5946};

        String currentDepTime = (departureTimeStr != null && !departureTimeStr.isEmpty()) ? departureTimeStr : "08:30 AM";
        String currentTime24 = TimeUtils.to24Hour(currentDepTime);
        if (currentTime24 == null) currentTime24 = "08:30";

        // 1. Initial Departure Stop
        ItineraryItemDto departItem = new ItineraryItemDto();
        departItem.setId(9001L);
        departItem.setDayNumber(1);
        departItem.setScheduleDate(effDate);
        departItem.setStartTime(TimeUtils.formatAmPm(currentTime24));
        String departEnd = TimeUtils.addMinutes(currentTime24, 15);
        departItem.setEndTime(TimeUtils.formatAmPm(departEnd));
        departItem.setActivity("🚗 Depart from " + returnOrigin + " (Return Journey)");
        departItem.setLocation(returnOrigin);
        departItem.setActivityType("TRANSIT");
        departItem.setCategory("Departure");
        departItem.setDurationMinutes(15);
        departItem.setLatitude(originCoords[0]);
        departItem.setLongitude(originCoords[1]);
        departItem.setTravelDistanceKm(0.0);
        departItem.setTravelTimeMinutes(0);
        departItem.setGoogleMapsUrl(GeoUtils.buildGoogleMapsUrl(returnOrigin, null, transportMode));
        items.add(departItem);

        currentTime24 = departEnd;
        double lastLat = originCoords[0];
        double lastLng = originCoords[1];

        double totalDist = 0.0;
        int totalTravelMins = 0;
        int totalBreakMins = 15;
        long itemIdSeq = 9002L;

        // Path of stops: intermediateStops -> final destination
        List<String> path = new ArrayList<>();
        if (intermediateStops != null && !intermediateStops.isEmpty()) {
            for (String s : intermediateStops) {
                if (s != null && !s.trim().isEmpty() && !s.equalsIgnoreCase(returnOrigin) && !s.equalsIgnoreCase(returnDestination)) {
                    path.add(s.trim());
                }
            }
        }
        path.add(returnDestination);

        boolean hadLunch = false;

        for (int pIdx = 0; pIdx < path.size(); pIdx++) {
            String nextStop = path.get(pIdx);
            boolean isFinal = (pIdx == path.size() - 1);

            double[] stopCoords = GeoUtils.getApproximateCoordinates(nextStop);
            if (stopCoords == null && googleMapsService != null) {
                stopCoords = googleMapsService.geocode(nextStop);
            }
            if (stopCoords == null) {
                stopCoords = destCoords;
            }

            // Calculate road route from lastLat/lastLng to stopCoords
            GeoUtils.RoadRouteResult roadLeg = GeoUtils.calculateRealRoadRoute(lastLat, lastLng, stopCoords[0], stopCoords[1], transportMode);
            double legDist = Math.max(1.0, roadLeg.getDistanceKm());
            int legMins = Math.max(10, roadLeg.getDurationMinutes());

            // If drive is long (> 3.5 hours) or crosses midday and haven't had lunch, schedule lunch mid-way
            if (!hadLunch && (legMins > 210 || TimeUtils.parseTime(currentTime24).plusMinutes(legMins / 2).isAfter(LocalTime.of(12, 15)))) {
                int preLunchDrive = Math.min(legMins / 2, 120);
                String lunchArrival = TimeUtils.addMinutes(currentTime24, preLunchDrive);
                String lunchStart = TimeUtils.roundUpTime24(lunchArrival, 15);
                String lunchEnd = TimeUtils.addMinutes(lunchStart, 60);

                double lunchDist = Math.round(legDist * 0.45 * 10.0) / 10.0;
                ItineraryItemDto lunchItem = new ItineraryItemDto();
                lunchItem.setId(itemIdSeq++);
                lunchItem.setDayNumber(1);
                lunchItem.setScheduleDate(effDate);
                lunchItem.setStartTime(TimeUtils.formatAmPm(lunchStart));
                lunchItem.setEndTime(TimeUtils.formatAmPm(lunchEnd));
                lunchItem.setActivity("🍴 Highway Lunch Break & Regional Gastronomy");
                lunchItem.setLocation("Corridor towards " + nextStop);
                lunchItem.setActivityType("MEAL");
                lunchItem.setCategory("Dining");
                lunchItem.setDurationMinutes(60);
                lunchItem.setLatitude((lastLat + stopCoords[0]) / 2.0);
                lunchItem.setLongitude((lastLng + stopCoords[1]) / 2.0);
                lunchItem.setTravelDistanceKm(lunchDist);
                lunchItem.setTravelTimeMinutes(preLunchDrive);
                lunchItem.setGoogleMapsUrl(GeoUtils.buildGoogleMapsUrl("Highway Restaurant near " + nextStop, null, transportMode));
                items.add(lunchItem);

                totalDist += lunchDist;
                totalTravelMins += preLunchDrive;
                totalBreakMins += 60;
                hadLunch = true;

                legDist = Math.round((legDist - lunchDist) * 10.0) / 10.0;
                legMins = Math.max(15, legMins - preLunchDrive);
                currentTime24 = lunchEnd;
                lastLat = (lastLat + stopCoords[0]) / 2.0;
                lastLng = (lastLng + stopCoords[1]) / 2.0;
            }

            String rawArrival = TimeUtils.addMinutes(currentTime24, legMins);
            String arrivalTimeFormatted = TimeUtils.formatAmPm(rawArrival);

            if (!isFinal) {
                // Intermediate stop sightseeing/rest
                int visitDur = 60;
                String stopEnd = TimeUtils.addMinutes(rawArrival, visitDur);

                ItineraryItemDto stopItem = new ItineraryItemDto();
                stopItem.setId(itemIdSeq++);
                stopItem.setDayNumber(1);
                stopItem.setScheduleDate(effDate);
                stopItem.setStartTime(arrivalTimeFormatted);
                stopItem.setEndTime(TimeUtils.formatAmPm(stopEnd));
                stopItem.setActivity("📍 Sightseeing & Explore " + nextStop);
                stopItem.setLocation(nextStop);
                stopItem.setActivityType("SIGHTSEEING");
                stopItem.setCategory("Attraction");
                stopItem.setDurationMinutes(visitDur);
                stopItem.setLatitude(stopCoords[0]);
                stopItem.setLongitude(stopCoords[1]);
                stopItem.setTravelDistanceKm(legDist);
                stopItem.setTravelTimeMinutes(legMins);
                stopItem.setGoogleMapsUrl(GeoUtils.buildGoogleMapsUrl(nextStop, null, transportMode));
                items.add(stopItem);

                totalDist += legDist;
                totalTravelMins += legMins;
                totalBreakMins += visitDur;
                currentTime24 = stopEnd;
                lastLat = stopCoords[0];
                lastLng = stopCoords[1];
            } else {
                // Final Arrival at origin
                ItineraryItemDto arrivalItem = new ItineraryItemDto();
                arrivalItem.setId(itemIdSeq++);
                arrivalItem.setDayNumber(1);
                arrivalItem.setScheduleDate(effDate);
                arrivalItem.setStartTime(arrivalTimeFormatted);
                arrivalItem.setEndTime(arrivalTimeFormatted);
                arrivalItem.setActivity("🏡 Arrival at " + returnDestination + " (Journey Completed)");
                arrivalItem.setLocation(returnDestination);
                arrivalItem.setActivityType("TRANSIT");
                arrivalItem.setCategory("Arrival");
                arrivalItem.setDurationMinutes(0);
                arrivalItem.setLatitude(stopCoords[0]);
                arrivalItem.setLongitude(stopCoords[1]);
                arrivalItem.setTravelDistanceKm(legDist);
                arrivalItem.setTravelTimeMinutes(legMins);
                arrivalItem.setGoogleMapsUrl(GeoUtils.buildGoogleMapsUrl(returnDestination, null, transportMode));
                items.add(arrivalItem);

                totalDist += legDist;
                totalTravelMins += legMins;
                currentTime24 = rawArrival;
            }
        }

        returnDay.setItems(items);
        returnDay.setTotalDistanceKm(Math.round(totalDist * 10.0) / 10.0);
        returnDay.setTravelTimeMinutes(totalTravelMins);
        returnDay.setBreakTimeMinutes(totalBreakMins);
        returnDay.setTotalDurationMinutes(totalTravelMins + totalBreakMins);
        returnDay.setStatus("Return Balanced");

        populateDayTransitions(returnDay, transportMode);

        return returnDay;
    }

    public static String normalizePlaceKey(String placeName) {
        if (placeName == null) return "";
        return placeName.trim().toLowerCase()
                .replaceAll("(?i)^(visit|explore|see|tour|check\\s*in|depart|travel\\s*to)\\s+", "")
                .replaceAll("[^a-z0-9]", "");
    }
}