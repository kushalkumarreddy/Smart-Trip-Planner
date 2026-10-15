package com.example.tripplanner.controller;

import com.example.tripplanner.entity.User;
import com.example.tripplanner.service.GoogleMapsService;
import com.example.tripplanner.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigApiController {

    private final GoogleMapsService googleMapsService;
    private final UserService userService;

    public ConfigApiController(GoogleMapsService googleMapsService, UserService userService) {
        this.googleMapsService = googleMapsService;
        this.userService = userService;
    }

    @GetMapping("/maps")
    public ResponseEntity<Map<String, Object>> getMapsConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("isLiveKeyConfigured", googleMapsService.isLiveApiConfigured());
        config.put("apiKey", googleMapsService.getApiKey());
        return ResponseEntity.ok(config);
    }

    @PostMapping("/theme")
    public ResponseEntity<Map<String, String>> saveThemePreference(@RequestBody Map<String, String> body,
                                                                   @AuthenticationPrincipal UserDetails userDetails) {
        String theme = (body != null && body.containsKey("theme")) ? body.get("theme") : "system";
        if (userDetails != null) {
            try {
                User user = userService.getUserByEmail(userDetails.getUsername());
                userService.updateThemePreference(user, theme);
            } catch (Exception ignored) {
            }
        }
        return ResponseEntity.ok(Map.of("theme", theme, "status", "saved"));
    }
}
