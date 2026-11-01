package com.example.tripplanner.dto;

import java.time.LocalDate;

public class ItineraryItemDto {

    private Long id;
    private Long tripId;
    private Integer dayNumber;
    private LocalDate scheduleDate;
    private String startTime;
    private String endTime;
    private String activity;
    private String location;
    private String activityType; // DEPARTURE, ARRIVAL, SIGHTSEEING, MEAL, REST, HOTEL
    private Long placeId;
    private Double travelDistanceKm;
    private Integer travelTimeMinutes;
    private Integer durationMinutes;
    private Double rating;
    private Boolean visited;
    private Boolean locked = false;
    private String notes;
    private Double latitude;
    private Double longitude;
    private String category;
    private String transitionCaption;
    private java.util.List<TimelineTransitionDto> transitionSegments = new java.util.ArrayList<>();
    private String proximityTier; // IN_DESTINATION, NEARBY_ATTRACTION, NEARBY_TOWN, REGIONAL_OPTIONAL
    private String townOrArea;

    public ItineraryItemDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public Integer getDayNumber() {
        return dayNumber;
    }

    public void setDayNumber(Integer dayNumber) {
        this.dayNumber = dayNumber;
    }

    public LocalDate getScheduleDate() {
        return scheduleDate;
    }

    public void setScheduleDate(LocalDate scheduleDate) {
        this.scheduleDate = scheduleDate;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getActivity() {
        return activity;
    }

    public void setActivity(String activity) {
        this.activity = activity;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getActivityType() {
        return activityType;
    }

    public void setActivityType(String activityType) {
        this.activityType = activityType;
    }

    public Long getPlaceId() {
        return placeId;
    }

    public void setPlaceId(Long placeId) {
        this.placeId = placeId;
    }

    public Double getTravelDistanceKm() {
        return travelDistanceKm;
    }

    public void setTravelDistanceKm(Double travelDistanceKm) {
        this.travelDistanceKm = travelDistanceKm;
    }

    public Integer getTravelTimeMinutes() {
        return travelTimeMinutes;
    }

    public void setTravelTimeMinutes(Integer travelTimeMinutes) {
        this.travelTimeMinutes = travelTimeMinutes;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public Boolean getVisited() {
        return visited != null ? visited : false;
    }

    public void setVisited(Boolean visited) {
        this.visited = visited;
    }

    public Boolean getLocked() {
        return locked != null ? locked : false;
    }

    public void setLocked(Boolean locked) {
        this.locked = locked;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTransitionCaption() {
        return transitionCaption;
    }

    public void setTransitionCaption(String transitionCaption) {
        this.transitionCaption = transitionCaption;
    }

    public java.util.List<TimelineTransitionDto> getTransitionSegments() {
        if (transitionSegments == null) {
            transitionSegments = new java.util.ArrayList<>();
        }
        return transitionSegments;
    }

    public void setTransitionSegments(java.util.List<TimelineTransitionDto> transitionSegments) {
        this.transitionSegments = transitionSegments;
    }

    public String getProximityTier() {
        return proximityTier;
    }

    public void setProximityTier(String proximityTier) {
        this.proximityTier = proximityTier;
    }

    public String getTownOrArea() {
        return townOrArea;
    }

    public void setTownOrArea(String townOrArea) {
        this.townOrArea = townOrArea;
    }
}
