package com.example.tripplanner.service;

import com.example.tripplanner.dto.DestinationDto;
import com.example.tripplanner.dto.SightseeingPlaceDto;
import com.example.tripplanner.entity.Destination;
import com.example.tripplanner.entity.Place;
import com.example.tripplanner.exception.ResourceNotFoundException;
import com.example.tripplanner.repository.DestinationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DestinationService {

    private final DestinationRepository destinationRepository;

    public DestinationService(DestinationRepository destinationRepository) {
        this.destinationRepository = destinationRepository;
    }

    public Destination getById(Long id) {
        return destinationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Destination not found with ID: " + id));
    }

    @Transactional
    public Destination toggleVisited(Long destinationId, boolean visited) {
        Destination destination = getById(destinationId);
        destination.setVisited(visited);
        destination.setVisitedAt(visited ? LocalDateTime.now() : null);
        return destinationRepository.save(destination);
    }

    public DestinationDto mapToDto(Destination destination) {
        DestinationDto dto = new DestinationDto(destination.getId(), destination.getName(), destination.getSequenceOrder());
        dto.setTripId(destination.getTrip() != null ? destination.getTrip().getId() : null);
        dto.setLatitude(destination.getLatitude());
        dto.setLongitude(destination.getLongitude());
        dto.setDistanceFromPrevious(destination.getDistanceFromPrevious());
        dto.setTravelTimeFromPrevious(destination.getTravelTimeFromPrevious());
        dto.setVisited(destination.getVisited());
        dto.setNotes(destination.getNotes());

        List<SightseeingPlaceDto> places = new ArrayList<>();
        if (destination.getPlaces() != null) {
            for (Place p : destination.getPlaces()) {
                SightseeingPlaceDto pDto = new SightseeingPlaceDto();
                pDto.setId(p.getId());
                pDto.setDestinationId(destination.getId());
                pDto.setDestinationName(destination.getName());
                pDto.setName(p.getName());
                pDto.setCategory(p.getCategory());
                pDto.setRating(p.getRating());
                pDto.setReviewCount(p.getReviewCount());
                pDto.setDistanceKm(p.getDistanceKm());
                pDto.setEstimatedVisitDurationMinutes(p.getEstimatedVisitDurationMinutes());
                pDto.setEstimatedTravelTimeMinutes(p.getEstimatedTravelTimeMinutes());
                pDto.setDescription(p.getDescription());
                pDto.setOpeningHours(p.getOpeningHours());
                pDto.setLatitude(p.getLatitude());
                pDto.setLongitude(p.getLongitude());
                pDto.setPriority(p.getPriority());
                pDto.setVisited(p.getVisited());
                pDto.setAddedToItinerary(p.getAddedToItinerary());
                places.add(pDto);
            }
        }
        dto.setPlaces(places);
        return dto;
    }
}
