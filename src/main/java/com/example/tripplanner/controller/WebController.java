package com.example.tripplanner.controller;

import com.example.tripplanner.dto.TripResponseDto;
import com.example.tripplanner.dto.UserRegisterDto;
import com.example.tripplanner.entity.User;
import com.example.tripplanner.service.GoogleMapsService;
import com.example.tripplanner.service.TripService;
import com.example.tripplanner.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class WebController {

    private final UserService userService;
    private final TripService tripService;
    private final GoogleMapsService googleMapsService;
    private final com.example.tripplanner.service.RecommendationService recommendationService;

    public WebController(UserService userService,
                         TripService tripService,
                         GoogleMapsService googleMapsService,
                         com.example.tripplanner.service.RecommendationService recommendationService) {
        this.userService = userService;
        this.tripService = tripService;
        this.googleMapsService = googleMapsService;
        this.recommendationService = recommendationService;
    }

    @GetMapping("/")
    public String index(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        model.addAttribute("isLoggedIn", userDetails != null);
        if (userDetails != null) {
            model.addAttribute("userEmail", userDetails.getUsername());
        }
        model.addAttribute("featuredDestinations", recommendationService.getFeaturedDestinations(null, null));
        model.addAttribute("categories", recommendationService.getAvailableCategories());
        return "index";
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout,
                            @RequestParam(value = "registered", required = false) String registered,
                            Model model) {
        if (error != null) model.addAttribute("errorMessage", "Invalid email or password. Please try again.");
        if (logout != null) model.addAttribute("successMessage", "You have been logged out successfully.");
        if (registered != null) model.addAttribute("successMessage", "Registration successful! Please login below.");
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("userRegisterDto", new UserRegisterDto());
        return "register";
    }

    @PostMapping("/register")
    public String handleRegister(@Valid @ModelAttribute("userRegisterDto") UserRegisterDto userRegisterDto,
                                 BindingResult bindingResult,
                                 Model model) {
        if (bindingResult.hasErrors()) {
            return "register";
        }
        try {
            userService.registerUser(userRegisterDto);
            return "redirect:/login?registered=true";
        } catch (Exception e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "register";
        }
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        List<TripResponseDto> trips = tripService.getUserTrips(user);

        // Aggregate user stats
        double totalDistanceAllTrips = trips.stream().mapToDouble(TripResponseDto::getTotalDistanceKm).sum();
        int totalDestinationsAllTrips = trips.stream().mapToInt(TripResponseDto::getDestinationsCount).sum();
        int totalSightseeingAllTrips = trips.stream().mapToInt(TripResponseDto::getSightseeingPlacesCount).sum();
        int totalVisitedAllTrips = trips.stream().mapToInt(TripResponseDto::getVisitedPlacesCount).sum();

        model.addAttribute("user", user);
        model.addAttribute("trips", trips);
        model.addAttribute("totalTripsCount", trips.size());
        model.addAttribute("totalDistanceKm", Math.round(totalDistanceAllTrips * 10.0) / 10.0);
        model.addAttribute("totalDestinationsCount", totalDestinationsAllTrips);
        model.addAttribute("totalSightseeingCount", totalSightseeingAllTrips);
        model.addAttribute("totalVisitedCount", totalVisitedAllTrips);
        model.addAttribute("overallCompletion", totalSightseeingAllTrips > 0
                ? (int) Math.round(((double) totalVisitedAllTrips / totalSightseeingAllTrips) * 100.0)
                : 0);

        return "dashboard";
    }

    @GetMapping("/create-trip")
    public String createTripPage(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        model.addAttribute("user", user);
        model.addAttribute("googleMapsApiKey", googleMapsService.getApiKey());
        model.addAttribute("isLiveMapsConfigured", googleMapsService.isLiveApiConfigured());
        return "create-trip";
    }

    @GetMapping("/trip/{id}")
    public String tripDetailsPage(@PathVariable("id") Long id, Model model, @AuthenticationPrincipal UserDetails userDetails) {
        User user = resolveCurrentUser(userDetails);
        TripResponseDto trip = tripService.getTripDtoById(id, user);

        model.addAttribute("user", user);
        model.addAttribute("trip", trip);
        model.addAttribute("googleMapsApiKey", googleMapsService.getApiKey());
        model.addAttribute("isLiveMapsConfigured", googleMapsService.isLiveApiConfigured());
        return "trip-details";
    }

    private User resolveCurrentUser(UserDetails userDetails) {
        if (userDetails != null) {
            return userService.getUserByEmail(userDetails.getUsername());
        }
        return userService.getOrCreateDemoUser();
    }
}
