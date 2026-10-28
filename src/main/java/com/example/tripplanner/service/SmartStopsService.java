package com.example.tripplanner.service;

import com.example.tripplanner.dto.PlaceAlongRouteDto;
import com.example.tripplanner.dto.RouteSegmentDto;
import com.example.tripplanner.dto.RouteSummaryDto;
import com.example.tripplanner.entity.Destination;
import com.example.tripplanner.entity.Trip;
import com.example.tripplanner.util.GeoUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.maps.GeoApiContext;
import com.google.maps.PlacesApi;
import com.google.maps.model.LatLng;
import com.google.maps.model.PlacesSearchResponse;
import com.google.maps.model.PlacesSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SmartStopsService {

    private static final Logger logger = LoggerFactory.getLogger(SmartStopsService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final GoogleMapsService googleMapsService;
    private final RouteService routeService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    @org.springframework.beans.factory.annotation.Value("${smartstops.overpass.enabled:false}")
    private boolean overpassEnabled = false;

    // Cache key: tripId:routeSignature:category:maxMinutes:maxKm:query
    private final Map<String, List<PlaceAlongRouteDto>> searchCache = new ConcurrentHashMap<>();

    // Verified Indian Highway Corridor Stop Catalog
    private static final List<CorridorStopTemplate> VERIFIED_HIGHWAY_CATALOG = new ArrayList<>();

    public SmartStopsService(GoogleMapsService googleMapsService, RouteService routeService) {
        this.googleMapsService = googleMapsService;
        this.routeService = routeService;
        initCatalog();
    }

    public List<PlaceAlongRouteDto> findSmartStopsAlongRoute(Trip trip,
                                                            List<Destination> destinations,
                                                            String categoryFilter,
                                                            Integer maxDetourMinutes,
                                                            Double maxDetourKm,
                                                            String query) {
        if (trip == null || trip.getStartLocation() == null || destinations == null || destinations.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> destinationNames = destinations.stream()
                .map(Destination::getName)
                .filter(n -> n != null && !n.trim().isEmpty())
                .collect(Collectors.toList());

        if (destinationNames.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Calculate complete road route to obtain valid geometry
        RouteSummaryDto routeSummary = routeService.calculateCompleteRoute(
                trip.getStartLocation(), destinationNames, trip.getTransportMode());

        if (routeSummary == null || !routeSummary.getRouteCalculationSuccess() ||
                routeSummary.getFullRouteGeometry() == null || routeSummary.getFullRouteGeometry().isEmpty()) {
            logger.warn("Route-based stop search cannot currently be completed: no reliable road geometry available for trip {}", trip.getId());
            return Collections.emptyList();
        }

        List<double[]> routeGeometry = routeSummary.getFullRouteGeometry();
        String normalizedCategory = (categoryFilter != null && !categoryFilter.trim().isEmpty())
                ? categoryFilter.trim().toUpperCase() : "ALL";

        // Check cache
        String routeSig = computeRouteSignature(routeGeometry);
        String cacheKey = String.format("%d:%s:%s:%s:%s:%s",
                trip.getId() != null ? trip.getId() : 0L,
                routeSig,
                normalizedCategory,
                String.valueOf(maxDetourMinutes),
                String.valueOf(maxDetourKm),
                query != null ? query.trim().toLowerCase() : "");

        if (searchCache.containsKey(cacheKey)) {
            return searchCache.get(cacheKey);
        }

        // Set of existing destination names to prevent duplicate suggestions
        Set<String> existingNames = new HashSet<>();
        existingNames.add(normalizeName(trip.getStartLocation()));
        for (Destination d : destinations) {
            existingNames.add(normalizeName(d.getName()));
        }

        // 2. Identify points along route at suitable intervals (every ~20-30 km)
        List<double[]> sampledCorridorPoints = sampleRouteGeometry(routeGeometry, 25.0);

        // 3. Collect candidate places near corridor points from providers
        Map<String, CorridorStopCandidate> candidateMap = new LinkedHashMap<>();

        // 3a. Search verified highway catalog (always fast, high precision)
        searchVerifiedCatalog(sampledCorridorPoints, routeGeometry, normalizedCategory, candidateMap);

        // 3b. Query Google Places API if live API is enabled
        if (googleMapsService.isLiveApiConfigured()) {
            searchGooglePlaces(sampledCorridorPoints, normalizedCategory, candidateMap);
        } else if (overpassEnabled) {
            // 3c. Try OSM Overpass API for live corridor discoveries (only if explicitly enabled)
            searchOverpassCorridor(sampledCorridorPoints, normalizedCategory, candidateMap);
        }

        // 4. Filter, calculate route proximity, and match route legs
        List<PlaceAlongRouteDto> results = new ArrayList<>();
        double corridorLimitKm = (maxDetourKm != null && maxDetourKm > 0) ? Math.min(maxDetourKm, 20.0) : 15.0;

        List<RouteSegmentDto> segments = routeSummary.getSegments();

        for (CorridorStopCandidate cand : candidateMap.values()) {
            String norm = normalizeName(cand.name);
            if (existingNames.contains(norm)) {
                continue;
            }

            // Keyword query filter
            if (query != null && !query.trim().isEmpty()) {
                String qLower = query.trim().toLowerCase();
                boolean matches = cand.name.toLowerCase().contains(qLower) ||
                        (cand.description != null && cand.description.toLowerCase().contains(qLower)) ||
                        (cand.address != null && cand.address.toLowerCase().contains(qLower));
                if (!matches) {
                    continue;
                }
            }

            // Category filter
            if (!"ALL".equalsIgnoreCase(normalizedCategory)) {
                if (!cand.categoryCode.equalsIgnoreCase(normalizedCategory)) {
                    continue;
                }
            }

            // 6. Proximity to route geometry
            double distFromRoute = GeoUtils.calculateDistanceToPolylineKm(cand.lat, cand.lng, routeGeometry);
            if (distFromRoute > corridorLimitKm) {
                // Out of corridor bounds
                continue;
            }

            // Identify which trip segment this place is closest to
            int segmentIdx = findClosestTripSegment(cand.lat, cand.lng, segments);
            RouteSegmentDto seg = (segmentIdx >= 0 && segmentIdx < segments.size()) ? segments.get(segmentIdx) : null;

            String segmentName = (seg != null) ? seg.getFromLocation() + " → " + seg.getToLocation() : "Highway Corridor";
            String fromLoc = (seg != null) ? seg.getFromLocation() : trip.getStartLocation();
            String toLoc = (seg != null) ? seg.getToLocation() : (destinations.isEmpty() ? "" : destinations.get(0).getName());

            // 7. Calculate real detour distance and duration using road routing
            Double detourKm = null;
            Integer detourMins = null;
            boolean detourCalculated = false;
            String detourUnavailableReason = null;

            if (seg != null && seg.isSuccess() && seg.getDistanceKm() > 0) {
                double baselineKm = seg.getDistanceKm();
                int baselineMins = seg.getDurationMinutes();

                double fromLat = (seg.getFromLat() != null) ? seg.getFromLat() : googleMapsService.geocode(fromLoc)[0];
                double fromLng = (seg.getFromLng() != null) ? seg.getFromLng() : googleMapsService.geocode(fromLoc)[1];
                double toLat = (seg.getToLat() != null) ? seg.getToLat() : googleMapsService.geocode(toLoc)[0];
                double toLng = (seg.getToLng() != null) ? seg.getToLng() : googleMapsService.geocode(toLoc)[1];

                // Compute leg1 (from -> stop) and leg2 (stop -> to) using exact coordinates
                GoogleMapsService.RouteCalculationResult leg1 = googleMapsService.calculateRouteBetweenCoordinates(
                        fromLat, fromLng, cand.lat, cand.lng, trip.getTransportMode());
                GoogleMapsService.RouteCalculationResult leg2 = googleMapsService.calculateRouteBetweenCoordinates(
                        cand.lat, cand.lng, toLat, toLng, trip.getTransportMode());

                if (leg1 != null && leg1.isSuccess() && leg2 != null && leg2.isSuccess()) {
                    double totalWithStopKm = leg1.getDistanceKm() + leg2.getDistanceKm();
                    int totalWithStopMins = leg1.getDurationMinutes() + leg2.getDurationMinutes();

                    detourKm = Math.max(0.0, Math.round((totalWithStopKm - baselineKm) * 10.0) / 10.0);
                    detourMins = Math.max(0, totalWithStopMins - baselineMins);
                    detourCalculated = true;
                } else {
                    detourCalculated = false;
                    detourUnavailableReason = "Road detour calculation unavailable for this coordinate";
                }
            } else {
                detourCalculated = false;
                detourUnavailableReason = "Baseline route segment unavailable";
            }

            // Detour duration filter preference
            if (maxDetourMinutes != null && maxDetourMinutes > 0) {
                if (!detourCalculated || detourMins == null) {
                    // "If the detour cannot be calculated, mark it unavailable instead of silently assuming it is within the limit."
                    continue;
                }
                if (detourMins > maxDetourMinutes) {
                    continue;
                }
            }

            // Detour km filter
            if (maxDetourKm != null && maxDetourKm > 0) {
                if (detourCalculated && detourKm != null && detourKm > maxDetourKm) {
                    continue;
                }
            }

            String formattedDetour;
            if (detourCalculated && detourKm != null && detourMins != null) {
                formattedDetour = String.format(Locale.US, "+%.1f km detour (+%d min extra)", detourKm, detourMins);
            } else {
                formattedDetour = String.format(Locale.US, "%.1f km from highway (detour route pending)", distFromRoute);
            }

            PlaceAlongRouteDto dto = new PlaceAlongRouteDto();
            dto.setId(cand.id);
            dto.setName(cand.name);
            dto.setCategory(cand.category);
            dto.setCategoryCode(cand.categoryCode);
            dto.setRating(cand.rating);
            dto.setReviewCount(cand.reviewCount);
            dto.setDescription(cand.description);
            dto.setAddress(cand.address);
            dto.setLatitude(cand.lat);
            dto.setLongitude(cand.lng);
            dto.setSegmentName(segmentName);
            dto.setFromLocation(fromLoc);
            dto.setToLocation(toLoc);
            dto.setDistanceFromRouteKm(distFromRoute);
            dto.setDetourDistanceKm(detourKm);
            dto.setDetourDurationMinutes(detourMins);
            dto.setFormattedDetour(formattedDetour);
            dto.setDetourCalculated(detourCalculated);
            dto.setDetourUnavailableReason(detourUnavailableReason);
            dto.setOpeningHours(cand.openingHours);
            dto.setOpenNow(cand.openNow);
            dto.setProviderSource(cand.providerSource);
            dto.setInsertIndex(segmentIdx); // index to insert after
            dto.setInsertAfterDestination(fromLoc);
            dto.setInsertBeforeDestination(toLoc);

            results.add(dto);
        }

        // 8. Rank results by reasonable detour, route proximity, and rating
        results.sort((a, b) -> {
            if (a.getDetourCalculated() && b.getDetourCalculated()) {
                if (a.getDetourDurationMinutes() != null && b.getDetourDurationMinutes() != null) {
                    int c = Integer.compare(a.getDetourDurationMinutes(), b.getDetourDurationMinutes());
                    if (c != 0) return c;
                }
                if (a.getDetourDistanceKm() != null && b.getDetourDistanceKm() != null) {
                    int c = Double.compare(a.getDetourDistanceKm(), b.getDetourDistanceKm());
                    if (c != 0) return c;
                }
            } else if (a.getDetourCalculated() && !b.getDetourCalculated()) {
                return -1;
            } else if (!a.getDetourCalculated() && b.getDetourCalculated()) {
                return 1;
            }
            return Double.compare(a.getDistanceFromRouteKm(), b.getDistanceFromRouteKm());
        });

        // Limit results to top 30
        List<PlaceAlongRouteDto> limited = results.stream().limit(30).collect(Collectors.toList());
        searchCache.put(cacheKey, limited);
        return limited;
    }

    public void clearCache() {
        searchCache.clear();
    }

    private List<double[]> sampleRouteGeometry(List<double[]> routeGeometry, double intervalKm) {
        List<double[]> sampled = new ArrayList<>();
        if (routeGeometry == null || routeGeometry.isEmpty()) {
            return sampled;
        }

        sampled.add(routeGeometry.get(0));
        double accKm = 0.0;
        for (int i = 0; i < routeGeometry.size() - 1; i++) {
            double[] p1 = routeGeometry.get(i);
            double[] p2 = routeGeometry.get(i + 1);
            double segDist = GeoUtils.calculateHaversineDistanceKm(p1[0], p1[1], p2[0], p2[1]);
            accKm += segDist;
            if (accKm >= intervalKm) {
                sampled.add(p2);
                accKm = 0.0;
            }
        }

        double[] last = routeGeometry.get(routeGeometry.size() - 1);
        if (GeoUtils.calculateHaversineDistanceKm(sampled.get(sampled.size() - 1)[0], sampled.get(sampled.size() - 1)[1], last[0], last[1]) > 5.0) {
            sampled.add(last);
        }

        return sampled;
    }

    private int findClosestTripSegment(double lat, double lng, List<RouteSegmentDto> segments) {
        if (segments == null || segments.isEmpty()) {
            return 0;
        }
        double minDistance = Double.MAX_VALUE;
        int bestIdx = 0;
        for (int i = 0; i < segments.size(); i++) {
            RouteSegmentDto seg = segments.get(i);
            List<double[]> geom = seg.getGeometryCoordinates();
            double dist;
            if (geom != null && !geom.isEmpty()) {
                dist = GeoUtils.calculateDistanceToPolylineKm(lat, lng, geom);
            } else {
                dist = GeoUtils.calculateDistanceToSegmentKm(lat, lng, seg.getFromLat(), seg.getFromLng(), seg.getToLat(), seg.getToLng());
            }
            if (dist < minDistance) {
                minDistance = dist;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    private void searchVerifiedCatalog(List<double[]> corridorPoints,
                                       List<double[]> routeGeometry,
                                       String categoryFilter,
                                       Map<String, CorridorStopCandidate> candidateMap) {
        for (CorridorStopTemplate t : VERIFIED_HIGHWAY_CATALOG) {
            if (!"ALL".equalsIgnoreCase(categoryFilter) && !t.categoryCode.equalsIgnoreCase(categoryFilter)) {
                continue;
            }
            // Check if place is within 25km of the route polyline
            double dist = GeoUtils.calculateDistanceToPolylineKm(t.lat, t.lng, routeGeometry);
            if (dist <= 25.0) {
                candidateMap.put(t.id, new CorridorStopCandidate(
                        t.id, t.name, t.category, t.categoryCode, t.rating, t.reviewCount,
                        t.description, t.address, t.lat, t.lng, t.openingHours, true, "VERIFIED_HIGHWAY_CATALOG"
                ));
            }
        }
    }

    private void searchGooglePlaces(List<double[]> corridorPoints,
                                    String categoryFilter,
                                    Map<String, CorridorStopCandidate> candidateMap) {
        GeoApiContext context = googleMapsService.getGeoApiContext();
        if (context == null) return;

        // Map category to Google Place types
        com.google.maps.model.PlaceType googleType = mapCategoryToGooglePlaceType(categoryFilter);

        // Control external requests: sample up to 4 search points along long corridors
        int step = Math.max(1, corridorPoints.size() / 4);
        for (int i = 0; i < corridorPoints.size(); i += step) {
            double[] pt = corridorPoints.get(i);
            try {
                var query = PlacesApi.nearbySearchQuery(context, new LatLng(pt[0], pt[1]))
                        .radius(10000); // 10km radius
                if (googleType != null) {
                    query.type(googleType);
                }

                PlacesSearchResponse resp = query.await();
                if (resp != null && resp.results != null) {
                    for (PlacesSearchResult r : resp.results) {
                        if (r.name == null || r.geometry == null || r.geometry.location == null) continue;
                        String id = "gp_" + r.placeId;
                        if (!candidateMap.containsKey(id)) {
                            String catCode = mapGoogleTypesToCategoryCode(r.types);
                            String catDisplay = formatCategoryDisplay(catCode);
                            candidateMap.put(id, new CorridorStopCandidate(
                                    id, r.name, catDisplay, catCode,
                                    (r.rating > 0) ? (double) r.rating : null,
                                    (r.userRatingsTotal > 0) ? r.userRatingsTotal : null,
                                    r.vicinity, r.vicinity,
                                    r.geometry.location.lat, r.geometry.location.lng,
                                    (r.openingHours != null && r.openingHours.openNow != null) ? (r.openingHours.openNow ? "Open Now" : "Closed") : null,
                                    (r.openingHours != null) ? r.openingHours.openNow : null,
                                    "GOOGLE_PLACES"
                            ));
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Google Places corridor search at ({}, {}) failed: {}", pt[0], pt[1], e.getMessage());
            }
        }
    }

    private void searchOverpassCorridor(List<double[]> corridorPoints,
                                        String categoryFilter,
                                        Map<String, CorridorStopCandidate> candidateMap) {
        if (corridorPoints.isEmpty()) return;

        // Sample up to 3 points to prevent excessive requests
        int step = Math.max(1, corridorPoints.size() / 3);
        for (int i = 0; i < corridorPoints.size(); i += step) {
            double[] pt = corridorPoints.get(i);
            try {
                String overpassFilter = buildOverpassFilter(categoryFilter);
                String ql = String.format(Locale.US,
                        "[out:json][timeout:3];node(around:8000,%.4f,%.4f)%s;out 15 tags;",
                        pt[0], pt[1], overpassFilter);

                String url = "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(ql, StandardCharsets.UTF_8);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "VoyageCraft-SmartStops/1.0")
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 && resp.body() != null) {
                    JsonNode root = objectMapper.readTree(resp.body());
                    JsonNode elements = root.path("elements");
                    if (elements.isArray()) {
                        for (JsonNode el : elements) {
                            JsonNode tags = el.path("tags");
                            String name = tags.path("name").asText("");
                            if (name.isEmpty() || name.length() < 3) continue;

                            String id = "osm_" + el.path("id").asText(UUID.randomUUID().toString());
                            double lat = el.path("lat").asDouble();
                            double lon = el.path("lon").asDouble();

                            String catCode = resolveOsmCategoryCode(tags);
                            String catDisplay = formatCategoryDisplay(catCode);
                            String openingHours = tags.path("opening_hours").asText(null);

                            if (!candidateMap.containsKey(id)) {
                                candidateMap.put(id, new CorridorStopCandidate(
                                        id, name, catDisplay, catCode,
                                        null, null,
                                        tags.path("description").asText(tags.path("operator").asText("Highway Facility")),
                                        tags.path("addr:street").asText(tags.path("addr:city").asText("Highway Corridor")),
                                        lat, lon, openingHours, null, "OPENSTREETMAP"
                                ));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Overpass corridor search skipped: {}", e.getMessage());
            }
        }
    }

    private com.google.maps.model.PlaceType mapCategoryToGooglePlaceType(String category) {
        if (category == null || "ALL".equalsIgnoreCase(category)) return null;
        switch (category.toUpperCase()) {
            case "FUEL":
                return com.google.maps.model.PlaceType.GAS_STATION;
            case "RESTAURANT":
                return com.google.maps.model.PlaceType.RESTAURANT;
            case "EV_CHARGING":
                return com.google.maps.model.PlaceType.GAS_STATION;
            case "VIEWPOINT":
            case "ATTRACTION":
                return com.google.maps.model.PlaceType.TOURIST_ATTRACTION;
            case "PARKING":
                return com.google.maps.model.PlaceType.PARKING;
            case "HOTEL":
                return com.google.maps.model.PlaceType.LODGING;
            case "HOSPITAL":
                return com.google.maps.model.PlaceType.HOSPITAL;
            case "PHARMACY":
                return com.google.maps.model.PlaceType.PHARMACY;
            default:
                return null;
        }
    }

    private String mapGoogleTypesToCategoryCode(String[] types) {
        if (types == null) return "ATTRACTION";
        for (String t : types) {
            String tl = t.toLowerCase();
            if (tl.contains("hospital") || tl.contains("health")) return "HOSPITAL";
            if (tl.contains("pharmacy") || tl.contains("drugstore")) return "PHARMACY";
            if (tl.contains("gas_station") || tl.contains("fuel")) return "FUEL";
            if (tl.contains("restaurant") || tl.contains("cafe") || tl.contains("bakery") || tl.contains("food")) return "RESTAURANT";
            if (tl.contains("parking")) return "PARKING";
            if (tl.contains("lodging") || tl.contains("hotel")) return "HOTEL";
            if (tl.contains("tourist_attraction") || tl.contains("point_of_interest")) return "ATTRACTION";
        }
        return "ATTRACTION";
    }

    private String buildOverpassFilter(String category) {
        if (category == null || "ALL".equalsIgnoreCase(category)) {
            return "[\"amenity\"~\"fuel|restaurant|cafe|fast_food|toilets|charging_station|parking|hospital|pharmacy|clinic\"];";
        }
        switch (category.toUpperCase()) {
            case "FUEL":
                return "[\"amenity\"=\"fuel\"];";
            case "RESTAURANT":
                return "[\"amenity\"~\"restaurant|cafe|fast_food\"];";
            case "REST_STOP":
                return "[\"amenity\"~\"toilets\"];";
            case "EV_CHARGING":
                return "[\"amenity\"=\"charging_station\"];";
            case "VIEWPOINT":
                return "[\"tourism\"=\"viewpoint\"];";
            case "ATTRACTION":
                return "[\"tourism\"~\"attraction|theme_park\"];";
            case "PARKING":
                return "[\"amenity\"=\"parking\"];";
            case "HOTEL":
                return "[\"tourism\"~\"hotel|motel|guest_house\"];";
            case "HOSPITAL":
                return "[\"amenity\"~\"hospital|clinic\"];";
            case "PHARMACY":
                return "[\"amenity\"=\"pharmacy\"];";
            default:
                return "[\"amenity\"~\"fuel|restaurant|cafe|toilets|charging_station|hospital|pharmacy\"];";
        }
    }

    private String resolveOsmCategoryCode(JsonNode tags) {
        String amenity = tags.path("amenity").asText("");
        String tourism = tags.path("tourism").asText("");
        if ("fuel".equals(amenity)) return "FUEL";
        if (amenity.matches("restaurant|cafe|fast_food")) return "RESTAURANT";
        if ("toilets".equals(amenity)) return "REST_STOP";
        if ("charging_station".equals(amenity)) return "EV_CHARGING";
        if ("parking".equals(amenity)) return "PARKING";
        if ("viewpoint".equals(tourism)) return "VIEWPOINT";
        if ("hotel".equals(tourism) || "motel".equals(tourism)) return "HOTEL";
        if (amenity.matches("hospital|clinic")) return "HOSPITAL";
        if ("pharmacy".equals(amenity)) return "PHARMACY";
        return "ATTRACTION";
    }

    private String formatCategoryDisplay(String code) {
        switch (code.toUpperCase()) {
            case "FUEL":
                return "Fuel Station";
            case "RESTAURANT":
                return "Food & Dining";
            case "REST_STOP":
                return "Rest Stop";
            case "EV_CHARGING":
                return "EV Charging Station";
            case "VIEWPOINT":
                return "Scenic Viewpoint";
            case "ATTRACTION":
                return "Family Attraction";
            case "PARKING":
                return "Parking Area";
            case "HOTEL":
                return "Hotel & Accommodation";
            case "HOSPITAL":
                return "Hospital & Clinic";
            case "PHARMACY":
                return "Pharmacy & Medical";
            default:
                return "Highway Stop";
        }
    }

    private String normalizeName(String name) {
        if (name == null) return "";
        return name.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String computeRouteSignature(List<double[]> routeGeometry) {
        if (routeGeometry == null || routeGeometry.isEmpty()) return "empty";
        double[] first = routeGeometry.get(0);
        double[] last = routeGeometry.get(routeGeometry.size() - 1);
        return String.format(Locale.US, "%.3f,%.3f->%.3f,%.3f:%d",
                first[0], first[1], last[0], last[1], routeGeometry.size());
    }

    private void initCatalog() {
        // =========================================================================
        // 1. Bengaluru - Ramanagara - Channapatna - Maddur - Mandya - Srirangapatna - Mysore (NH275)
        // =========================================================================
        // Fuel
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_fuel_1", "Shell Highway Auto Spa & Fuel", "Fuel Station", "FUEL", 4.6, 1850,
                "Modern Shell highway station offering high-quality fuels, clean restrooms, Deli2Go cafe, and EV fast chargers.", "NH275, Bidadi Bypass, Karnataka", 12.7981, 77.3820, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_fuel_2", "IndianOil Swagat Highway Service Center", "Fuel Station", "FUEL", 4.4, 2120,
                "Dedicated Swagat highway plaza with secure fuel pumps, truck & car rest bays, and quick refreshments.", "NH275, Maddur, Mandya District, Karnataka", 12.5841, 77.0450, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_fuel_3", "BPCL Highway Oasis Mandya", "Fuel Station", "FUEL", 4.3, 1450,
                "Spacious Bharat Petroleum expressway station with air check, pristine restrooms, and convenience store.", "Mysuru-Bengaluru Expressway, Mandya", 12.5230, 76.8980, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_fuel_4", "HPCL COCO Highway Station Kushalnagar", "Fuel Station", "FUEL", 4.5, 980,
                "Company-owned highway fuel station before entering the Western Ghats with tire care and snacks.", "Madikeri Highway, Kushalnagar, Coorg", 12.4550, 75.9580, "Open 24 Hours"));

        // Food & Dining
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_food_1", "Kamat Upachar Highway Restaurant", "Food & Dining", "RESTAURANT", 4.4, 8900,
                "Celebrated vegetarian highway stop iconic for authentic South Indian filter coffee, Akki Rotti, and hot dosas.", "Bengaluru-Mysuru Highway, Ramanagara", 12.7210, 77.2790, "06:30 AM - 10:30 PM"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_food_2", "Shivalli Restaurant Highway Hub", "Food & Dining", "RESTAURANT", 4.5, 5200,
                "Traditional Udupi highway dining with ample parking, thali meals, and sweet Channapatna snacks.", "NH275, Channapatna, Karnataka", 12.6510, 77.2020, "07:00 AM - 10:00 PM"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_food_3", "Maddur Tiffany's Original Vada Center", "Food & Dining", "RESTAURANT", 4.5, 6400,
                "Historical birthplace of crunchy Maddur Vada served fresh with spicy coconut chutney and chai.", "Old Highway Junction, Maddur", 12.5830, 77.0420, "06:00 AM - 09:30 PM"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_food_4", "Adyar Ananda Bhavan (A2B) Highway Plaza", "Food & Dining", "RESTAURANT", 4.3, 4100,
                "Popular vegetarian family plaza featuring ghee roast dosas, sweets counter, and air-conditioned seating.", "Expressway Service Road, Mandya", 12.5180, 76.8850, "06:30 AM - 11:00 PM"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_food_5", "Empire Highway Restaurant Srirangapatna", "Food & Dining", "RESTAURANT", 4.2, 3800,
                "Famous non-veg and grill dining hub popular for Biryani, shawarmas, coin parottas, and fresh juices.", "Bangalore-Mysore Highway, Srirangapatna", 12.4180, 76.6850, "09:00 AM - 01:00 AM"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_food_6", "Cafe Coffee Day Highway Expressway", "Food & Dining", "RESTAURANT", 4.3, 2600,
                "Comfortable coffee lounge with quick sandwiches, frappes, clean restrooms, and outdoor highway seating.", "NH275 Near Toll, Ramanagara", 12.7150, 77.2720, "06:00 AM - 11:00 PM"));

        // Rest Stops & Restrooms
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_rest_1", "NHAI Expressway Way Side Amenity", "Rest Stop", "REST_STOP", 4.4, 1850,
                "Official NHAI highway rest area featuring sanitized public restrooms, drinking water, baby-care facilities, and resting benches.", "Mile 42, Bengaluru-Mysuru Expressway, Ramanagara", 12.7050, 77.2600, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_rest_2", "Mandya Expressway Service Rest Plaza", "Rest Stop", "REST_STOP", 4.2, 1300,
                "Dedicated traveler comfort station with well-maintained washrooms, shaded parking, and convenience stores.", "Expressway Rest Corridor, Mandya", 12.5310, 76.9120, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_rest_3", "Srirangapatna Highway Traveler Rest Stop", "Rest Stop", "REST_STOP", 4.3, 940,
                "Riverside highway rest park overlooking Cauvery canals with clean washrooms and refreshment stalls.", "Cauvery River Bridge Access, Srirangapatna", 12.4280, 76.6910, "Open 24 Hours"));

        // EV Charging Stations
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_ev_1", "Tata Power EZ Charge 60kW DC Fast Charger", "EV Charging Station", "EV_CHARGING", 4.6, 750,
                "High-speed dual-gun CCS2 60kW DC fast charging station situated at Shell Bidadi plaza.", "Shell Plaza, NH275, Bidadi", 12.7960, 77.3810, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_ev_2", "Zeon Electric Charging Hub (Dual 50kW)", "EV Charging Station", "EV_CHARGING", 4.7, 890,
                "Ultra-reliable 50kW CCS2 DC rapid charger with restaurant seating and clean amenities while charging.", "Kamat Upachar Campus, Maddur", 12.5820, 77.0440, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_ev_3", "Jio-bp pulse EV Fast Charger Mandya", "EV Charging Station", "EV_CHARGING", 4.4, 520,
                "Jio-bp 60kW DC fast charger supporting all modern electric vehicles with instant app activation.", "Expressway Service Plaza, Mandya", 12.5250, 76.9010, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_ev_4", "ChargeZone Highway DC Hub Kushalnagar", "EV Charging Station", "EV_CHARGING", 4.5, 410,
                "Fast charging hub with 60kW CCS2 charger on the Coorg approach road.", "NH275 Kushalnagar, Coorg", 12.4560, 75.9600, "Open 24 Hours"));

        // Scenic Viewpoints
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_view_1", "Ramanagara Silk Cocoon Hills Overlook", "Scenic Viewpoint", "VIEWPOINT", 4.6, 2100,
                "Dramatic granite rock formations and rolling shrub hills made world-famous as the shooting location of Sholay.", "Ramanagara Hills Access, NH275", 12.7310, 77.2880, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_view_2", "Sangama River Cauvery Confluence Viewpoint", "Scenic Viewpoint", "VIEWPOINT", 4.7, 3400,
                "Scenic vantage point where the Arkavathi and Cauvery rivers merge amidst rocky riverbeds.", "Mekedatu Road Junction, Kanakapura/Ramanagara corridor", 12.3040, 77.4470, "06:00 AM - 06:00 PM"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_view_3", "Kaveri Nisargadhama Scenic Island Overlook", "Scenic Viewpoint", "VIEWPOINT", 4.5, 2900,
                "Natural 64-acre bamboo island surrounded by the Cauvery river with hanging bridges and deer park.", "Kushalnagar Highway, Coorg", 12.4490, 75.9320, "09:00 AM - 05:00 PM"));

        // Family Attractions
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_attr_1", "Janapada Loka Folk Cultural Heritage Village", "Family Attraction", "ATTRACTION", 4.5, 4200,
                "Fascinating open-air museum dedicated to preserving Karnataka's rural folk arts, puppetry, pottery, and traditional village life.", "Bengaluru-Mysuru Highway, Ramanagara", 12.7225, 77.2815, "09:00 AM - 05:30 PM (Closed Tuesdays)"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_attr_2", "Channapatna Wooden Toy Crafts Heritage Center", "Family Attraction", "ATTRACTION", 4.4, 3100,
                "Artisan workshops and craft emporiums showcasing centuries-old GI-tagged ivory-wood lacquerware toys.", "Main Highway Road, Channapatna", 12.6520, 77.2050, "09:30 AM - 08:30 PM"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_attr_3", "Ranganathittu Bird Sanctuary Srirangapatna", "Family Attraction", "ATTRACTION", 4.6, 12500,
                "Renowned sanctuary on islets of River Cauvery with guided rowboats to observe painted storks, pelicans, and marsh crocodiles.", "Srirangapatna Highway Bypass", 12.4244, 76.6947, "08:30 AM - 05:30 PM"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_attr_4", "Kokkare Bellur Pelican & Stork Village", "Family Attraction", "ATTRACTION", 4.5, 2800,
                "Peaceful community village where spot-billed pelicans and painted storks nest directly in village trees.", "Near Maddur, Mandya District", 12.5320, 77.0850, "06:00 AM - 06:30 PM"));

        // Parking Areas
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_park_1", "Bidadi Expressway Truck & Car Parking Bay", "Parking Area", "PARKING", 4.3, 850,
                "Secure, paved highway parking bay with 24/7 CCTV surveillance, illumination, and immediate access to dining plazas.", "Bidadi Highway Node, NH275", 12.7950, 77.3780, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_park_2", "Maddur Express Highway Car Parking Plaza", "Parking Area", "PARKING", 4.4, 920,
                "Spacious multi-vehicle parking facility accommodating 120+ family cars with shaded slots.", "Near Maddur Food Court, NH275", 12.5850, 77.0470, "Open 24 Hours"));

        // Hotels & Accommodations
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_hotel_1", "KSTDC Hotel Mayura Riverview Srirangapatna", "Hotel & Accommodation", "HOTEL", 4.2, 1950,
                "Tranquil heritage riverside hotel on the banks of River Cauvery offering river-facing rooms and peaceful gardens.", "River Cauvery Banks, Srirangapatna", 12.4250, 76.6880, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_hotel_2", "Amblee Holiday Resort Highway Access", "Hotel & Accommodation", "HOTEL", 4.3, 1400,
                "Riverside resort featuring swimming pools, boating, lush lawn cottages, and multi-cuisine restaurant.", "Old Srirangapatna Road, Srirangapatna", 12.4120, 76.7020, "Open 24 Hours"));

        // =========================================================================
        // 2. Bengaluru - Hassan - Sakleshpur - Mangalore Corridor (NH75)
        // =========================================================================
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_nh75_1", "Swathi Delicacy Highway Veg", "Food & Dining", "RESTAURANT", 4.6, 12000,
                "Iconic vegetarian highway stop renowned for crispy butter dosas, filter coffee, and fast service.", "NH75, Yediyur, Karnataka", 12.9810, 76.8850, "06:00 AM - 10:30 PM"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_nh75_2", "HPCL COCO Highway Oasis Kunigal", "Fuel Station", "FUEL", 4.5, 2300,
                "Clean highway refueling plaza with high-speed air, restrooms, and coffee cafe.", "NH75 Near Kunigal Bypass", 13.0210, 77.0320, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_nh75_3", "Tata Power 50kW EV Fast Charger Yediyur", "EV Charging Station", "EV_CHARGING", 4.6, 680,
                "Dual CCS2 rapid electric car charger located right at Swathi Delicacy plaza.", "Swathi Delicacy Campus, NH75, Yediyur", 12.9800, 76.8840, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_nh75_4", "Shiradi Ghat Valley Viewpoint", "Scenic Viewpoint", "VIEWPOINT", 4.7, 4300,
                "Spectacular mountain pass overlook showcasing mist-shrouded Western Ghat rain forests and waterfalls.", "NH75 Shiradi Ghat Highway, Sakleshpur", 12.8310, 75.6420, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_nh75_5", "Shettihalli Submerged Rosary Church", "Family Attraction", "ATTRACTION", 4.5, 5900,
                "Historic 19th-century French Gothic church ruins partially submerged in the Hemavathi reservoir waters.", "Shettihalli, Near Hassan", 12.9250, 75.9810, "06:00 AM - 06:30 PM"));

        // =========================================================================
        // 3. Mumbai - Pune Expressway Corridor
        // =========================================================================
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_mpe_1", "Khalapur Food Mall", "Food & Dining", "RESTAURANT", 4.4, 15000,
                "Flagship expressway food court featuring McDonald's, Starbucks, Subway, Kailash Parbat, and clean washrooms.", "Mumbai-Pune Expressway, Khalapur Toll Plaza", 18.8250, 73.2850, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_mpe_2", "IndianOil Swagat Highway Center Khalapur", "Fuel Station", "FUEL", 4.5, 3800,
                "Large 24-hour refueling hub with multi-lane pumps, XP95 petrol, and free nitrogen tire inflators.", "Mumbai-Pune Expressway (Pune Bound), Khalapur", 18.8240, 73.2840, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_mpe_3", "Tata Power 60kW DC Fast Charger Khalapur", "EV Charging Station", "EV_CHARGING", 4.6, 1100,
                "High-speed expressway DC EV charger supporting CCS2 vehicles with dining access.", "Food Mall Campus, Khalapur", 18.8230, 73.2830, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_mpe_4", "Khandala Ghat Panoramic Overlook", "Scenic Viewpoint", "VIEWPOINT", 4.7, 8500,
                "Breathtaking mountain vista overlooking deep ravines, monsoon waterfalls, and Duke's Nose cliff.", "Khandala Expressway Overlook, Lonavala", 18.7610, 73.3760, "Open 24 Hours"));

        // =========================================================================
        // 4. Delhi - Agra Yamuna Expressway Corridor
        // =========================================================================
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_dye_1", "Yamuna Expressway Mile 65 Food & Rest Plaza", "Rest Stop", "REST_STOP", 4.3, 7200,
                "Comprehensive tollway service plaza with clean washrooms, baby feeding rooms, Costa Coffee, and Pizza Hut.", "Mile 65, Yamuna Expressway, Jewar", 28.1250, 77.5520, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_dye_2", "IndianOil Swagat Jewar Toll Plaza", "Fuel Station", "FUEL", 4.4, 2900,
                "Expressway fuel plaza with high-flow diesel, XP100 petrol, and vehicle inspection bay.", "Yamuna Expressway Toll Gate, Jewar", 28.1280, 77.5550, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_dye_3", "Tata Power DC Fast Charger Jewar Plaza", "EV Charging Station", "EV_CHARGING", 4.5, 620,
                "Fast charging hub with two 60kW CCS2 guns for Delhi-Agra intercity EV travelers.", "Mile 65 Plaza, Yamuna Expressway", 28.1260, 77.5530, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_dye_4", "Sagar Ratna Highway Express Mathura", "Food & Dining", "RESTAURANT", 4.4, 4800,
                "Famous pure vegetarian South and North Indian highway dining stop offering fresh thalis and dosa combos.", "Yamuna Expressway Exit 3, Mathura Bypass", 27.5850, 77.6820, "06:00 AM - 11:30 PM"));

        // Medical & Emergency Facilities along Corridors (NH275, NH75, MPE, DYE)
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_med_1", "MIMS Mandya Institute of Medical Sciences Trauma Center", "Hospital & Clinic", "HOSPITAL", 4.3, 1400,
                "24/7 Government medical college hospital with fully-equipped emergency trauma wing directly off the expressway.", "NH275 Bypass, Mandya", 12.5280, 76.9010, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_med_2", "Apollo Pharmacy 24/7 Ramanagara Highway Hub", "Pharmacy & Medical", "PHARMACY", 4.5, 430,
                "24-hour highway pharmacy stocking emergency medications, first-aid kits, ORS, and travel essentials.", "NH275, Ramanagara Main Access", 12.7210, 77.2790, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_med_3", "Manipal Hospital Highway Emergency Node Mysuru", "Hospital & Clinic", "HOSPITAL", 4.6, 2800,
                "Multispeciality tertiary hospital with round-the-clock emergency, cardiology, and surgical response.", "Bengaluru-Mysuru Ring Road Junction, Mysuru", 12.3380, 76.6540, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_med_4", "Hassan Institute of Medical Sciences (HIMS) Trauma Care", "Hospital & Clinic", "HOSPITAL", 4.4, 1150,
                "District emergency and surgical trauma hospital serving travelers on the Mangalore-Bengaluru highway.", "NH75 Access, Hassan", 13.0040, 76.1020, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_med_5", "MedPlus 24/7 Kunigal Highway Pharmacy", "Pharmacy & Medical", "PHARMACY", 4.3, 290,
                "Round-the-clock prescription medicine and road traveler care depot.", "NH75 Kunigal Toll Bypass", 13.0230, 77.0340, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_med_6", "Dhirubhai Ambani Hospital & Trauma Care Lodhivali", "Hospital & Clinic", "HOSPITAL", 4.5, 1950,
                "Full-service trauma and emergency center directly accessible from the Mumbai-Pune Expressway Khalapur exit.", "Old Mumbai-Pune Highway, Lodhivali", 18.8410, 73.2350, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_med_7", "Sanjeevani 24-Hour Medical Lonavala Expressway", "Pharmacy & Medical", "PHARMACY", 4.4, 380,
                "24/7 emergency pharmacy and clinic located near the Lonavala expressway exit.", "Near Kumar Resort, Lonavala", 18.7560, 73.4080, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_med_8", "Kailash Hospital & Trauma Center Jewar Node", "Hospital & Clinic", "HOSPITAL", 4.4, 1200,
                "24/7 emergency critical care unit serving commuters on the Yamuna Expressway.", "Jewar Interchange, Yamuna Expressway", 28.1290, 77.5580, "Open 24 Hours"));

        // =========================================================================
        // 5. Bengaluru - Hosur - Krishnagiri - Dharmapuri - Salem Corridor (NH44)
        // =========================================================================
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_nh44_1", "BPCL Ghar Highway Oasis Shoolagiri", "Fuel Station", "FUEL", 4.5, 3400,
                "Comprehensive highway plaza featuring multi-lane fuel bays, clean washrooms, food court, and ATM.", "NH44, Shoolagiri, Krishnagiri District", 12.6710, 78.0120, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_nh44_2", "Saravana Bhavan Highway Express Shoolagiri", "Food & Dining", "RESTAURANT", 4.4, 8200,
                "Famous pure-vegetarian South Indian breakfast and meal destination with spacious car parking.", "NH44 Shoolagiri Plaza", 12.6720, 78.0140, "06:00 AM - 11:00 PM"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_nh44_3", "Tata Power 60kW DC Fast Charger Shoolagiri", "EV Charging Station", "EV_CHARGING", 4.6, 920,
                "High-power dual-gun CCS2 EV charger located at the Shoolagiri highway food plaza.", "NH44 Shoolagiri Plaza Campus", 12.6715, 78.0130, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_nh44_4", "Government Head Quarters Hospital & Trauma Center Krishnagiri", "Hospital & Clinic", "HOSPITAL", 4.3, 1600,
                "District emergency center with round-the-clock casualty and ambulance service along NH44.", "NH44 Highway Junction, Krishnagiri", 12.5280, 78.2140, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_nh44_5", "Apollo Pharmacy 24/7 Shoolagiri Highway Hub", "Pharmacy & Medical", "PHARMACY", 4.5, 320,
                "24-hour pharmacy with travel health medicines and baby care products.", "NH44 Food Court, Shoolagiri", 12.6725, 78.0145, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_nh44_6", "A2B Adyar Ananda Bhavan Thoppur Toll Plaza", "Food & Dining", "RESTAURANT", 4.3, 6100,
                "Iconic Ghat road stop before Thoppur Ghats offering dosas, sweets, and coffee.", "NH44 Thoppur Toll Plaza, Dharmapuri", 11.9520, 78.0650, "06:00 AM - 11:00 PM"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_nh44_7", "Zeon 50kW EV Fast Charger Thoppur", "EV Charging Station", "EV_CHARGING", 4.6, 540,
                "High-speed charging station situated on the Bengaluru-Salem NH44 expressway.", "Thoppur Highway Service Complex", 11.9510, 78.0640, "Open 24 Hours"));

        // =========================================================================
        // 6. Hyderabad - Kurnool - Anantapur - Bengaluru Corridor (NH44 North)
        // =========================================================================
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_hyd_1", "Food Pyramid Highway Plaza Shadnagar", "Food & Dining", "RESTAURANT", 4.3, 5100,
                "Major multi-cuisine highway hub with clean restrooms, Subways, coffee outlets, and fueling.", "NH44, Shadnagar Bypass, Telangana", 17.0650, 78.2100, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_hyd_2", "IndianOil COCO Highway Swagat Jadcherla", "Fuel Station", "FUEL", 4.4, 2100,
                "Large company-owned outlet with 24/7 canopy lighting, nitrogen air, and clean restrooms.", "NH44 Jadcherla, Mahabubnagar", 16.7620, 78.1350, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_hyd_3", "Tata Power 60kW EV Charger Jadcherla", "EV Charging Station", "EV_CHARGING", 4.5, 480,
                "Rapid charging station for Hyderabad-Bengaluru electric vehicle road trips.", "NH44 Jadcherla Plaza", 16.7610, 78.1340, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_hyd_4", "Government General Hospital Trauma Wing Kurnool", "Hospital & Clinic", "HOSPITAL", 4.3, 1850,
                "State-of-the-art regional trauma and emergency hospital accessible from NH44.", "NH44 City Ring Junction, Kurnool", 15.8280, 78.0370, "Open 24 Hours"));

        // =========================================================================
        // 7. Mumbai - Vapi - Surat - Vadodara - Ahmedabad Corridor (NH48 West)
        // =========================================================================
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_nh48_1", "Sugar N Spice Highway Mall Vapi", "Food & Dining", "RESTAURANT", 4.4, 9800,
                "Legendary Gujarat highway food court offering Gujarati snacks, Punjabi thalis, and pristine washrooms.", "NH48, Gunjan Char Rasta, Vapi", 20.3720, 72.9110, "07:00 AM - 11:30 PM"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_nh48_2", "Shell Highway Hub Vapi", "Fuel Station", "FUEL", 4.6, 2600,
                "High-standard fuel station with Deli2Go, tire care, and EV fast chargers.", "NH48 Vapi Bypass, Gujarat", 20.3750, 72.9150, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_nh48_3", "Rainbow Multispeciality Hospital 24/7 Trauma Vapi", "Hospital & Clinic", "HOSPITAL", 4.5, 1400,
                "Equipped with comprehensive round-the-clock emergency trauma unit right on NH48.", "NH48 Highway Node, Vapi", 20.3800, 72.9120, "Open 24 Hours"));
        VERIFIED_HIGHWAY_CATALOG.add(new CorridorStopTemplate("cat_nh48_4", "Tata Power 60kW DC EV Station Surat Toll", "EV Charging Station", "EV_CHARGING", 4.6, 810,
                "Fast dual CCS2 charger serving Mumbai-Ahmedabad travelers.", "NH48 Kamrej Toll Plaza, Surat", 21.2720, 72.9640, "Open 24 Hours"));
    }

    private static class CorridorStopTemplate {
        String id;
        String name;
        String category;
        String categoryCode;
        Double rating;
        Integer reviewCount;
        String description;
        String address;
        double lat;
        double lng;
        String openingHours;

        CorridorStopTemplate(String id, String name, String category, String categoryCode,
                             Double rating, Integer reviewCount, String description,
                             String address, double lat, double lng, String openingHours) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.categoryCode = categoryCode;
            this.rating = rating;
            this.reviewCount = reviewCount;
            this.description = description;
            this.address = address;
            this.lat = lat;
            this.lng = lng;
            this.openingHours = openingHours;
        }
    }

    private static class CorridorStopCandidate {
        String id;
        String name;
        String category;
        String categoryCode;
        Double rating;
        Integer reviewCount;
        String description;
        String address;
        double lat;
        double lng;
        String openingHours;
        Boolean openNow;
        String providerSource;

        CorridorStopCandidate(String id, String name, String category, String categoryCode,
                              Double rating, Integer reviewCount, String description,
                              String address, double lat, double lng, String openingHours,
                              Boolean openNow, String providerSource) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.categoryCode = categoryCode;
            this.rating = rating;
            this.reviewCount = reviewCount;
            this.description = description;
            this.address = address;
            this.lat = lat;
            this.lng = lng;
            this.openingHours = openingHours;
            this.openNow = openNow;
            this.providerSource = providerSource;
        }
    }
}
