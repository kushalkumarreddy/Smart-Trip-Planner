package com.example.tripplanner.controller;

import com.example.tripplanner.service.GoogleMapsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigApiController {

    private final GoogleMapsService googleMapsService;

    public ConfigApiController(GoogleMapsService googleMapsService) {
        this.googleMapsService = googleMapsService;
    }

    @GetMapping("/maps")
    public ResponseEntity<Map<String, Object>> getMapsConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("isLiveKeyConfigured", googleMapsService.isLiveApiConfigured());
        config.put("apiKey", googleMapsService.getApiKey());
        return ResponseEntity.ok(config);
    }
}
