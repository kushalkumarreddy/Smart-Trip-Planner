package com.example.tripplanner.dto;

import java.time.LocalDateTime;

public class ChecklistItemDto {

    private Long id;
    private Long tripId;
    private String category;
    private String title;
    private String notes;
    private Boolean completed;
    private Boolean essential;
    private Integer sortOrder;
    private LocalDateTime createdAt;

    public ChecklistItemDto() {
    }

    public ChecklistItemDto(Long id, Long tripId, String category, String title, String notes, Boolean completed, Boolean essential, Integer sortOrder) {
        this.id = id;
        this.tripId = tripId;
        this.category = category;
        this.title = title;
        this.notes = notes;
        this.completed = completed;
        this.essential = essential;
        this.sortOrder = sortOrder;
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
        this.completed = completed;
    }

    public Boolean getEssential() {
        return essential != null ? essential : false;
    }

    public void setEssential(Boolean essential) {
        this.essential = essential;
    }

    public Integer getSortOrder() {
        return sortOrder;
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
}
