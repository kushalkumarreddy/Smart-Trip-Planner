package com.example.tripplanner.dto;

import java.util.ArrayList;
import java.util.List;

public class DestinationDto {

    private Long id;
    private Long tripId;
    private String name;
    private Double latitude;
    private Double longitude;
    private Integer sequenceOrder;
    private Double distanceFromPrevious;
    private Integer travelTimeFromPrevious;
    private Boolean visited;
    private String notes;
    private String hotelName;
    private List<SightseeingPlaceDto> places = new ArrayList<>();

    public DestinationDto() {
    }

    public DestinationDto(Long id, String name, Integer sequenceOrder) {
        this.id = id;
        this.name = name;
        this.sequenceOrder = sequenceOrder;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Integer getSequenceOrder() {
        return sequenceOrder;
    }

    public void setSequenceOrder(Integer sequenceOrder) {
        this.sequenceOrder = sequenceOrder;
    }

    public Double getDistanceFromPrevious() {
        return distanceFromPrevious;
    }

    public void setDistanceFromPrevious(Double distanceFromPrevious) {
        this.distanceFromPrevious = distanceFromPrevious;
    }

    public Integer getTravelTimeFromPrevious() {
        return travelTimeFromPrevious;
    }

    public void setTravelTimeFromPrevious(Integer travelTimeFromPrevious) {
        this.travelTimeFromPrevious = travelTimeFromPrevious;
    }

    public Boolean getVisited() {
        return visited;
    }

    public void setVisited(Boolean visited) {
        this.visited = visited;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getHotelName() {
        return hotelName;
    }

    public void setHotelName(String hotelName) {
        this.hotelName = hotelName;
    }

    public List<SightseeingPlaceDto> getPlaces() {
        return places;
    }

    public void setPlaces(List<SightseeingPlaceDto> places) {
        this.places = places;
    }
}
