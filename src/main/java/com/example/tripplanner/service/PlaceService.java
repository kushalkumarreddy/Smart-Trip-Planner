package com.example.tripplanner.service;

import com.example.tripplanner.dto.SightseeingPlaceDto;
import com.example.tripplanner.entity.Destination;
import com.example.tripplanner.entity.Place;
import com.example.tripplanner.exception.ResourceNotFoundException;
import com.example.tripplanner.repository.PlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PlaceService {

    private final PlaceRepository placeRepository;

    public PlaceService(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    public Place getById(Long id) {
        return placeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Place not found with ID: " + id));
    }

    @Transactional
    public Place toggleVisited(Long placeId, boolean visited) {
        Place place = getById(placeId);
        place.setVisited(visited);
        place.setVisitedAt(visited ? LocalDateTime.now() : null);
        return placeRepository.save(place);
    }

    @Transactional
    public Place savePlaceFromDto(Destination destination, SightseeingPlaceDto dto) {
        Place place = new Place();
        place.setDestination(destination);
        place.setName(dto.getName());
        place.setCategory(dto.getCategory() != null ? dto.getCategory() : "Historical");
        place.setRating(dto.getRating() != null ? dto.getRating() : 4.5);
        place.setReviewCount(dto.getReviewCount() != null ? dto.getReviewCount() : 100);
        place.setLatitude(dto.getLatitude());
        place.setLongitude(dto.getLongitude());
        place.setDistanceKm(dto.getDistanceKm() != null ? dto.getDistanceKm() : 2.0);
        place.setEstimatedVisitDurationMinutes(dto.getEstimatedVisitDurationMinutes() != null ? dto.getEstimatedVisitDurationMinutes() : 90);
        place.setEstimatedTravelTimeMinutes(dto.getEstimatedTravelTimeMinutes() != null ? dto.getEstimatedTravelTimeMinutes() : 15);
        place.setDescription(dto.getDescription());
        place.setOpeningHours(dto.getOpeningHours() != null ? dto.getOpeningHours() : "09:00 AM - 06:00 PM");
        place.setPriority(dto.getPriority() != null ? dto.getPriority() : "Medium");
        place.setVisited(dto.getVisited() != null ? dto.getVisited() : false);
        place.setAddedToItinerary(dto.getAddedToItinerary() != null ? dto.getAddedToItinerary() : true);

        return placeRepository.save(place);
    }
}
