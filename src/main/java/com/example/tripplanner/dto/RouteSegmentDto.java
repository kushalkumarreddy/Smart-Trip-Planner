package com.example.tripplanner.dto;

public class RouteSegmentDto {

    private String fromLocation;
    private String toLocation;
    private Double fromLat;
    private Double fromLng;
    private Double toLat;
    private Double toLng;
    private Double distanceKm;
    private Integer durationMinutes;
    private String transportMode;
    private String summaryText;
    private String googleMapsSegmentUrl;

    public RouteSegmentDto() {
    }

    public RouteSegmentDto(String fromLocation, String toLocation, Double distanceKm, Integer durationMinutes, String transportMode) {
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
        this.distanceKm = distanceKm;
        this.durationMinutes = durationMinutes;
        this.transportMode = transportMode;
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

    public Double getFromLat() {
        return fromLat;
    }

    public void setFromLat(Double fromLat) {
        this.fromLat = fromLat;
    }

    public Double getFromLng() {
        return fromLng;
    }

    public void setFromLng(Double fromLng) {
        this.fromLng = fromLng;
    }

    public Double getToLat() {
        return toLat;
    }

    public void setToLat(Double toLat) {
        this.toLat = toLat;
    }

    public Double getToLng() {
        return toLng;
    }

    public void setToLng(Double toLng) {
        this.toLng = toLng;
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

    public String getTransportMode() {
        return transportMode;
    }

    public void setTransportMode(String transportMode) {
        this.transportMode = transportMode;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    private java.util.List<double[]> geometryCoordinates = new java.util.ArrayList<>();

    public String getGoogleMapsSegmentUrl() {
        return googleMapsSegmentUrl;
    }

    public void setGoogleMapsSegmentUrl(String googleMapsSegmentUrl) {
        this.googleMapsSegmentUrl = googleMapsSegmentUrl;
    }

    public java.util.List<double[]> getGeometryCoordinates() {
        return geometryCoordinates;
    }

    public void setGeometryCoordinates(java.util.List<double[]> geometryCoordinates) {
        this.geometryCoordinates = geometryCoordinates;
    }
}
