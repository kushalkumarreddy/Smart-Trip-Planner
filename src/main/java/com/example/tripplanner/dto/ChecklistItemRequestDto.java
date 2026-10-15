package com.example.tripplanner.dto;

import jakarta.validation.constraints.NotBlank;

public class ChecklistItemRequestDto {

    private String category = "General";

    @NotBlank(message = "Title is required")
    private String title;

    private String notes;
    private Boolean essential = false;
    private Integer sortOrder = 0;

    public ChecklistItemRequestDto() {
    }

    public ChecklistItemRequestDto(String category, String title, String notes, Boolean essential) {
        this.category = category;
        this.title = title;
        this.notes = notes;
        this.essential = essential;
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

    public Boolean getEssential() {
        return essential != null ? essential : false;
    }

    public void setEssential(Boolean essential) {
        this.essential = essential;
    }

    public Integer getSortOrder() {
        return sortOrder != null ? sortOrder : 0;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
