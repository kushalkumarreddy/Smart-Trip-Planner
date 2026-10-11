package com.example.tripplanner.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "trip_name", nullable = false, length = 150)
    private String tripName;

    @Column(name = "start_location", nullable = false, length = 200)
    private String startLocation;

    @Column(name = "start_lat")
    private Double startLat;

    @Column(name = "start_lng")
    private Double startLng;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "transport_mode", length = 50)
    private String transportMode = "CAR"; // CAR, BIKE, PUBLIC_TRANSPORT, WALKING

    @Column(name = "travel_style", length = 50)
    private String travelStyle = "BALANCED"; // RELAXED, BALANCED, FAST_PACED

    @Column(name = "travelers_count")
    private Integer travelersCount = 1;

    @Column(name = "daily_start_time", length = 20)
    private String dailyStartTime = "08:00";

    @Column(name = "daily_end_time", length = 20)
    private String dailyEndTime = "20:00";

    @Column(name = "hotel_location")
    private String hotelLocation;

    @Column(name = "hotel_lat")
    private Double hotelLat;

    @Column(name = "hotel_lng")
    private Double hotelLng;

    @Column(name = "budget_range", length = 50)
    private String budgetRange;

    @Column(name = "preferred_interests", columnDefinition = "TEXT")
    private String preferredInterests;

    @Column(name = "total_distance")
    private Double totalDistance = 0.0;

    @Column(name = "total_travel_time_minutes")
    private Integer totalTravelTimeMinutes = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sequenceOrder ASC")
    private List<Destination> destinations = new ArrayList<>();

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("dayNumber ASC, startTime ASC")
    private List<ItineraryItem> itineraries = new ArrayList<>();

    public Trip() {
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

    // Helper methods for bi-directional relationship management
    public void addDestination(Destination destination) {
        destinations.add(destination);
        destination.setTrip(this);
    }

    public void removeDestination(Destination destination) {
        destinations.remove(destination);
        destination.setTrip(null);
    }

    public void addItineraryItem(ItineraryItem item) {
        itineraries.add(item);
        item.setTrip(this);
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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

    public Double getStartLat() {
        return startLat;
    }

    public void setStartLat(Double startLat) {
        this.startLat = startLat;
    }

    public Double getStartLng() {
        return startLng;
    }

    public void setStartLng(Double startLng) {
        this.startLng = startLng;
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

    public String getDailyStartTime() {
        return dailyStartTime;
    }

    public void setDailyStartTime(String dailyStartTime) {
        this.dailyStartTime = dailyStartTime;
    }

    public String getDailyEndTime() {
        return dailyEndTime;
    }

    public void setDailyEndTime(String dailyEndTime) {
        this.dailyEndTime = dailyEndTime;
    }

    public String getHotelLocation() {
        return hotelLocation;
    }

    public void setHotelLocation(String hotelLocation) {
        this.hotelLocation = hotelLocation;
    }

    public Double getHotelLat() {
        return hotelLat;
    }

    public void setHotelLat(Double hotelLat) {
        this.hotelLat = hotelLat;
    }

    public Double getHotelLng() {
        return hotelLng;
    }

    public void setHotelLng(Double hotelLng) {
        this.hotelLng = hotelLng;
    }

    public String getBudgetRange() {
        return budgetRange;
    }

    public void setBudgetRange(String budgetRange) {
        this.budgetRange = budgetRange;
    }

    public String getPreferredInterests() {
        return preferredInterests;
    }

    public void setPreferredInterests(String preferredInterests) {
        this.preferredInterests = preferredInterests;
    }

    public Double getTotalDistance() {
        return totalDistance;
    }

    public void setTotalDistance(Double totalDistance) {
        this.totalDistance = totalDistance;
    }

    public Integer getTotalTravelTimeMinutes() {
        return totalTravelTimeMinutes;
    }

    public void setTotalTravelTimeMinutes(Integer totalTravelTimeMinutes) {
        this.totalTravelTimeMinutes = totalTravelTimeMinutes;
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

    public List<Destination> getDestinations() {
        return destinations;
    }

    public void setDestinations(List<Destination> destinations) {
        this.destinations = destinations;
    }

    public List<ItineraryItem> getItineraries() {
        return itineraries;
    }

    public void setItineraries(List<ItineraryItem> itineraries) {
        this.itineraries = itineraries;
    }
}
