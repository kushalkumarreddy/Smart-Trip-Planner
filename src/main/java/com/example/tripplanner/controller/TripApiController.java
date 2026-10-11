package com.example.tripplanner.controller;

import com.example.tripplanner.dto.SightseeingPlaceDto;
import com.example.tripplanner.dto.TripRequestDto;
import com.example.tripplanner.dto.TripResponseDto;
import com.example.tripplanner.entity.User;
import com.example.tripplanner.service.TripService;
import com.example.tripplanner.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trips")
public class TripApiController {

    private final TripService tripService;
    private final UserService userService;

    public TripApiController(TripService tripService, UserService userService) {
        this.tripService = tripService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<TripResponseDto> createTrip(@Valid @RequestBody TripRequestDto request,
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto created = tripService.createTrip(request, user);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<TripResponseDto>> getUserTrips(@AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        List<TripResponseDto> trips = tripService.getUserTrips(user);
        return ResponseEntity.ok(trips);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripResponseDto> getTripById(@PathVariable("id") Long id,
                                                       @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto trip = tripService.getTripDtoById(id, user);
        return ResponseEntity.ok(trip);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteTrip(@PathVariable("id") Long id,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        tripService.deleteTrip(id, user);
        return ResponseEntity.ok(Map.of("message", "Trip deleted successfully"));
    }

    @PostMapping("/{id}/optimize")
    public ResponseEntity<TripResponseDto> optimizeTripRoute(@PathVariable("id") Long id,
                                                             @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto optimized = tripService.applyOptimizedRoute(id, user);
        return ResponseEntity.ok(optimized);
    }

    @PostMapping("/{id}/destinations/{destinationId}/places")
    public ResponseEntity<TripResponseDto> addPlaceToTrip(@PathVariable("id") Long id,
                                                          @PathVariable("destinationId") Long destinationId,
                                                          @RequestBody SightseeingPlaceDto placeDto,
                                                          @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto updated = tripService.addSightseeingPlaceToTrip(id, destinationId, placeDto, user);
        return ResponseEntity.ok(updated);
    }

    private User resolveCurrentUser(UserDetails userDetails) {
        if (userDetails != null) {
            return userService.getUserByEmail(userDetails.getUsername());
        }
        return userService.getOrCreateDemoUser();
    }
}
