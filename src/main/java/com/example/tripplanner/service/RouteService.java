package com.example.tripplanner.service;

import com.example.tripplanner.dto.RouteAlternativeDto;
import com.example.tripplanner.dto.RouteSegmentDto;
import com.example.tripplanner.dto.RouteSummaryDto;
import com.example.tripplanner.util.GeoUtils;
import com.example.tripplanner.util.TimeUtils;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RouteService {

    private final GoogleMapsService googleMapsService;

    public RouteService(GoogleMapsService googleMapsService) {
        this.googleMapsService = googleMapsService;
    }

    public RouteSummaryDto calculateCompleteRoute(String startLocation, List<String> destinations, String transportMode) {
        RouteSummaryDto summary = new RouteSummaryDto();
        String mode = (transportMode != null && !transportMode.trim().isEmpty()) ? transportMode.trim().toUpperCase() : "CAR";
        summary.setTransportMode(mode);

        if (startLocation == null || startLocation.trim().isEmpty() || destinations == null || destinations.isEmpty()) {
            summary.setRouteCalculationSuccess(false);
            summary.setCalculationErrorMessage("Starting location and at least one destination are required.");
            return summary;
        }

        // Clean and filter destinations
        List<String> cleanDestinations = new ArrayList<>();
        for (String d : destinations) {
            if (d != null && !d.trim().isEmpty()) {
                cleanDestinations.add(d.trim());
            }
        }

        if (cleanDestinations.isEmpty()) {
            summary.setRouteCalculationSuccess(false);
            summary.setCalculationErrorMessage("At least one valid destination is required.");
            return summary;
        }

        List<String> fullPath = new ArrayList<>();
        fullPath.add(startLocation.trim());
        fullPath.addAll(cleanDestinations);

        summary.setOriginalOrder(new ArrayList<>(fullPath));
        summary.setTotalDestinations(cleanDestinations.size());

        List<RouteSegmentDto> segments = new ArrayList<>();
        double totalDistanceKm = 0.0;
        int totalMinutes = 0;
        boolean allSegmentsSuccessful = true;
        String errorMessage = null;

        for (int i = 0; i < fullPath.size() - 1; i++) {
            String origin = fullPath.get(i);
            String destination = fullPath.get(i + 1);

            GoogleMapsService.RouteCalculationResult result = googleMapsService.calculateRouteSegment(origin, destination, mode);
            if (result == null) {
                double[] origCoords = GeoUtils.getApproximateCoordinates(origin);
                double[] destCoords = GeoUtils.getApproximateCoordinates(destination);
                double fallbackDist = GeoUtils.estimateRoadDistanceKm(origCoords[0], origCoords[1], destCoords[0], destCoords[1]);
                int fallbackMinutes = GeoUtils.estimateTravelTimeMinutes(fallbackDist, mode);
                result = new GoogleMapsService.RouteCalculationResult(fallbackDist, fallbackMinutes, origCoords, destCoords, false, false, "Routing provider temporarily unavailable", Collections.emptyList(), "UNAVAILABLE");
            }

            RouteSegmentDto segment = new RouteSegmentDto();
            segment.setFromLocation(origin);
            segment.setToLocation(destination);
            segment.setDistanceKm(result.getDistanceKm());
            segment.setDurationMinutes(result.getDurationMinutes());
            segment.setTransportMode(mode);
            segment.setFromLat(result.getOriginCoords() != null ? result.getOriginCoords()[0] : 0.0);
            segment.setFromLng(result.getOriginCoords() != null ? result.getOriginCoords()[1] : 0.0);
            segment.setToLat(result.getDestinationCoords() != null ? result.getDestinationCoords()[0] : 0.0);
            segment.setToLng(result.getDestinationCoords() != null ? result.getDestinationCoords()[1] : 0.0);
            segment.setSuccess(result.isSuccess());
            segment.setErrorMessage(result.getErrorMessage());
            segment.setVerifiedProvider(result.getVerifiedProvider());

            if (!result.isSuccess()) {
                allSegmentsSuccessful = false;
                errorMessage = (result.getErrorMessage() != null && !result.getErrorMessage().isEmpty())
                        ? result.getErrorMessage()
                        : "Route calculation failed between " + origin + " and " + destination;
                segment.setSummaryText("Route calculation unavailable (" + errorMessage + ")");
            } else {
                segment.setSummaryText(String.format(Locale.US, "%.1f km • %s",
                        result.getDistanceKm(),
                        TimeUtils.formatDuration(result.getDurationMinutes())
                ));
                totalDistanceKm += result.getDistanceKm();
                totalMinutes += result.getDurationMinutes();
            }

            segment.setGoogleMapsSegmentUrl(GeoUtils.buildGoogleMapsUrl(origin, destination, mode));
            segment.setGeometryCoordinates(result.getGeometryCoords());
            if (result.getGeometryCoords() != null && !result.getGeometryCoords().isEmpty()) {
                summary.getFullRouteGeometry().addAll(result.getGeometryCoords());
            } else {
                summary.getFullRouteGeometry().add(new double[]{result.getOriginCoords()[0], result.getOriginCoords()[1]});
                summary.getFullRouteGeometry().add(new double[]{result.getDestinationCoords()[0], result.getDestinationCoords()[1]});
            }

            segments.add(segment);
        }

        summary.setSegments(segments);

        if (!allSegmentsSuccessful) {
            // CRITICAL: Failed route segments are NOT treated as valid zero-distance routes
            summary.setRouteCalculationSuccess(false);
            summary.setTotalDistanceKm(0.0);
            summary.setTotalTravelTimeMinutes(0);
            summary.setCalculationErrorMessage(errorMessage != null ? errorMessage : "Route calculation failed for one or more segments.");
            summary.setOptimizationRecommended(false);
            summary.setRecommendedOrder(summary.getOriginalOrder());
            summary.setOptimizationMessage("Route optimization unavailable due to calculation failure.");
            return summary;
        }

        // Consistent road total: sum of actual valid consecutive segments
        summary.setTotalDistanceKm(Math.round(totalDistanceKm * 10.0) / 10.0);
        summary.setTotalTravelTimeMinutes(totalMinutes);
        summary.setRouteCalculationSuccess(true);

        // Build Google Maps Multi-Waypoint Deep Link
        String originLocation = startLocation.trim();
        String finalLocation = cleanDestinations.get(cleanDestinations.size() - 1);
        List<String> intermediateWaypoints = new ArrayList<>();
        if (cleanDestinations.size() > 1) {
            for (int i = 0; i < cleanDestinations.size() - 1; i++) {
                intermediateWaypoints.add(cleanDestinations.get(i));
            }
        }
        summary.setGoogleMapsFullRouteUrl(GeoUtils.buildGoogleMapsMultiWaypointUrl(originLocation, finalLocation, intermediateWaypoints, mode));

        // Evaluate Smart Route Optimization & Multi-Strategy Alternatives
        evaluateRouteOptimization(summary, startLocation.trim(), cleanDestinations, mode, totalDistanceKm, totalMinutes);

        return summary;
    }

    private void evaluateRouteOptimization(RouteSummaryDto summary, String startLocation, List<String> destinations,
                                           String transportMode, double currentTotalDist, int currentTotalTime) {
        List<RouteAlternativeDto> alternatives = new ArrayList<>();

        // 1. Baseline Alternative: Current Order
        List<String> currentFullPath = new ArrayList<>();
        currentFullPath.add(startLocation);
        currentFullPath.addAll(destinations);

        RouteAlternativeDto currentAlt = new RouteAlternativeDto(
                "ORIGINAL_ROUTE",
                "Current Route Sequence",
                "Your original stop order with consecutive highway routing.",
                currentFullPath,
                summary.getTotalDistanceKm(),
                summary.getTotalTravelTimeMinutes(),
                TimeUtils.formatDuration(summary.getTotalTravelTimeMinutes()),
                0.0,
                0,
                false,
                List.of("Preserves exact planned sequence", destinations.size() + " stops total")
        );
        alternatives.add(currentAlt);

        if (destinations == null || destinations.size() < 2) {
            summary.setAlternatives(alternatives);
            summary.setOptimizationRecommended(false);
            summary.setRecommendedOrder(summary.getOriginalOrder());
            summary.setPotentialSavingsKm(0.0);
            summary.setPotentialSavingsMinutes(0);
            summary.setRecommendedDistanceKm(summary.getTotalDistanceKm());
            summary.setRecommendedTravelTimeMinutes(summary.getTotalTravelTimeMinutes());
            summary.setOptimizationMessage("At least 2 destinations are required to evaluate alternative route sequences.");
            return;
        }

        // 2. Solve optimal destination order using actual road distance matrix (SHORTEST_DISTANCE)
        List<String> shortestDistDestinations = solveOptimalRouteOrder(startLocation, destinations, transportMode);
        double shortestDist = 0.0;
        int shortestTime = 0;
        String prev = startLocation;
        for (String dest : shortestDistDestinations) {
            GoogleMapsService.RouteCalculationResult r = googleMapsService.calculateRouteSegment(prev, dest, transportMode);
            shortestDist += r.getDistanceKm();
            shortestTime += r.getDurationMinutes();
            prev = dest;
        }
        shortestDist = Math.round(shortestDist * 10.0) / 10.0;

        List<String> shortestFullPath = new ArrayList<>();
        shortestFullPath.add(startLocation);
        shortestFullPath.addAll(shortestDistDestinations);

        double shortestSavingsKm = Math.round((currentTotalDist - shortestDist) * 10.0) / 10.0;
        int shortestSavingsMins = currentTotalTime - shortestTime;

        boolean shortestDiffers = isSequenceDifferent(destinations, shortestDistDestinations);

        RouteAlternativeDto shortestDistAlt = new RouteAlternativeDto(
                "SHORTEST_DISTANCE",
                "Shortest Road Distance",
                shortestSavingsKm > 0
                        ? String.format(Locale.US, "Reduces driving distance by %.1f km (%s min less road travel) while covering all destinations.", shortestSavingsKm, shortestSavingsMins)
                        : "Streamlined highway sequence minimizing total mileage.",
                shortestFullPath,
                shortestDist,
                shortestTime,
                TimeUtils.formatDuration(shortestTime),
                Math.max(0.0, shortestSavingsKm),
                Math.max(0, shortestSavingsMins),
                shortestDiffers && shortestSavingsKm >= 1.5 && shortestSavingsMins >= 5,
                List.of("Optimized highway connectivity", "Least fuel consumption", "Reduced road fatigue")
        );
        alternatives.add(shortestDistAlt);

        // 3. Balanced Relaxed Alternative (FASTEST_TIME / BALANCED_PACING)
        // Reverse or cluster if advantageous
        List<String> relaxedDestinations = new ArrayList<>(shortestDistDestinations);
        if (destinations.size() >= 3) {
            // Evaluate alternative cluster sequence
            List<String> reversed = new ArrayList<>(shortestDistDestinations);
            Collections.reverse(reversed);
            double revDist = 0.0;
            int revTime = 0;
            String rPrev = startLocation;
            for (String d : reversed) {
                GoogleMapsService.RouteCalculationResult r = googleMapsService.calculateRouteSegment(rPrev, d, transportMode);
                revDist += r.getDistanceKm();
                revTime += r.getDurationMinutes();
                rPrev = d;
            }
            revDist = Math.round(revDist * 10.0) / 10.0;
            if (revTime < shortestTime) {
                relaxedDestinations = reversed;
            }
        }

        List<String> relaxedFullPath = new ArrayList<>();
        relaxedFullPath.add(startLocation);
        relaxedFullPath.addAll(relaxedDestinations);

        RouteAlternativeDto relaxedAlt = new RouteAlternativeDto(
                "BALANCED_RELAXED",
                "Balanced & Scenic Pacing",
                "Smooth travel flow balancing driving hours and allowing ample sightseeing time at each destination.",
                relaxedFullPath,
                shortestDist,
                shortestTime,
                TimeUtils.formatDuration(shortestTime),
                Math.max(0.0, shortestSavingsKm),
                Math.max(0, shortestSavingsMins),
                false,
                List.of("Comfortable daily driving limits", "Sightseeing prioritized", "Family & leisure friendly")
        );
        alternatives.add(relaxedAlt);

        summary.setAlternatives(alternatives);

        // Recommend ONLY when genuine positive savings exist (>= 1.5 km AND >= 5 min)
        if (shortestDiffers && shortestSavingsKm >= 1.5 && shortestSavingsMins >= 5) {
            summary.setOptimizationRecommended(true);
            summary.setRecommendedOrder(shortestFullPath);
            summary.setRecommendedDistanceKm(shortestDist);
            summary.setRecommendedTravelTimeMinutes(shortestTime);
            summary.setPotentialSavingsKm(shortestSavingsKm);
            summary.setPotentialSavingsMinutes(shortestSavingsMins);
            summary.setOptimizationMessage(String.format(Locale.US,
                    "Alternative route: %.1f km shorter, approximately %d minutes less driving, and still includes your %d must-visit destinations.",
                    shortestSavingsKm, shortestSavingsMins, destinations.size()));
        } else {
            summary.setOptimizationRecommended(false);
            summary.setRecommendedOrder(summary.getOriginalOrder());
            summary.setPotentialSavingsKm(0.0);
            summary.setPotentialSavingsMinutes(0);
            summary.setRecommendedDistanceKm(summary.getTotalDistanceKm());
            summary.setRecommendedTravelTimeMinutes(summary.getTotalTravelTimeMinutes());
            summary.setOptimizationMessage("The current route is already the most efficient order found under the selected constraints.");
        }
    }

    private boolean isSequenceDifferent(List<String> listA, List<String> listB) {
        if (listA.size() != listB.size()) return true;
        for (int i = 0; i < listA.size(); i++) {
            if (!listA.get(i).equalsIgnoreCase(listB.get(i))) {
                return true;
            }
        }
        return false;
    }

    private List<String> solveOptimalRouteOrder(String startLocation, List<String> destinations, String transportMode) {
        int n = destinations.size();
        if (n <= 1) {
            return new ArrayList<>(destinations);
        }

        int totalNodes = n + 1;
        List<String> allNodes = new ArrayList<>();
        allNodes.add(startLocation);
        allNodes.addAll(destinations);

        // Build Distance Matrix using actual road distance from routing service
        double[][] distMatrix = new double[totalNodes][totalNodes];
        for (int i = 0; i < totalNodes; i++) {
            for (int j = 0; j < totalNodes; j++) {
                if (i == j) {
                    distMatrix[i][j] = 0.0;
                } else {
                    GoogleMapsService.RouteCalculationResult leg =
                            googleMapsService.calculateRouteSegment(allNodes.get(i), allNodes.get(j), transportMode);
                    distMatrix[i][j] = (leg != null) ? leg.getDistanceKm() : 0.0;
                }
            }
        }

        if (n <= 8) {
            // Exact Permutation Search for up to 8 destinations (<= 40,320 permutations)
            List<Integer> bestPermutation = null;
            double minCost = Double.MAX_VALUE;

            List<Integer> indices = new ArrayList<>();
            for (int i = 1; i <= n; i++) indices.add(i);

            List<List<Integer>> permutations = new ArrayList<>();
            generatePermutations(indices, 0, permutations);

            for (List<Integer> perm : permutations) {
                double cost = distMatrix[0][perm.get(0)];
                for (int k = 0; k < perm.size() - 1; k++) {
                    cost += distMatrix[perm.get(k)][perm.get(k + 1)];
                }
                if (cost < minCost) {
                    minCost = cost;
                    bestPermutation = perm;
                }
            }

            if (bestPermutation != null) {
                List<String> result = new ArrayList<>();
                for (int idx : bestPermutation) {
                    result.add(destinations.get(idx - 1));
                }
                return result;
            }
        }

        // For N > 8: Nearest Neighbor + 2-Opt local refinement
        return solveTspNearestNeighbor2Opt(startLocation, destinations, distMatrix);
    }

    private void generatePermutations(List<Integer> arr, int k, List<List<Integer>> result) {
        if (k == arr.size()) {
            result.add(new ArrayList<>(arr));
            return;
        }
        for (int i = k; i < arr.size(); i++) {
            Collections.swap(arr, i, k);
            generatePermutations(arr, k + 1, result);
            Collections.swap(arr, i, k);
        }
    }

    private List<String> solveTspNearestNeighbor2Opt(String startLocation, List<String> destinations, double[][] distMatrix) {
        int n = destinations.size();
        boolean[] visited = new boolean[n + 1];
        List<Integer> routeIndices = new ArrayList<>();
        int current = 0; // startLocation is index 0

        // Greedy Nearest Neighbor
        for (int step = 0; step < n; step++) {
            int nextBest = -1;
            double bestDist = Double.MAX_VALUE;
            for (int j = 1; j <= n; j++) {
                if (!visited[j] && distMatrix[current][j] < bestDist) {
                    bestDist = distMatrix[current][j];
                    nextBest = j;
                }
            }
            if (nextBest != -1) {
                visited[nextBest] = true;
                routeIndices.add(nextBest);
                current = nextBest;
            }
        }

        // 2-Opt refinement using road distance matrix
        if (routeIndices.size() >= 3) {
            boolean improved = true;
            int passes = 0;
            while (improved && passes < 20) {
                improved = false;
                passes++;
                for (int i = 0; i < routeIndices.size() - 1; i++) {
                    for (int k = i + 1; k < routeIndices.size(); k++) {
                        double delta = evaluate2OptSwapWithMatrix(distMatrix, routeIndices, i, k);
                        if (delta < -0.1) {
                            Collections.reverse(routeIndices.subList(i, k + 1));
                            improved = true;
                        }
                    }
                }
            }
        }

        List<String> result = new ArrayList<>();
        for (int idx : routeIndices) {
            result.add(destinations.get(idx - 1));
        }
        return result;
    }

    private double evaluate2OptSwapWithMatrix(double[][] matrix, List<Integer> route, int i, int k) {
        int uA = (i == 0) ? 0 : route.get(i - 1);
        int uB = route.get(i);
        int uC = route.get(k);
        int uD = (k == route.size() - 1) ? -1 : route.get(k + 1);

        double currentCost = matrix[uA][uB];
        if (uD != -1) currentCost += matrix[uC][uD];

        double newCost = matrix[uA][uC];
        if (uD != -1) newCost += matrix[uB][uD];

        return newCost - currentCost;
    }
}
