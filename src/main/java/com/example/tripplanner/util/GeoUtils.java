package com.example.tripplanner.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;

    // Dictionary of well-known coordinates for popular destinations and regional travel
    private static final Map<String, double[]> KNOWN_COORDINATES = new HashMap<>();

    static {
        // Karnataka
        KNOWN_COORDINATES.put("bengaluru", new double[]{12.9716, 77.5946});
        KNOWN_COORDINATES.put("bangalore", new double[]{12.9716, 77.5946});
        KNOWN_COORDINATES.put("mysore", new double[]{12.2958, 76.6394});
        KNOWN_COORDINATES.put("mysuru", new double[]{12.2958, 76.6394});
        KNOWN_COORDINATES.put("coorg", new double[]{12.3375, 75.8069});
        KNOWN_COORDINATES.put("madikeri", new double[]{12.4244, 75.7382});
        KNOWN_COORDINATES.put("chikmagalur", new double[]{13.3161, 75.7720});
        KNOWN_COORDINATES.put("hampi", new double[]{15.3350, 76.4600});
        KNOWN_COORDINATES.put("gokarna", new double[]{14.5479, 74.3188});
        KNOWN_COORDINATES.put("mangalore", new double[]{12.9141, 74.8560});
        KNOWN_COORDINATES.put("kabini", new double[]{11.9427, 76.3533});

        // Kerala
        KNOWN_COORDINATES.put("wayanad", new double[]{11.6854, 76.1320});
        KNOWN_COORDINATES.put("munnar", new double[]{10.0889, 77.0595});
        KNOWN_COORDINATES.put("kochi", new double[]{9.9312, 76.2673});
        KNOWN_COORDINATES.put("alleppey", new double[]{9.4981, 76.3388});
        KNOWN_COORDINATES.put("alappuzha", new double[]{9.4981, 76.3388});
        KNOWN_COORDINATES.put("thekkady", new double[]{9.6031, 77.1615});
        KNOWN_COORDINATES.put("trivandrum", new double[]{8.5241, 76.9366});

        // Tamil Nadu
        KNOWN_COORDINATES.put("ooty", new double[]{11.4102, 76.6950});
        KNOWN_COORDINATES.put("udagamandalam", new double[]{11.4102, 76.6950});
        KNOWN_COORDINATES.put("coonoor", new double[]{11.3530, 76.7959});
        KNOWN_COORDINATES.put("kodaikanal", new double[]{10.2381, 77.4892});
        KNOWN_COORDINATES.put("chennai", new double[]{13.0827, 80.2707});
        KNOWN_COORDINATES.put("madurai", new double[]{9.9252, 78.1198});
        KNOWN_COORDINATES.put("pondicherry", new double[]{11.9416, 79.8083});

        // Other Major Tourist Hubs
        KNOWN_COORDINATES.put("goa", new double[]{15.2993, 74.1240});
        KNOWN_COORDINATES.put("hyderabad", new double[]{17.3850, 78.4867});
        KNOWN_COORDINATES.put("mumbai", new double[]{19.0760, 72.8777});
        KNOWN_COORDINATES.put("delhi", new double[]{28.6139, 77.2090});
        KNOWN_COORDINATES.put("new delhi", new double[]{28.6139, 77.2090});
        KNOWN_COORDINATES.put("agra", new double[]{27.1767, 78.0081});
        KNOWN_COORDINATES.put("jaipur", new double[]{26.9124, 75.7873});
        KNOWN_COORDINATES.put("udaipur", new double[]{24.5854, 73.7125});
        KNOWN_COORDINATES.put("manali", new double[]{32.2432, 77.1892});
        KNOWN_COORDINATES.put("shimla", new double[]{31.1048, 77.1734});
    }

    public static double[] getApproximateCoordinates(String locationName) {
        if (locationName == null || locationName.trim().isEmpty()) {
            return new double[]{12.9716, 77.5946}; // Default Bengaluru
        }

        String cleaned = locationName.toLowerCase().trim();
        for (Map.Entry<String, double[]> entry : KNOWN_COORDINATES.entrySet()) {
            if (cleaned.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Generate deterministic pseudorandom coordinates based on name hash if not in dictionary
        int hash = Math.abs(cleaned.hashCode());
        double pseudoLat = 10.0 + ((hash % 1500) / 100.0);
        double pseudoLng = 74.0 + (((hash / 1000) % 800) / 100.0);
        return new double[]{pseudoLat, pseudoLng};
    }

    public static double calculateHaversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(EARTH_RADIUS_KM * c * 10.0) / 10.0;
    }

    public static double estimateRoadDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double aerial = calculateHaversineDistanceKm(lat1, lon1, lat2, lon2);
        // Realistic road detour coefficient (curving roads, hill roads, highways)
        double windingFactor = (aerial > 50) ? 1.28 : 1.35;
        return Math.round(aerial * windingFactor * 10.0) / 10.0;
    }

    public static int estimateTravelTimeMinutes(double roadDistanceKm, String transportMode) {
        if (roadDistanceKm <= 0.1) {
            return 5;
        }

        double speedKmh;
        int fixedBufferMinutes;

        switch (transportMode != null ? transportMode.toUpperCase() : "CAR") {
            case "BIKE":
                speedKmh = 42.0;
                fixedBufferMinutes = 10;
                break;
            case "WALKING":
                speedKmh = 4.8;
                fixedBufferMinutes = 2;
                break;
            case "PUBLIC_TRANSPORT":
                speedKmh = 36.0;
                fixedBufferMinutes = 25; // waiting and connection buffer
                break;
            case "CAR":
            default:
                speedKmh = (roadDistanceKm > 80) ? 55.0 : 45.0; // highway vs city
                fixedBufferMinutes = 15;
                break;
        }

        int travelMinutes = (int) Math.round((roadDistanceKm / speedKmh) * 60.0);
        return travelMinutes + fixedBufferMinutes;
    }

    public static String buildGoogleMapsUrl(String origin, String destination, String mode) {
        try {
            String travelModeParam = mapTransportModeToGoogle(mode);
            return String.format(
                    "https://www.google.com/maps/dir/?api=1&origin=%s&destination=%s&travelmode=%s",
                    URLEncoder.encode(origin, StandardCharsets.UTF_8),
                    URLEncoder.encode(destination, StandardCharsets.UTF_8),
                    travelModeParam
            );
        } catch (Exception e) {
            return "https://www.google.com/maps";
        }
    }

    public static String buildGoogleMapsMultiWaypointUrl(String origin, String destination, List<String> waypoints, String mode) {
        try {
            String travelModeParam = mapTransportModeToGoogle(mode);
            StringBuilder url = new StringBuilder("https://www.google.com/maps/dir/?api=1");
            url.append("&origin=").append(URLEncoder.encode(origin, StandardCharsets.UTF_8));
            url.append("&destination=").append(URLEncoder.encode(destination, StandardCharsets.UTF_8));

            if (waypoints != null && !waypoints.isEmpty()) {
                String joined = String.join("|", waypoints);
                url.append("&waypoints=").append(URLEncoder.encode(joined, StandardCharsets.UTF_8));
            }

            url.append("&travelmode=").append(travelModeParam);
            return url.toString();
        } catch (Exception e) {
            return "https://www.google.com/maps";
        }
    }

    private static String mapTransportModeToGoogle(String mode) {
        if (mode == null) return "driving";
        switch (mode.toUpperCase()) {
            case "BIKE":
                return "bicycling";
            case "WALKING":
                return "walking";
            case "PUBLIC_TRANSPORT":
                return "transit";
            case "CAR":
            default:
                return "driving";
        }
    }
}
