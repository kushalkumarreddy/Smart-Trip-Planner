package com.example.tripplanner.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TripSnapshotDto {

    private Integer versionNumber;
    private String snapshotTimestamp;
    private Long tripId;
    private String tripName;
    private String startLocation;
    private Double startLat;
    private Double startLng;
    private LocalDate startDate;
    private LocalDate endDate;
    private String transportMode;
    private String travelStyle;
    private Integer travelersCount;
    private String dailyStartTime;
    private String dailyEndTime;
    private String hotelLocation;
    private Double hotelLat;
    private Double hotelLng;
    private String budgetRange;
    private String preferredInterests;
    private Double totalDistance = 0.0;
    private Integer totalTravelTimeMinutes = 0;

    private List<DestinationSnapshotDto> destinations = new ArrayList<>();
    private List<ItinerarySnapshotDto> itineraries = new ArrayList<>();

    public TripSnapshotDto() {
    }

    public static class DestinationSnapshotDto {
        private String name;
        private Double latitude;
        private Double longitude;
        private Integer sequenceOrder;
        private Double distanceFromPrevious = 0.0;
        private Integer travelTimeFromPrevious = 0;
        private Boolean visited = false;
        private String notes;

        // Hotel / Accommodation details
        private String hotelName;
        private String hotelAddress;
        private Double hotelLat;
        private Double hotelLng;
        private LocalDate checkInDate;
        private LocalDate checkOutDate;
        private String checkInTime;
        private String checkOutTime;
        private Integer roomsCount = 1;
        private Integer guestsCount = 1;
        private String bookingReference;
        private String hotelNotes;

        // Stay duration
        private Integer stayDurationDays = 1;
        private Integer stayDurationNights = 0;
        private LocalDate arrivalDate;
        private LocalDate departureDate;
        private String arrivalTime;
        private String departureTime;

        private List<PlaceSnapshotDto> places = new ArrayList<>();

        public DestinationSnapshotDto() {
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

        public List<PlaceSnapshotDto> getPlaces() {
            return places;
        }

        public void setPlaces(List<PlaceSnapshotDto> places) {
            this.places = places;
        }
    }

    public static class PlaceSnapshotDto {
        private String name;
        private String category = "Historical";
        private Double rating = 4.5;
        private Integer reviewCount = 100;
        private Double latitude;
        private Double longitude;
        private Double distanceKm = 0.0;
        private Integer estimatedVisitDurationMinutes = 90;
        private Integer estimatedTravelTimeMinutes = 15;
        private String description;
        private String openingHours = "09:00 AM - 06:00 PM";
        private String photoUrl;
        private String googlePlaceId;
        private String priority = "Medium";
        private Boolean visited = false;
        private Boolean addedToItinerary = false;

        public PlaceSnapshotDto() {
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

        public Double getDistanceKm() {
            return distanceKm;
        }

        public void setDistanceKm(Double distanceKm) {
            this.distanceKm = distanceKm;
        }

        public Integer getEstimatedVisitDurationMinutes() {
            return estimatedVisitDurationMinutes;
        }

        public void setEstimatedVisitDurationMinutes(Integer estimatedVisitDurationMinutes) {
            this.estimatedVisitDurationMinutes = estimatedVisitDurationMinutes;
        }

        public Integer getEstimatedTravelTimeMinutes() {
            return estimatedTravelTimeMinutes;
        }

        public void setEstimatedTravelTimeMinutes(Integer estimatedTravelTimeMinutes) {
            this.estimatedTravelTimeMinutes = estimatedTravelTimeMinutes;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getOpeningHours() {
            return openingHours;
        }

        public void setOpeningHours(String openingHours) {
            this.openingHours = openingHours;
        }

        public String getPhotoUrl() {
            return photoUrl;
        }

        public void setPhotoUrl(String photoUrl) {
            this.photoUrl = photoUrl;
        }

        public String getGooglePlaceId() {
            return googlePlaceId;
        }

        public void setGooglePlaceId(String googlePlaceId) {
            this.googlePlaceId = googlePlaceId;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        public Boolean getVisited() {
            return visited;
        }

        public void setVisited(Boolean visited) {
            this.visited = visited;
        }

        public Boolean getAddedToItinerary() {
            return addedToItinerary;
        }

        public void setAddedToItinerary(Boolean addedToItinerary) {
            this.addedToItinerary = addedToItinerary;
        }
    }

    public static class ItinerarySnapshotDto {
        private Integer dayNumber = 1;
        private LocalDate scheduleDate;
        private String startTime;
        private String endTime;
        private String activity;
        private String location;
        private String activityType = "SIGHTSEEING";
        private Double travelDistanceKm = 0.0;
        private Integer travelTimeMinutes = 0;
        private Integer durationMinutes = 60;
        private Double rating;
        private Boolean visited = false;
        private Boolean locked = false;
        private String notes;
        private String placeName;

        public ItinerarySnapshotDto() {
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
            return locked;
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

        public String getPlaceName() {
            return placeName;
        }

        public void setPlaceName(String placeName) {
            this.placeName = placeName;
        }
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getSnapshotTimestamp() {
        return snapshotTimestamp;
    }

    public void setSnapshotTimestamp(String snapshotTimestamp) {
        this.snapshotTimestamp = snapshotTimestamp;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
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

    public List<DestinationSnapshotDto> getDestinations() {
        return destinations;
    }

    public void setDestinations(List<DestinationSnapshotDto> destinations) {
        this.destinations = destinations;
    }

    public List<ItinerarySnapshotDto> getItineraries() {
        return itineraries;
    }

    public void setItineraries(List<ItinerarySnapshotDto> itineraries) {
        this.itineraries = itineraries;
    }
}
