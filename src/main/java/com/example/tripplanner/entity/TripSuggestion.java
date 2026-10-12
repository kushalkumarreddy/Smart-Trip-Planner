package com.example.tripplanner.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "trip_suggestions")
public class TripSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "place_name", nullable = false, length = 200)
    private String placeName;

    @Column(length = 100)
    private String category = "Sightseeing";

    @Column(name = "destination_name", length = 200)
    private String destinationName;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "suggested_by_name", length = 100)
    private String suggestedByName = "Trip Member";

    @Column(name = "votes_count")
    private Integer votesCount = 1;

    @Column(name = "voter_names", columnDefinition = "TEXT")
    private String voterNames = "";

    @Column(length = 50)
    private String status = "PENDING"; // PENDING, ACCEPTED, REJECTED

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public TripSuggestion() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.votesCount == null) {
            this.votesCount = 1;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Trip getTrip() {
        return trip;
    }

    public void setTrip(Trip trip) {
        this.trip = trip;
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
