package com.example.tripplanner.repository;

import com.example.tripplanner.entity.Trip;
import com.example.tripplanner.entity.TripSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripSuggestionRepository extends JpaRepository<TripSuggestion, Long> {

    List<TripSuggestion> findByTripOrderByVotesCountDescCreatedAtDesc(Trip trip);

    void deleteByTripId(Long tripId);
}
