package com.example.tripplanner.repository;

import com.example.tripplanner.entity.Destination;
import com.example.tripplanner.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaceRepository extends JpaRepository<Place, Long> {
    List<Place> findByDestinationOrderByPriorityAscRatingDesc(Destination destination);
    List<Place> findByDestinationIdOrderByPriorityAscRatingDesc(Long destinationId);
    List<Place> findByDestinationIdAndCategoryIgnoreCase(Long destinationId, String category);
}
