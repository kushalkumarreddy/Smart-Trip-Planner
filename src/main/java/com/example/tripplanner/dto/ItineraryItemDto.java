package com.example.tripplanner.dto;

import java.time.LocalDate;

public class ItineraryItemDto {

    private Long id;
    private Long tripId;
    private Integer dayNumber;
    private LocalDate scheduleDate;
    private String startTime;
    private String endTime;
    private String activity;
    private String location;
    private String activityType; // DEPARTURE, ARRIVAL, SIGHTSEEING, MEAL, REST, HOTEL
    private Long placeId;
    private Double travelDistanceKm;
    private Integer travelTimeMinutes;
    private Integer durationMinutes;
    private Double rating;
    private Boolean visited;
    private Boolean locked = false;
    private String notes;
    private Double latitude;
    private Double longitude;
    private String category;
    private String transitionCaption;
    private java.util.List<TimelineTransitionDto> transitionSegments = new java.util.ArrayList<>();
    private String proximityTier; // IN_DESTINATION, NEARBY_ATTRACTION, NEARBY_TOWN, REGIONAL_OPTIONAL
    private String townOrArea;
    private String googleMapsUrl;

    public String getGoogleMapsUrl() {
        if (googleMapsUrl != null && !googleMapsUrl.isEmpty()) return googleMapsUrl;
        if (latitude != null && longitude != null && latitude != 0.0 && longitude != 0.0) {
            return String.format(java.util.Locale.US, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f", latitude, longitude);
        }
        if (location != null && !location.isEmpty()) {
            return "https://www.google.com/maps/search/?api=1&query=" + java.net.URLEncoder.encode(location, java.nio.charset.StandardCharsets.UTF_8);
        }
        return null;
    }

    public void setGoogleMapsUrl(String googleMapsUrl) {
        this.googleMapsUrl = googleMapsUrl;
    }

    public ItineraryItemDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
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

    public Long getPlaceId() {
        return placeId;
    }

    public void setPlaceId(Long placeId) {
        this.placeId = placeId;
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
        return visited != null ? visited : false;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTransitionCaption() {
        if (transitionCaption == null || transitionCaption.isEmpty()) {
            return transitionCaption;
        }
        String clean = transitionCaption.replaceAll("[\\p{So}\\p{Cn}\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+", "")
                                        .replaceAll("^[\\s·•-]+", "")
                                        .trim();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)\\s*(?:mins|minutes|min)").matcher(clean);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            try {
                int mins = Integer.parseInt(matcher.group(1));
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(com.example.tripplanner.util.TimeUtils.formatDuration(mins)));
            } catch (Exception e) {
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public void setTransitionCaption(String transitionCaption) {
        if (transitionCaption == null || transitionCaption.isEmpty()) {
            this.transitionCaption = transitionCaption;
            return;
        }
        String clean = transitionCaption.replaceAll("[\\p{So}\\p{Cn}\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+", "")
                                        .replaceAll("^[\\s·•-]+", "")
                                        .trim();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)\\s*(?:mins|minutes|min)").matcher(clean);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            try {
                int mins = Integer.parseInt(matcher.group(1));
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(com.example.tripplanner.util.TimeUtils.formatDuration(mins)));
            } catch (Exception e) {
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);
        this.transitionCaption = sb.toString();
    }

    public String getFormattedTravelTime() {
        return (travelTimeMinutes != null && travelTimeMinutes > 0)
                ? com.example.tripplanner.util.TimeUtils.formatDuration(travelTimeMinutes)
                : "";
    }

    public java.util.List<TimelineTransitionDto> getTransitionSegments() {
        if (transitionSegments == null) {
            transitionSegments = new java.util.ArrayList<>();
        }
        return transitionSegments;
    }

    public void setTransitionSegments(java.util.List<TimelineTransitionDto> transitionSegments) {
        this.transitionSegments = transitionSegments;
    }

    public String getProximityTier() {
        return proximityTier;
    }

    public void setProximityTier(String proximityTier) {
        this.proximityTier = proximityTier;
    }

    public String getTownOrArea() {
        return townOrArea;
    }

    public void setTownOrArea(String townOrArea) {
        this.townOrArea = townOrArea;
    }

    public String getFormattedDuration() {
        return (durationMinutes != null && durationMinutes > 0) ? com.example.tripplanner.util.TimeUtils.formatDuration(durationMinutes) : "";
    }

    public String getFormattedStartTime() {
        return (startTime != null && !startTime.isEmpty()) ? com.example.tripplanner.util.TimeUtils.formatAmPm(startTime) : "";
    }

    public String getFormattedEndTime() {
        return (endTime != null && !endTime.isEmpty()) ? com.example.tripplanner.util.TimeUtils.formatAmPm(endTime) : "";
    }

    public String getFormattedTimeSpan() {
        String s = getFormattedStartTime();
        String e = getFormattedEndTime();
        if (!s.isEmpty() && !e.isEmpty()) return s + " – " + e;
        if (!s.isEmpty()) return s;
        return "";
    }

    public String getTimelineType() {
        String act = activity != null ? activity.toLowerCase() : "";
        String type = activityType != null ? activityType.toUpperCase() : "";
        String cat = category != null ? category.toUpperCase() : "";

        if (act.contains("return") || act.contains("journey home") || act.contains("depart for home") || act.contains("drive back")) {
            return "RETURN";
        }
        if (type.equals("DEPARTURE") || act.contains("depart") || act.contains("starting journey") || act.contains("start journey")) {
            return "DEPARTURE";
        }
        if (type.equals("TRANSIT") || type.equals("DRIVING") || act.contains("transit") || act.contains("train") || act.contains("flight")) {
            return "TRANSIT";
        }
        if (type.equals("ARRIVAL") || act.contains("arrival") || act.contains("arrive at")) {
            return "ARRIVAL";
        }
        if (type.equals("MEAL") || cat.contains("FOOD") || cat.contains("RESTAURANT") || act.contains("breakfast") || act.contains("lunch") || act.contains("dinner")) {
            return "MEAL";
        }
        if (type.equals("REST") || act.contains("tea") || act.contains("coffee") || act.contains("relax") || act.contains("rest break") || act.contains("leisure")) {
            return "REST";
        }
        if (act.contains("overnight") || act.contains("night stay")) {
            return "OVERNIGHT";
        }
        if (type.equals("HOTEL") || cat.contains("LODGING") || act.contains("hotel") || act.contains("resort") || act.contains("check-in") || act.contains("check-out")) {
            return "HOTEL";
        }
        return "SIGHTSEEING";
    }

    public String getTimelineTypeLabel() {
        return switch (getTimelineType()) {
            case "DEPARTURE" -> "Departure";
            case "TRANSIT" -> "Transit / Journey";
            case "ARRIVAL" -> "Arrival";
            case "SIGHTSEEING" -> "Sightseeing";
            case "MEAL" -> "Dining";
            case "REST" -> "Rest & Relaxation";
            case "HOTEL" -> "Hotel / Stay";
            case "OVERNIGHT" -> "Overnight Stay";
            case "RETURN" -> "Return Journey";
            default -> "Attraction";
        };
    }

    public String getTimelineIcon() {
        return switch (getTimelineType()) {
            case "DEPARTURE" -> "bi-geo-alt-fill";
            case "TRANSIT" -> "bi-car-front-fill";
            case "ARRIVAL" -> "bi-pin-map-fill";
            case "SIGHTSEEING" -> "bi-compass";
            case "MEAL" -> "bi-cup-hot-fill";
            case "REST" -> "bi-pause-circle-fill";
            case "HOTEL" -> "bi-building";
            case "OVERNIGHT" -> "bi-moon-stars-fill";
            case "RETURN" -> "bi-arrow-left-circle-fill";
            default -> "bi-geo-alt";
        };
    }

    public String getTimelineColorClass() {
        return switch (getTimelineType()) {
            case "DEPARTURE" -> "timeline-dot-departure";
            case "TRANSIT" -> "timeline-dot-transit";
            case "ARRIVAL" -> "timeline-dot-arrival";
            case "SIGHTSEEING" -> "timeline-dot-sightseeing";
            case "MEAL" -> "timeline-dot-meal";
            case "REST" -> "timeline-dot-rest";
            case "HOTEL" -> "timeline-dot-hotel";
            case "OVERNIGHT" -> "timeline-dot-overnight";
            case "RETURN" -> "timeline-dot-return";
            default -> "timeline-dot-sightseeing";
        };
    }
}
