package com.example.tripplanner.dto;

import java.util.ArrayList;
import java.util.List;

public class TripVersionComparisonDto {

    private Long tripId;
    private TripVersionSummaryDto versionA;
    private TripVersionSummaryDto versionB;

    private List<String> destinationChanges = new ArrayList<>();
    private List<String> stayDurationChanges = new ArrayList<>();
    private List<String> travelDateChanges = new ArrayList<>();
    private List<String> accommodationChanges = new ArrayList<>();
    private List<String> activityChanges = new ArrayList<>();
    private List<String> routePreferenceChanges = new ArrayList<>();

    private Double distanceDiffKm = 0.0; // Version B - Version A
    private Integer travelTimeDiffMinutes = 0; // Version B - Version A
    private String summaryText;
    private Boolean identical = false;

    public TripVersionComparisonDto() {
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public TripVersionSummaryDto getVersionA() {
        return versionA;
    }

    public void setVersionA(TripVersionSummaryDto versionA) {
        this.versionA = versionA;
    }

    public TripVersionSummaryDto getVersionB() {
        return versionB;
    }

    public void setVersionB(TripVersionSummaryDto versionB) {
        this.versionB = versionB;
    }

    public List<String> getDestinationChanges() {
        return destinationChanges;
    }

    public void setDestinationChanges(List<String> destinationChanges) {
        this.destinationChanges = destinationChanges;
    }

    public List<String> getStayDurationChanges() {
        return stayDurationChanges;
    }

    public void setStayDurationChanges(List<String> stayDurationChanges) {
        this.stayDurationChanges = stayDurationChanges;
    }

    public List<String> getTravelDateChanges() {
        return travelDateChanges;
    }

    public void setTravelDateChanges(List<String> travelDateChanges) {
        this.travelDateChanges = travelDateChanges;
    }

    public List<String> getAccommodationChanges() {
        return accommodationChanges;
    }

    public void setAccommodationChanges(List<String> accommodationChanges) {
        this.accommodationChanges = accommodationChanges;
    }

    public List<String> getActivityChanges() {
        return activityChanges;
    }

    public void setActivityChanges(List<String> activityChanges) {
        this.activityChanges = activityChanges;
    }

    public List<String> getRoutePreferenceChanges() {
        return routePreferenceChanges;
    }

    public void setRoutePreferenceChanges(List<String> routePreferenceChanges) {
        this.routePreferenceChanges = routePreferenceChanges;
    }

    public Double getDistanceDiffKm() {
        return distanceDiffKm;
    }

    public void setDistanceDiffKm(Double distanceDiffKm) {
        this.distanceDiffKm = distanceDiffKm;
    }

    public Integer getTravelTimeDiffMinutes() {
        return travelTimeDiffMinutes;
    }

    public void setTravelTimeDiffMinutes(Integer travelTimeDiffMinutes) {
        this.travelTimeDiffMinutes = travelTimeDiffMinutes;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public Boolean getIdentical() {
        return identical;
    }

    public boolean isIdentical() {
        return Boolean.TRUE.equals(identical);
    }

    public void setIdentical(Boolean identical) {
        this.identical = identical;
    }
}
