package com.example.tripplanner.dto;

import java.io.Serializable;

public class TimelineTransitionDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String icon; // bi-car-front, bi-person-walking, bi-bus-front, bi-cup-hot, bi-sun, bi-clock, bi-building, bi-exclamation-triangle
    private String text; // e.g. "🚗 Travel to next stop · 25 min · 12 km"
    private String type; // TRAVEL, BUFFER, FREE_TIME, CONFLICT, HOTEL_TRANSFER, BREAK, WALK, TRANSIT, BIKE
    private Double distanceKm;
    private Integer durationMinutes;
    private String fromLocation;
    private String toLocation;

    public TimelineTransitionDto() {
    }

    public TimelineTransitionDto(String icon, String text, String type, Double distanceKm, Integer durationMinutes) {
        this.icon = icon;
        this.text = text;
        this.type = type;
        this.distanceKm = distanceKm;
        this.durationMinutes = durationMinutes;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getFromLocation() {
        return fromLocation;
    }

    public void setFromLocation(String fromLocation) {
        this.fromLocation = fromLocation;
    }

    public String getToLocation() {
        return toLocation;
    }

    public void setToLocation(String toLocation) {
        this.toLocation = toLocation;
    }
}

