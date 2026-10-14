package com.example.tripplanner.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "itineraries")
public class ItineraryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber = 1;

    @Column(name = "schedule_date")
    private LocalDate scheduleDate;

    @Column(name = "start_time", nullable = false, length = 20)
    private String startTime;

    @Column(name = "end_time", nullable = false, length = 20)
    private String endTime;

    @Column(nullable = false)
    private String activity;

    @Column(nullable = false)
    private String location;

    @Column(name = "activity_type", length = 50)
    private String activityType = "SIGHTSEEING"; // DEPARTURE, ARRIVAL, SIGHTSEEING, MEAL, REST, HOTEL

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id")
    private Place place;

    @Column(name = "travel_distance_km")
    private Double travelDistanceKm = 0.0;

    @Column(name = "travel_time_minutes")
    private Integer travelTimeMinutes = 0;

    @Column(name = "duration_minutes")
    private Integer durationMinutes = 60;

    private Double rating;

    private Boolean visited = false;

    @Column(name = "locked")
    private Boolean locked = false;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public ItineraryItem() {
    }

    public ItineraryItem(Integer dayNumber, String startTime, String endTime, String activity, String location, String activityType) {
        this.dayNumber = dayNumber;
        this.startTime = startTime;
        this.endTime = endTime;
        this.activity = activity;
        this.location = location;
        this.activityType = activityType;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Trip getTrip() {
        return trip;
    }

    public void setTrip(Trip trip) {
        this.trip = trip;
    }

    public Integer getDayNumber() {
        return dayNumber;
    }

    public void setDayNumber(Integer dayNumber) {
        this.dayNumber = dayNumber;
    }

    public LocalDate getScheduleDate() {
        return scheduleDate;
    }

    public void setScheduleDate(LocalDate scheduleDate) {
        this.scheduleDate = scheduleDate;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getActivity() {
        return activity;
    }

    public void setActivity(String activity) {
        this.activity = activity;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getActivityType() {
        return activityType;
    }

    public void setActivityType(String activityType) {
        this.activityType = activityType;
    }

    public Place getPlace() {
        return place;
    }

    public void setPlace(Place place) {
        this.place = place;
    }

    public Double getTravelDistanceKm() {
        return travelDistanceKm;
    }

    public void setTravelDistanceKm(Double travelDistanceKm) {
        this.travelDistanceKm = travelDistanceKm;
    }

    public Integer getTravelTimeMinutes() {
        return travelTimeMinutes;
    }

    public void setTravelTimeMinutes(Integer travelTimeMinutes) {
        this.travelTimeMinutes = travelTimeMinutes;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public Boolean getVisited() {
        return visited;
    }

    public void setVisited(Boolean visited) {
        this.visited = visited;
    }

    public Boolean getLocked() {
        return locked != null ? locked : false;
    }

    public void setLocked(Boolean locked) {
        this.locked = locked;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
