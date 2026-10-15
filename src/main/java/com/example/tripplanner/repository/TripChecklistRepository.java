package com.example.tripplanner.repository;

import com.example.tripplanner.entity.Trip;
import com.example.tripplanner.entity.TripChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripChecklistRepository extends JpaRepository<TripChecklistItem, Long> {
    List<TripChecklistItem> findByTripOrderByCategoryAscSortOrderAscIdAsc(Trip trip);
    List<TripChecklistItem> findByTripIdOrderByCategoryAscSortOrderAscIdAsc(Long tripId);
    void deleteByTrip(Trip trip);
}
