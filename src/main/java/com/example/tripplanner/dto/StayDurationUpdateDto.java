package com.example.tripplanner.dto;

import java.time.LocalDate;

public class StayDurationUpdateDto {

    private Integer stayDurationDays;
    private Integer stayDurationNights;
    private LocalDate arrivalDate;
    private LocalDate departureDate;
    private String arrivalTime;
    private String departureTime;
    private Boolean recalculateSchedule = true;

    public StayDurationUpdateDto() {
    }

    public StayDurationUpdateDto(Integer stayDurationDays, Integer stayDurationNights) {
        this.stayDurationDays = stayDurationDays;
        this.stayDurationNights = stayDurationNights;
    }

    public Integer getStayDurationDays() {
        return stayDurationDays;
    }

    public void setStayDurationDays(Integer stayDurationDays) {
        this.stayDurationDays = stayDurationDays;
    }

    public Integer getStayDurationNights() {
        return stayDurationNights;
    }

    public void setStayDurationNights(Integer stayDurationNights) {
        this.stayDurationNights = stayDurationNights;
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

    public Boolean getRecalculateSchedule() {
        return recalculateSchedule != null ? recalculateSchedule : true;
    }

    public void setRecalculateSchedule(Boolean recalculateSchedule) {
        this.recalculateSchedule = recalculateSchedule;
    }
}
