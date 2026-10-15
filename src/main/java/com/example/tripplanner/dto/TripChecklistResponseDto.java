package com.example.tripplanner.dto;

import java.util.ArrayList;
import java.util.List;

public class TripChecklistResponseDto {

    private Long tripId;
    private String tripName;
    private int totalCount = 0;
    private int completedCount = 0;
    private int remainingCount = 0;
    private int completionPercentage = 0;
    private String completionSummary; // e.g. "8 of 12 tasks completed"
    private List<CategoryGroup> categories = new ArrayList<>();

    public static class CategoryGroup {
        private String category;
        private String icon;
        private int totalCount = 0;
        private int completedCount = 0;
        private List<ChecklistItemDto> items = new ArrayList<>();

        public CategoryGroup() {
        }

        public CategoryGroup(String category, String icon) {
            this.category = category;
            this.icon = icon;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public void setTotalCount(int totalCount) {
            this.totalCount = totalCount;
        }

        public int getCompletedCount() {
            return completedCount;
        }

        public void setCompletedCount(int completedCount) {
            this.completedCount = completedCount;
        }

        public List<ChecklistItemDto> getItems() {
            return items;
        }

        public void setItems(List<ChecklistItemDto> items) {
            this.items = items;
        }
    }

    public TripChecklistResponseDto() {
    }

    public TripChecklistResponseDto(Long tripId, String tripName) {
        this.tripId = tripId;
        this.tripName = tripName;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public String getTripName() {
        return tripName;
    }

    public void setTripName(String tripName) {
        this.tripName = tripName;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getCompletedCount() {
        return completedCount;
    }

    public void setCompletedCount(int completedCount) {
        this.completedCount = completedCount;
    }

    public int getRemainingCount() {
        return remainingCount;
    }

    public void setRemainingCount(int remainingCount) {
        this.remainingCount = remainingCount;
    }

    public int getCompletionPercentage() {
        return completionPercentage;
    }

    public void setCompletionPercentage(int completionPercentage) {
        this.completionPercentage = completionPercentage;
    }

    public String getCompletionSummary() {
        return completionSummary;
    }

    public void setCompletionSummary(String completionSummary) {
        this.completionSummary = completionSummary;
    }

    public List<CategoryGroup> getCategories() {
        return categories;
    }

    public void setCategories(List<CategoryGroup> categories) {
        this.categories = categories;
    }
}
