package com.example.tripplanner.service;

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

            if (!result.isSuccess()) {
                allSegmentsSuccessful = false;
                errorMessage = result.getErrorMessage() != null ? result.getErrorMessage() : "Unable to calculate the current route. Please try again.";
            }

            RouteSegmentDto segment = new RouteSegmentDto();
            segment.setFromLocation(origin);
            segment.setToLocation(destination);
            segment.setDistanceKm(result.getDistanceKm());
            segment.setDurationMinutes(result.getDurationMinutes());
            segment.setTransportMode(mode);
            segment.setFromLat(result.getOriginCoords()[0]);
            segment.setFromLng(result.getOriginCoords()[1]);
            segment.setToLat(result.getDestinationCoords()[0]);
            segment.setToLng(result.getDestinationCoords()[1]);

            segment.setSummaryText(String.format(Locale.US, "%.1f km • %s",
                    result.getDistanceKm(),
                    TimeUtils.formatDuration(result.getDurationMinutes())
            ));

            segment.setGoogleMapsSegmentUrl(GeoUtils.buildGoogleMapsUrl(origin, destination, mode));
            segment.setGeometryCoordinates(result.getGeometryCoords());
            if (result.getGeometryCoords() != null && !result.getGeometryCoords().isEmpty()) {
                summary.getFullRouteGeometry().addAll(result.getGeometryCoords());
            } else {
                summary.getFullRouteGeometry().add(new double[]{result.getOriginCoords()[0], result.getOriginCoords()[1]});
                summary.getFullRouteGeometry().add(new double[]{result.getDestinationCoords()[0], result.getDestinationCoords()[1]});
            }

            segments.add(segment);
            totalDistanceKm += result.getDistanceKm();
            totalMinutes += result.getDurationMinutes();
        }

        summary.setSegments(segments);
        // Consistent road total: sum of actual consecutive segments
        summary.setTotalDistanceKm(Math.round(totalDistanceKm * 10.0) / 10.0);
        summary.setTotalTravelTimeMinutes(totalMinutes);
        summary.setRouteCalculationSuccess(allSegmentsSuccessful);
        if (!allSegmentsSuccessful) {
            summary.setCalculationErrorMessage(errorMessage != null ? errorMessage : "Unable to calculate the current route. Please try again.");
        }

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

        // Evaluate Smart Route Optimization using actual road routes
        if (allSegmentsSuccessful) {
            evaluateRouteOptimization(summary, startLocation.trim(), cleanDestinations, mode, totalDistanceKm, totalMinutes);
        } else {
            summary.setOptimizationRecommended(false);
            summary.setRecommendedOrder(summary.getOriginalOrder());
            summary.setOptimizationMessage("Route optimization unavailable due to calculation failure.");
        }

        return summary;
    }

    private void evaluateRouteOptimization(RouteSummaryDto summary, String startLocation, List<String> destinations,
                                           String transportMode, double currentTotalDist, int currentTotalTime) {
        if (destinations == null || destinations.size() < 2) {
            summary.setOptimizationRecommended(false);
            summary.setRecommendedOrder(summary.getOriginalOrder());
            summary.setPotentialSavingsKm(0.0);
            summary.setPotentialSavingsMinutes(0);
            summary.setRecommendedDistanceKm(summary.getTotalDistanceKm());
            summary.setRecommendedTravelTimeMinutes(summary.getTotalTravelTimeMinutes());
            summary.setOptimizationMessage("At least 2 destinations are required to evaluate alternative route sequences.");
            return;
        }

        // Solve optimal destination order using actual road distance matrix
        List<String> optimizedDestinations = solveOptimalRouteOrder(startLocation, destinations, transportMode);

        // Calculate exact road metrics of the recommended route
        double optimizedDist = 0.0;
        int optimizedTime = 0;
        String prev = startLocation;

        for (String dest : optimizedDestinations) {
            GoogleMapsService.RouteCalculationResult r = googleMapsService.calculateRouteSegment(prev, dest, transportMode);
            optimizedDist += r.getDistanceKm();
            optimizedTime += r.getDurationMinutes();
            prev = dest;
        }

        optimizedDist = Math.round(optimizedDist * 10.0) / 10.0;

        List<String> recommendedFullPath = new ArrayList<>();
        recommendedFullPath.add(startLocation);
        recommendedFullPath.addAll(optimizedDestinations);
        summary.setRecommendedOrder(recommendedFullPath);
        summary.setRecommendedDistanceKm(optimizedDist);
        summary.setRecommendedTravelTimeMinutes(optimizedTime);

        // Check if the order is genuinely different
        boolean isSameOrder = true;
        if (optimizedDestinations.size() == destinations.size()) {
            for (int i = 0; i < destinations.size(); i++) {
                if (!destinations.get(i).equalsIgnoreCase(optimizedDestinations.get(i))) {
                    isSameOrder = false;
                    break;
                }
            }
        } else {
            isSameOrder = false;
        }

        double savingsKm = Math.round((currentTotalDist - optimizedDist) * 10.0) / 10.0;
        int savingsMins = currentTotalTime - optimizedTime;

        // Recommend ONLY when genuine positive savings exist (>= 1.5 km AND >= 5 min)
        if (!isSameOrder && savingsKm >= 1.5 && savingsMins >= 5) {
            summary.setOptimizationRecommended(true);
            summary.setPotentialSavingsKm(Math.max(0.0, savingsKm));
            summary.setPotentialSavingsMinutes(Math.max(0, savingsMins));
            summary.setOptimizationMessage(String.format(Locale.US,
                    "Smart Route Optimization Available! Save %s km and %s min of travel by reordering your stops.",
                    summary.getPotentialSavingsKm(), summary.getPotentialSavingsMinutes()));
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
