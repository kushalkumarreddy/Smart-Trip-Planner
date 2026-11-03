package com.example.tripplanner.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Data transfer object encapsulating accurate round-trip metrics and highway return route details.
 * Connects the final itinerary destination back to the journey origin.
 */
public class RoundTripDetailsDto {

    private boolean success = true;
    private String errorMessage;

    private String startLocation;
    private String finalDestination;

    // Outbound journey
    private Double outboundDistanceKm = 0.0;
    private Integer outboundDurationMinutes = 0;
    private String formattedOutboundDuration = "0 hr 0 min";

    // Return journey
    private Double returnDistanceKm = 0.0;
    private Integer returnDurationMinutes = 0;
    private String formattedReturnDuration = "0 hr 0 min";

    // Combined round-trip totals
    private Double totalRoundTripDistanceKm = 0.0;
    private Integer totalRoundTripDurationMinutes = 0;
    private String formattedTotalRoundTripDuration = "0 hr 0 min";

    // Provenance & routing provider
    private String routingProvider = "OSRM Highway Routing Engine";
    private String transportMode = "CAR";

    // Road route polyline coordinates for map visualization: [[lat, lng], [lat, lng], ...]
    private List<double[]> returnRouteGeometry = new ArrayList<>();

    // External navigation link for the return leg
    private String googleMapsReturnUrl;

    // Dedicated Return Journey Architecture (Requirement 3)
    private String returnStartDate;
    private String returnDepartureTime = "08:30 AM";
    private String expectedArrivalTime;
    private List<String> returnIntermediateStops = new ArrayList<>();
    private List<ItineraryItemDto> returnItems = new ArrayList<>();
    private List<DayItineraryDto> returnDayItineraries = new ArrayList<>();
    private Double tollCostEstimate = 0.0;
    private Double fuelCostEstimate = 0.0;
    private Double fuelLitersEstimate = 0.0;
    private Boolean hasOvernightStay = false;
    private String overnightStayLocation;

    public RoundTripDetailsDto() {
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getStartLocation() {
        return startLocation;
    }

    public void setStartLocation(String startLocation) {
        this.startLocation = startLocation;
    }

    public String getFinalDestination() {
        return finalDestination;
    }

    public void setFinalDestination(String finalDestination) {
        this.finalDestination = finalDestination;
    }

    public Double getOutboundDistanceKm() {
        return outboundDistanceKm;
    }

    public void setOutboundDistanceKm(Double outboundDistanceKm) {
        this.outboundDistanceKm = outboundDistanceKm;
    }

    public Integer getOutboundDurationMinutes() {
        return outboundDurationMinutes;
    }

    public void setOutboundDurationMinutes(Integer outboundDurationMinutes) {
        this.outboundDurationMinutes = outboundDurationMinutes;
    }

    public String getFormattedOutboundDuration() {
        return formattedOutboundDuration;
    }

    public void setFormattedOutboundDuration(String formattedOutboundDuration) {
        this.formattedOutboundDuration = formattedOutboundDuration;
    }

    public Double getReturnDistanceKm() {
        return returnDistanceKm;
    }

    public void setReturnDistanceKm(Double returnDistanceKm) {
        this.returnDistanceKm = returnDistanceKm;
    }

    public Integer getReturnDurationMinutes() {
        return returnDurationMinutes;
    }

    public void setReturnDurationMinutes(Integer returnDurationMinutes) {
        this.returnDurationMinutes = returnDurationMinutes;
    }

    public String getFormattedReturnDuration() {
        return formattedReturnDuration;
    }

    public void setFormattedReturnDuration(String formattedReturnDuration) {
        this.formattedReturnDuration = formattedReturnDuration;
    }

    public Double getTotalRoundTripDistanceKm() {
        return totalRoundTripDistanceKm;
    }

    public void setTotalRoundTripDistanceKm(Double totalRoundTripDistanceKm) {
        this.totalRoundTripDistanceKm = totalRoundTripDistanceKm;
    }

    public Integer getTotalRoundTripDurationMinutes() {
        return totalRoundTripDurationMinutes;
    }

    public void setTotalRoundTripDurationMinutes(Integer totalRoundTripDurationMinutes) {
        this.totalRoundTripDurationMinutes = totalRoundTripDurationMinutes;
    }

    public String getFormattedTotalRoundTripDuration() {
        return formattedTotalRoundTripDuration;
    }

    public void setFormattedTotalRoundTripDuration(String formattedTotalRoundTripDuration) {
        this.formattedTotalRoundTripDuration = formattedTotalRoundTripDuration;
    }

    public String getRoutingProvider() {
        return routingProvider;
    }

    public void setRoutingProvider(String routingProvider) {
        this.routingProvider = routingProvider;
    }

    public String getTransportMode() {
        return transportMode;
    }

    public void setTransportMode(String transportMode) {
        this.transportMode = transportMode;
    }

    public List<double[]> getReturnRouteGeometry() {
        return returnRouteGeometry;
    }

    public void setReturnRouteGeometry(List<double[]> returnRouteGeometry) {
        this.returnRouteGeometry = returnRouteGeometry;
    }

    public String getGoogleMapsReturnUrl() {
        return googleMapsReturnUrl;
    }

    public void setGoogleMapsReturnUrl(String googleMapsReturnUrl) {
        this.googleMapsReturnUrl = googleMapsReturnUrl;
    }

    // Convenience aliases
    public Double getRoundTripTotalDistanceKm() {
        return totalRoundTripDistanceKm;
    }

    public Integer getRoundTripTotalDurationMinutes() {
        return totalRoundTripDurationMinutes;
    }

    public String getReturnOrigin() {
        return finalDestination;
    }

    public String getReturnDestination() {
        return startLocation;
    }

    public String getReturnStartDate() {
        return returnStartDate;
    }

    public void setReturnStartDate(String returnStartDate) {
        this.returnStartDate = returnStartDate;
    }

    public String getReturnDepartureTime() {
        return returnDepartureTime;
    }

    public void setReturnDepartureTime(String returnDepartureTime) {
        this.returnDepartureTime = returnDepartureTime;
    }

    public String getExpectedArrivalTime() {
        return expectedArrivalTime;
    }

    public void setExpectedArrivalTime(String expectedArrivalTime) {
        this.expectedArrivalTime = expectedArrivalTime;
    }

    public List<String> getReturnIntermediateStops() {
        if (returnIntermediateStops == null) returnIntermediateStops = new ArrayList<>();
        return returnIntermediateStops;
    }

    public void setReturnIntermediateStops(List<String> returnIntermediateStops) {
        this.returnIntermediateStops = returnIntermediateStops;
    }

    public List<ItineraryItemDto> getReturnItems() {
        if (returnItems == null) returnItems = new ArrayList<>();
        return returnItems;
    }

    public void setReturnItems(List<ItineraryItemDto> returnItems) {
        this.returnItems = returnItems;
    }

    public List<DayItineraryDto> getReturnDayItineraries() {
        if (returnDayItineraries == null) returnDayItineraries = new ArrayList<>();
        return returnDayItineraries;
    }

    public void setReturnDayItineraries(List<DayItineraryDto> returnDayItineraries) {
        this.returnDayItineraries = returnDayItineraries;
    }

    public Double getTollCostEstimate() {
        return tollCostEstimate;
    }

    public void setTollCostEstimate(Double tollCostEstimate) {
        this.tollCostEstimate = tollCostEstimate;
    }

    public Double getFuelCostEstimate() {
        return fuelCostEstimate;
    }

    public void setFuelCostEstimate(Double fuelCostEstimate) {
        this.fuelCostEstimate = fuelCostEstimate;
    }

    public Double getFuelLitersEstimate() {
        return fuelLitersEstimate;
    }

    public void setFuelLitersEstimate(Double fuelLitersEstimate) {
        this.fuelLitersEstimate = fuelLitersEstimate;
    }

    public Boolean getHasOvernightStay() {
        return hasOvernightStay;
    }

    public void setHasOvernightStay(Boolean hasOvernightStay) {
        this.hasOvernightStay = hasOvernightStay;
    }

    public String getOvernightStayLocation() {
        return overnightStayLocation;
    }

    public void setOvernightStayLocation(String overnightStayLocation) {
        this.overnightStayLocation = overnightStayLocation;
    }
}

