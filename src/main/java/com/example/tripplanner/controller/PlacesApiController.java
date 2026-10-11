package com.example.tripplanner.controller;

import com.example.tripplanner.service.TripService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/places")
public class PlacesApiController {

    private final TripService tripService;

    public PlacesApiController(TripService tripService) {
        this.tripService = tripService;
    }

    @PutMapping("/{id}/visited")
    public ResponseEntity<Map<String, Object>> toggleVisited(@PathVariable("id") Long id,
                                                            @RequestBody Map<String, Boolean> body) {
        boolean visited = body.getOrDefault("visited", true);
        Map<String, Object> progress = tripService.togglePlaceVisited(id, visited);
        return ResponseEntity.ok(progress);
    }
}
