package com.example.tripplanner.dto;

public class PlaceAlongRouteDto {

    private String name;
    private String category;
    private Double rating;
    private Integer reviewCount;
    private String description;
    private Double latitude;
    private Double longitude;
    private String segmentName;       // e.g. "Bengaluru → Mysore"
    private String fromLocation;      // e.g. "Bengaluru"
    private String toLocation;        // e.g. "Mysore"
    private Double detourDistanceKm;  // Extra km required
    private Integer detourDurationMinutes; // Extra minutes required
    private String formattedDetour;   // e.g. "+8.5 km, +15 min detour"
    private Integer insertIndex;      // Index where it can be inserted into the trip destination order

    public PlaceAlongRouteDto() {
    }

    public PlaceAlongRouteDto(String name, String category, Double rating, Integer reviewCount,
                              String description, Double latitude, Double longitude,
                              String segmentName, String fromLocation, String toLocation,
                              Double detourDistanceKm, Integer detourDurationMinutes,
                              String formattedDetour, Integer insertIndex) {
        this.name = name;
        this.category = category;
        this.rating = rating;
        this.reviewCount = reviewCount;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
        this.segmentName = segmentName;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
        this.detourDistanceKm = detourDistanceKm;
        this.detourDurationMinutes = detourDurationMinutes;
        this.formattedDetour = formattedDetour;
        this.insertIndex = insertIndex;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public String getSegmentName() {
        return segmentName;
    }

    public void setSegmentName(String segmentName) {
        this.segmentName = segmentName;
    }

    public String getFromLocation() {
        return fromLocation;
    }

    public void setFromLocation(String fromLocation) {
        this.fromLocation = fromLocation;
    }

    public String getToLocation() {
        return toLocation;
    }

    public void setToLocation(String toLocation) {
        this.toLocation = toLocation;
    }

    public Double getDetourDistanceKm() {
        return detourDistanceKm;
    }

    public void setDetourDistanceKm(Double detourDistanceKm) {
        this.detourDistanceKm = detourDistanceKm;
    }

    public Integer getDetourDurationMinutes() {
        return detourDurationMinutes;
    }

    public void setDetourDurationMinutes(Integer detourDurationMinutes) {
        this.detourDurationMinutes = detourDurationMinutes;
    }

    public String getFormattedDetour() {
        return formattedDetour;
    }

    public void setFormattedDetour(String formattedDetour) {
        this.formattedDetour = formattedDetour;
    }

    public Integer getInsertIndex() {
        return insertIndex;
    }

    public void setInsertIndex(Integer insertIndex) {
        this.insertIndex = insertIndex;
    }
}
