package com.example.tripplanner.repository;

import com.example.tripplanner.entity.Destination;
import com.example.tripplanner.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DestinationRepository extends JpaRepository<Destination, Long> {
    List<Destination> findByTripOrderBySequenceOrderAsc(Trip trip);
    List<Destination> findByTripIdOrderBySequenceOrderAsc(Long tripId);
    Optional<Destination> findByIdAndTripId(Long id, Long tripId);
    void deleteByTripId(Long tripId);
}
