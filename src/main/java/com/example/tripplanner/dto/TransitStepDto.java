package com.example.tripplanner.dto;

public class TransitStepDto {

    private String vehicleType; // BUS, TRAIN, SUBWAY, TRAM, WALK
    private String lineName;
    private String headsign;
    private String departureStop;
    private String arrivalStop;
    private String departureTime;
    private String arrivalTime;
    private Integer numStops;
    private String agencyName;
    private String instructions;
    private String durationText;
    private String distanceText;

    public TransitStepDto() {
    }

    public TransitStepDto(String vehicleType, String lineName, String departureStop, String arrivalStop,
                          String departureTime, String arrivalTime, Integer numStops, String agencyName) {
        this.vehicleType = vehicleType;
        this.lineName = lineName;
        this.departureStop = departureStop;
        this.arrivalStop = arrivalStop;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.numStops = numStops;
        this.agencyName = agencyName;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getLineName() {
        return lineName;
    }

    public void setLineName(String lineName) {
        this.lineName = lineName;
    }

    public String getHeadsign() {
        return headsign;
    }

    public void setHeadsign(String headsign) {
        this.headsign = headsign;
    }

    public String getDepartureStop() {
        return departureStop;
    }

    public void setDepartureStop(String departureStop) {
        this.departureStop = departureStop;
    }

    public String getArrivalStop() {
        return arrivalStop;
    }

    public void setArrivalStop(String arrivalStop) {
        this.arrivalStop = arrivalStop;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(String arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public Integer getNumStops() {
        return numStops;
    }

    public void setNumStops(Integer numStops) {
        this.numStops = numStops;
    }

    public String getAgencyName() {
        return agencyName;
    }

    public void setAgencyName(String agencyName) {
        this.agencyName = agencyName;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getDurationText() {
        return durationText;
    }

    public void setDurationText(String durationText) {
        this.durationText = durationText;
    }

    public String getDistanceText() {
        return distanceText;
    }

    public void setDistanceText(String distanceText) {
        this.distanceText = distanceText;
    }
}
