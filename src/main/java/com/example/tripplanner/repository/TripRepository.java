package com.example.tripplanner.repository;

import com.example.tripplanner.entity.Trip;
import com.example.tripplanner.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {
    List<Trip> findByUserOrderByCreatedAtDesc(User user);

    Optional<Trip> findByIdAndUser(Long id, User user);

    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.destinations d LEFT JOIN FETCH t.itineraries i WHERE t.id = :id AND t.user.id = :userId")
    Optional<Trip> findByIdWithDetailsAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("SELECT t FROM Trip t LEFT JOIN FETCH t.destinations d LEFT JOIN FETCH t.itineraries i WHERE t.id = :id")
    Optional<Trip> findByIdWithDetails(@Param("id") Long id);
}
