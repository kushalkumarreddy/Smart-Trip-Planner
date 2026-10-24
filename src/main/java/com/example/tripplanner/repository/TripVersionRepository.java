package com.example.tripplanner.repository;

import com.example.tripplanner.entity.Trip;
import com.example.tripplanner.entity.TripVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripVersionRepository extends JpaRepository<TripVersion, Long> {

    List<TripVersion> findByTripOrderByVersionNumberDesc(Trip trip);

    List<TripVersion> findByTripIdOrderByVersionNumberDesc(Long tripId);

    Optional<TripVersion> findByIdAndTripId(Long id, Long tripId);

    @Query("SELECT COALESCE(MAX(v.versionNumber), 0) FROM TripVersion v WHERE v.trip.id = :tripId")
    Integer findMaxVersionNumberByTripId(@Param("tripId") Long tripId);

    long countByTrip(Trip trip);

    List<TripVersion> findByTripOrderByVersionNumberAsc(Trip trip);
}
