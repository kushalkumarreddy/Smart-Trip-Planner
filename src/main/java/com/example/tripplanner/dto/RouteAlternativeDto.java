package com.example.tripplanner.dto;

import java.util.ArrayList;
import java.util.List;

public class RouteAlternativeDto {

    private String strategy; // SHORTEST_DISTANCE, SHORTEST_TIME, SCENIC_HERITAGE, BALANCED_RELAXED
    private String title;
    private String description;
    private List<String> orderedLocations = new ArrayList<>();
    private Double totalDistanceKm = 0.0;
    private Integer totalTravelTimeMinutes = 0;
    private String formattedTravelTime;
    private Double savingsKm = 0.0;
    private Integer savingsMinutes = 0;
    private Boolean recommended = false;
    private List<String> highlights = new ArrayList<>();

    public RouteAlternativeDto() {
    }

    public RouteAlternativeDto(String strategy, String title, String description,
                               List<String> orderedLocations, Double totalDistanceKm,
                               Integer totalTravelTimeMinutes, String formattedTravelTime,
                               Double savingsKm, Integer savingsMinutes, Boolean recommended,
                               List<String> highlights) {
        this.strategy = strategy;
        this.title = title;
        this.description = description;
        this.orderedLocations = orderedLocations != null ? orderedLocations : new ArrayList<>();
        this.totalDistanceKm = totalDistanceKm;
        this.totalTravelTimeMinutes = totalTravelTimeMinutes;
        this.formattedTravelTime = formattedTravelTime;
        this.savingsKm = savingsKm;
        this.savingsMinutes = savingsMinutes;
        this.recommended = recommended;
        this.highlights = highlights != null ? highlights : new ArrayList<>();
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getOrderedLocations() {
        return orderedLocations;
    }

    public void setOrderedLocations(List<String> orderedLocations) {
        this.orderedLocations = orderedLocations;
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

    public String getFormattedTravelTime() {
        return formattedTravelTime;
    }

    public void setFormattedTravelTime(String formattedTravelTime) {
        this.formattedTravelTime = formattedTravelTime;
    }

    public Double getSavingsKm() {
        return savingsKm;
    }

    public void setSavingsKm(Double savingsKm) {
        this.savingsKm = savingsKm;
    }

    public Integer getSavingsMinutes() {
        return savingsMinutes;
    }

    public void setSavingsMinutes(Integer savingsMinutes) {
        this.savingsMinutes = savingsMinutes;
    }

    public Boolean getRecommended() {
        return recommended;
    }

    public void setRecommended(Boolean recommended) {
        this.recommended = recommended;
    }

    public List<String> getHighlights() {
        return highlights;
    }

    public void setHighlights(List<String> highlights) {
        this.highlights = highlights;
    }
}
