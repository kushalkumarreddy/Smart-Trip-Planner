package com.example.tripplanner.dto;

import java.time.LocalDateTime;

public class TripVersionSummaryDto {

    private Long id;
    private Long tripId;
    private Integer versionNumber;
    private String changeDescription;
    private String changeSource;
    private String createdByName;
    private String createdByEmail;
    private LocalDateTime createdAt;
    private String formattedCreatedAt;
    private Integer destinationsCount = 0;
    private String destinationsSummary;
    private Double totalDistanceKm = 0.0;
    private Integer totalTravelTimeMinutes = 0;
    private String formattedTravelTime;
    private Boolean isCurrent = false;

    public TripVersionSummaryDto() {
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

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getChangeDescription() {
        return changeDescription;
    }

    public void setChangeDescription(String changeDescription) {
        this.changeDescription = changeDescription;
    }

    public String getChangeSource() {
        return changeSource;
    }

    public void setChangeSource(String changeSource) {
        this.changeSource = changeSource;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public String getCreatedByEmail() {
        return createdByEmail;
    }

    public void setCreatedByEmail(String createdByEmail) {
        this.createdByEmail = createdByEmail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getFormattedCreatedAt() {
        return formattedCreatedAt;
    }

    public void setFormattedCreatedAt(String formattedCreatedAt) {
        this.formattedCreatedAt = formattedCreatedAt;
    }

    public Integer getDestinationsCount() {
        return destinationsCount;
    }

    public void setDestinationsCount(Integer destinationsCount) {
        this.destinationsCount = destinationsCount;
    }

    public String getDestinationsSummary() {
        return destinationsSummary;
    }

    public void setDestinationsSummary(String destinationsSummary) {
        this.destinationsSummary = destinationsSummary;
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

    public Boolean getIsCurrent() {
        return isCurrent;
    }

    public void setIsCurrent(Boolean isCurrent) {
        this.isCurrent = isCurrent;
    }
}
