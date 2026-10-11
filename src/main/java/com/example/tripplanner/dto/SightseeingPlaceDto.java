package com.example.tripplanner.dto;

public class SightseeingPlaceDto {

    private Long id;
    private Long destinationId;
    private String destinationName;
    private String name;
    private String category;
    private Double rating;
    private Integer reviewCount;
    private Double latitude;
    private Double longitude;
    private Double distanceKm;
    private Integer estimatedVisitDurationMinutes;
    private Integer estimatedTravelTimeMinutes;
    private String description;
    private String openingHours;
    private String photoUrl;
    private String googlePlaceId;
    private String priority; // High, Medium, Low
    private Boolean visited;
    private Boolean addedToItinerary;
    private Double matchScore;

    public SightseeingPlaceDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDestinationId() {
        return destinationId;
    }

    public void setDestinationId(Long destinationId) {
        this.destinationId = destinationId;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public Integer getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(Integer reviewCount) {
        this.reviewCount = reviewCount;
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

    public Double getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(Double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public Integer getEstimatedVisitDurationMinutes() {
        return estimatedVisitDurationMinutes;
    }

    public void setEstimatedVisitDurationMinutes(Integer estimatedVisitDurationMinutes) {
        this.estimatedVisitDurationMinutes = estimatedVisitDurationMinutes;
    }

    public Integer getEstimatedTravelTimeMinutes() {
        return estimatedTravelTimeMinutes;
    }

    public void setEstimatedTravelTimeMinutes(Integer estimatedTravelTimeMinutes) {
        this.estimatedTravelTimeMinutes = estimatedTravelTimeMinutes;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOpeningHours() {
        return openingHours;
    }

    public void setOpeningHours(String openingHours) {
        this.openingHours = openingHours;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getGooglePlaceId() {
        return googlePlaceId;
    }

    public void setGooglePlaceId(String googlePlaceId) {
        this.googlePlaceId = googlePlaceId;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public Boolean getVisited() {
        return visited;
    }

    public void setVisited(Boolean visited) {
        this.visited = visited;
    }

    public Boolean getAddedToItinerary() {
        return addedToItinerary;
    }

    public void setAddedToItinerary(Boolean addedToItinerary) {
        this.addedToItinerary = addedToItinerary;
    }

    public Double getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(Double matchScore) {
        this.matchScore = matchScore;
    }
}
