package com.example.tripplanner.controller;

import com.example.tripplanner.service.TripService;
import com.example.tripplanner.util.GeoUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/places")
public class PlacesApiController {

    private final TripService tripService;
    private final com.example.tripplanner.service.GoogleMapsService googleMapsService;

    public PlacesApiController(TripService tripService,
                               com.example.tripplanner.service.GoogleMapsService googleMapsService) {
        this.tripService = tripService;
        this.googleMapsService = googleMapsService;
    }

    @PutMapping("/{id}/visited")
    public ResponseEntity<Map<String, Object>> toggleVisited(@PathVariable("id") Long id,
                                                            @RequestBody Map<String, Boolean> body) {
        boolean visited = body.getOrDefault("visited", true);
        Map<String, Object> progress = tripService.togglePlaceVisited(id, visited, null);
        return ResponseEntity.ok(progress);
    }

    @PostMapping("/{id}/toggle-visited")
    public ResponseEntity<Map<String, Object>> toggleVisitedPost(@PathVariable("id") Long id,
                                                                 @RequestBody(required = false) Map<String, Boolean> body) {
        Map<String, Object> progress;
        if (body != null && body.containsKey("visited")) {
            progress = tripService.togglePlaceVisited(id, body.get("visited"), null);
        } else {
            progress = tripService.togglePlaceVisited(id, (com.example.tripplanner.entity.User) null);
        }
        return ResponseEntity.ok(progress);
    }

    /**
     * Autocomplete endpoint providing Google-Maps-like options and suggestions as user types.
     */
    @GetMapping("/autocomplete")
    public ResponseEntity<List<Map<String, Object>>> autocomplete(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "limit", defaultValue = "6") int limit) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<Map<String, Object>> suggestions = googleMapsService.autocompletePlaces(query.trim(), limit);
        return ResponseEntity.ok(suggestions);
    }
}
