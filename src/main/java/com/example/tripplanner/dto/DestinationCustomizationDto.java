package com.example.tripplanner.dto;

import java.io.Serializable;

/**
 * Encapsulates customized intermediate stop parameters for route corridor planning.
 * Supports mandatory vs optional sightseeing stops and overnight stays.
 */
public class DestinationCustomizationDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private Boolean mandatory = true;
    private Boolean sightseeing = true;
    private Boolean overnight = false;
    private Integer stayNights = 1;
    private String hotelName;

    public DestinationCustomizationDto() {
    }

    public DestinationCustomizationDto(String name) {
        this.name = name;
        this.mandatory = true;
        this.sightseeing = true;
        this.overnight = false;
    }

    public DestinationCustomizationDto(String name, Boolean mandatory, Boolean sightseeing, Boolean overnight) {
        this.name = name;
        this.mandatory = mandatory != null ? mandatory : true;
        this.sightseeing = sightseeing != null ? sightseeing : true;
        this.overnight = overnight != null ? overnight : false;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getMandatory() {
        return mandatory != null ? mandatory : true;
    }

    public void setMandatory(Boolean mandatory) {
        this.mandatory = mandatory;
    }

    public Boolean getSightseeing() {
        return sightseeing != null ? sightseeing : true;
    }

    public void setSightseeing(Boolean sightseeing) {
        this.sightseeing = sightseeing;
    }

    public Boolean getOvernight() {
        return overnight != null ? overnight : false;
    }

    public void setOvernight(Boolean overnight) {
        this.overnight = overnight;
    }

    public Integer getStayNights() {
        return stayNights != null && stayNights > 0 ? stayNights : 1;
    }

    public void setStayNights(Integer stayNights) {
        this.stayNights = stayNights;
    }

    public String getHotelName() {
        return hotelName;
    }

    public void setHotelName(String hotelName) {
        this.hotelName = hotelName;
    }
}
