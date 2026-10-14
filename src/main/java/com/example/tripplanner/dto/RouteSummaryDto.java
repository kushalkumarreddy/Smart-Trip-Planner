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
    private Double recommendedDistanceKm = 0.0;
    private Integer recommendedTravelTimeMinutes = 0;
    private String optimizationMessage;

    // Viable alternative routes comparing metrics
    private List<RouteAlternativeDto> alternatives = new ArrayList<>();

    // Calculation Status & Error Handling
    private Boolean routeCalculationSuccess = true;
    private String calculationErrorMessage;

    // Full trip navigation link in Google Maps
    private String googleMapsFullRouteUrl;

    public RouteSummaryDto() {
    }

    public List<RouteAlternativeDto> getAlternatives() {
        return alternatives;
    }

    public void setAlternatives(List<RouteAlternativeDto> alternatives) {
        this.alternatives = alternatives;
    }

    public Boolean getRouteCalculationSuccess() {
        return routeCalculationSuccess;
    }

    public void setRouteCalculationSuccess(Boolean routeCalculationSuccess) {
        this.routeCalculationSuccess = routeCalculationSuccess;
    }

    public String getCalculationErrorMessage() {
        return calculationErrorMessage;
    }

    public void setCalculationErrorMessage(String calculationErrorMessage) {
        this.calculationErrorMessage = calculationErrorMessage;
    }

    public Double getRecommendedDistanceKm() {
        return recommendedDistanceKm;
    }

    public void setRecommendedDistanceKm(Double recommendedDistanceKm) {
        this.recommendedDistanceKm = recommendedDistanceKm;
    }

    public Integer getRecommendedTravelTimeMinutes() {
        return recommendedTravelTimeMinutes;
    }

    public void setRecommendedTravelTimeMinutes(Integer recommendedTravelTimeMinutes) {
        this.recommendedTravelTimeMinutes = recommendedTravelTimeMinutes;
    }

    public String getOptimizationMessage() {
        return optimizationMessage;
    }

    public void setOptimizationMessage(String optimizationMessage) {
        this.optimizationMessage = optimizationMessage;
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

    private List<double[]> fullRouteGeometry = new ArrayList<>();

    public String getGoogleMapsFullRouteUrl() {
        return googleMapsFullRouteUrl;
    }

    public void setGoogleMapsFullRouteUrl(String googleMapsFullRouteUrl) {
        this.googleMapsFullRouteUrl = googleMapsFullRouteUrl;
    }

    public List<double[]> getFullRouteGeometry() {
        return fullRouteGeometry;
    }

    public void setFullRouteGeometry(List<double[]> fullRouteGeometry) {
        this.fullRouteGeometry = fullRouteGeometry;
    }
}
