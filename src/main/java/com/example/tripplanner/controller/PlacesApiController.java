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

    /**
     * Resolves geographical address and place name from clicked map coordinates.
     */
    @GetMapping("/reverse-geocode")
    public ResponseEntity<Map<String, Object>> reverseGeocode(
            @RequestParam("lat") double lat,
            @RequestParam("lng") double lng) {
        Map<String, Object> details = googleMapsService.reverseGeocode(lat, lng);
        return ResponseEntity.ok(details);
    }

    /**
     * Secure Place Photo Proxy endpoint:
     * Loads Google Places photo on the backend without exposing the server's API key
     * or causing client-side CORS issues, falling back gracefully to category SVGs.
     */
    @GetMapping("/photo")
    public ResponseEntity<byte[]> getPlacePhoto(
            @RequestParam(value = "photoReference", required = false) String photoReference,
            @RequestParam(value = "maxWidth", defaultValue = "800") int maxWidth) {
        if (photoReference != null && !photoReference.trim().isEmpty()) {
            byte[] imageBytes = googleMapsService.fetchPlacePhoto(photoReference, maxWidth);
            if (imageBytes != null && imageBytes.length > 0) {
                return ResponseEntity.ok()
                        .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "image/jpeg")
                        .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                        .body(imageBytes);
            }
        }

        // Return 302 redirect to reliable local vector fallback
        return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND)
                .header(org.springframework.http.HttpHeaders.LOCATION, "/images/placeholder-travel.svg")
                .build();
    }
}
