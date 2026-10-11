package com.example.tripplanner.dto;

import java.util.ArrayList;
import java.util.List;

public class RouteSummaryDto {

    private String transportMode;
    private Double totalDistanceKm = 0.0;
    private Integer totalTravelTimeMinutes = 0;
    private Integer totalDestinations = 0;
    private List<RouteSegmentDto> segments = new ArrayList<>();

    // Smart Route Optimization Fields
    private List<String> originalOrder = new ArrayList<>();
    private List<String> recommendedOrder = new ArrayList<>();
    private Boolean optimizationRecommended = false;
    private Double potentialSavingsKm = 0.0;
    private Integer potentialSavingsMinutes = 0;

    // Full trip navigation link in Google Maps
    private String googleMapsFullRouteUrl;

    public RouteSummaryDto() {
    }

    public String getTransportMode() {
        return transportMode;
    }

    public void setTransportMode(String transportMode) {
        this.transportMode = transportMode;
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

    public Integer getTotalDestinations() {
        return totalDestinations;
    }

    public void setTotalDestinations(Integer totalDestinations) {
        this.totalDestinations = totalDestinations;
    }

    public List<RouteSegmentDto> getSegments() {
        return segments;
    }

    public void setSegments(List<RouteSegmentDto> segments) {
        this.segments = segments;
    }

    public List<String> getOriginalOrder() {
        return originalOrder;
    }

    public void setOriginalOrder(List<String> originalOrder) {
        this.originalOrder = originalOrder;
    }

    public List<String> getRecommendedOrder() {
        return recommendedOrder;
    }

    public void setRecommendedOrder(List<String> recommendedOrder) {
        this.recommendedOrder = recommendedOrder;
    }

    public Boolean getOptimizationRecommended() {
        return optimizationRecommended;
    }

    public void setOptimizationRecommended(Boolean optimizationRecommended) {
        this.optimizationRecommended = optimizationRecommended;
    }

    public Double getPotentialSavingsKm() {
        return potentialSavingsKm;
    }

    public void setPotentialSavingsKm(Double potentialSavingsKm) {
        this.potentialSavingsKm = potentialSavingsKm;
    }

    public Integer getPotentialSavingsMinutes() {
        return potentialSavingsMinutes;
    }

    public void setPotentialSavingsMinutes(Integer potentialSavingsMinutes) {
        this.potentialSavingsMinutes = potentialSavingsMinutes;
    }

    public String getGoogleMapsFullRouteUrl() {
        return googleMapsFullRouteUrl;
    }

    public void setGoogleMapsFullRouteUrl(String googleMapsFullRouteUrl) {
        this.googleMapsFullRouteUrl = googleMapsFullRouteUrl;
    }
}
