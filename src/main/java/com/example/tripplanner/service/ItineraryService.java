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
        String hotelLocation = trip.getHotelLocation();

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

            // 1. Morning Departure
            String dayOrigin = (hotelLocation != null && !hotelLocation.isEmpty() && dayIndex > 0) ? hotelLocation : previousDayEndLocation;
            ItineraryItemDto departItem = new ItineraryItemDto();
            departItem.setDayNumber(dayNumber);
            departItem.setScheduleDate(currentDate);
            departItem.setStartTime(currentTime);
            departItem.setEndTime(currentTime);
            departItem.setActivity("🚗 Depart from " + dayOrigin);
            departItem.setLocation(dayOrigin);
            departItem.setActivityType("DEPARTURE");
            items.add(departItem);

            String currentLegOrigin = dayOrigin;
            boolean hadLunch = false;

            // Process destinations for this day
            if (dayDests.isEmpty()) {
                // Leisure / sight day in previous destination
                List<SightseeingPlaceDto> leisurePlaces = recommendationService.getRecommendedPlaces(previousDayEndLocation, null, "rating", null);
                int added = 0;
                for (SightseeingPlaceDto p : leisurePlaces) {
                    if (added >= 3) break;
                    // Transit to place
                    int transitMins = p.getEstimatedTravelTimeMinutes() != null ? p.getEstimatedTravelTimeMinutes() : 15;
                    double distKm = p.getDistanceKm() != null ? p.getDistanceKm() : 3.0;
                    currentTime = TimeUtils.addMinutes(currentTime, defaultBufferMinutes);

                    // Add Sightseeing activity
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

                    currentTime = visitEnd;
                    dayDistance += distKm;
                    dayTravelMinutes += transitMins;
                    daySightseeingMinutes += dur;
                    totalPlacesCount++;
                    added++;
                }
            } else {
                for (Destination dest : dayDests) {
                    // Travel from current point to this destination
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

                    // Activity buffer after travel
                    currentTime = TimeUtils.addMinutes(currentTime, defaultBufferMinutes);
                    dayBreakMinutes += defaultBufferMinutes;

                    // Lunch check: If time is around 12:00 - 14:00 and haven't had lunch yet
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
                        currentTime = TimeUtils.addMinutes(currentTime, 15); // brief post-meal buffer
                    }

                    // Fetch places for this destination (either from persisted place entities or recommended catalog)
                    List<SightseeingPlaceDto> destPlaces = getOrGeneratePlacesForDestination(dest);
                    int placesScheduled = 0;

                    for (SightseeingPlaceDto place : destPlaces) {
                        if (placesScheduled >= 3) break; // keep schedule realistic

                        int visitDuration = place.getEstimatedVisitDurationMinutes() != null ? place.getEstimatedVisitDurationMinutes() : 90;
                        int travelTime = place.getEstimatedTravelTimeMinutes() != null ? place.getEstimatedTravelTimeMinutes() : 15;
                        double placeDist = place.getDistanceKm() != null ? place.getDistanceKm() : 2.5;

                        String actStart = TimeUtils.addMinutes(currentTime, travelTime);
                        String actEnd = TimeUtils.addMinutes(actStart, visitDuration);

                        // If activity extends beyond daily cutoff (e.g. 20:30), stop scheduling
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
                        visitItem.setNotes(place.getDescription());
                        items.add(visitItem);

                        if (visitItem.getVisited()) {
                            visitedPlacesCount++;
                        }
                        totalPlacesCount++;

                        dayDistance += placeDist;
                        dayTravelMinutes += travelTime;
                        daySightseeingMinutes += visitDuration;
                        placesScheduled++;

                        // Add 15-20 min buffer between activities
                        currentTime = TimeUtils.addMinutes(actEnd, defaultBufferMinutes);
                        dayBreakMinutes += defaultBufferMinutes;
                    }
                }
            }

            // Evening Hotel / Dinner Check-in
            String eveningStay = (hotelLocation != null && !hotelLocation.isEmpty()) ? hotelLocation : ("Hotel / Resort in " + previousDayEndLocation);
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
            if (dayTravelMinutes >= 420) { // > 7 hours travel
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

        // Retrieve curated recommendations
        return recommendationService.getRecommendedPlaces(dest.getName(), null, "popularity", null);
    }
}
