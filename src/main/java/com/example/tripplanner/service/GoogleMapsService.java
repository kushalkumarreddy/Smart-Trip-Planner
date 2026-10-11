package com.example.tripplanner.service;

import com.example.tripplanner.dto.SightseeingPlaceDto;
import com.example.tripplanner.util.GeoUtils;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.TravelMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GoogleMapsService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleMapsService.class);

    @Value("${google.maps.api-key:}")
    private String apiKey;

    @Value("${google.maps.enabled:true}")
    private boolean apiEnabled;

    private GeoApiContext geoApiContext;

    public boolean isLiveApiConfigured() {
        return apiEnabled && apiKey != null && !apiKey.trim().isEmpty() && !apiKey.equalsIgnoreCase("YOUR_API_KEY_HERE");
    }

    public synchronized GeoApiContext getGeoApiContext() {
        if (geoApiContext == null && isLiveApiConfigured()) {
            try {
                this.geoApiContext = new GeoApiContext.Builder()
                        .apiKey(apiKey.trim())
                        .build();
                logger.info("Google Maps GeoApiContext initialized successfully with provided API key.");
            } catch (Exception e) {
                logger.error("Failed to initialize Google Maps GeoApiContext: {}", e.getMessage());
            }
        }
        return geoApiContext;
    }

    public double[] geocode(String address) {
        if (address == null || address.trim().isEmpty()) {
            return new double[]{12.9716, 77.5946};
        }

        if (isLiveApiConfigured()) {
            try {
                GeoApiContext context = getGeoApiContext();
                if (context != null) {
                    GeocodingResult[] results = GeocodingApi.geocode(context, address).await();
                    if (results != null && results.length > 0) {
                        return new double[]{
                                results[0].geometry.location.lat,
                                results[0].geometry.location.lng
                        };
                    }
                }
            } catch (Exception e) {
                logger.warn("Live Google Geocoding failed for '{}', falling back to offline geo engine: {}", address, e.getMessage());
            }
        }

        return GeoUtils.getApproximateCoordinates(address);
    }

    public RouteCalculationResult calculateRouteSegment(String origin, String destination, String transportMode) {
        double[] originCoords = geocode(origin);
        double[] destCoords = geocode(destination);

        if (isLiveApiConfigured()) {
            try {
                GeoApiContext context = getGeoApiContext();
                if (context != null) {
                    TravelMode mode = mapTravelMode(transportMode);
                    DirectionsResult result = DirectionsApi.newRequest(context)
                            .origin(origin)
                            .destination(destination)
                            .mode(mode)
                            .await();

                    if (result != null && result.routes.length > 0 && result.routes[0].legs.length > 0) {
                        long distanceMeters = result.routes[0].legs[0].distance.inMeters;
                        long durationSeconds = result.routes[0].legs[0].duration.inSeconds;
                        double distanceKm = Math.round((distanceMeters / 1000.0) * 10.0) / 10.0;
                        int durationMinutes = (int) Math.round(durationSeconds / 60.0);

                        return new RouteCalculationResult(
                                distanceKm,
                                durationMinutes,
                                originCoords,
                                destCoords,
                                true
                        );
                    }
                }
            } catch (Exception e) {
                logger.warn("Live Google Directions failed between '{}' and '{}': {}. Falling back to internal engine.", origin, destination, e.getMessage());
            }
        }

        // Realistic internal calculation engine
        double distanceKm = GeoUtils.estimateRoadDistanceKm(originCoords[0], originCoords[1], destCoords[0], destCoords[1]);
        int durationMinutes = GeoUtils.estimateTravelTimeMinutes(distanceKm, transportMode);

        return new RouteCalculationResult(distanceKm, durationMinutes, originCoords, destCoords, false);
    }

    private TravelMode mapTravelMode(String transportMode) {
        if (transportMode == null) return TravelMode.DRIVING;
        switch (transportMode.toUpperCase()) {
            case "BIKE":
                return TravelMode.BICYCLING;
            case "WALKING":
                return TravelMode.WALKING;
            case "PUBLIC_TRANSPORT":
                return TravelMode.TRANSIT;
            case "CAR":
            default:
                return TravelMode.DRIVING;
        }
    }

    public String getApiKey() {
        return (apiKey != null) ? apiKey.trim() : "";
    }

    public static class RouteCalculationResult {
        private final double distanceKm;
        private final int durationMinutes;
        private final double[] originCoords;
        private final double[] destinationCoords;
        private final boolean liveApiUsed;

        public RouteCalculationResult(double distanceKm, int durationMinutes, double[] originCoords, double[] destinationCoords, boolean liveApiUsed) {
            this.distanceKm = distanceKm;
            this.durationMinutes = durationMinutes;
            this.originCoords = originCoords;
            this.destinationCoords = destinationCoords;
            this.liveApiUsed = liveApiUsed;
        }

        public double getDistanceKm() {
            return distanceKm;
        }

        public int getDurationMinutes() {
            return durationMinutes;
        }

        public double[] getOriginCoords() {
            return originCoords;
        }

        public double[] getDestinationCoords() {
            return destinationCoords;
        }

        public boolean isLiveApiUsed() {
            return liveApiUsed;
        }
    }
}
