package com.example.tripplanner.service;

import com.example.tripplanner.dto.RouteSegmentDto;
import com.example.tripplanner.dto.RouteSummaryDto;
import com.example.tripplanner.util.GeoUtils;
import com.example.tripplanner.util.TimeUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class RouteService {

    private final GoogleMapsService googleMapsService;

    public RouteService(GoogleMapsService googleMapsService) {
        this.googleMapsService = googleMapsService;
    }

    public RouteSummaryDto calculateCompleteRoute(String startLocation, List<String> destinations, String transportMode) {
        RouteSummaryDto summary = new RouteSummaryDto();
        summary.setTransportMode(transportMode != null ? transportMode : "CAR");

        if (startLocation == null || destinations == null || destinations.isEmpty()) {
            return summary;
        }

        List<String> fullPath = new ArrayList<>();
        fullPath.add(startLocation);
        fullPath.addAll(destinations);

        summary.setOriginalOrder(new ArrayList<>(fullPath));
        summary.setTotalDestinations(destinations.size());

        List<RouteSegmentDto> segments = new ArrayList<>();
        double totalDistanceKm = 0.0;
        int totalMinutes = 0;

        for (int i = 0; i < fullPath.size() - 1; i++) {
            String origin = fullPath.get(i);
            String destination = fullPath.get(i + 1);

            GoogleMapsService.RouteCalculationResult result = googleMapsService.calculateRouteSegment(origin, destination, transportMode);

            RouteSegmentDto segment = new RouteSegmentDto();
            segment.setFromLocation(origin);
            segment.setToLocation(destination);
            segment.setDistanceKm(result.getDistanceKm());
            segment.setDurationMinutes(result.getDurationMinutes());
            segment.setTransportMode(transportMode);
            segment.setFromLat(result.getOriginCoords()[0]);
            segment.setFromLng(result.getOriginCoords()[1]);
            segment.setToLat(result.getDestinationCoords()[0]);
            segment.setToLng(result.getDestinationCoords()[1]);

            segment.setSummaryText(String.format("%s km • %s",
                    result.getDistanceKm(),
                    TimeUtils.formatDuration(result.getDurationMinutes())
            ));

            segment.setGoogleMapsSegmentUrl(GeoUtils.buildGoogleMapsUrl(origin, destination, transportMode));

            segments.add(segment);
            totalDistanceKm += result.getDistanceKm();
            totalMinutes += result.getDurationMinutes();
        }

        summary.setSegments(segments);
        summary.setTotalDistanceKm(Math.round(totalDistanceKm * 10.0) / 10.0);
        summary.setTotalTravelTimeMinutes(totalMinutes);

        // Build Google Maps Multi-Waypoint Deep Link
        String originLocation = startLocation;
        String finalLocation = destinations.get(destinations.size() - 1);
        List<String> intermediateWaypoints = new ArrayList<>();
        if (destinations.size() > 1) {
            for (int i = 0; i < destinations.size() - 1; i++) {
                intermediateWaypoints.add(destinations.get(i));
            }
        }
        summary.setGoogleMapsFullRouteUrl(GeoUtils.buildGoogleMapsMultiWaypointUrl(originLocation, finalLocation, intermediateWaypoints, transportMode));

        // Evaluate Smart Route Optimization (TSP 2-opt / nearest neighbor)
        evaluateRouteOptimization(summary, startLocation, destinations, transportMode, totalDistanceKm, totalMinutes);

        return summary;
    }

    private void evaluateRouteOptimization(RouteSummaryDto summary, String startLocation, List<String> destinations,
                                           String transportMode, double currentTotalDist, int currentTotalTime) {
        if (destinations.size() < 3) {
            summary.setOptimizationRecommended(false);
            summary.setRecommendedOrder(summary.getOriginalOrder());
            return;
        }

        // Run TSP Nearest Neighbor + 2-Opt permutation search
        List<String> optimizedDestinations = solveTspRoute(startLocation, new ArrayList<>(destinations));

        // Calculate distance of the optimized route
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

        // Only recommend if significant savings (e.g. saves at least 15 km or 5% of travel)
        double savingsKm = Math.round((currentTotalDist - optimizedDist) * 10.0) / 10.0;
        int savingsMins = Math.max(0, currentTotalTime - optimizedTime);

        if (savingsKm >= 15.0 && savingsKm > (currentTotalDist * 0.05)) {
            summary.setOptimizationRecommended(true);
            summary.setPotentialSavingsKm(savingsKm);
            summary.setPotentialSavingsMinutes(savingsMins);
        } else {
            summary.setOptimizationRecommended(false);
            summary.setPotentialSavingsKm(0.0);
            summary.setPotentialSavingsMinutes(0);
        }
    }

    private List<String> solveTspRoute(String startLocation, List<String> destinations) {
        List<String> unvisited = new ArrayList<>(destinations);
        List<String> route = new ArrayList<>();
        String current = startLocation;

        // Nearest neighbor greedy pass
        while (!unvisited.isEmpty()) {
            double[] currentCoords = GeoUtils.getApproximateCoordinates(current);
            String bestNext = null;
            double minDistance = Double.MAX_VALUE;

            for (String candidate : unvisited) {
                double[] candCoords = GeoUtils.getApproximateCoordinates(candidate);
                double dist = GeoUtils.estimateRoadDistanceKm(currentCoords[0], currentCoords[1], candCoords[0], candCoords[1]);
                if (dist < minDistance) {
                    minDistance = dist;
                    bestNext = candidate;
                }
            }

            if (bestNext != null) {
                route.add(bestNext);
                unvisited.remove(bestNext);
                current = bestNext;
            } else {
                break;
            }
        }

        // 2-Opt refinement for fine-tuning
        if (route.size() >= 4) {
            boolean improved = true;
            int passes = 0;
            while (improved && passes < 10) {
                improved = false;
                passes++;
                for (int i = 0; i < route.size() - 1; i++) {
                    for (int k = i + 1; k < route.size(); k++) {
                        double delta = evaluate2OptSwap(startLocation, route, i, k);
                        if (delta < -1.0) { // Improvement detected
                            Collections.reverse(route.subList(i, k + 1));
                            improved = true;
                        }
                    }
                }
            }
        }

        return route;
    }

    private double evaluate2OptSwap(String start, List<String> route, int i, int k) {
        // Evaluate cost change if sublist(i..k) is reversed
        double[] pA = (i == 0) ? GeoUtils.getApproximateCoordinates(start) : GeoUtils.getApproximateCoordinates(route.get(i - 1));
        double[] pB = GeoUtils.getApproximateCoordinates(route.get(i));
        double[] pC = GeoUtils.getApproximateCoordinates(route.get(k));
        double[] pD = (k == route.size() - 1) ? null : GeoUtils.getApproximateCoordinates(route.get(k + 1));

        double currentDist = GeoUtils.estimateRoadDistanceKm(pA[0], pA[1], pB[0], pB[1]);
        if (pD != null) {
            currentDist += GeoUtils.estimateRoadDistanceKm(pC[0], pC[1], pD[0], pD[1]);
        }

        double newDist = GeoUtils.estimateRoadDistanceKm(pA[0], pA[1], pC[0], pC[1]);
        if (pD != null) {
            newDist += GeoUtils.estimateRoadDistanceKm(pB[0], pB[1], pD[0], pD[1]);
        }

        return newDist - currentDist;
    }
}
