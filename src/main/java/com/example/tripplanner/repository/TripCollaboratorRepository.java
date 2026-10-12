package com.example.tripplanner.repository;

import com.example.tripplanner.entity.Trip;
import com.example.tripplanner.entity.TripCollaborator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripCollaboratorRepository extends JpaRepository<TripCollaborator, Long> {

    List<TripCollaborator> findByTripOrderByIdAsc(Trip trip);

    Optional<TripCollaborator> findByTripAndUserEmail(Trip trip, String userEmail);

    void deleteByTripId(Long tripId);
}
