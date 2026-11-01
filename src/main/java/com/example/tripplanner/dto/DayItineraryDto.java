package com.example.tripplanner.dto;

import com.example.tripplanner.util.TimeUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    private String destinationName;
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

    public String getDestinationName() {
        return destinationName;
    }

    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    public List<ItineraryItemDto> getItems() {
        return items;
    }

    public void setItems(List<ItineraryItemDto> items) {
        this.items = items;
    }

    public String getPlannedStartTime() {
        if (items != null && !items.isEmpty()) {
            for (ItineraryItemDto item : items) {
                if (item.getStartTime() != null && !item.getStartTime().isEmpty()) {
                    return TimeUtils.formatAmPm(item.getStartTime());
                }
            }
        }
        return "8:00 AM";
    }

    public String getPlannedEndTime() {
        if (items != null && !items.isEmpty()) {
            for (int i = items.size() - 1; i >= 0; i--) {
                ItineraryItemDto item = items.get(i);
                if (item.getEndTime() != null && !item.getEndTime().isEmpty()) {
                    return TimeUtils.formatAmPm(item.getEndTime());
                }
            }
        }
        return "8:30 PM";
    }

    public String getPlannedTimeWindow() {
        return getPlannedStartTime() + " – " + getPlannedEndTime();
    }

    public int getPlannedStopsCount() {
        if (items == null) return 0;
        int count = 0;
        for (ItineraryItemDto it : items) {
            String actType = it.getActivityType() != null ? it.getActivityType().toUpperCase() : "";
            if (!"OVERNIGHT".equalsIgnoreCase(actType)) {
                count++;
            }
        }
        return count > 0 ? count : items.size();
    }

    public String getFormattedTravelTime() {
        return TimeUtils.formatDuration(travelTimeMinutes != null ? travelTimeMinutes : 0);
    }

    public String getFormattedSightseeingTime() {
        return TimeUtils.formatDuration(sightseeingTimeMinutes != null ? sightseeingTimeMinutes : 0);
    }

    public String getFormattedBreakTime() {
        return TimeUtils.formatDuration(breakTimeMinutes != null ? breakTimeMinutes : 0);
    }

    public int getTotalActiveMinutes() {
        int t = travelTimeMinutes != null ? travelTimeMinutes : 0;
        int s = sightseeingTimeMinutes != null ? sightseeingTimeMinutes : 0;
        int b = breakTimeMinutes != null ? breakTimeMinutes : 0;
        return t + s + b;
    }

    public String getFormattedTotalActiveTime() {
        return TimeUtils.formatDuration(getTotalActiveMinutes());
    }

    public String getFormattedFreeTime() {
        int free = unallocatedTimeMinutes != null ? Math.max(0, unallocatedTimeMinutes) : 0;
        return TimeUtils.formatDuration(free);
    }

    public String getFormattedDistance() {
        if (totalDistanceKm == null || totalDistanceKm <= 0.05) return "0 km";
        return String.format(Locale.US, "%.1f km", totalDistanceKm);
    }

    public String getFeasibilityState() {
        if (status != null && (status.equalsIgnoreCase("Overcrowded") || status.equalsIgnoreCase("Overloaded"))) {
            return "Overloaded";
        }
        if (unallocatedTimeMinutes != null && unallocatedTimeMinutes < 0) {
            return "Overloaded";
        }
        if (totalDurationMinutes != null && totalDurationMinutes > 750) { // > 12.5 hrs
            return "Overloaded";
        }
        if (items != null) {
            for (ItineraryItemDto it : items) {
                if (it.getNotes() != null && (it.getNotes().contains("⚠️ Closed") || it.getNotes().contains("⚠️ Schedule conflict") || it.getNotes().contains("closes before arrival"))) {
                    return "Overloaded";
                }
            }
        }
        if ((unallocatedTimeMinutes != null && unallocatedTimeMinutes < 45) || (totalDurationMinutes != null && totalDurationMinutes > 540) || (travelTimeMinutes != null && travelTimeMinutes > 240)) {
            return "Busy";
        }
        return "Comfortable";
    }

    public String getFeasibilityBadgeClass() {
        return switch (getFeasibilityState()) {
            case "Overloaded" -> "badge-feasibility-overloaded";
            case "Busy" -> "badge-feasibility-busy";
            default -> "badge-feasibility-comfortable";
        };
    }

    public String getFeasibilityDescription() {
        return switch (getFeasibilityState()) {
            case "Overloaded" -> "Schedule exceeds realistic available daytime with potential timing constraints. Consider moving some stops.";
            case "Busy" -> "Full and active schedule with tight buffers between stops. Plan ahead for punctual departures.";
            default -> "Well-paced schedule with comfortable travel buffers and generous leisure time.";
        };
    }

    public List<String> getProblematicActivities() {
        List<String> list = new ArrayList<>();
        if (items != null) {
            for (ItineraryItemDto it : items) {
                if (it.getNotes() != null && (it.getNotes().contains("⚠️") || it.getNotes().contains("conflict") || it.getNotes().contains("Closed"))) {
                    list.add(it.getActivity() != null ? it.getActivity() : "Stop #" + it.getId());
                }
            }
            if (list.isEmpty() && "Overloaded".equals(getFeasibilityState()) && items.size() > 4) {
                for (int i = items.size() - 1; i >= 0; i--) {
                    ItineraryItemDto it = items.get(i);
                    if ("SIGHTSEEING".equalsIgnoreCase(it.getActivityType())) {
                        list.add(it.getActivity());
                        break;
                    }
                }
            }
        }
        return list;
    }

    public int getTravelPercentage() {
        int totalDay = getTotalDayMinutesForBar();
        if (totalDay <= 0) return 0;
        int t = travelTimeMinutes != null ? travelTimeMinutes : 0;
        return (int) Math.round((t * 100.0) / totalDay);
    }

    public int getSightseeingPercentage() {
        int totalDay = getTotalDayMinutesForBar();
        if (totalDay <= 0) return 0;
        int s = sightseeingTimeMinutes != null ? sightseeingTimeMinutes : 0;
        return (int) Math.round((s * 100.0) / totalDay);
    }

    public int getBreakPercentage() {
        int totalDay = getTotalDayMinutesForBar();
        if (totalDay <= 0) return 0;
        int b = breakTimeMinutes != null ? breakTimeMinutes : 0;
        return (int) Math.round((b * 100.0) / totalDay);
    }

    public int getFreeTimePercentage() {
        int used = getTravelPercentage() + getSightseeingPercentage() + getBreakPercentage();
        return Math.max(0, 100 - used);
    }

    private int getTotalDayMinutesForBar() {
        int active = getTotalActiveMinutes();
        int free = unallocatedTimeMinutes != null ? Math.max(0, unallocatedTimeMinutes) : 0;
        int sum = active + free;
        return sum > 0 ? sum : 720;
    }
}
