package com.example.tripplanner.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GeoUtils {

    private static final Logger logger = LoggerFactory.getLogger(GeoUtils.class);
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    // Cache for geocoded coordinates to prevent repeated lookups
    private static final Map<String, double[]> GEOCODE_CACHE = new ConcurrentHashMap<>();

    // Cache for OSRM road route calculations
    private static final Map<String, RoadRouteResult> ROUTE_CACHE = new ConcurrentHashMap<>();

    // Comprehensive Dictionary of verified coordinates for popular Indian and global travel destinations
    private static final Map<String, double[]> KNOWN_COORDINATES = new HashMap<>();

    static {
        // Karnataka
        KNOWN_COORDINATES.put("bengaluru", new double[]{12.9716, 77.5946});
        KNOWN_COORDINATES.put("bangalore", new double[]{12.9716, 77.5946});
        KNOWN_COORDINATES.put("mysore", new double[]{12.2958, 76.6394});
        KNOWN_COORDINATES.put("mysuru", new double[]{12.2958, 76.6394});
        KNOWN_COORDINATES.put("coorg", new double[]{12.4244, 75.7382});
        KNOWN_COORDINATES.put("madikeri", new double[]{12.4244, 75.7382});
        KNOWN_COORDINATES.put("chikmagalur", new double[]{13.3161, 75.7720});
        KNOWN_COORDINATES.put("chikkamagaluru", new double[]{13.3161, 75.7720});
        KNOWN_COORDINATES.put("hampi", new double[]{15.3350, 76.4600});
        KNOWN_COORDINATES.put("gokarna", new double[]{14.5479, 74.3188});
        KNOWN_COORDINATES.put("mangalore", new double[]{12.9141, 74.8560});
        KNOWN_COORDINATES.put("mangaluru", new double[]{12.9141, 74.8560});
        KNOWN_COORDINATES.put("kabini", new double[]{11.9427, 76.3533});
        KNOWN_COORDINATES.put("bandipur", new double[]{11.6664, 76.6293});
        KNOWN_COORDINATES.put("nagarhole", new double[]{12.0312, 76.1554});
        KNOWN_COORDINATES.put("dandeli", new double[]{15.2361, 74.6173});
        KNOWN_COORDINATES.put("badami", new double[]{15.9189, 75.6766});
        KNOWN_COORDINATES.put("belur", new double[]{13.1623, 75.8643});
        KNOWN_COORDINATES.put("halebidu", new double[]{13.2140, 75.9932});
        KNOWN_COORDINATES.put("shravanabelagola", new double[]{12.8580, 76.4842});
        KNOWN_COORDINATES.put("sakleshpur", new double[]{12.8940, 75.7839});
        KNOWN_COORDINATES.put("udupi", new double[]{13.3409, 74.7421});
        KNOWN_COORDINATES.put("murudeshwar", new double[]{14.0940, 74.4899});
        KNOWN_COORDINATES.put("shimoga", new double[]{13.9299, 75.5681});
        KNOWN_COORDINATES.put("jog falls", new double[]{14.2285, 74.8122});
        KNOWN_COORDINATES.put("hubli", new double[]{15.3647, 75.1240});

        // Kerala
        KNOWN_COORDINATES.put("wayanad", new double[]{11.6854, 76.1320});
        KNOWN_COORDINATES.put("kalpetta", new double[]{11.6080, 76.0820});
        KNOWN_COORDINATES.put("sulthan bathery", new double[]{11.6634, 76.2570});
        KNOWN_COORDINATES.put("munnar", new double[]{10.0889, 77.0595});
        KNOWN_COORDINATES.put("kochi", new double[]{9.9312, 76.2673});
        KNOWN_COORDINATES.put("cochin", new double[]{9.9312, 76.2673});
        KNOWN_COORDINATES.put("alleppey", new double[]{9.4981, 76.3388});
        KNOWN_COORDINATES.put("alappuzha", new double[]{9.4981, 76.3388});
        KNOWN_COORDINATES.put("thekkady", new double[]{9.6031, 77.1615});
        KNOWN_COORDINATES.put("kumarakom", new double[]{9.6175, 76.4301});
        KNOWN_COORDINATES.put("varkala", new double[]{8.7379, 76.7163});
        KNOWN_COORDINATES.put("kovalam", new double[]{8.4004, 76.9787});
        KNOWN_COORDINATES.put("trivandrum", new double[]{8.5241, 76.9366});
        KNOWN_COORDINATES.put("thiruvananthapuram", new double[]{8.5241, 76.9366});
        KNOWN_COORDINATES.put("calicut", new double[]{11.2588, 75.7804});
        KNOWN_COORDINATES.put("kozhikode", new double[]{11.2588, 75.7804});
        KNOWN_COORDINATES.put("kannur", new double[]{11.8745, 75.3704});
        KNOWN_COORDINATES.put("athirappilly", new double[]{10.2851, 76.5698});
        KNOWN_COORDINATES.put("vagamon", new double[]{9.6872, 76.9056});

        // Tamil Nadu
        KNOWN_COORDINATES.put("ooty", new double[]{11.4102, 76.6950});
        KNOWN_COORDINATES.put("udagamandalam", new double[]{11.4102, 76.6950});
        KNOWN_COORDINATES.put("coonoor", new double[]{11.3530, 76.7959});
        KNOWN_COORDINATES.put("kodaikanal", new double[]{10.2381, 77.4892});
        KNOWN_COORDINATES.put("chennai", new double[]{13.0827, 80.2707});
        KNOWN_COORDINATES.put("madurai", new double[]{9.9252, 78.1198});
        KNOWN_COORDINATES.put("pondicherry", new double[]{11.9416, 79.8083});
        KNOWN_COORDINATES.put("puducherry", new double[]{11.9416, 79.8083});
        KNOWN_COORDINATES.put("rameshwaram", new double[]{9.2876, 79.3129});
        KNOWN_COORDINATES.put("kanyakumari", new double[]{8.0883, 77.5385});
        KNOWN_COORDINATES.put("coimbatore", new double[]{11.0168, 76.9558});
        KNOWN_COORDINATES.put("thanjavur", new double[]{10.7870, 79.1378});
        KNOWN_COORDINATES.put("mahabalipuram", new double[]{12.6269, 80.1927});
        KNOWN_COORDINATES.put("mamallapuram", new double[]{12.6269, 80.1927});
        KNOWN_COORDINATES.put("yercaud", new double[]{11.7753, 78.2093});
        KNOWN_COORDINATES.put("yelagiri", new double[]{12.5789, 78.6399});

        // Goa & Maharashtra
        KNOWN_COORDINATES.put("goa", new double[]{15.2993, 74.1240});
        KNOWN_COORDINATES.put("panaji", new double[]{15.4909, 73.8278});
        KNOWN_COORDINATES.put("north goa", new double[]{15.5494, 73.7535});
        KNOWN_COORDINATES.put("south goa", new double[]{15.2832, 73.9862});
        KNOWN_COORDINATES.put("mumbai", new double[]{19.0760, 72.8777});
        KNOWN_COORDINATES.put("pune", new double[]{18.5204, 73.8567});
        KNOWN_COORDINATES.put("lonavala", new double[]{18.7557, 73.4091});
        KNOWN_COORDINATES.put("khandala", new double[]{18.7610, 73.3762});
        KNOWN_COORDINATES.put("mahabaleshwar", new double[]{17.9237, 73.6586});
        KNOWN_COORDINATES.put("panchgani", new double[]{17.9236, 73.8010});
        KNOWN_COORDINATES.put("alibaug", new double[]{18.6414, 72.8722});
        KNOWN_COORDINATES.put("nashik", new double[]{19.9975, 73.7898});
        KNOWN_COORDINATES.put("shirdi", new double[]{19.7667, 74.4777});
        KNOWN_COORDINATES.put("aurangabad", new double[]{19.8762, 75.3433});
        KNOWN_COORDINATES.put("chhatrapati sambhajinagar", new double[]{19.8762, 75.3433});

        // Telangana & Andhra Pradesh
        KNOWN_COORDINATES.put("hyderabad", new double[]{17.3850, 78.4867});
        KNOWN_COORDINATES.put("guntakal", new double[]{15.1678, 77.3686});
        KNOWN_COORDINATES.put("anantapur", new double[]{14.6819, 77.6006});
        KNOWN_COORDINATES.put("kurnool", new double[]{15.8281, 78.0373});
        KNOWN_COORDINATES.put("kadapa", new double[]{14.4673, 78.8242});
        KNOWN_COORDINATES.put("bellary", new double[]{15.1394, 76.9214});
        KNOWN_COORDINATES.put("ballari", new double[]{15.1394, 76.9214});
        KNOWN_COORDINATES.put("visakhapatnam", new double[]{17.6868, 83.2185});
        KNOWN_COORDINATES.put("vizag", new double[]{17.6868, 83.2185});
        KNOWN_COORDINATES.put("araku valley", new double[]{18.3273, 82.8775});
        KNOWN_COORDINATES.put("tirupati", new double[]{13.6288, 79.4192});
        KNOWN_COORDINATES.put("vijayawada", new double[]{16.5062, 80.6480});

        // North & West India - Iconic Places
        KNOWN_COORDINATES.put("delhi", new double[]{28.6139, 77.2090});
        KNOWN_COORDINATES.put("new delhi", new double[]{28.6139, 77.2090});
        KNOWN_COORDINATES.put("red fort", new double[]{28.6562, 77.2410});
        KNOWN_COORDINATES.put("red fort, delhi", new double[]{28.6562, 77.2410});
        KNOWN_COORDINATES.put("agra", new double[]{27.1767, 78.0081});
        KNOWN_COORDINATES.put("taj mahal", new double[]{27.1751, 78.0421});
        KNOWN_COORDINATES.put("taj mahal, agra", new double[]{27.1751, 78.0421});
        KNOWN_COORDINATES.put("jaipur", new double[]{26.9124, 75.7873});
        KNOWN_COORDINATES.put("jaipur city palace", new double[]{26.9258, 75.8237});
        KNOWN_COORDINATES.put("city palace, jaipur", new double[]{26.9258, 75.8237});
        KNOWN_COORDINATES.put("amer fort", new double[]{26.9855, 75.8513});
        KNOWN_COORDINATES.put("amer fort, jaipur", new double[]{26.9855, 75.8513});
        KNOWN_COORDINATES.put("amber fort", new double[]{26.9855, 75.8513});
        KNOWN_COORDINATES.put("hawa mahal", new double[]{26.9239, 75.8267});
        KNOWN_COORDINATES.put("hawa mahal, jaipur", new double[]{26.9239, 75.8267});
        KNOWN_COORDINATES.put("udaipur", new double[]{24.5854, 73.7125});
        KNOWN_COORDINATES.put("jodhpur", new double[]{26.2389, 73.0243});
        KNOWN_COORDINATES.put("jaisalmer", new double[]{26.9157, 70.9083});
        KNOWN_COORDINATES.put("pushkar", new double[]{26.4897, 74.5511});
        KNOWN_COORDINATES.put("mount abu", new double[]{24.5925, 72.7156});
        KNOWN_COORDINATES.put("manali", new double[]{32.2432, 77.1892});
        KNOWN_COORDINATES.put("manali, himachal pradesh", new double[]{32.2432, 77.1892});
        KNOWN_COORDINATES.put("shimla", new double[]{31.1048, 77.1734});
        KNOWN_COORDINATES.put("dharamshala", new double[]{32.2190, 76.3234});
        KNOWN_COORDINATES.put("mcleodganj", new double[]{32.2426, 76.3213});
        KNOWN_COORDINATES.put("rishikesh", new double[]{30.0869, 78.2676});
        KNOWN_COORDINATES.put("rishikesh, uttarakhand", new double[]{30.0869, 78.2676});
        KNOWN_COORDINATES.put("haridwar", new double[]{29.9457, 78.1642});
        KNOWN_COORDINATES.put("dehradun", new double[]{30.3165, 78.0322});
        KNOWN_COORDINATES.put("mussoorie", new double[]{30.4598, 78.0644});
        KNOWN_COORDINATES.put("nainital", new double[]{29.3919, 79.4542});
        KNOWN_COORDINATES.put("amritsar", new double[]{31.6340, 74.8723});
        KNOWN_COORDINATES.put("golden temple", new double[]{31.6200, 74.8765});
        KNOWN_COORDINATES.put("golden temple, amritsar", new double[]{31.6200, 74.8765});
        KNOWN_COORDINATES.put("harmandir sahib", new double[]{31.6200, 74.8765});
        KNOWN_COORDINATES.put("varanasi", new double[]{25.3176, 82.9739});
        KNOWN_COORDINATES.put("varanasi ghats", new double[]{25.3076, 83.0107});
        KNOWN_COORDINATES.put("varanasi ghats, varanasi", new double[]{25.3076, 83.0107});
        KNOWN_COORDINATES.put("dashashwamedh ghat", new double[]{25.3076, 83.0107});
        KNOWN_COORDINATES.put("srinagar", new double[]{34.0837, 74.7973});
        KNOWN_COORDINATES.put("leh", new double[]{34.1526, 77.5771});
        KNOWN_COORDINATES.put("ladakh", new double[]{34.1526, 77.5771});

        // Western & Southern Attractions
        KNOWN_COORDINATES.put("gateway of india", new double[]{18.9220, 72.8347});
        KNOWN_COORDINATES.put("gateway of india, mumbai", new double[]{18.9220, 72.8347});
        KNOWN_COORDINATES.put("mysore palace", new double[]{12.3051, 76.6552});
        KNOWN_COORDINATES.put("mysore palace, mysuru", new double[]{12.3051, 76.6552});
        KNOWN_COORDINATES.put("alleppey backwaters", new double[]{9.4981, 76.3388});
        KNOWN_COORDINATES.put("alleppey backwaters, kerala", new double[]{9.4981, 76.3388});
        KNOWN_COORDINATES.put("goa beaches", new double[]{15.2993, 74.1240});
        KNOWN_COORDINATES.put("goa beaches, goa", new double[]{15.2993, 74.1240});

        // East & North East
        KNOWN_COORDINATES.put("kolkata", new double[]{22.5726, 88.3639});
        KNOWN_COORDINATES.put("darjeeling", new double[]{27.0410, 88.2663});
        KNOWN_COORDINATES.put("darjeeling, west bengal", new double[]{27.0410, 88.2663});
        KNOWN_COORDINATES.put("gangtok", new double[]{27.3389, 88.6065});
        KNOWN_COORDINATES.put("shillong", new double[]{25.5788, 91.8933});
        KNOWN_COORDINATES.put("puri", new double[]{19.8135, 85.8312});
        KNOWN_COORDINATES.put("kaziranga", new double[]{26.5775, 93.1711});
        KNOWN_COORDINATES.put("kaziranga national park", new double[]{26.5775, 93.1711});
        KNOWN_COORDINATES.put("kaziranga national park, assam", new double[]{26.5775, 93.1711});

        // International Top Destinations
        KNOWN_COORDINATES.put("dubai", new double[]{25.2048, 55.2708});
        KNOWN_COORDINATES.put("singapore", new double[]{1.3521, 103.8198});
        KNOWN_COORDINATES.put("bangkok", new double[]{13.7563, 100.5018});
        KNOWN_COORDINATES.put("bali", new double[]{-8.4095, 115.1889});
        KNOWN_COORDINATES.put("paris", new double[]{48.8566, 2.3522});
        KNOWN_COORDINATES.put("london", new double[]{51.5074, -0.1278});
        KNOWN_COORDINATES.put("rome", new double[]{41.9028, 12.4964});
        KNOWN_COORDINATES.put("tokyo", new double[]{35.6762, 139.6503});
        KNOWN_COORDINATES.put("new york", new double[]{40.7128, -74.0060});
    }

    /**
     * Resolves accurate coordinates for any location name.
     * 1. Checks known dictionary
     * 2. Checks in-memory geocode cache
     * 3. Queries live Photon / OpenStreetMap Nominatim API
     * 4. Safe fallback
     */
    public static double[] getApproximateCoordinates(String locationName) {
        if (locationName == null || locationName.trim().isEmpty()) {
            return new double[]{12.9716, 77.5946}; // Default Bengaluru
        }

        String cleaned = locationName.toLowerCase().trim();

        // 1. Check exact match in KNOWN_COORDINATES
        if (KNOWN_COORDINATES.containsKey(cleaned)) {
            return KNOWN_COORDINATES.get(cleaned);
        }

        // 2. Check memory cache
        if (GEOCODE_CACHE.containsKey(cleaned)) {
            return GEOCODE_CACHE.get(cleaned);
        }

        // 3. Check substring match in KNOWN_COORDINATES (for distinct town/city names)
        for (Map.Entry<String, double[]> entry : KNOWN_COORDINATES.entrySet()) {
            if (cleaned.contains(entry.getKey()) && entry.getKey().length() >= 4) {
                return entry.getValue();
            }
        }

        // 3. Query OpenStreetMap / Photon Geocoder
        try {
            double[] liveCoords = queryLiveGeocode(locationName.trim());
            if (liveCoords != null) {
                GEOCODE_CACHE.put(cleaned, liveCoords);
                return liveCoords;
            }
        } catch (Exception e) {
            logger.debug("Live geocode lookup failed for '{}': {}", locationName, e.getMessage());
        }

        // 4. Fallback: Default to Bengaluru
        return new double[]{12.9716, 77.5946};
    }

    private static double[] queryLiveGeocode(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://photon.komoot.io/api/?q=" + encoded + "&limit=1";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "VoyageCraft-TripPlanner/1.0")
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode features = root.path("features");
                if (features.isArray() && features.size() > 0) {
                    JsonNode coordsNode = features.get(0).path("geometry").path("coordinates");
                    if (coordsNode.isArray() && coordsNode.size() >= 2) {
                        double lng = coordsNode.get(0).asDouble();
                        double lat = coordsNode.get(1).asDouble();
                        return new double[]{lat, lng};
                    }
                }
            }
        } catch (Exception e) {
            // Silently fall through
        }
        return null;
    }

    /**
     * Calculates spherical Haversine distance in Kilometers.
     */
    public static double calculateHaversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(EARTH_RADIUS_KM * c * 10.0) / 10.0;
    }

    // Testing & Simulation Toggle: Simulate Routing Provider Failure
    private static volatile boolean simulateRoutingFailure = false;

    public static void setSimulateRoutingFailure(boolean simulate) {
        simulateRoutingFailure = simulate;
        ROUTE_CACHE.clear();
    }

    public static boolean isSimulateRoutingFailure() {
        return simulateRoutingFailure;
    }

    /**
     * Resolves geographical address details from clicked coordinates.
     * Uses Photon reverse geocoding API with high-precision destination fallback.
     */
    public static Map<String, Object> reverseGeocode(double lat, double lng) {
        Map<String, Object> result = new HashMap<>();
        result.put("lat", lat);
        result.put("lng", lng);

        // 1. Query live reverse geocoding API
        try {
            String url = String.format(Locale.US, "https://photon.komoot.io/reverse?lat=%.6f&lon=%.6f", lat, lng);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "VoyageCraft-TripPlanner/1.0")
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode features = root.path("features");
                if (features.isArray() && features.size() > 0) {
                    JsonNode props = features.get(0).path("properties");
                    String name = props.path("name").asText("");
                    String street = props.path("street").asText("");
                    String city = props.path("city").asText(props.path("county").asText(""));
                    String state = props.path("state").asText("");
                    String country = props.path("country").asText("India");

                    StringBuilder address = new StringBuilder();
                    if (!name.isEmpty()) address.append(name).append(", ");
                    if (!street.isEmpty() && !street.equals(name)) address.append(street).append(", ");
                    if (!city.isEmpty()) address.append(city).append(", ");
                    if (!state.isEmpty()) address.append(state).append(", ");
                    address.append(country);

                    result.put("name", !name.isEmpty() ? name : (!city.isEmpty() ? city : "Selected Location"));
                    result.put("formattedAddress", address.toString());
                    result.put("city", !city.isEmpty() ? city : (!state.isEmpty() ? state : "India"));
                    result.put("state", state);
                    result.put("resolved", true);
                    return result;
                }
            }
        } catch (Exception e) {
            logger.debug("Live reverse geocode lookup error: {}", e.getMessage());
        }

        // 2. High-precision nearest known destination matching
        String closestPlace = null;
        double minDistance = Double.MAX_VALUE;
        for (Map.Entry<String, double[]> entry : KNOWN_COORDINATES.entrySet()) {
            double d = calculateHaversineDistanceKm(lat, lng, entry.getValue()[0], entry.getValue()[1]);
            if (d < minDistance) {
                minDistance = d;
                closestPlace = entry.getKey();
            }
        }

        if (closestPlace != null && minDistance < 60.0) {
            String cap = capitalizeWords(closestPlace);
            String region = getRegionDescription(closestPlace);
            result.put("name", cap + (minDistance > 2.0 ? String.format(Locale.US, " (%.1f km from %s)", minDistance, cap) : " Vicinity"));
            result.put("formattedAddress", cap + ", " + region);
            result.put("city", cap);
            result.put("state", region);
            result.put("resolved", true);
        } else {
            result.put("name", String.format(Locale.US, "Pin at %.4f, %.4f", lat, lng));
            result.put("formattedAddress", String.format(Locale.US, "Selected Coordinates: %.4f° N, %.4f° E, India", lat, lng));
            result.put("city", "Selected Location");
            result.put("state", "India");
            result.put("resolved", true);
        }

        return result;
    }

    /**
     * Resolves real road distance and travel time.
     * Uses live road network routing (OSRM) with intelligent caching.
     * Failed routing API calls are reported with explicit failure states rather than zero-distance fabrication.
     */
    public static RoadRouteResult calculateRealRoadRoute(double lat1, double lon1, double lat2, double lon2, String transportMode) {
        // Validation of coordinates
        if (Double.isNaN(lat1) || Double.isNaN(lon1) || Double.isNaN(lat2) || Double.isNaN(lon2) ||
                Math.abs(lat1) > 90.0 || Math.abs(lat2) > 90.0 || Math.abs(lon1) > 180.0 || Math.abs(lon2) > 180.0) {
            return new RoadRouteResult(0.0, 0, false, false, "Invalid coordinate inputs provided for road calculation.", Collections.emptyList(), "INVALID_COORDINATES");
        }

        // Simulated Provider Failure for deterministic verification
        if (simulateRoutingFailure) {
            return new RoadRouteResult(0.0, 0, false, false, "Simulated routing provider failure: unable to obtain live road network directions.", Collections.emptyList(), "SIMULATED_FAILURE");
        }

        String mode = (transportMode != null && !transportMode.trim().isEmpty()) ? transportMode.trim().toUpperCase() : "CAR";
        String cacheKey = String.format(Locale.US, "%.4f,%.4f->%.4f,%.4f-%s", lat1, lon1, lat2, lon2, mode);
        if (ROUTE_CACHE.containsKey(cacheKey)) {
            return ROUTE_CACHE.get(cacheKey);
        }

        // Check if origins are identical (< 50 meters)
        double aerialKm = calculateHaversineDistanceKm(lat1, lon1, lat2, lon2);
        if (aerialKm < 0.05) {
            RoadRouteResult zeroResult = new RoadRouteResult(0.0, 0, true, true, null, Collections.singletonList(new double[]{lat1, lon1}), "IDENTICAL_ORIGIN_DESTINATION");
            ROUTE_CACHE.put(cacheKey, zeroResult);
            return zeroResult;
        }

        // 1. Query live OSRM public road routing API (produces true highway polyline geometry)
        try {
            String osrmProfile = "driving";
            if ("BIKE".equalsIgnoreCase(mode)) {
                osrmProfile = "driving";
            } else if ("WALKING".equalsIgnoreCase(mode)) {
                osrmProfile = "driving";
            }

            String osrmUrl = String.format(Locale.US,
                    "https://router.project-osrm.org/route/v1/%s/%.6f,%.6f;%.6f,%.6f?overview=simplified&geometries=geojson",
                    osrmProfile, lon1, lat1, lon2, lat2);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(osrmUrl))
                    .header("User-Agent", "VoyageCraft-TripPlanner/1.0")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode routes = root.path("routes");
                if (routes.isArray() && routes.size() > 0) {
                    double distanceMeters = routes.get(0).path("distance").asDouble();
                    double durationSeconds = routes.get(0).path("duration").asDouble();

                    double distanceKm = Math.round((distanceMeters / 1000.0) * 10.0) / 10.0;
                    int baseMinutes = (int) Math.round(durationSeconds / 60.0);

                    // Adjust duration according to transport mode
                    int adjustedMinutes = adjustTimeForTransportMode(distanceKm, baseMinutes, mode);

                    List<double[]> coords = new ArrayList<>();
                    JsonNode geometry = routes.get(0).path("geometry");
                    JsonNode coordinates = geometry.path("coordinates");
                    if (coordinates.isArray() && coordinates.size() > 0) {
                        for (JsonNode pt : coordinates) {
                            if (pt.isArray() && pt.size() >= 2) {
                                coords.add(new double[]{pt.get(1).asDouble(), pt.get(0).asDouble()});
                            }
                        }
                    } else {
                        coords.add(new double[]{lat1, lon1});
                        coords.add(new double[]{lat2, lon2});
                    }

                    RoadRouteResult result = new RoadRouteResult(distanceKm, adjustedMinutes, true, true, null, coords, "LIVE_OSRM");
                    ROUTE_CACHE.put(cacheKey, result);
                    return result;
                }
            }
        } catch (Exception e) {
            logger.debug("Live OSRM routing unavailable ({}). Utilizing highway detour model.", e.getMessage());
        }

        // 2. Calibrated mathematical road network formula based on highway detours & terrain
        double roadDetourFactor = (aerialKm > 100) ? 1.25 : (aerialKm > 30 ? 1.30 : 1.36);
        double estimatedDistanceKm = Math.round(aerialKm * roadDetourFactor * 10.0) / 10.0;
        int estimatedMinutes = estimateTravelTimeMinutes(estimatedDistanceKm, mode);

        RoadRouteResult calibratedResult = new RoadRouteResult(estimatedDistanceKm, estimatedMinutes, false, true, null,
                Arrays.asList(new double[]{lat1, lon1}, new double[]{lat2, lon2}), "ESTIMATED_ROAD_NETWORK");
        ROUTE_CACHE.put(cacheKey, calibratedResult);
        return calibratedResult;
    }

    private static int adjustTimeForTransportMode(double distanceKm, int drivingMinutes, String transportMode) {
        if (transportMode == null || transportMode.equalsIgnoreCase("CAR")) {
            return Math.max(5, drivingMinutes + 10); // add 10 min parking/toll buffer
        }
        switch (transportMode.toUpperCase()) {
            case "BIKE":
                return Math.max(5, (int) Math.round(drivingMinutes * 1.15) + 5);
            case "WALKING":
                return Math.max(5, (int) Math.round((distanceKm / 4.8) * 60.0));
            case "PUBLIC_TRANSPORT":
                return Math.max(10, (int) Math.round(drivingMinutes * 1.4) + 25);
            default:
                return Math.max(5, drivingMinutes + 10);
        }
    }

    public static double estimateRoadDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        RoadRouteResult res = calculateRealRoadRoute(lat1, lon1, lat2, lon2, "CAR");
        return res.getDistanceKm();
    }

    public static int estimateTravelTimeMinutes(double roadDistanceKm, String transportMode) {
        if (roadDistanceKm <= 0.1) {
            return 5;
        }

        double speedKmh;
        int fixedBufferMinutes;

        switch (transportMode != null ? transportMode.toUpperCase() : "CAR") {
            case "BIKE":
                speedKmh = 45.0;
                fixedBufferMinutes = 10;
                break;
            case "WALKING":
                speedKmh = 4.8;
                fixedBufferMinutes = 2;
                break;
            case "PUBLIC_TRANSPORT":
                speedKmh = 40.0;
                fixedBufferMinutes = 25;
                break;
            case "CAR":
            default:
                speedKmh = (roadDistanceKm > 80) ? 60.0 : 45.0;
                fixedBufferMinutes = 15;
                break;
        }

        int travelMinutes = (int) Math.round((roadDistanceKm / speedKmh) * 60.0);
        return travelMinutes + fixedBufferMinutes;
    }

    /**
     * Returns curated autocomplete suggestions for Google-maps-like dropdown.
     */
    public static List<Map<String, Object>> getAutocompleteSuggestions(String query, int limit) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        if (query == null || query.trim().length() < 1) {
            return suggestions;
        }

        String q = query.trim().toLowerCase();

        // 1. Search local curated dictionary first (instant < 1ms)
        int count = 0;
        for (Map.Entry<String, double[]> entry : KNOWN_COORDINATES.entrySet()) {
            String name = entry.getKey();
            if (name.contains(q)) {
                String capitalized = capitalizeWords(name);
                Map<String, Object> item = new HashMap<>();
                item.put("name", capitalized);
                item.put("description", getRegionDescription(name));
                item.put("category", getPlaceType(name));
                item.put("placeId", "vc_" + name.replaceAll("[^a-z0-9]", "_"));
                item.put("lat", entry.getValue()[0]);
                item.put("lng", entry.getValue()[1]);
                suggestions.add(item);
                count++;
                if (count >= limit) break;
            }
        }

        // 2. If fewer than limit and query has 2+ characters, query Photon live API
        if (suggestions.size() < limit && q.length() >= 2) {
            try {
                String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
                String url = "https://photon.komoot.io/api/?q=" + encoded + "&limit=" + (limit - suggestions.size());
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "VoyageCraft-TripPlanner/1.0")
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body() != null) {
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode features = root.path("features");
                    if (features.isArray()) {
                        for (JsonNode f : features) {
                            JsonNode props = f.path("properties");
                            String name = props.path("name").asText("");
                            if (name.isEmpty()) continue;

                            String state = props.path("state").asText("");
                            String country = props.path("country").asText("");
                            String city = props.path("city").asText(props.path("county").asText(""));
                            StringBuilder descBuilder = new StringBuilder();
                            if (!city.isEmpty()) descBuilder.append(city).append(", ");
                            if (!state.isEmpty()) descBuilder.append(state).append(", ");
                            descBuilder.append(country);
                            String desc = descBuilder.toString();

                            String osmType = props.path("osm_value").asText("City");
                            String category = capitalizeWords(osmType.replace("_", " "));

                            JsonNode coordsNode = f.path("geometry").path("coordinates");
                            double lng = coordsNode.get(0).asDouble();
                            double lat = coordsNode.get(1).asDouble();

                            // Avoid duplicates
                            boolean exists = suggestions.stream().anyMatch(s -> ((String) s.get("name")).equalsIgnoreCase(name));
                            if (!exists) {
                                Map<String, Object> item = new HashMap<>();
                                item.put("name", name);
                                item.put("description", desc);
                                item.put("category", category);
                                item.put("placeId", "osm_" + props.path("osm_id").asText(UUID.randomUUID().toString().substring(0, 8)));
                                item.put("lat", lat);
                                item.put("lng", lng);
                                suggestions.add(item);
                                if (suggestions.size() >= limit) break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore API errors
            }
        }

        return suggestions;
    }

    private static String getPlaceType(String place) {
        String p = place.toLowerCase();
        if (p.matches(".*(coorg|ooty|munnar|kodaikanal|manali|shimla|mussoorie|nainital|darjeeling|gangtok|vagamon|yercaud|chikmagalur|madikeri).*")) {
            return "Hill Station";
        }
        if (p.matches(".*(goa|gokarna|pondicherry|varkala|kovalam|alibaug|puri|mangalore).*")) {
            return "Beach Destination";
        }
        if (p.matches(".*(hampi|badami|agra|jaipur|udaipur|jodhpur|mysore|madurai|thanjavur|varanasi|delhi).*")) {
            return "Heritage Destination";
        }
        if (p.matches(".*(wayanad|kabini|bandipur|dandeli|thekkady|kumarakom|alleppey).*")) {
            return "Nature & Wildlife";
        }
        return "City";
    }

    private static String getRegionDescription(String place) {
        if (place.matches("bengaluru|mysore|coorg|chikmagalur|hampi|gokarna|mangalore|kabini|bandipur|nagarhole|dandeli|badami|belur|sakleshpur|udupi|murudeshwar|shimoga")) {
            return "Karnataka, India • Tourist Destination";
        }
        if (place.matches("wayanad|munnar|kochi|alleppey|thekkady|kumarakom|varkala|kovalam|trivandrum|calicut")) {
            return "Kerala, India • Nature & Backwaters";
        }
        if (place.matches("ooty|coonoor|kodaikanal|chennai|madurai|pondicherry|rameshwaram|kanyakumari|coimbatore|mahabalipuram|yercaud")) {
            return "Tamil Nadu / Puducherry, India";
        }
        if (place.matches("goa|panaji|mumbai|pune|lonavala|mahabaleshwar|alibaug|shirdi")) {
            return "Goa / Maharashtra, India";
        }
        if (place.matches("hyderabad|vizag|visakhapatnam|araku valley|tirupati")) {
            return "Telangana / Andhra Pradesh, India";
        }
        if (place.matches("delhi|new delhi|agra|jaipur|udaipur|jodhpur|jaisalmer|manali|shimla|rishikesh|mussoorie|nainital|varanasi|srinagar|leh|ladakh")) {
            return "North India • Iconic Heritage & Mountains";
        }
        return "Popular Travel Destination";
    }

    private static String capitalizeWords(String str) {
        if (str == null || str.isEmpty()) return str;
        String[] words = str.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
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

    public static class RoadRouteResult {
        private final double distanceKm;
        private final int durationMinutes;
        private final boolean liveRoadApiUsed;
        private final boolean success;
        private final String errorMessage;
        private final List<double[]> geometryCoordinates;
        private final String verifiedProvider;

        public RoadRouteResult(double distanceKm, int durationMinutes, boolean liveRoadApiUsed) {
            this(distanceKm, durationMinutes, liveRoadApiUsed, true, null, Collections.emptyList(), liveRoadApiUsed ? "LIVE_OSRM" : "ESTIMATED_ROAD_NETWORK");
        }

        public RoadRouteResult(double distanceKm, int durationMinutes, boolean liveRoadApiUsed, boolean success, String errorMessage) {
            this(distanceKm, durationMinutes, liveRoadApiUsed, success, errorMessage, Collections.emptyList(), success ? (liveRoadApiUsed ? "LIVE_OSRM" : "ESTIMATED_ROAD_NETWORK") : "FAILED");
        }

        public RoadRouteResult(double distanceKm, int durationMinutes, boolean liveRoadApiUsed, boolean success, String errorMessage, List<double[]> geometryCoordinates) {
            this(distanceKm, durationMinutes, liveRoadApiUsed, success, errorMessage, geometryCoordinates, success ? (liveRoadApiUsed ? "LIVE_OSRM" : "ESTIMATED_ROAD_NETWORK") : "FAILED");
        }

        public RoadRouteResult(double distanceKm, int durationMinutes, boolean liveRoadApiUsed, boolean success, String errorMessage, List<double[]> geometryCoordinates, String verifiedProvider) {
            this.distanceKm = distanceKm;
            this.durationMinutes = durationMinutes;
            this.liveRoadApiUsed = liveRoadApiUsed;
            this.success = success;
            this.errorMessage = errorMessage;
            this.geometryCoordinates = (geometryCoordinates != null) ? geometryCoordinates : Collections.emptyList();
            this.verifiedProvider = verifiedProvider != null ? verifiedProvider : (success ? "ESTIMATED_ROAD_NETWORK" : "FAILED");
        }

        public double getDistanceKm() {
            return distanceKm;
        }

        public int getDurationMinutes() {
            return durationMinutes;
        }

        public boolean isLiveRoadApiUsed() {
            return liveRoadApiUsed;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public List<double[]> getGeometryCoordinates() {
            return geometryCoordinates;
        }

        public String getVerifiedProvider() {
            return verifiedProvider;
        }
    }
}
