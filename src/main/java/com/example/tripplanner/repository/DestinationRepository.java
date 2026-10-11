package com.example.tripplanner.repository;

import com.example.tripplanner.entity.Destination;
import com.example.tripplanner.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DestinationRepository extends JpaRepository<Destination, Long> {
    List<Destination> findByTripOrderBySequenceOrderAsc(Trip trip);
    List<Destination> findByTripIdOrderBySequenceOrderAsc(Long tripId);
}
