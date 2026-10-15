package com.example.tripplanner.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "destinations")
public class Destination {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(nullable = false, length = 200)
    private String name;

    private Double latitude;
    private Double longitude;

    @Column(name = "sequence_order", nullable = false)
    private Integer sequenceOrder = 0;

    @Column(name = "distance_from_previous")
    private Double distanceFromPrevious = 0.0; // in km

    @Column(name = "travel_time_from_previous")
    private Integer travelTimeFromPrevious = 0; // in minutes

    private Boolean visited = false;

    @Column(name = "visited_at")
    private LocalDateTime visitedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "hotel_name", length = 255)
    private String hotelName;

    @Column(name = "hotel_address", length = 500)
    private String hotelAddress;

    @Column(name = "hotel_lat")
    private Double hotelLat;

    @Column(name = "hotel_lng")
    private Double hotelLng;

    @Column(name = "check_in_date")
    private LocalDate checkInDate;

    @Column(name = "check_out_date")
    private LocalDate checkOutDate;

    @Column(name = "check_in_time", length = 20)
    private String checkInTime;

    @Column(name = "check_out_time", length = 20)
    private String checkOutTime;

    @Column(name = "rooms_count")
    private Integer roomsCount = 1;

    @Column(name = "guests_count")
    private Integer guestsCount = 1;

    @Column(name = "booking_reference", length = 100)
    private String bookingReference;

    @Column(name = "hotel_notes", columnDefinition = "TEXT")
    private String hotelNotes;

    @Column(name = "stay_duration_days")
    private Integer stayDurationDays = 1;

    @Column(name = "stay_duration_nights")
    private Integer stayDurationNights = 0;

    @Column(name = "arrival_date")
    private LocalDate arrivalDate;

    @Column(name = "departure_date")
    private LocalDate departureDate;

    @Column(name = "arrival_time", length = 20)
    private String arrivalTime;

    @Column(name = "departure_time", length = 20)
    private String departureTime;

    @OneToMany(mappedBy = "destination", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("priority ASC, rating DESC")
    private List<Place> places = new ArrayList<>();

    public Destination() {
    }

    public Destination(String name, Integer sequenceOrder) {
        this.name = name;
        this.sequenceOrder = sequenceOrder;
        this.stayDurationDays = 1;
        this.stayDurationNights = 0;
    }

    public Destination(String name, Integer sequenceOrder, Integer stayDurationDays) {
        this.name = name;
        this.sequenceOrder = sequenceOrder;
        this.stayDurationDays = stayDurationDays != null && stayDurationDays > 0 ? stayDurationDays : 1;
        this.stayDurationNights = Math.max(0, this.stayDurationDays - 1);
    }

    public void addPlace(Place place) {
        places.add(place);
        place.setDestination(this);
    }

    public void removePlace(Place place) {
        places.remove(place);
        place.setDestination(null);
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public Integer getSequenceOrder() {
        return sequenceOrder;
    }

    public void setSequenceOrder(Integer sequenceOrder) {
        this.sequenceOrder = sequenceOrder;
    }

    public Double getDistanceFromPrevious() {
        return distanceFromPrevious;
    }

    public void setDistanceFromPrevious(Double distanceFromPrevious) {
        this.distanceFromPrevious = distanceFromPrevious;
    }

    public Integer getTravelTimeFromPrevious() {
        return travelTimeFromPrevious;
    }

    public void setTravelTimeFromPrevious(Integer travelTimeFromPrevious) {
        this.travelTimeFromPrevious = travelTimeFromPrevious;
    }

    public Boolean getVisited() {
        return visited;
    }

    public void setVisited(Boolean visited) {
        this.visited = visited;
    }

    public LocalDateTime getVisitedAt() {
        return visitedAt;
    }

    public void setVisitedAt(LocalDateTime visitedAt) {
        this.visitedAt = visitedAt;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getHotelName() {
        return hotelName;
    }

    public void setHotelName(String hotelName) {
        this.hotelName = hotelName;
    }

    public String getHotelAddress() {
        return hotelAddress;
    }

    public void setHotelAddress(String hotelAddress) {
        this.hotelAddress = hotelAddress;
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

    public LocalDate getCheckInDate() {
        return checkInDate;
    }

    public void setCheckInDate(LocalDate checkInDate) {
        this.checkInDate = checkInDate;
    }

    public LocalDate getCheckOutDate() {
        return checkOutDate;
    }

    public void setCheckOutDate(LocalDate checkOutDate) {
        this.checkOutDate = checkOutDate;
    }

    public String getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(String checkInTime) {
        this.checkInTime = checkInTime;
    }

    public String getCheckOutTime() {
        return checkOutTime;
    }

    public void setCheckOutTime(String checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    public Integer getRoomsCount() {
        return roomsCount;
    }

    public void setRoomsCount(Integer roomsCount) {
        this.roomsCount = roomsCount;
    }

    public Integer getGuestsCount() {
        return guestsCount;
    }

    public void setGuestsCount(Integer guestsCount) {
        this.guestsCount = guestsCount;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public String getHotelNotes() {
        return hotelNotes;
    }

    public void setHotelNotes(String hotelNotes) {
        this.hotelNotes = hotelNotes;
    }

    public List<Place> getPlaces() {
        return places;
    }

    public void setPlaces(List<Place> places) {
        this.places = places;
    }

    public Integer getStayDurationDays() {
        return stayDurationDays != null && stayDurationDays > 0 ? stayDurationDays : 1;
    }

    public void setStayDurationDays(Integer stayDurationDays) {
        this.stayDurationDays = stayDurationDays != null && stayDurationDays > 0 ? stayDurationDays : 1;
    }

    public Integer getStayDurationNights() {
        return stayDurationNights != null ? stayDurationNights : 0;
    }

    public void setStayDurationNights(Integer stayDurationNights) {
        this.stayDurationNights = stayDurationNights != null && stayDurationNights >= 0 ? stayDurationNights : 0;
    }

    public LocalDate getArrivalDate() {
        return arrivalDate;
    }

    public void setArrivalDate(LocalDate arrivalDate) {
        this.arrivalDate = arrivalDate;
    }

    public LocalDate getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(LocalDate departureDate) {
        this.departureDate = departureDate;
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(String arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }
}
