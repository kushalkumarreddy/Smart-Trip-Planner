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

    private final java.util.Map<String, RouteCalculationResult> routeCache = new java.util.concurrent.ConcurrentHashMap<>();

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
        if (origin == null || destination == null) {
            return new RouteCalculationResult(0.0, 0, new double[]{12.9716, 77.5946}, new double[]{12.9716, 77.5946}, false, false, "Origin and destination cannot be null");
        }

        String mode = (transportMode != null && !transportMode.trim().isEmpty()) ? transportMode.trim().toUpperCase() : "CAR";
        String cacheKey = origin.trim().toLowerCase() + "->" + destination.trim().toLowerCase() + "@" + mode;

        if (routeCache.containsKey(cacheKey)) {
            return routeCache.get(cacheKey);
        }

        double[] originCoords = geocode(origin);
        double[] destCoords = geocode(destination);

        // Check if origins are identical
        if (origin.trim().equalsIgnoreCase(destination.trim()) ||
                (Math.abs(originCoords[0] - destCoords[0]) < 0.0005 && Math.abs(originCoords[1] - destCoords[1]) < 0.0005)) {
            RouteCalculationResult zeroResult = new RouteCalculationResult(0.0, 0, originCoords, destCoords, true, true, null, java.util.Collections.singletonList(originCoords));
            routeCache.put(cacheKey, zeroResult);
            return zeroResult;
        }

        // 1. Try Live Google Directions API if configured
        if (isLiveApiConfigured()) {
            try {
                GeoApiContext context = getGeoApiContext();
                if (context != null) {
                    TravelMode travelMode = mapTravelMode(mode);
                    DirectionsResult result = DirectionsApi.newRequest(context)
                            .origin(origin)
                            .destination(destination)
                            .mode(travelMode)
                            .await();

                    if (result != null && result.routes != null && result.routes.length > 0 && result.routes[0].legs.length > 0) {
                        long distanceMeters = result.routes[0].legs[0].distance.inMeters;
                        long durationSeconds = result.routes[0].legs[0].duration.inSeconds;
                        double distanceKm = Math.round((distanceMeters / 1000.0) * 10.0) / 10.0;
                        int durationMinutes = (int) Math.round(durationSeconds / 60.0);

                        RouteCalculationResult res = new RouteCalculationResult(
                                distanceKm,
                                durationMinutes,
                                originCoords,
                                destCoords,
                                true,
                                true,
                                null,
                                java.util.Arrays.asList(originCoords, destCoords)
                        );
                        routeCache.put(cacheKey, res);
                        return res;
                    }
                }
            } catch (Exception e) {
                logger.warn("Live Google Directions failed between '{}' and '{}': {}. Falling back to road engine.", origin, destination, e.getMessage());
            }
        }

        // 2. High-precision highway routing engine (OSRM & verified highway routes)
        GeoUtils.RoadRouteResult roadResult = GeoUtils.calculateRealRoadRoute(
                originCoords[0], originCoords[1], destCoords[0], destCoords[1], mode
        );

        RouteCalculationResult res = new RouteCalculationResult(
                roadResult.getDistanceKm(),
                roadResult.getDurationMinutes(),
                originCoords,
                destCoords,
                roadResult.isLiveRoadApiUsed(),
                roadResult.isSuccess(),
                roadResult.isSuccess() ? null : "Unable to calculate the current route. Please try again.",
                roadResult.getGeometryCoordinates()
        );

        routeCache.put(cacheKey, res);
        return res;
    }

    /**
     * Resolves Google Places autocomplete suggestions when live API is available,
     * falling back to the curated geospatial location engine.
     */
    public List<java.util.Map<String, Object>> autocompletePlaces(String query, int limit) {
        if (query == null || query.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }

        int maxResults = Math.max(1, Math.min(10, limit));

        if (isLiveApiConfigured()) {
            try {
                GeoApiContext context = getGeoApiContext();
                if (context != null) {
                    com.google.maps.PlaceAutocompleteRequest.SessionToken sessionToken =
                            new com.google.maps.PlaceAutocompleteRequest.SessionToken();
                    com.google.maps.model.AutocompletePrediction[] predictions =
                            com.google.maps.PlacesApi.placeAutocomplete(context, query.trim(), sessionToken).await();

                    if (predictions != null && predictions.length > 0) {
                        List<java.util.Map<String, Object>> results = new ArrayList<>();
                        for (int i = 0; i < Math.min(predictions.length, maxResults); i++) {
                            com.google.maps.model.AutocompletePrediction p = predictions[i];
                            java.util.Map<String, Object> item = new java.util.HashMap<>();
                            String mainText = (p.structuredFormatting != null && p.structuredFormatting.mainText != null)
                                    ? p.structuredFormatting.mainText : p.description;
                            String secondaryText = (p.structuredFormatting != null && p.structuredFormatting.secondaryText != null)
                                    ? p.structuredFormatting.secondaryText : "Location";

                            item.put("name", mainText);
                            item.put("description", secondaryText);
                            item.put("placeId", p.placeId);
                            item.put("category", (p.types != null && p.types.length > 0) ? p.types[0].replace("_", " ") : "Destination");

                            // Resolve approximate coordinates for map
                            double[] coords = GeoUtils.getApproximateCoordinates(mainText);
                            item.put("lat", coords[0]);
                            item.put("lng", coords[1]);
                            results.add(item);
                        }
                        return results;
                    }
                }
            } catch (Exception e) {
                logger.warn("Live Google Places Autocomplete failed for '{}': {}", query, e.getMessage());
            }
        }

        // Fallback to high-quality internal autocomplete suggestions
        return GeoUtils.getAutocompleteSuggestions(query.trim(), maxResults);
    }

    public void clearRouteCache() {
        routeCache.clear();
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
        private final boolean success;
        private final String errorMessage;
        private final List<double[]> geometryCoords;

        public RouteCalculationResult(double distanceKm, int durationMinutes) {
            this(distanceKm, durationMinutes, new double[]{0.0, 0.0}, new double[]{0.0, 0.0}, false, true, null, java.util.Collections.emptyList());
        }

        public RouteCalculationResult(double distanceKm, int durationMinutes, double[] originCoords, double[] destinationCoords, boolean liveApiUsed) {
            this(distanceKm, durationMinutes, originCoords, destinationCoords, liveApiUsed, true, null, java.util.Collections.emptyList());
        }

        public RouteCalculationResult(double distanceKm, int durationMinutes, double[] originCoords, double[] destinationCoords, boolean liveApiUsed, boolean success, String errorMessage) {
            this(distanceKm, durationMinutes, originCoords, destinationCoords, liveApiUsed, success, errorMessage, java.util.Collections.emptyList());
        }

        public RouteCalculationResult(double distanceKm, int durationMinutes, double[] originCoords, double[] destinationCoords, boolean liveApiUsed, boolean success, String errorMessage, List<double[]> geometryCoords) {
            this.distanceKm = distanceKm;
            this.durationMinutes = durationMinutes;
            this.originCoords = originCoords;
            this.destinationCoords = destinationCoords;
            this.liveApiUsed = liveApiUsed;
            this.success = success;
            this.errorMessage = errorMessage;
            this.geometryCoords = (geometryCoords != null) ? geometryCoords : java.util.Collections.emptyList();
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

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public List<double[]> getGeometryCoords() {
            return geometryCoords;
        }
    }
}
