package com.example.tripplanner.dto;

import java.time.LocalDate;

public class AccommodationStayDto {

    private Long destinationId;
    private String destinationName;
    private String hotelName;
    private String hotelAddress;
    private Double hotelLat;
    private Double hotelLng;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private String checkInTime;
    private String checkOutTime;
    private Integer roomsCount = 1;
    private Integer guestsCount = 2;
    private String bookingReference;
    private String hotelNotes;
    private String googlePlaceId;

    public AccommodationStayDto() {
    }

    public AccommodationStayDto(Long destinationId, String hotelName, String hotelAddress, Double hotelLat, Double hotelLng) {
        this.destinationId = destinationId;
        this.hotelName = hotelName;
        this.hotelAddress = hotelAddress;
        this.hotelLat = hotelLat;
        this.hotelLng = hotelLng;
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

    public String getGooglePlaceId() {
        return googlePlaceId;
    }

    public void setGooglePlaceId(String googlePlaceId) {
        this.googlePlaceId = googlePlaceId;
    }
}
