package com.example.tripplanner.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "trip_checklists")
public class TripChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(nullable = false, length = 100)
    private String category = "General";

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "completed", nullable = false)
    private Boolean completed = false;

    @Column(name = "essential", nullable = false)
    private Boolean essential = false;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public TripChecklistItem() {
    }

    public TripChecklistItem(Trip trip, String category, String title, String notes, boolean essential, int sortOrder) {
        this.trip = trip;
        this.category = category;
        this.title = title;
        this.notes = notes;
        this.essential = essential;
        this.sortOrder = sortOrder;
        this.completed = false;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Boolean getCompleted() {
        return completed != null ? completed : false;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed != null ? completed : false;
    }

    public Boolean getEssential() {
        return essential != null ? essential : false;
    }

    public void setEssential(Boolean essential) {
        this.essential = essential != null ? essential : false;
    }

    public Integer getSortOrder() {
        return sortOrder != null ? sortOrder : 0;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
