package com.example.tripplanner.dto;

public class PlaceAlongRouteDto {

    private String id;
    private String name;
    private String category;
    private String categoryCode; // FUEL, RESTAURANT, REST_STOP, EV_CHARGING, VIEWPOINT, ATTRACTION, PARKING, HOTEL
    private Double rating;
    private Integer reviewCount;
    private String description;
    private String address;
    private Double latitude;
    private Double longitude;
    private String segmentName;       // e.g. "Bengaluru → Mysore"
    private String fromLocation;      // e.g. "Bengaluru"
    private String toLocation;        // e.g. "Mysore"
    private Double distanceFromRouteKm; // Actual perpendicular distance from highway corridor
    private Double detourDistanceKm;  // Extra road km required
    private Integer detourDurationMinutes; // Extra road minutes required
    private String formattedDetour;   // e.g. "+3.5 km, +6 min detour"
    private Boolean detourCalculated = true; // True if real routing calculated detour; false if unavailable
    private String detourUnavailableReason;
    private String openingHours;
    private Boolean openNow;
    private String providerSource = "VERIFIED_HIGHWAY_CATALOG"; // GOOGLE_PLACES, OPENSTREETMAP, VERIFIED_HIGHWAY_CATALOG
    private Integer insertIndex;      // Index where it can be inserted into the trip destination order
    private String insertAfterDestination;
    private String insertBeforeDestination;

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
        this.detourCalculated = (detourDistanceKm != null && detourDurationMinutes != null);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
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

    public Double getDistanceFromRouteKm() {
        return distanceFromRouteKm;
    }

    public void setDistanceFromRouteKm(Double distanceFromRouteKm) {
        this.distanceFromRouteKm = distanceFromRouteKm;
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

    public Boolean getDetourCalculated() {
        return detourCalculated;
    }

    public void setDetourCalculated(Boolean detourCalculated) {
        this.detourCalculated = detourCalculated;
    }

    public String getDetourUnavailableReason() {
        return detourUnavailableReason;
    }

    public void setDetourUnavailableReason(String detourUnavailableReason) {
        this.detourUnavailableReason = detourUnavailableReason;
    }

    public String getOpeningHours() {
        return openingHours;
    }

    public void setOpeningHours(String openingHours) {
        this.openingHours = openingHours;
    }

    public Boolean getOpenNow() {
        return openNow;
    }

    public void setOpenNow(Boolean openNow) {
        this.openNow = openNow;
    }

    public String getProviderSource() {
        return providerSource;
    }

    public void setProviderSource(String providerSource) {
        this.providerSource = providerSource;
    }

    public Integer getInsertIndex() {
        return insertIndex;
    }

    public void setInsertIndex(Integer insertIndex) {
        this.insertIndex = insertIndex;
    }

    public String getInsertAfterDestination() {
        return insertAfterDestination;
    }

    public void setInsertAfterDestination(String insertAfterDestination) {
        this.insertAfterDestination = insertAfterDestination;
    }

    public String getInsertBeforeDestination() {
        return insertBeforeDestination;
    }

    public void setInsertBeforeDestination(String insertBeforeDestination) {
        this.insertBeforeDestination = insertBeforeDestination;
    }
}
