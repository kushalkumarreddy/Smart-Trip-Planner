package com.example.tripplanner.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TripResponseDto {

    private Long id;
    private String tripName;
    private String startLocation;
    private Double startLat;
    private Double startLng;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer totalDays;
    private String transportMode;
    private String travelStyle;
    private Integer travelersCount;
    private String dailyStartTime;
    private String dailyEndTime;
    private String hotelLocation;
    private Double hotelLat;
    private Double hotelLng;
    private String budgetRange;
    private String preferredInterests;

    // Metrics & KPIs
    private Double totalDistanceKm = 0.0;
    private Integer totalTravelTimeMinutes = 0;
    private String formattedTotalTravelTime;
    private Integer destinationsCount = 0;
    private Integer sightseeingPlacesCount = 0;
    private Integer visitedPlacesCount = 0;
    private Integer completionPercentage = 0;

    // Feasibility & Warnings
    private String feasibilityStatus = "Optimal"; // Optimal, Busy, Difficult
    private List<String> warnings = new ArrayList<>();

    // Collaboration & Sharing
    private String shareCode;
    private List<TripCollaboratorDto> collaborators = new ArrayList<>();
    private List<TripSuggestionDto> suggestions = new ArrayList<>();

    // Detailed Data
    private List<DestinationDto> destinations = new ArrayList<>();
    private List<DayItineraryDto> dayItineraries = new ArrayList<>();
    private RouteSummaryDto routeSummary;
    private List<SightseeingPlaceDto> recommendedNearbyPlaces = new ArrayList<>();

    public TripResponseDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTripName() {
        return tripName;
    }

    public void setTripName(String tripName) {
        this.tripName = tripName;
    }

    public String getStartLocation() {
        return startLocation;
    }

    public void setStartLocation(String startLocation) {
        this.startLocation = startLocation;
    }

    public Double getStartLat() {
        return startLat;
    }

    public void setStartLat(Double startLat) {
        this.startLat = startLat;
    }

    public Double getStartLng() {
        return startLng;
    }

    public void setStartLng(Double startLng) {
        this.startLng = startLng;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(Integer totalDays) {
        this.totalDays = totalDays;
    }

    public String getTransportMode() {
        return transportMode;
    }

    public void setTransportMode(String transportMode) {
        this.transportMode = transportMode;
    }

    public String getTravelStyle() {
        return travelStyle;
    }

    public void setTravelStyle(String travelStyle) {
        this.travelStyle = travelStyle;
    }

    public Integer getTravelersCount() {
        return travelersCount;
    }

    public void setTravelersCount(Integer travelersCount) {
        this.travelersCount = travelersCount;
    }

    public String getDailyStartTime() {
        return dailyStartTime;
    }

    public void setDailyStartTime(String dailyStartTime) {
        this.dailyStartTime = dailyStartTime;
    }

    public String getDailyEndTime() {
        return dailyEndTime;
    }

    public void setDailyEndTime(String dailyEndTime) {
        this.dailyEndTime = dailyEndTime;
    }

    public String getHotelLocation() {
        return hotelLocation;
    }

    public void setHotelLocation(String hotelLocation) {
        this.hotelLocation = hotelLocation;
    }

    public Double getHotelLat() {
        return hotelLat;
    }

    public void setHotelLat(Double hotelLat) {
        this.hotelLat = hotelLat;
    }

    public Double getHotelLng() {
        return hotelLng;
    }

    public void setHotelLng(Double hotelLng) {
        this.hotelLng = hotelLng;
    }

    public Double getTotalDistanceKm() {
        return totalDistanceKm;
    }

    public void setTotalDistanceKm(Double totalDistanceKm) {
        this.totalDistanceKm = totalDistanceKm;
    }

    public Integer getTotalTravelTimeMinutes() {
        return totalTravelTimeMinutes;
    }

    public void setTotalTravelTimeMinutes(Integer totalTravelTimeMinutes) {
        this.totalTravelTimeMinutes = totalTravelTimeMinutes;
    }

    public String getFormattedTotalTravelTime() {
        return formattedTotalTravelTime;
    }

    public void setFormattedTotalTravelTime(String formattedTotalTravelTime) {
        this.formattedTotalTravelTime = formattedTotalTravelTime;
    }

    public Integer getDestinationsCount() {
        return destinationsCount;
    }

    public void setDestinationsCount(Integer destinationsCount) {
        this.destinationsCount = destinationsCount;
    }

    public Integer getSightseeingPlacesCount() {
        return sightseeingPlacesCount;
    }

    public void setSightseeingPlacesCount(Integer sightseeingPlacesCount) {
        this.sightseeingPlacesCount = sightseeingPlacesCount;
    }

    public Integer getVisitedPlacesCount() {
        return visitedPlacesCount;
    }

    public void setVisitedPlacesCount(Integer visitedPlacesCount) {
        this.visitedPlacesCount = visitedPlacesCount;
    }

    public Integer getCompletionPercentage() {
        return completionPercentage;
    }

    public void setCompletionPercentage(Integer completionPercentage) {
        this.completionPercentage = completionPercentage;
    }

    public String getFeasibilityStatus() {
        return feasibilityStatus;
    }

    public void setFeasibilityStatus(String feasibilityStatus) {
        this.feasibilityStatus = feasibilityStatus;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public List<DestinationDto> getDestinations() {
        return destinations;
    }

    public void setDestinations(List<DestinationDto> destinations) {
        this.destinations = destinations;
    }

    public List<DayItineraryDto> getDayItineraries() {
        return dayItineraries;
    }

    public void setDayItineraries(List<DayItineraryDto> dayItineraries) {
        this.dayItineraries = dayItineraries;
    }

    public RouteSummaryDto getRouteSummary() {
        return routeSummary;
    }

    public void setRouteSummary(RouteSummaryDto routeSummary) {
        this.routeSummary = routeSummary;
    }

    public String getShareCode() {
        return shareCode;
    }

    public void setShareCode(String shareCode) {
        this.shareCode = shareCode;
    }

    public List<TripCollaboratorDto> getCollaborators() {
        return collaborators;
    }

    public void setCollaborators(List<TripCollaboratorDto> collaborators) {
        this.collaborators = collaborators;
    }

    public List<TripSuggestionDto> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<TripSuggestionDto> suggestions) {
        this.suggestions = suggestions;
    }

    public List<SightseeingPlaceDto> getRecommendedNearbyPlaces() {
        return recommendedNearbyPlaces;
    }

    public void setRecommendedNearbyPlaces(List<SightseeingPlaceDto> recommendedNearbyPlaces) {
        this.recommendedNearbyPlaces = recommendedNearbyPlaces;
    }

    public String getBudgetRange() {
        return budgetRange;
    }

    public void setBudgetRange(String budgetRange) {
        this.budgetRange = budgetRange;
    }

    public String getPreferredInterests() {
        return preferredInterests;
    }

    public void setPreferredInterests(String preferredInterests) {
        this.preferredInterests = preferredInterests;
    }
}
