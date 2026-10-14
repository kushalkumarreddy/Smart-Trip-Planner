package com.example.tripplanner.dto;

import java.util.ArrayList;
import java.util.List;

public class FeaturedDestinationDto {

    private String id;
    private String name;
    private String city;
    private String state;
    private String category; // Mountains & Hills, Historical & Heritage, Temples & Spiritual, Beaches, Wildlife & Nature, Waterfalls
    private String description;
    private String imageUrl;
    private String idealDuration;
    private Double latitude;
    private Double longitude;
    private Double rating;
    private Integer reviewCount;
    private List<String> highlights = new ArrayList<>();
    private String bestTimeToVisit;

    public FeaturedDestinationDto() {
    }

    public FeaturedDestinationDto(String id, String name, String city, String state, String category,
                                  String description, String imageUrl, String idealDuration,
                                  Double latitude, Double longitude, Double rating, Integer reviewCount,
                                  List<String> highlights, String bestTimeToVisit) {
        this.id = id;
        this.name = name;
        this.city = city;
        this.state = state;
        this.category = category;
        this.description = description;
        this.imageUrl = imageUrl;
        this.idealDuration = idealDuration;
        this.latitude = latitude;
        this.longitude = longitude;
        this.rating = rating;
        this.reviewCount = reviewCount;
        this.highlights = highlights != null ? highlights : new ArrayList<>();
        this.bestTimeToVisit = bestTimeToVisit;
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

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getIdealDuration() {
        return idealDuration;
    }

    public String getIdealDays() {
        return idealDuration;
    }

    public void setIdealDuration(String idealDuration) {
        this.idealDuration = idealDuration;
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

    public List<String> getHighlights() {
        return highlights;
    }

    public void setHighlights(List<String> highlights) {
        this.highlights = highlights;
    }

    public String getBestTimeToVisit() {
        return bestTimeToVisit;
    }

    public void setBestTimeToVisit(String bestTimeToVisit) {
        this.bestTimeToVisit = bestTimeToVisit;
    }
}
