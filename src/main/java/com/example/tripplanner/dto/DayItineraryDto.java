package com.example.tripplanner.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DayItineraryDto {

    private Integer dayNumber;
    private LocalDate date;
    private String title;
    // Metrics definitions (Requirement 6):
    // travelTimeMinutes: Sum of travel durations between sequential itinerary locations.
    // sightseeingTimeMinutes: Sum of attraction and experience durations.
    // breakTimeMinutes: Sum of meal, rest, and other planned breaks.
    // totalDurationMinutes: The elapsed period between the day's first planned activity start and last planned activity end time.
    // unallocatedTimeMinutes: Any genuine remaining time that has not been assigned before day's dailyEndTime.
    private Double totalDistanceKm = 0.0;
    private Integer travelTimeMinutes = 0;
    private Integer sightseeingTimeMinutes = 0;
    private Integer breakTimeMinutes = 0;
    private Integer totalDurationMinutes = 0;
    private Integer unallocatedTimeMinutes = 0;
    private String status = "Balanced"; // Balanced, Relaxed, Long Driving Day, Overcrowded
    private String warningMessage;
    private Integer visitedCount = 0;
    private Integer totalPlacesCount = 0;
    private List<ItineraryItemDto> items = new ArrayList<>();

    public DayItineraryDto() {
    }

    public DayItineraryDto(Integer dayNumber, LocalDate date, String title) {
        this.dayNumber = dayNumber;
        this.date = date;
        this.title = title;
    }

    public Integer getDayNumber() {
        return dayNumber;
    }

    public void setDayNumber(Integer dayNumber) {
        this.dayNumber = dayNumber;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Double getTotalDistanceKm() {
        return totalDistanceKm;
    }

    public void setTotalDistanceKm(Double totalDistanceKm) {
        this.totalDistanceKm = totalDistanceKm;
    }

    public Integer getTravelTimeMinutes() {
        return travelTimeMinutes;
    }

    public void setTravelTimeMinutes(Integer travelTimeMinutes) {
        this.travelTimeMinutes = travelTimeMinutes;
    }

    public Integer getSightseeingTimeMinutes() {
        return sightseeingTimeMinutes;
    }

    public void setSightseeingTimeMinutes(Integer sightseeingTimeMinutes) {
        this.sightseeingTimeMinutes = sightseeingTimeMinutes;
    }

    public Integer getBreakTimeMinutes() {
        return breakTimeMinutes;
    }

    public void setBreakTimeMinutes(Integer breakTimeMinutes) {
        this.breakTimeMinutes = breakTimeMinutes;
    }

    public Integer getTotalDurationMinutes() {
        return totalDurationMinutes;
    }

    public void setTotalDurationMinutes(Integer totalDurationMinutes) {
        this.totalDurationMinutes = totalDurationMinutes;
    }

    public Integer getUnallocatedTimeMinutes() {
        return unallocatedTimeMinutes;
    }

    public void setUnallocatedTimeMinutes(Integer unallocatedTimeMinutes) {
        this.unallocatedTimeMinutes = unallocatedTimeMinutes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getWarningMessage() {
        return warningMessage;
    }

    public void setWarningMessage(String warningMessage) {
        this.warningMessage = warningMessage;
    }

    public Integer getVisitedCount() {
        return visitedCount;
    }

    public void setVisitedCount(Integer visitedCount) {
        this.visitedCount = visitedCount;
    }

    public Integer getTotalPlacesCount() {
        return totalPlacesCount;
    }

    public void setTotalPlacesCount(Integer totalPlacesCount) {
        this.totalPlacesCount = totalPlacesCount;
    }

    public List<ItineraryItemDto> getItems() {
        return items;
    }

    public void setItems(List<ItineraryItemDto> items) {
        this.items = items;
    }
}
