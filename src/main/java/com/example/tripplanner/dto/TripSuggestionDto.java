package com.example.tripplanner.dto;

import java.time.LocalDateTime;

public class TripSuggestionDto {

    private Long id;
    private String placeName;
    private String category;
    private String destinationName;
    private String notes;
    private String suggestedByName;
    private Integer votesCount;
    private String voterNames;
    private String status;
    private LocalDateTime createdAt;

    public TripSuggestionDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPlaceName() {
        return placeName;
    }

    public void setPlaceName(String placeName) {
        this.placeName = placeName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getSuggestedByName() {
        return suggestedByName;
    }

    public void setSuggestedByName(String suggestedByName) {
        this.suggestedByName = suggestedByName;
    }

    public Integer getVotesCount() {
        return votesCount;
    }

    public void setVotesCount(Integer votesCount) {
        this.votesCount = votesCount;
    }

    public String getVoterNames() {
        return voterNames;
    }

    public void setVoterNames(String voterNames) {
        this.voterNames = voterNames;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
