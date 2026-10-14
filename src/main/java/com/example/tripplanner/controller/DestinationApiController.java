package com.example.tripplanner.controller;

import com.example.tripplanner.dto.FeaturedDestinationDto;
import com.example.tripplanner.dto.SightseeingPlaceDto;
import com.example.tripplanner.entity.Destination;
import com.example.tripplanner.service.DestinationService;
import com.example.tripplanner.service.RecommendationService;
import com.example.tripplanner.service.TripService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/destinations")
public class DestinationApiController {

    private final DestinationService destinationService;
    private final RecommendationService recommendationService;
    private final TripService tripService;

    public DestinationApiController(DestinationService destinationService,
                                    RecommendationService recommendationService,
                                    TripService tripService) {
        this.destinationService = destinationService;
        this.recommendationService = recommendationService;
        this.tripService = tripService;
    }

    /**
     * Curated Famous Indian Destinations Catalog (Section 2)
     */
    @GetMapping("/featured")
    public ResponseEntity<List<FeaturedDestinationDto>> getFeaturedDestinations(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "query", required = false) String query) {
        List<FeaturedDestinationDto> featured = recommendationService.getFeaturedDestinations(category, query);
        return ResponseEntity.ok(featured);
    }

    /**
     * Categories list for filter pills / drawer
     */
    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(recommendationService.getAvailableCategories());
    }

    @GetMapping("/featured/{id}")
    public ResponseEntity<FeaturedDestinationDto> getFeaturedDestinationById(@PathVariable("id") String id) {
        return recommendationService.getFeaturedDestinationById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/visited")
    public ResponseEntity<Map<String, Object>> toggleVisited(@PathVariable("id") Long id,
                                                            @RequestBody Map<String, Boolean> body) {
        boolean visited = body.getOrDefault("visited", true);
        Map<String, Object> progress = tripService.toggleDestinationVisited(id, visited);
        return ResponseEntity.ok(progress);
    }

    @GetMapping("/{id}/nearby-places")
    public ResponseEntity<List<SightseeingPlaceDto>> getNearbyPlaces(@PathVariable("id") Long id,
                                                                     @RequestParam(value = "sortBy", defaultValue = "popularity") String sortBy,
                                                                     @RequestParam(value = "category", defaultValue = "ALL") String category) {
        Destination destination = destinationService.getById(id);
        List<SightseeingPlaceDto> places = recommendationService.getRecommendedPlaces(destination.getName(), null, sortBy, category);
        return ResponseEntity.ok(places);
    }
}
