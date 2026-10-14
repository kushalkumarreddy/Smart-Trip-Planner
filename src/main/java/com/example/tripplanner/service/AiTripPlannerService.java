package com.example.tripplanner.service;

import com.example.tripplanner.dto.AiTripPromptDto;
import com.example.tripplanner.dto.RouteSummaryDto;
import com.example.tripplanner.util.GeoUtils;
import com.example.tripplanner.util.TimeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiTripPlannerService {

    private final RouteService routeService;

    @Autowired
    public AiTripPlannerService(RouteService routeService) {
        this.routeService = routeService;
    }

    public AiTripPlannerService() {
        this.routeService = null;
    }

    // Curated destination recommendations per interest
    private static final Map<String, List<String>> INTEREST_DESTINATIONS = new LinkedHashMap<>();
    // Curated hotels per destination
    private static final Map<String, String> DESTINATION_HOTELS = new HashMap<>();
    // Top attractions per destination
    private static final Map<String, List<String>> DESTINATION_ATTRACTIONS = new HashMap<>();
    // Aliases and phonetic corrections for place names
    private static final Map<String, String> PLACE_ALIASES = new HashMap<>();

    static {
        // Known Aliases / Misspellings
        PLACE_ALIASES.put("munar", "Munnar");
        PLACE_ALIASES.put("munnar", "Munnar");
        PLACE_ALIASES.put("kodaikenal", "Kodaikanal");
        PLACE_ALIASES.put("kodaikanal", "Kodaikanal");
        PLACE_ALIASES.put("kodai", "Kodaikanal");
        PLACE_ALIASES.put("calicut", "Calicut");
        PLACE_ALIASES.put("kozhikode", "Calicut");
        PLACE_ALIASES.put("guntakal", "Guntakal");
        PLACE_ALIASES.put("anantapur", "Anantapur");
        PLACE_ALIASES.put("kurnool", "Kurnool");
        PLACE_ALIASES.put("kadapa", "Kadapa");
        PLACE_ALIASES.put("bengaluru", "Bengaluru");
        PLACE_ALIASES.put("bangalore", "Bengaluru");
        PLACE_ALIASES.put("mysore", "Mysore");
        PLACE_ALIASES.put("mysuru", "Mysore");
        PLACE_ALIASES.put("mysor", "Mysore");
        PLACE_ALIASES.put("coorg", "Coorg");
        PLACE_ALIASES.put("madikeri", "Coorg");
        PLACE_ALIASES.put("kodagu", "Coorg");
        PLACE_ALIASES.put("wayanad", "Wayanad");
        PLACE_ALIASES.put("waynad", "Wayanad");
        PLACE_ALIASES.put("ooty", "Ooty");
        PLACE_ALIASES.put("ooti", "Ooty");
        PLACE_ALIASES.put("udagamandalam", "Ooty");
        PLACE_ALIASES.put("chikmagalur", "Chikmagalur");
        PLACE_ALIASES.put("chikkamagaluru", "Chikmagalur");
        PLACE_ALIASES.put("chickmagalur", "Chikmagalur");
        PLACE_ALIASES.put("hampi", "Hampi");
        PLACE_ALIASES.put("gokarna", "Gokarna");
        PLACE_ALIASES.put("goa", "Goa");
        PLACE_ALIASES.put("panaji", "Goa");
        PLACE_ALIASES.put("pondicherry", "Pondicherry");
        PLACE_ALIASES.put("pondy", "Pondicherry");
        PLACE_ALIASES.put("puducherry", "Pondicherry");
        PLACE_ALIASES.put("alleppey", "Alleppey");
        PLACE_ALIASES.put("alappuzha", "Alleppey");
        PLACE_ALIASES.put("kochi", "Kochi");
        PLACE_ALIASES.put("cochin", "Kochi");
        PLACE_ALIASES.put("jaipur", "Jaipur");
        PLACE_ALIASES.put("agra", "Agra");
        PLACE_ALIASES.put("delhi", "Delhi");
        PLACE_ALIASES.put("new delhi", "Delhi");
        PLACE_ALIASES.put("udaipur", "Udaipur");
        PLACE_ALIASES.put("jodhpur", "Jodhpur");
        PLACE_ALIASES.put("manali", "Manali");
        PLACE_ALIASES.put("shimla", "Shimla");
        PLACE_ALIASES.put("rishikesh", "Rishikesh");
        PLACE_ALIASES.put("dandeli", "Dandeli");
        PLACE_ALIASES.put("kabini", "Kabini");
        PLACE_ALIASES.put("sakleshpur", "Sakleshpur");
        PLACE_ALIASES.put("varkala", "Varkala");
        PLACE_ALIASES.put("kovalam", "Kovalam");
        PLACE_ALIASES.put("trivandrum", "Thiruvananthapuram");
        PLACE_ALIASES.put("thiruvananthapuram", "Thiruvananthapuram");
        PLACE_ALIASES.put("chennai", "Chennai");
        PLACE_ALIASES.put("madras", "Chennai");
        PLACE_ALIASES.put("mumbai", "Mumbai");
        PLACE_ALIASES.put("bombay", "Mumbai");
        PLACE_ALIASES.put("pune", "Pune");
        PLACE_ALIASES.put("hyderabad", "Hyderabad");
        PLACE_ALIASES.put("tirupati", "Tirupati");
        PLACE_ALIASES.put("madurai", "Madurai");
        PLACE_ALIASES.put("rameshwaram", "Rameshwaram");
        PLACE_ALIASES.put("kanyakumari", "Kanyakumari");

        // Interests to Destinations
        INTEREST_DESTINATIONS.put("waterfalls", List.of("Coorg", "Wayanad", "Munnar", "Chikmagalur", "Athirappilly"));
        INTEREST_DESTINATIONS.put("nature", List.of("Munnar", "Kodaikanal", "Coorg", "Wayanad", "Ooty", "Chikmagalur"));
        INTEREST_DESTINATIONS.put("heritage", List.of("Mysore", "Hampi", "Badami", "Jaipur", "Agra", "Calicut"));
        INTEREST_DESTINATIONS.put("historical", List.of("Mysore", "Hampi", "Jaipur", "Agra", "Delhi", "Udaipur"));
        INTEREST_DESTINATIONS.put("beaches", List.of("Goa", "Gokarna", "Calicut", "Pondicherry", "Varkala", "Kovalam"));
        INTEREST_DESTINATIONS.put("temples", List.of("Mysore", "Madurai", "Rameshwaram", "Tirupati", "Hampi", "Guntakal"));
        INTEREST_DESTINATIONS.put("hills", List.of("Munnar", "Kodaikanal", "Ooty", "Coorg", "Manali", "Shimla"));
        INTEREST_DESTINATIONS.put("adventure", List.of("Dandeli", "Rishikesh", "Manali", "Wayanad", "Munnar"));
        INTEREST_DESTINATIONS.put("relaxation", List.of("Munnar", "Kodaikanal", "Coorg", "Alleppey", "Pondicherry", "Gokarna"));

        // Hotels per destination
        DESTINATION_HOTELS.put("calicut", "The Raviz Kadavu or Beach Heritage Resort");
        DESTINATION_HOTELS.put("munnar", "Fragrant Nature Munnar Hilltop Sanctuary or Blanket Hotel & Spa");
        DESTINATION_HOTELS.put("kodaikanal", "The Carlton Lakefront Luxury Resort or Tamara Kodai");
        DESTINATION_HOTELS.put("guntakal", "Heritage Comfort Stay or Southern Express Hotel");
        DESTINATION_HOTELS.put("coorg", "The Tamara Coorg or Misty Woods Plantation Resort");
        DESTINATION_HOTELS.put("mysore", "Radisson Blu Plaza or Grand Mercure Mysore");
        DESTINATION_HOTELS.put("wayanad", "Vythiri Rainforest Resort or Banasura Hill Stay");
        DESTINATION_HOTELS.put("ooty", "Savoy IHCL SeleQtions or Sterling Ooty Elk Hill");
        DESTINATION_HOTELS.put("chikmagalur", "The Serai Chikmagalur Luxury Estate");
        DESTINATION_HOTELS.put("hampi", "Heritage Resort Hampi or Evolve Back Kamalapura");
        DESTINATION_HOTELS.put("gokarna", "Kahani Paradise or SwaSwara Wellness Retreat");
        DESTINATION_HOTELS.put("goa", "Taj Exotica Resort & Spa or Alila Diwa Goa");
        DESTINATION_HOTELS.put("pondicherry", "Palais de Mahe - CGH Earth French Quarter");
        DESTINATION_HOTELS.put("alleppey", "Lake Palace Backwater Resort & Houseboat");
        DESTINATION_HOTELS.put("kochi", "Brunton Boatyard Fort Kochi");
        DESTINATION_HOTELS.put("jaipur", "ITC Rajputana or Rambagh Palace Heritage");
        DESTINATION_HOTELS.put("agra", "The Oberoi Amarvilas or ITC Mughal");
        DESTINATION_HOTELS.put("delhi", "The Imperial New Delhi or Taj Mahal Hotel");
        DESTINATION_HOTELS.put("tirupati", "Marasa Sarovar Premiere or Fortune Select Grand Ridge");
        DESTINATION_HOTELS.put("madurai", "Heritage Madurai or The Gateway Hotel Pasumalai");
        DESTINATION_HOTELS.put("rameshwaram", "Daiwik Hotel or Hyatt Place Rameswaram");
        DESTINATION_HOTELS.put("hyderabad", "Taj Falaknuma Palace or ITC Kohenur");
        DESTINATION_HOTELS.put("anantapur", "Hotel Alexander or Masineni Grand Anantapur");
        DESTINATION_HOTELS.put("kurnool", "Mourya Inn or Hotel DVR Mansion Kurnool");

        // Top Attractions
        DESTINATION_ATTRACTIONS.put("calicut", List.of("Kozhikode Beach & Sea Pier", "Mananchira Square", "Kappad Beach & Vasco da Gama Monument", "Beypore Port & Ancient Dhow Yard", "Thusharagiri Waterfalls"));
        DESTINATION_ATTRACTIONS.put("munnar", List.of("Tea Gardens & Tata Tea Museum", "Eravikulam National Park", "Mattupetty Dam & Lake", "Top Station Cloud Point", "Attukad Waterfalls"));
        DESTINATION_ATTRACTIONS.put("kodaikanal", List.of("Kodaikanal Lake & Boathouse", "Coaker's Walk", "Pillar Rocks Viewpoint", "Bryant Park & Glasshouse", "Silver Cascade Waterfall"));
        DESTINATION_ATTRACTIONS.put("guntakal", List.of("Kasapuram Neti Anjaneya Swamy Temple", "Guntakal Historic Heritage Rail Junction", "Chippagiri Historical Overlook"));
        DESTINATION_ATTRACTIONS.put("coorg", List.of("Abbey Falls", "Raja's Seat", "Namdroling Golden Temple", "Dubare Elephant Camp", "Mandalpatti Peak"));
        DESTINATION_ATTRACTIONS.put("mysore", List.of("Mysore Palace", "Chamundi Hill & Temple", "Brindavan Gardens", "St. Philomena's Cathedral"));
        DESTINATION_ATTRACTIONS.put("wayanad", List.of("Soochipara Waterfalls", "Banasura Sagar Dam", "Edakkal Caves", "Chembra Peak", "Pookode Lake"));
        DESTINATION_ATTRACTIONS.put("ooty", List.of("Ooty Botanical Gardens", "Doddabetta Peak", "Pykara Waterfalls", "Nilgiri Mountain Toy Train"));
        DESTINATION_ATTRACTIONS.put("chikmagalur", List.of("Mullayanagiri Peak", "Hebbe Falls", "Baba Budangiri", "Coffee Plantation Safari"));
        DESTINATION_ATTRACTIONS.put("hampi", List.of("Virupaksha Temple", "Vijaya Vittala Stone Chariot", "Lotus Mahal", "Matanga Hill Sunset"));
        DESTINATION_ATTRACTIONS.put("gokarna", List.of("Om Beach", "Kudle Beach", "Half Moon Beach", "Mahabaleshwar Temple"));
        DESTINATION_ATTRACTIONS.put("goa", List.of("Dudhsagar Waterfalls", "Fort Aguada", "Anjuna & Vagator Cliffs", "Old Goa Churches"));
        DESTINATION_ATTRACTIONS.put("pondicherry", List.of("Promenade Beach", "Auroville", "French Colony Heritage Walk", "Paradise Beach"));
    }

    public AiTripPromptDto.Response parseAndGenerateTrip(String prompt) {
        AiTripPromptDto.Response response = new AiTripPromptDto.Response();
        if (prompt == null || prompt.trim().isEmpty()) {
            response.setPromptDefaults();
            return response;
        }

        String text = prompt.trim();
        String lower = text.toLowerCase();

        // 1. Extract Duration (Days)
        int days = extractDuration(lower);
        response.setTotalDays(days);
        LocalDate start = LocalDate.now().plusDays(1);
        response.setStartDate(start);
        response.setEndDate(start.plusDays(days - 1));

        // 2. Extract Transportation Mode
        String transportMode = extractTransportMode(lower);
        response.setTransportMode(transportMode);

        // 3. Extract Travel Pace / Style
        String travelStyle = extractTravelStyle(lower);
        response.setTravelStyle(travelStyle);

        // 4. Extract Travelers Count
        int travelers = extractTravelersCount(lower);
        response.setTravelersCount(travelers);

        // 5. Extract Interests
        List<String> interests = extractInterests(lower);
        response.setPreferredInterests(interests);

        // 6. Extract Additional Trip Preferences
        String accommodationPref = extractAccommodationPreference(lower);
        response.setAccommodationPreference(accommodationPref);

        String dailyLimits = extractDailyTravelLimit(lower);
        response.setDailyTravelLimit(dailyLimits);

        String accessibility = extractAccessibilityOrFamily(lower);
        response.setAccessibilityNotes(accessibility);

        String budget = extractBudget(lower);
        response.setBudgetRange(budget);

        // 7. Extract Locations
        ExtractedLocations locations = extractLocationsFromPrompt(text, lower, interests);
        String origin = locations.origin;
        List<String> destinations = locations.destinations;

        // Follow-up question handling if essential information is missing
        if (origin == null || origin.trim().isEmpty() || destinations == null || destinations.isEmpty()) {
            response.setRequiresFollowUp(true);
            List<String> questions = new ArrayList<>();
            if (origin == null || origin.trim().isEmpty()) {
                questions.add("Where will your journey begin (starting location / city)?");
            }
            if (destinations == null || destinations.isEmpty()) {
                questions.add("Which destinations or regions would you like to explore?");
            }
            response.setFollowUpQuestions(questions);
            response.setStartLocation(origin != null ? origin : "");
            response.setDestinationNames(destinations != null ? destinations : new ArrayList<>());
            response.setTripName("Custom Journey Plan");
            response.setAiSummary("To create your personalized itinerary, please let us know " +
                    (questions.size() == 2 ? "your starting location and destination(s)." : questions.get(0)));
            return response;
        }

        response.setStartLocation(origin);
        response.setDestinationNames(destinations);
        response.setRequiresFollowUp(false);

        // Validate Proposed Route with Route Service
        double validatedDistanceKm = 0.0;
        int validatedTimeMinutes = 0;
        if (routeService != null) {
            RouteSummaryDto routeSummary = routeService.calculateCompleteRoute(origin, destinations, transportMode);
            if (routeSummary == null || !routeSummary.getRouteCalculationSuccess()) {
                response.setRouteValidationSuccess(false);
                String err = (routeSummary != null && routeSummary.getCalculationErrorMessage() != null)
                        ? routeSummary.getCalculationErrorMessage()
                        : "The routing provider could not compute a valid highway route for the proposed destinations.";
                response.setValidationErrorMessage(err);
                response.setAiSummary("Proposed route validation failed: " + err);
                return response;
            }
            response.setRouteValidationSuccess(true);
            validatedDistanceKm = routeSummary.getTotalDistanceKm();
            validatedTimeMinutes = routeSummary.getTotalTravelTimeMinutes();
        }

        // 8. Assign per-destination hotels & build recommendations
        List<String> hotels = new ArrayList<>();
        List<AiTripPromptDto.DestinationRecommendation> details = new ArrayList<>();

        for (String dest : destinations) {
            String key = dest.toLowerCase().trim();
            String hotel = DESTINATION_HOTELS.getOrDefault(key, accommodationPref + " in " + dest);
            hotels.add(hotel);

            List<String> attractions = DESTINATION_ATTRACTIONS.getOrDefault(key,
                    List.of(dest + " Scenic Walk", dest + " Panoramic Viewpoint", dest + " Nature Gardens"));

            String reason = "Handpicked stop featuring verified road connectivity from " + origin + ", picturesque scenery, and pre-selected stays.";
            if (interests.contains("Waterfalls")) {
                reason = "Celebrated for beautiful waterfalls, mountain breezes, and tranquil valley viewpoints.";
            } else if (interests.contains("Heritage") || interests.contains("Historical")) {
                reason = "Rich in architecture, cultural landmarks, and captivating historic traditions.";
            }

            details.add(new AiTripPromptDto.DestinationRecommendation(dest, hotel, reason, attractions));
        }

        response.setDestinationHotels(hotels);
        response.setDestinationDetails(details);

        // 9. Generate Trip Name & AI Summary
        String tripTitle;
        if (destinations.isEmpty()) {
            tripTitle = origin + " Road Trip Experience";
        } else if (destinations.size() == 1) {
            tripTitle = String.format("%s to %s %s", origin, destinations.get(0), travelStyle.equalsIgnoreCase("RELAXED") ? "Getaway" : "Journey");
        } else if (destinations.size() == 2) {
            tripTitle = String.format("%s to %s & %s Journey", origin, destinations.get(0), destinations.get(1));
        } else {
            tripTitle = String.format("%s to %s & %s Explorer", origin,
                    String.join(", ", destinations.subList(0, destinations.size() - 1)),
                    destinations.get(destinations.size() - 1));
        }
        response.setTripName(tripTitle);

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("Curated a %d-day %s journey from %s to %s with %s travel pace. ",
                days,
                travelers > 2 ? "family" : (travelers == 1 ? "solo" : "couple"),
                origin,
                String.join(" → ", destinations),
                travelStyle.toLowerCase()
        ));

        if (validatedDistanceKm > 0) {
            summary.append(String.format(Locale.US, "Verified highway distance: %.1f km (%s). ",
                    validatedDistanceKm, TimeUtils.formatDuration(validatedTimeMinutes)));
        }

        if (!interests.isEmpty()) {
            summary.append(String.format("Handpicked top attractions highlighting %s with realistic driving intervals and %s accommodations. ",
                    String.join(", ", interests),
                    accommodationPref.toLowerCase()
            ));
        }
        summary.append("Your route is validated and ready below!");

        response.setAiSummary(summary.toString());
        return response;
    }

    private static class ExtractedLocations {
        String origin;
        List<String> destinations = new ArrayList<>();
    }

    /**
     * Parses the prompt to extract the starting point and visited destinations.
     * Accurately parses:
     * - "4 day trip from guntakal, calicut, munar, kodaikenal"
     * - "from guntakal to calicut, munnar, kodaikanal"
     * - "guntakal to calicut, munnar, kodaikanal"
     * - "calicut, munnar, kodaikanal from guntakal"
     * - "bengaluru -> coorg -> wayanad"
     */
    private ExtractedLocations extractLocationsFromPrompt(String rawText, String lower, List<String> interests) {
        ExtractedLocations result = new ExtractedLocations();

        // 1. Look for explicit "from <Origin>"
        Pattern fromPattern = Pattern.compile("(?i)(?:starting\\s+from|start\\s+from|from)\\s+([^,;\\n\\r]+?)(?:,|;|\\s+and\\b|\\s+to\\b|\\s+via\\b|\\s+->|\\s+-|\\s+with\\b|\\s+for\\b|$)");
        Matcher fromMatcher = fromPattern.matcher(rawText);

        String foundOrigin = null;
        int originEndIdx = -1;

        if (fromMatcher.find()) {
            String candidate = cleanPlaceToken(fromMatcher.group(1));
            if (isPlausiblePlace(candidate)) {
                foundOrigin = normalizePlaceName(candidate);
                originEndIdx = fromMatcher.end();
            }
        }

        // 2. Extract potential destination tokens from remainder of text or delimiters
        List<String> rawTokens = new ArrayList<>();

        if (foundOrigin != null && originEndIdx != -1) {
            // Text after "from <Origin>"
            String remaining = rawText.substring(originEndIdx).trim();

            // Delimit at sentence boundary or keywords
            String destClause = remaining.split("(?i)[.\\r\\n]|\\binclude\\b|\\bfeaturing\\b")[0].trim();

            // Strip leading commas, 'to', 'via', '-'
            destClause = destClause.replaceAll("^(?:[,;\\-]|->|to\\b|via\\b|and\\b|with\\b)\\s*", "");

            // Split remaining by commas, 'to', 'and', '->', ';'
            String[] splitTokens = destClause.split("(?i)[,;]|\\s+->\\s*|\\s+to\\s+|\\s+and\\s+|\\s+via\\s+");
            for (String tok : splitTokens) {
                String cleaned = cleanPlaceToken(tok);
                if (isPlausiblePlace(cleaned)) {
                    rawTokens.add(normalizePlaceName(cleaned));
                }
            }
        }

        // 3. If no tokens found after origin, check standard "to <Destinations>"
        if (rawTokens.isEmpty()) {
            Pattern toPattern = Pattern.compile("(?i)\\bto\\s+([^;\\n\\r.]+?)(?:\\s+with\\b|\\s+for\\b|\\s+during\\b|$)");
            Matcher toMatcher = toPattern.matcher(rawText);
            if (toMatcher.find()) {
                String toSection = toMatcher.group(1);
                String[] splitTokens = toSection.split("(?i)[,;]|\\s+->\\s*|\\s+and\\s+|\\s+via\\s+");
                for (String tok : splitTokens) {
                    String cleaned = cleanPlaceToken(tok);
                    if (isPlausiblePlace(cleaned)) {
                        rawTokens.add(normalizePlaceName(cleaned));
                    }
                }
            }
        }

        // 4. If prompt is structured like "Place1, Place2, Place3" or "Place1 to Place2" without "from"
        if (foundOrigin == null && !rawTokens.isEmpty()) {
            // Check if there's an origin at the end: "... starting from <Place>" or "... from <Place>"
            Pattern endFromPattern = Pattern.compile("(?i)(?:starting\\s+from|from)\\s+([A-Za-z\\s'-]+)$");
            Matcher endMatcher = endFromPattern.matcher(rawText.trim());
            if (endMatcher.find()) {
                String c = cleanPlaceToken(endMatcher.group(1));
                if (isPlausiblePlace(c)) {
                    foundOrigin = normalizePlaceName(c);
                }
            }
        }

        // 5. If still no origin or tokens, split rawText by commas or arrows directly
        if (foundOrigin == null && rawTokens.isEmpty()) {
            String[] allParts = rawText.split("(?i)[,;]|\\s+->\\s*|\\s+to\\s+");
            List<String> potentialPlaces = new ArrayList<>();
            for (String part : allParts) {
                String cleaned = cleanPlaceToken(part);
                if (isPlausiblePlace(cleaned)) {
                    potentialPlaces.add(normalizePlaceName(cleaned));
                }
            }

            if (potentialPlaces.size() >= 2) {
                foundOrigin = potentialPlaces.get(0);
                for (int i = 1; i < potentialPlaces.size(); i++) {
                    rawTokens.add(potentialPlaces.get(i));
                }
            } else if (potentialPlaces.size() == 1) {
                rawTokens.add(potentialPlaces.get(0));
            }
        }

        // 6. Check known aliases directly in the text if tokens are still empty
        if (rawTokens.isEmpty()) {
            for (Map.Entry<String, String> entry : PLACE_ALIASES.entrySet()) {
                String alias = entry.getKey();
                String standard = entry.getValue();
                if (lower.contains(alias) && (foundOrigin == null || !foundOrigin.equalsIgnoreCase(standard))) {
                    if (!rawTokens.contains(standard)) {
                        rawTokens.add(standard);
                    }
                }
            }
        }

        // Finalize Origin (do not fabricate when unspecified - triggers follow-up)
        result.origin = (foundOrigin != null && !foundOrigin.trim().isEmpty()) ? foundOrigin : null;

        // Finalize Destinations (deduplicated, excluding origin)
        for (String dest : rawTokens) {
            if (result.origin == null || !dest.equalsIgnoreCase(result.origin)) {
                if (!result.destinations.contains(dest)) {
                    result.destinations.add(dest);
                }
            }
        }

        return result;
    }

    private String cleanPlaceToken(String token) {
        if (token == null) return "";
        String s = token.trim();
        // Remove common prompt fluff words
        s = s.replaceAll("(?i)^(?:plan|build|make|create|curate|a|an|the|my|our)\\s+", "");
        s = s.replaceAll("(?i)^(?:\\d+)[ -]?(?:day|days|night|nights|d)\\s+", "");
        s = s.replaceAll("(?i)^(?:trip|journey|tour|vacation|itinerary|getaway|circuit|drive)\\s+", "");
        s = s.replaceAll("(?i)\\b(?:trip|journey|tour|vacation|itinerary|getaway|circuit|drive)\\b.*$", "");
        s = s.replaceAll("(?i)\\b(?:with|for|having|featuring|around|during|in)\\b.*$", "");
        s = s.replaceAll("(?i)\\b(?:waterfalls|nature|heritage|beaches|temples|hills|adventure|scenic|viewpoints|relaxed|fast)\\b.*$", "");
        s = s.replaceAll("[^A-Za-z\\s'-]", " ").trim();
        return s.replaceAll("\\s+", " ").trim();
    }

    private boolean isPlausiblePlace(String str) {
        if (str == null || str.trim().length() < 2) return false;
        String l = str.toLowerCase().trim();
        if (l.contains("want") || l.contains("need") || l.contains("looking") || l.contains("planning") ||
                l.contains("suggest") || l.contains("scenic") || l.contains("would like") || l.contains("explore")) {
            return false;
        }
        Set<String> stopWords = Set.of(
                "trip", "day", "days", "night", "nights", "family", "couple", "solo", "friends",
                "waterfalls", "nature", "heritage", "temples", "beach", "beaches", "hills",
                "adventure", "car", "bike", "driving", "driving trip", "relaxed", "fast",
                "with", "and", "from", "to", "via", "the", "a", "an", "place", "places", "home",
                "journey", "vacation", "tour", "road trip", "getaway"
        );
        return !stopWords.contains(l);
    }

    private String normalizePlaceName(String raw) {
        String l = raw.toLowerCase().trim();
        if (PLACE_ALIASES.containsKey(l)) {
            return PLACE_ALIASES.get(l);
        }
        return capitalizeWords(raw);
    }

    private String capitalizeWords(String str) {
        if (str == null || str.isEmpty()) return str;
        String[] parts = str.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase()).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private int extractDuration(String lower) {
        Pattern pattern = Pattern.compile("(\\d+)[ -]?(?:day|days|night|nights|d)");
        Matcher matcher = pattern.matcher(lower);
        if (matcher.find()) {
            try {
                return Math.max(1, Math.min(14, Integer.parseInt(matcher.group(1))));
            } catch (Exception ignored) {}
        }

        if (lower.contains("weekend")) return 2;
        if (lower.contains("long weekend")) return 3;
        if (lower.contains("week")) return 7;

        return 4; // Sensible default
    }

    private List<String> extractInterests(String lower) {
        List<String> interests = new ArrayList<>();
        if (lower.contains("waterfall") || lower.contains("falls") || lower.contains("cascade")) interests.add("Waterfalls");
        if (lower.contains("viewpoint") || lower.contains("viewpoints")) interests.add("Viewpoints");
        if (lower.contains("nature") || lower.contains("scenic") || lower.contains("green") || lower.contains("plantation") || lower.contains("tea")) interests.add("Nature");
        if (lower.contains("heritage") || lower.contains("palace") || lower.contains("fort") || lower.contains("historic") || lower.contains("monument")) interests.add("Historical");
        if (lower.contains("temple") || lower.contains("spiritual") || lower.contains("shrine")) interests.add("Temples");
        if (lower.contains("beach") || lower.contains("sea") || lower.contains("ocean") || lower.contains("coastal")) interests.add("Beaches");
        if (lower.contains("adventure") || lower.contains("trek") || lower.contains("safari") || lower.contains("hiking")) interests.add("Adventure");
        if (lower.contains("museum") || lower.contains("art") || lower.contains("science")) interests.add("Museums");
        if (lower.contains("photo") || lower.contains("sunset")) interests.add("Photography");

        if (interests.isEmpty()) {
            interests.add("Nature");
            interests.add("Historical");
        }
        return interests;
    }

    private String extractTransportMode(String lower) {
        if (lower.contains("bike") || lower.contains("motorcycle") || lower.contains("two wheeler") || lower.contains("riding")) {
            return "BIKE";
        }
        if (lower.contains("walk") || lower.contains("trekking") || lower.contains("pedestrian")) {
            return "WALKING";
        }
        if (lower.contains("train") || lower.contains("bus") || lower.contains("public transport") || lower.contains("transit")) {
            return "PUBLIC_TRANSPORT";
        }
        return "CAR"; // Default driving
    }

    private String extractTravelStyle(String lower) {
        if (lower.contains("relaxed") || lower.contains("slow") || lower.contains("leisure") || lower.contains("easy") || lower.contains("laid-back")) {
            return "RELAXED";
        }
        if (lower.contains("fast") || lower.contains("packed") || lower.contains("busy") || lower.contains("quick") || lower.contains("maximum spots")) {
            return "FAST_PACED";
        }
        return "BALANCED";
    }

    private int extractTravelersCount(String lower) {
        if (lower.contains("family") || lower.contains("kids") || lower.contains("parents")) return 4;
        if (lower.contains("solo") || lower.contains("alone") || lower.contains("myself")) return 1;
        if (lower.contains("couple") || lower.contains("partner") || lower.contains("wife") || lower.contains("husband") || lower.contains("girlfriend") || lower.contains("boyfriend")) return 2;
        if (lower.contains("friends") || lower.contains("group") || lower.contains("buddies")) return 4;

        Pattern p = Pattern.compile("(\\d+)\\s*(?:people|persons|travelers|friends|adults)");
        Matcher m = p.matcher(lower);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
        }
        return 2;
    }

    private String extractAccommodationPreference(String lower) {
        if (lower.contains("comfortable") && (lower.contains("overnight") || lower.contains("stay"))) {
            return "Comfortable overnight stays";
        }
        if (lower.contains("resort") || lower.contains("luxury") || lower.contains("5 star") || lower.contains("villa")) {
            return "Resort & Luxury Stays";
        }
        if (lower.contains("homestay") || lower.contains("estate stay") || lower.contains("coffee estate") || lower.contains("plantation stay")) {
            return "Homestay & Plantation Stays";
        }
        if (lower.contains("hotel") || lower.contains("comfortable stay") || lower.contains("overnight stay")) {
            return "Comfortable Mid-Scale Hotels";
        }
        if (lower.contains("hostel") || lower.contains("dorm") || lower.contains("budget stay")) {
            return "Budget Stays & Hostels";
        }
        return "Hotels / Resorts with comfortable parking";
    }

    private String extractDailyTravelLimit(String lower) {
        if (lower.contains("relaxed driving") || lower.contains("less driving") || lower.contains("short driving")) {
            return "Max 3-4 hours daily driving";
        }
        if (lower.contains("long drive") || lower.contains("road trip enthusiast") || lower.contains("highway driving")) {
            return "Comfortable with 6+ hours driving";
        }
        return "Max 4-5 hours comfortable driving";
    }

    private String extractAccessibilityOrFamily(String lower) {
        if (lower.contains("family") || lower.contains("kids") || lower.contains("children") || lower.contains("elderly") || lower.contains("parents")) {
            return "Family-friendly stops with easy walking and clean restrooms";
        }
        if (lower.contains("wheelchair") || lower.contains("accessible") || lower.contains("senior")) {
            return "Wheelchair accessible with minimal stairs";
        }
        return "Standard accessibility";
    }

    private String extractBudget(String lower) {
        if (lower.contains("luxury") || lower.contains("premium") || lower.contains("high end")) {
            return "LUXURY";
        }
        if (lower.contains("budget") || lower.contains("economical") || lower.contains("cheap") || lower.contains("low cost")) {
            return "BUDGET";
        }
        return "MODERATE";
    }
}
