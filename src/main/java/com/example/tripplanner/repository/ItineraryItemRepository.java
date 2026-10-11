package com.example.tripplanner.repository;

import com.example.tripplanner.entity.ItineraryItem;
import com.example.tripplanner.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, Long> {
    List<ItineraryItem> findByTripOrderByDayNumberAscStartTimeAsc(Trip trip);
    List<ItineraryItem> findByTripIdOrderByDayNumberAscStartTimeAsc(Long tripId);
    void deleteByTripId(Long tripId);
}
