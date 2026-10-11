package com.example.tripplanner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TripRequestDto {

    private String tripName;

    @NotBlank(message = "Start location is required")
    private String startLocation;

    @NotEmpty(message = "At least one destination is required")
    private List<String> destinationNames = new ArrayList<>();

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private String transportMode = "CAR"; // CAR, BIKE, PUBLIC_TRANSPORT, WALKING
    private String travelStyle = "BALANCED"; // RELAXED, BALANCED, FAST_PACED
    private Integer travelersCount = 1;
    private String dailyStartTime = "08:00";
    private String dailyEndTime = "20:00";
    private String hotelLocation;
    private String budgetRange;
    private List<String> preferredInterests = new ArrayList<>();

    public TripRequestDto() {
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

    public List<String> getDestinationNames() {
        return destinationNames;
    }

    public void setDestinationNames(List<String> destinationNames) {
        this.destinationNames = destinationNames;
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

    public String getBudgetRange() {
        return budgetRange;
    }

    public void setBudgetRange(String budgetRange) {
        this.budgetRange = budgetRange;
    }

    public List<String> getPreferredInterests() {
        return preferredInterests;
    }

    public void setPreferredInterests(List<String> preferredInterests) {
        this.preferredInterests = preferredInterests;
    }
}
