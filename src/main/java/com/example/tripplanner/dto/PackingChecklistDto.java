package com.example.tripplanner.dto;

import java.util.ArrayList;
import java.util.List;

public class PackingChecklistDto {

    private Long tripId;
    private String tripName;
    private int totalDays;
    private List<ChecklistCategory> categories = new ArrayList<>();

    public static class ChecklistCategory {
        private String name;
        private String icon;
        private List<ChecklistItem> items = new ArrayList<>();

        public ChecklistCategory() {
        }

        public ChecklistCategory(String name, String icon, List<ChecklistItem> items) {
            this.name = name;
            this.icon = icon;
            this.items = items != null ? items : new ArrayList<>();
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public List<ChecklistItem> getItems() {
            return items;
        }

        public void setItems(List<ChecklistItem> items) {
            this.items = items;
        }
    }

    public static class ChecklistItem {
        private String name;
        private String reason;
        private boolean essential;

        public ChecklistItem() {
        }

        public ChecklistItem(String name, String reason, boolean essential) {
            this.name = name;
            this.reason = reason;
            this.essential = essential;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public boolean isEssential() {
            return essential;
        }

        public void setEssential(boolean essential) {
            this.essential = essential;
        }
    }

    public PackingChecklistDto() {
    }

    public PackingChecklistDto(Long tripId, String tripName, int totalDays) {
        this.tripId = tripId;
        this.tripName = tripName;
        this.totalDays = totalDays;
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

    public int getTotalDays() {
        return totalDays;
    }

    public void setTotalDays(int totalDays) {
        this.totalDays = totalDays;
    }

    public List<ChecklistCategory> getCategories() {
        return categories;
    }

    public void setCategories(List<ChecklistCategory> categories) {
        this.categories = categories;
    }
}
