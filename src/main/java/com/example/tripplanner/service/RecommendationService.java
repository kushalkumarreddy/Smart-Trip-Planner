package com.example.tripplanner.service;

import com.example.tripplanner.dto.SightseeingPlaceDto;
import com.example.tripplanner.util.GeoUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    // Curated catalog of iconic sightseeing spots for top destinations
    private static final Map<String, List<PlaceTemplate>> CURATED_PLACES_DB = new HashMap<>();

    static {
        // Mysore
        List<PlaceTemplate> mysorePlaces = new ArrayList<>();
        mysorePlaces.add(new PlaceTemplate("Mysore Palace", "Historical", 4.7, 48500, 2.4, 120, 8,
                "Magnificent royal residence of the Wadiyar dynasty featuring Indo-Saracenic architecture, stained glass, and illuminated night views.", "08:30 AM - 05:30 PM", 12.3051, 76.6552));
        mysorePlaces.add(new PlaceTemplate("Chamundi Hill & Temple", "Temples", 4.7, 32100, 11.8, 90, 25,
                "Sacred hilltop shrine of Goddess Chamundeshwari with panoramic city vistas and the iconic giant Nandi statue.", "07:30 AM - 09:00 PM", 12.2743, 76.6715));
        mysorePlaces.add(new PlaceTemplate("Brindavan Gardens", "Parks", 4.5, 29400, 18.5, 90, 35,
                "Terraced ornamental garden adjacent to Krishnarajasagara dam celebrated for musical fountain light shows.", "10:00 AM - 08:00 PM", 12.4244, 76.5728));
        mysorePlaces.add(new PlaceTemplate("St. Philomena's Cathedral", "Historical", 4.6, 18900, 3.2, 45, 10,
                "One of the tallest Neo-Gothic churches in Asia featuring ornate spires and biblical stained glass windows.", "08:00 AM - 06:00 PM", 12.3208, 76.6578));
        mysorePlaces.add(new PlaceTemplate("Karanji Lake & Nature Park", "Nature", 4.4, 14200, 4.5, 75, 12,
                "Scenic urban lake featuring a butterfly park, nature boardwalks, and India's largest walk-through aviary.", "08:30 AM - 05:30 PM", 12.3025, 76.6744));
        mysorePlaces.add(new PlaceTemplate("Railway Museum Mysore", "Museums", 4.3, 8500, 2.8, 60, 9,
                "Engaging outdoor museum showcasing vintage locomotives, royal carriages, and railway heritage.", "10:00 AM - 05:30 PM", 12.3168, 76.6439));
        CURATED_PLACES_DB.put("mysore", mysorePlaces);
        CURATED_PLACES_DB.put("mysuru", mysorePlaces);

        // Coorg (Madikeri)
        List<PlaceTemplate> coorgPlaces = new ArrayList<>();
        coorgPlaces.add(new PlaceTemplate("Abbey Falls", "Waterfalls", 4.5, 26000, 7.5, 60, 20,
                "Roaring cascade nestled inside lush spice estates and coffee plantations accessible via a hanging bridge.", "09:00 AM - 05:00 PM", 12.4542, 75.7198));
        coorgPlaces.add(new PlaceTemplate("Raja's Seat", "Nature", 4.6, 21500, 1.2, 60, 5,
                "Historic garden overlook where kings watched spectacular sunsets over misty valleys and Western Ghats.", "06:00 AM - 08:00 PM", 12.4208, 75.7350));
        coorgPlaces.add(new PlaceTemplate("Namdroling Golden Temple", "Temples", 4.8, 38000, 34.0, 90, 55,
                "Spectacular Tibetan Buddhist monastery featuring three 40-foot gilded statues and serene meditation halls.", "09:00 AM - 06:00 PM", 12.4278, 75.9657));
        coorgPlaces.add(new PlaceTemplate("Dubare Elephant Camp", "Adventure", 4.4, 19200, 28.0, 120, 45,
                "Eco-tourism riverside sanctuary on River Cauvery where travelers can observe and wash gentle elephants.", "09:00 AM - 05:00 PM", 12.3686, 75.9048));
        coorgPlaces.add(new PlaceTemplate("Madikeri Fort & Palace", "Historical", 4.2, 11400, 0.8, 60, 3,
                "17th-century stone fortification featuring an imposing clock tower, palace artifacts, and city vistas.", "10:00 AM - 05:30 PM", 12.4241, 75.7381));
        coorgPlaces.add(new PlaceTemplate("Mandalpatti Peak", "Adventure", 4.7, 16800, 19.0, 150, 45,
                "Thrilling 4x4 jeep trail climbing to panoramic mist-covered Western Ghat ridge lines and vistas.", "06:00 AM - 06:00 PM", 12.5186, 75.7001));
        CURATED_PLACES_DB.put("coorg", coorgPlaces);
        CURATED_PLACES_DB.put("madikeri", coorgPlaces);

        // Wayanad
        List<PlaceTemplate> wayanadPlaces = new ArrayList<>();
        wayanadPlaces.add(new PlaceTemplate("Banasura Sagar Dam", "Nature", 4.5, 23000, 18.0, 90, 35,
                "Largest earthen dam in India featuring boating across tranquil waters surrounded by mountain ranges.", "09:00 AM - 05:00 PM", 11.6698, 75.9576));
        wayanadPlaces.add(new PlaceTemplate("Edakkal Caves", "Historical", 4.6, 28500, 24.0, 120, 45,
                "Neolithic rock shelters containing ancient petroglyphic engravings dating back over 6,000 years.", "09:00 AM - 04:00 PM", 11.6288, 76.2343));
        wayanadPlaces.add(new PlaceTemplate("Soochipara Waterfalls", "Waterfalls", 4.6, 17800, 22.0, 100, 40,
                "Three-tiered cascade plunging through dense evergreen forests with a natural pool for dipping.", "09:00 AM - 05:00 PM", 11.5126, 76.1614));
        wayanadPlaces.add(new PlaceTemplate("Chembra Peak & Heart Lake", "Adventure", 4.7, 14200, 16.0, 180, 30,
                "Highest summit in Wayanad celebrated for its heart-shaped natural alpine lake and guided treks.", "07:00 AM - 02:00 PM", 11.5147, 76.0886));
        wayanadPlaces.add(new PlaceTemplate("Pookode Lake", "Nature", 4.3, 19100, 14.0, 60, 25,
                "Serene freshwater lake surrounded by dense evergreen woods offering pedal boating and freshwater aquarium.", "09:00 AM - 05:00 PM", 11.5432, 76.0274));
        wayanadPlaces.add(new PlaceTemplate("Lakkidi View Point", "Photography", 4.5, 12900, 15.0, 45, 25,
                "Highest gateway pass overlooking winding serpentine hairpin curves of Thamarassery Churam pass.", "Open 24 Hours", 11.5152, 76.0357));
        CURATED_PLACES_DB.put("wayanad", wayanadPlaces);

        // Ooty
        List<PlaceTemplate> ootyPlaces = new ArrayList<>();
        ootyPlaces.add(new PlaceTemplate("Ooty Botanical Gardens", "Parks", 4.5, 34000, 2.5, 90, 8,
                "Sprawling 55-acre terraced gardens established in 1848 harboring exotic flora, orchids, and a fossil tree.", "07:00 AM - 06:30 PM", 11.4173, 76.7118));
        ootyPlaces.add(new PlaceTemplate("Ooty Lake & Boathouse", "Nature", 4.3, 31000, 2.2, 75, 7,
                "Picturesque artificial lake created in 1824 with paddle boats, toy train rides, and eucalyptus groves.", "09:00 AM - 06:00 PM", 11.4057, 76.6876));
        ootyPlaces.add(new PlaceTemplate("Doddabetta Peak", "Nature", 4.6, 27500, 8.5, 75, 22,
                "Highest mountain in the Nilgiri Hills (2,637 m) offering telescope observatory panoramas above clouds.", "09:00 AM - 06:30 PM", 11.4005, 76.7354));
        ootyPlaces.add(new PlaceTemplate("Rose Garden", "Photography", 4.5, 21800, 1.8, 60, 6,
                "Spectacular curved terrace garden featuring more than 20,000 varieties of blooming hybrid roses.", "08:30 AM - 06:00 PM", 11.4069, 76.7142));
        ootyPlaces.add(new PlaceTemplate("Nilgiri Mountain Toy Train", "Historical", 4.8, 25400, 1.0, 90, 5,
                "UNESCO World Heritage rack-and-pinion steam railway winding through tunnels, viaducts, and tea slopes.", "07:00 AM - 06:00 PM", 11.4064, 76.6953));
        ootyPlaces.add(new PlaceTemplate("Pykara Lake & Waterfalls", "Waterfalls", 4.6, 16300, 21.0, 90, 40,
                "Pristine lake and tiered rapids amidst rolling Shola grasslands offering speed boating adventures.", "09:30 AM - 05:30 PM", 11.4589, 76.6025));
        CURATED_PLACES_DB.put("ooty", ootyPlaces);
        CURATED_PLACES_DB.put("udagamandalam", ootyPlaces);

        // Bengaluru
        List<PlaceTemplate> blrPlaces = new ArrayList<>();
        blrPlaces.add(new PlaceTemplate("Lalbagh Botanical Garden", "Parks", 4.6, 42000, 4.0, 90, 15,
                "Historic botanical haven commissioned by Hyder Ali featuring a Victorian glass house and ancient bonsai.", "06:00 AM - 07:00 PM", 12.9507, 77.5848));
        blrPlaces.add(new PlaceTemplate("Bangalore Palace", "Historical", 4.4, 28000, 3.5, 90, 12,
                "Tudor-style royal estate with fortified towers, wood carvings, and antique memorabilia.", "10:00 AM - 05:30 PM", 12.9988, 77.5921));
        blrPlaces.add(new PlaceTemplate("Cubbon Park", "Nature", 4.6, 35000, 1.5, 75, 8,
                "Sprawling green lung in the heart of the city shaded by towering bamboo and mahogany canopies.", "06:00 AM - 06:00 PM", 12.9763, 77.5929));
        blrPlaces.add(new PlaceTemplate("Visvesvaraya Industrial & Tech Museum", "Museums", 4.6, 21000, 1.8, 90, 8,
                "Interactive science and aviation exhibits, full-scale engine demos, and kid-friendly galleries.", "09:30 AM - 06:00 PM", 12.9752, 77.5963));
        blrPlaces.add(new PlaceTemplate("ISKCON Temple Bangalore", "Temples", 4.7, 39000, 8.0, 90, 22,
                "Monumental cultural and spiritual temple complex atop Hare Krishna Hill known for its spiritual grandeur.", "07:15 AM - 08:30 PM", 13.0098, 77.5511));
        blrPlaces.add(new PlaceTemplate("Bannerghatta Biological Park", "Adventure", 4.4, 31000, 22.0, 180, 50,
                "Wild safari sanctuary, butterfly conservatory, and zoo featuring tigers, lions, and bears.", "09:30 AM - 05:00 PM", 12.8009, 77.5777));
        CURATED_PLACES_DB.put("bengaluru", blrPlaces);
        CURATED_PLACES_DB.put("bangalore", blrPlaces);

        // Calicut / Kozhikode
        List<PlaceTemplate> calicutPlaces = new ArrayList<>();
        calicutPlaces.add(new PlaceTemplate("Kozhikode Beach & Sea Pier", "Photography", 4.6, 28000, 1.5, 75, 6,
                "Scenic Malabar coastline promenade celebrated for golden sunsets, century-old piers, and local cuisine.", "Open 24 Hours", 11.2590, 75.7700));
        calicutPlaces.add(new PlaceTemplate("Mananchira Square", "Parks", 4.5, 14000, 0.8, 60, 4,
                "Tranquil heritage park surrounding an ancient freshwater tank built by the Zamorin kings.", "09:00 AM - 08:00 PM", 11.2530, 75.7820));
        calicutPlaces.add(new PlaceTemplate("Kappad Beach & Vasco da Gama Monument", "Historical", 4.5, 21000, 16.5, 90, 35,
                "Historic shoreline where Portuguese explorer Vasco da Gama landed in 1498, framed by rocky headlands.", "06:00 AM - 07:00 PM", 11.3900, 75.7170));
        calicutPlaces.add(new PlaceTemplate("Beypore Port & Ancient Uru Dhow Yard", "Historical", 4.6, 17500, 11.2, 90, 25,
                "Historic maritime port celebrated for centuries of handcrafted wooden dhow (Uru) shipbuilding traditions.", "09:00 AM - 06:00 PM", 11.1640, 75.8080));
        calicutPlaces.add(new PlaceTemplate("Thusharagiri Waterfalls", "Waterfalls", 4.6, 12000, 48.0, 120, 70,
                "Picturesque Western Ghats waterfalls and trekking path cascading through rubber, areca nut, and spice estates.", "08:00 AM - 05:00 PM", 11.4700, 76.0500));
        CURATED_PLACES_DB.put("calicut", calicutPlaces);
        CURATED_PLACES_DB.put("kozhikode", calicutPlaces);

        // Munnar
        List<PlaceTemplate> munnarPlaces = new ArrayList<>();
        munnarPlaces.add(new PlaceTemplate("Tea Gardens & Tata Tea Museum", "Nature", 4.7, 36000, 2.5, 90, 8,
                "Lush undulating tea terraces and heritage museum illustrating the evolution of tea plantations.", "09:00 AM - 05:00 PM", 10.0889, 77.0595));
        munnarPlaces.add(new PlaceTemplate("Eravikulam National Park", "Nature", 4.7, 41000, 8.5, 150, 25,
                "Sanctuary for the endangered Nilgiri Tahr mountain goat and blooming Neelakurinji shrublands.", "07:30 AM - 04:00 PM", 10.1500, 77.0600));
        munnarPlaces.add(new PlaceTemplate("Mattupetty Dam & Lake", "Adventure", 4.5, 32000, 12.0, 90, 28,
                "Mountain storage dam offering scenic speed boating and tranquil reflections of surrounding tea hills.", "09:30 AM - 05:00 PM", 10.1060, 77.1250));
        munnarPlaces.add(new PlaceTemplate("Top Station Cloud Point", "Photography", 4.6, 29000, 32.0, 90, 55,
                "Highest altitude lookout on the Munnar-Kodaikanal road offering panoramic views over the Western Ghats and Tamil Nadu plains.", "06:00 AM - 06:30 PM", 10.1240, 77.2450));
        munnarPlaces.add(new PlaceTemplate("Attukad Waterfalls", "Waterfalls", 4.5, 21000, 9.0, 75, 22,
                "Spectacular mountain waterfall rushing between rolling hills and jungle trails, ideal for photography.", "09:00 AM - 06:00 PM", 10.0540, 77.0420));
        CURATED_PLACES_DB.put("munnar", munnarPlaces);
        CURATED_PLACES_DB.put("munar", munnarPlaces);

        // Kodaikanal
        List<PlaceTemplate> kodaiPlaces = new ArrayList<>();
        kodaiPlaces.add(new PlaceTemplate("Kodaikanal Lake & Boathouse", "Nature", 4.6, 38000, 1.2, 90, 5,
                "Star-shaped serene lake built in 1863 surrounded by cycling pathways, horse rides, and mist.", "08:30 AM - 06:30 PM", 10.2350, 77.4890));
        kodaiPlaces.add(new PlaceTemplate("Coaker's Walk", "Photography", 4.6, 29000, 1.8, 60, 6,
                "One-kilometer paved mountain walkway on steep cliff edges overlooking plains and rolling clouds.", "07:00 AM - 07:00 PM", 10.2310, 77.4950));
        kodaiPlaces.add(new PlaceTemplate("Pillar Rocks Viewpoint", "Nature", 4.5, 24000, 7.5, 60, 18,
                "Majestic trio of giant vertical granite boulders rising 400 feet into the sky amidst misty valleys.", "09:00 AM - 05:30 PM", 10.2100, 77.4680));
        kodaiPlaces.add(new PlaceTemplate("Bryant Park & Glasshouse", "Parks", 4.4, 21000, 1.0, 75, 4,
                "Botanical haven hosting hundreds of rose and hybrid flower varieties near the east edge of the lake.", "09:00 AM - 06:00 PM", 10.2330, 77.4940));
        kodaiPlaces.add(new PlaceTemplate("Silver Cascade Waterfall", "Waterfalls", 4.4, 19000, 8.0, 45, 18,
                "180-foot natural waterfall pouring over steep cliffs on the main Ghat road approaching Kodaikanal.", "Open 24 Hours", 10.2550, 77.5180));
        CURATED_PLACES_DB.put("kodaikanal", kodaiPlaces);
        CURATED_PLACES_DB.put("kodaikenal", kodaiPlaces);

        // Guntakal & surrounding region
        List<PlaceTemplate> guntakalPlaces = new ArrayList<>();
        guntakalPlaces.add(new PlaceTemplate("Kasapuram Neti Anjaneya Swamy Temple", "Temples", 4.7, 18500, 8.5, 90, 20,
                "Renowned and revered shrine of Lord Hanuman famous for ghee offerings and peaceful spiritual courtyards.", "06:00 AM - 09:00 PM", 15.1420, 77.3080));
        guntakalPlaces.add(new PlaceTemplate("Guntakal Historic Heritage Rail Junction", "Historical", 4.3, 4200, 1.0, 60, 5,
                "Historic British-era junction station founded in the 1860s showcasing colonial railway heritage.", "Open 24 Hours", 15.1678, 77.3686));
        guntakalPlaces.add(new PlaceTemplate("Chippagiri Historical Overlook", "Historical", 4.5, 3100, 18.0, 75, 35,
                "Spiritual and historic hill village associated with medieval Haridasa philosopher Sri Vijayadasaru.", "07:00 AM - 06:00 PM", 15.2800, 77.2100));
        CURATED_PLACES_DB.put("guntakal", guntakalPlaces);
    }

    public List<SightseeingPlaceDto> getRecommendedPlaces(String destinationName, List<String> userInterests, String sortBy, String filterCategory) {
        if (destinationName == null || destinationName.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String lookup = destinationName.toLowerCase().trim();
        List<PlaceTemplate> templates = null;

        for (Map.Entry<String, List<PlaceTemplate>> entry : CURATED_PLACES_DB.entrySet()) {
            if (lookup.contains(entry.getKey())) {
                templates = entry.getValue();
                break;
            }
        }

        // If not found in static catalog, generate realistic places anchored on the destination coordinates
        if (templates == null || templates.isEmpty()) {
            templates = generateGenericPlacesForLocation(destinationName);
        }

        double[] destCoords = GeoUtils.getApproximateCoordinates(destinationName);

        // Convert templates to DTOs and score them
        List<SightseeingPlaceDto> results = new ArrayList<>();
        for (PlaceTemplate t : templates) {
            // Apply category filter if requested
            if (filterCategory != null && !filterCategory.equalsIgnoreCase("ALL") && !filterCategory.trim().isEmpty()) {
                if (!t.category.equalsIgnoreCase(filterCategory.trim())) {
                    continue;
                }
            }

            SightseeingPlaceDto dto = new SightseeingPlaceDto();
            dto.setName(t.name);
            dto.setDestinationName(destinationName);
            dto.setCategory(t.category);
            dto.setRating(t.rating);
            dto.setReviewCount(t.reviewCount);
            dto.setDistanceKm(t.distanceKm);
            dto.setEstimatedVisitDurationMinutes(t.visitDurationMinutes);
            dto.setEstimatedTravelTimeMinutes(t.travelTimeMinutes);
            dto.setDescription(t.description);
            dto.setOpeningHours(t.openingHours);
            dto.setLatitude(t.latitude != 0 ? t.latitude : destCoords[0] + (Math.random() - 0.5) * 0.05);
            dto.setLongitude(t.longitude != 0 ? t.longitude : destCoords[1] + (Math.random() - 0.5) * 0.05);
            dto.setVisited(false);
            dto.setAddedToItinerary(false);

            // Calculate Multi-Factor Recommendation Score:
            // Score = (Rating/5)*35 + (log(Reviews)/log(50000))*25 + (1 / (1 + distance/5))*25 + interestBonus*15
            double ratingFactor = (t.rating / 5.0) * 35.0;
            double reviewFactor = (Math.log10(Math.max(10, t.reviewCount)) / Math.log10(50000)) * 25.0;
            double distanceFactor = (1.0 / (1.0 + (t.distanceKm / 8.0))) * 25.0;

            double interestFactor = 0.0;
            if (userInterests != null && !userInterests.isEmpty()) {
                for (String interest : userInterests) {
                    if (t.category.equalsIgnoreCase(interest) || interest.equalsIgnoreCase("All")) {
                        interestFactor = 15.0;
                        break;
                    }
                }
            } else {
                interestFactor = 10.0;
            }

            double totalScore = ratingFactor + reviewFactor + distanceFactor + interestFactor;
            dto.setMatchScore(Math.round(totalScore * 10.0) / 10.0);

            // Determine Priority
            if (totalScore >= 75.0 || t.rating >= 4.6) {
                dto.setPriority("High");
            } else if (totalScore >= 60.0) {
                dto.setPriority("Medium");
            } else {
                dto.setPriority("Low");
            }

            results.add(dto);
        }

        // Apply Sorting
        if ("distance".equalsIgnoreCase(sortBy)) {
            results.sort(Comparator.comparing(SightseeingPlaceDto::getDistanceKm));
        } else if ("rating".equalsIgnoreCase(sortBy)) {
            results.sort(Comparator.comparing(SightseeingPlaceDto::getRating).reversed()
                    .thenComparing(SightseeingPlaceDto::getReviewCount, Comparator.reverseOrder()));
        } else {
            // Default: Popularity / Weighted Match Score
            results.sort(Comparator.comparing(SightseeingPlaceDto::getMatchScore).reversed());
        }

        return results;
    }

    private List<PlaceTemplate> generateGenericPlacesForLocation(String locationName) {
        double[] coords = GeoUtils.getApproximateCoordinates(locationName);
        List<PlaceTemplate> list = new ArrayList<>();
        list.add(new PlaceTemplate(locationName + " Old Town & Heritage Walk", "Historical", 4.6, 9500, 1.8, 90, 8,
                "Atmospheric historic quarter showcasing traditional architecture, landmarks, and rich local cultural history.", "09:00 AM - 06:00 PM",
                coords[0] + 0.005, coords[1] + 0.004));
        list.add(new PlaceTemplate(locationName + " Scenic Viewpoint", "Nature", 4.7, 12400, 4.2, 60, 14,
                "Scenic elevated hilltop viewpoint overlooking valleys and breathtaking horizon sunsets.", "06:00 AM - 07:00 PM",
                coords[0] - 0.012, coords[1] + 0.008));
        list.add(new PlaceTemplate(locationName + " Botanical Park", "Parks", 4.4, 7200, 3.1, 75, 10,
                "Peaceful landscaped botanical gardens with shaded paths, floral displays, and leisure lawns.", "08:00 AM - 06:30 PM",
                coords[0] + 0.008, coords[1] - 0.007));
        list.add(new PlaceTemplate(locationName + " Central Heritage Museum", "Museums", 4.5, 6800, 2.5, 90, 9,
                "Comprehensive regional gallery displaying archaeological treasures, local art, and historic relics.", "10:00 AM - 05:00 PM",
                coords[0] - 0.004, coords[1] - 0.003));
        list.add(new PlaceTemplate(locationName + " Sunset Point & Lake", "Photography", 4.5, 8900, 5.0, 60, 16,
                "Popular shoreline vantage point favored by photographers for golden hour reflections and calm breezes.", "Open 24 Hours",
                coords[0] + 0.015, coords[1] + 0.011));
        return list;
    }

    private static class PlaceTemplate {
        String name;
        String category;
        double rating;
        int reviewCount;
        double distanceKm;
        int visitDurationMinutes;
        int travelTimeMinutes;
        String description;
        String openingHours;
        double latitude;
        double longitude;

        PlaceTemplate(String name, String category, double rating, int reviewCount, double distanceKm,
                      int visitDurationMinutes, int travelTimeMinutes, String description, String openingHours,
                      double latitude, double longitude) {
            this.name = name;
            this.category = category;
            this.rating = rating;
            this.reviewCount = reviewCount;
            this.distanceKm = distanceKm;
            this.visitDurationMinutes = visitDurationMinutes;
            this.travelTimeMinutes = travelTimeMinutes;
            this.description = description;
            this.openingHours = openingHours;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}
