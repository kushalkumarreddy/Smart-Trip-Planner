package com.example.tripplanner.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AiTripPromptDto {

    public static class Request {
        private String prompt;

        public Request() {
        }

        public Request(String prompt) {
            this.prompt = prompt;
        }

        public String getPrompt() {
            return prompt;
        }

        public void setPrompt(String prompt) {
            this.prompt = prompt;
        }
    }

    public static class DestinationRecommendation {
        private String name;
        private String suggestedHotel;
        private String reason;
        private List<String> topAttractions = new ArrayList<>();

        public DestinationRecommendation() {
        }

        public DestinationRecommendation(String name, String suggestedHotel, String reason, List<String> topAttractions) {
            this.name = name;
            this.suggestedHotel = suggestedHotel;
            this.reason = reason;
            this.topAttractions = topAttractions;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSuggestedHotel() {
            return suggestedHotel;
        }

        public void setSuggestedHotel(String suggestedHotel) {
            this.suggestedHotel = suggestedHotel;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public List<String> getTopAttractions() {
            return topAttractions;
        }

        public void setTopAttractions(List<String> topAttractions) {
            this.topAttractions = topAttractions;
        }
    }

    public static class Response {
        private String tripName;
        private String startLocation;
        private List<String> destinationNames = new ArrayList<>();
        private List<String> destinationHotels = new ArrayList<>();
        private List<DestinationRecommendation> destinationDetails = new ArrayList<>();
        private Integer totalDays = 3;
        private LocalDate startDate;
        private LocalDate endDate;
        private String transportMode = "CAR";
        private String travelStyle = "BALANCED";
        private Integer travelersCount = 2;
        private String budgetRange = "Moderate";
        private List<String> preferredInterests = new ArrayList<>();
        private String aiSummary;

        public Response() {
        }

        public void setPromptDefaults() {
            this.tripName = "Scenic Weekend Getaway";
            this.startLocation = "Bengaluru";
            this.destinationNames = List.of("Coorg");
            this.destinationHotels = List.of("The Tamara Coorg or Misty Woods Plantation Resort");
            this.totalDays = 3;
            this.startDate = LocalDate.now().plusDays(1);
            this.endDate = this.startDate.plusDays(2);
            this.transportMode = "CAR";
            this.travelStyle = "BALANCED";
            this.travelersCount = 2;
            this.budgetRange = "Moderate";
            this.preferredInterests = List.of("Nature", "Waterfalls");
            this.aiSummary = "Curated a balanced 3-day getaway from Bengaluru to Coorg featuring scenic hills, waterfalls, and coffee estate stays.";
        }

        public String getTripName() {
            return tripName;
        }

        public void setTripName(String tripName) {
            this.tripName = tripName;
        }

        public String getStartLocation() {
            return startLocation;
        }

        public void setStartLocation(String startLocation) {
            this.startLocation = startLocation;
        }

        public List<String> getDestinationNames() {
            return destinationNames;
        }

        public void setDestinationNames(List<String> destinationNames) {
            this.destinationNames = destinationNames;
        }

        public List<String> getDestinationHotels() {
            return destinationHotels;
        }

        public void setDestinationHotels(List<String> destinationHotels) {
            this.destinationHotels = destinationHotels;
        }

        public List<DestinationRecommendation> getDestinationDetails() {
            return destinationDetails;
        }

        public void setDestinationDetails(List<DestinationRecommendation> destinationDetails) {
            this.destinationDetails = destinationDetails;
        }

        public Integer getTotalDays() {
            return totalDays;
        }

        public void setTotalDays(Integer totalDays) {
            this.totalDays = totalDays;
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public void setStartDate(LocalDate startDate) {
            this.startDate = startDate;
        }

        public LocalDate getEndDate() {
            return endDate;
        }

        public void setEndDate(LocalDate endDate) {
            this.endDate = endDate;
        }

        public String getTransportMode() {
            return transportMode;
        }

        public void setTransportMode(String transportMode) {
            this.transportMode = transportMode;
        }

        public String getTravelStyle() {
            return travelStyle;
        }

        public void setTravelStyle(String travelStyle) {
            this.travelStyle = travelStyle;
        }

        public Integer getTravelersCount() {
            return travelersCount;
        }

        public void setTravelersCount(Integer travelersCount) {
            this.travelersCount = travelersCount;
        }

        public String getBudgetRange() {
            return budgetRange;
        }

        public void setBudgetRange(String budgetRange) {
            this.budgetRange = budgetRange;
        }

        public List<String> getPreferredInterests() {
            return preferredInterests;
        }

        public void setPreferredInterests(List<String> preferredInterests) {
            this.preferredInterests = preferredInterests;
        }

        public String getAiSummary() {
            return aiSummary;
        }

        public void setAiSummary(String aiSummary) {
            this.aiSummary = aiSummary;
        }
    }
}
