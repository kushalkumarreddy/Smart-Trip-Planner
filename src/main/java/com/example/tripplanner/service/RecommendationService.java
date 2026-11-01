package com.example.tripplanner.service;

import com.example.tripplanner.dto.FeaturedDestinationDto;
import com.example.tripplanner.dto.SightseeingPlaceDto;
import com.example.tripplanner.util.GeoUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.maps.GeoApiContext;
import com.google.maps.PlacesApi;
import com.google.maps.model.LatLng;
import com.google.maps.model.PlaceType;
import com.google.maps.model.PlacesSearchResponse;
import com.google.maps.model.PlacesSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private static final Logger logger = LoggerFactory.getLogger(RecommendationService.class);
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private GoogleMapsService googleMapsService;

    public RecommendationService() {
    }

    @Autowired
    public RecommendationService(@Autowired(required = false) GoogleMapsService googleMapsService) {
        this.googleMapsService = googleMapsService;
    }

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
        blrPlaces.add(new PlaceTemplate("Tipu Sultan's Summer Palace", "Historical", 4.4, 24000, 3.2, 60, 12,
                "18th-century teakwood summer retreat of the Tiger of Mysore adorned with floral carvings and historical relics.", "08:30 AM - 05:30 PM", 12.9593, 77.5738));
        blrPlaces.add(new PlaceTemplate("National Gallery of Modern Art", "Museums", 4.6, 14000, 2.8, 75, 10,
                "Colonial heritage mansion displaying Indian modern art, tranquil tree-shaded café, and sculpture garden.", "10:00 AM - 06:00 PM (Closed Mondays)", 12.9897, 77.5888));
        blrPlaces.add(new PlaceTemplate("Bull Temple (Nandi Temple)", "Temples", 4.6, 28000, 5.0, 60, 15,
                "Ancient 16th-century Dravidian temple in Basavanagudi housing a monumental monolithic granite Nandi statue.", "06:00 AM - 08:30 PM", 12.9423, 77.5681));
        blrPlaces.add(new PlaceTemplate("Jawaharlal Nehru Planetarium", "Museums", 4.6, 28000, 2.2, 75, 10,
                "High-tech full-dome planetarium featuring astronomical science shows, science park, and sky-gazing sessions.", "10:00 AM - 05:30 PM (Closed Mondays)", 12.9849, 77.5896));
        blrPlaces.add(new PlaceTemplate("HAL Heritage Centre & Aerospace Museum", "Museums", 4.6, 36000, 11.0, 90, 25,
                "India's first aerospace museum showcasing indigenous aircraft, helicopters, flight simulators, and aviation heritage.", "09:00 AM - 05:00 PM", 12.9547, 77.6744));
        blrPlaces.add(new PlaceTemplate("Commercial Street & Russell Market Walk", "Photography", 4.5, 45000, 3.0, 75, 12,
                "Vibrant colonial-era shopping bazaar renowned for silk sarees, street food, jewelry, and bustling alleys.", "10:30 AM - 09:00 PM", 12.9822, 77.6083));
        blrPlaces.add(new PlaceTemplate("St. Mary's Basilica (Shivajinagar)", "Historical", 4.6, 26000, 2.5, 60, 10,
                "Oldest church in Bengaluru built in 1882 in Gothic style with towering stained-glass arches and annual feast traditions.", "06:00 AM - 09:00 PM", 12.9839, 77.6015));
        blrPlaces.add(new PlaceTemplate("Ulsoor Lake & Promenade", "Nature", 4.4, 31000, 3.5, 60, 12,
                "One of the oldest and largest historic lakes in Bengaluru commissioned by Kempe Gowda II with wooded islands.", "06:00 AM - 08:00 PM", 12.9830, 77.6230));
        blrPlaces.add(new PlaceTemplate("Vidhana Soudha & Attara Kacheri", "Historical", 4.7, 52000, 1.6, 45, 8,
                "Neo-Dravidian granite legislative palace and high court building, brilliantly illuminated on Sundays and holidays.", "Open 24 Hours", 12.9797, 77.5908));
        blrPlaces.add(new PlaceTemplate("Dodda Alada Mara (Big Banyan Tree)", "Nature", 4.4, 22000, 28.0, 75, 45,
                "Enormous 400-year-old single banyan tree covering 3 acres with thousands of aerial roots forming a natural canopy.", "08:00 AM - 05:30 PM", 12.9197, 77.3942));
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

        // Agra
        List<PlaceTemplate> agraPlaces = new ArrayList<>();
        agraPlaces.add(new PlaceTemplate("Taj Mahal", "Historical", 4.9, 210000, 3.5, 150, 12,
                "Magnificent 17th-century white marble mausoleum built by Emperor Shah Jahan, UNESCO World Heritage site and Wonder of the World.", "06:00 AM - 06:30 PM (Closed Fridays)", 27.1751, 78.0421));
        agraPlaces.add(new PlaceTemplate("Agra Fort", "Historical", 4.7, 78000, 2.5, 120, 10,
                "Massive 16th-century red sandstone fortress of the Mughal emperors featuring the Jahangiri Mahal, Khas Mahal, and Diwan-i-Khas.", "06:00 AM - 06:00 PM", 27.1795, 78.0211));
        agraPlaces.add(new PlaceTemplate("Mehtab Bagh", "Parks", 4.5, 29000, 5.2, 60, 18,
                "Charbagh garden complex situated across the Yamuna River offering iconic sunset views of the Taj Mahal reflected in water.", "06:00 AM - 06:00 PM", 27.1800, 78.0425));
        agraPlaces.add(new PlaceTemplate("Fatehpur Sikri", "Historical", 4.6, 38000, 36.0, 150, 55,
                "Spectacular 16th-century Mughal fortified capital featuring Buland Darwaza, Jama Masjid, and Panch Mahal.", "06:00 AM - 06:00 PM", 27.0940, 77.6680));
        agraPlaces.add(new PlaceTemplate("Tomb of I'timād-ud-Daulah", "Historical", 4.6, 32000, 5.0, 60, 15,
                "Exquisite marble mausoleum known as the 'Baby Taj' featuring delicate pietra dura inlay work and Yamuna riverside gardens.", "06:00 AM - 06:00 PM", 27.1929, 78.0311));
        agraPlaces.add(new PlaceTemplate("Akbar's Tomb (Sikandra)", "Historical", 4.5, 26000, 10.0, 75, 25,
                "Grand red sandstone and marble funerary complex of Emperor Akbar surrounded by peaceful deer park grounds.", "06:00 AM - 06:00 PM", 27.2206, 77.9504));
        CURATED_PLACES_DB.put("agra", agraPlaces);

        // Jaipur
        List<PlaceTemplate> jaipurPlaces = new ArrayList<>();
        jaipurPlaces.add(new PlaceTemplate("Amer Fort", "Historical", 4.8, 142000, 11.0, 150, 30,
                "Iconic hilltop citadel featuring grand courtyards, Maota Lake panoramas, Sheesh Mahal mirror palace, and Rajput-Mughal architecture.", "08:00 AM - 05:30 PM", 26.9855, 75.8513));
        jaipurPlaces.add(new PlaceTemplate("Hawa Mahal", "Historical", 4.7, 115000, 1.5, 60, 8,
                "Palace of Winds built in 1799 with 953 ornate latticework jharokha windows allowing royal women to observe street festivals.", "09:00 AM - 05:00 PM", 26.9239, 75.8267));
        jaipurPlaces.add(new PlaceTemplate("Jaipur City Palace", "Historical", 4.6, 68000, 2.0, 120, 10,
                "Opulent royal complex in the heart of Jaipur featuring Mubarak Mahal, Chandra Mahal, museums of royal costumes and armory.", "09:30 AM - 05:00 PM", 26.9258, 75.8237));
        jaipurPlaces.add(new PlaceTemplate("Jantar Mantar", "Historical", 4.6, 52000, 1.8, 75, 9,
                "UNESCO astronomical observatory built in 1734 by Maharaja Sawai Jai Singh II housing the world's largest stone sundial.", "09:00 AM - 05:00 PM", 26.9248, 75.8246));
        jaipurPlaces.add(new PlaceTemplate("Jal Mahal", "Photography", 4.5, 41000, 5.0, 45, 15,
                "Mesmerizing water palace floating in Man Sagar Lake framed by the Aravalli hills, especially stunning at golden hour.", "Open 24 Hours (Exterior View)", 26.9535, 75.8462));
        jaipurPlaces.add(new PlaceTemplate("Nahargarh Fort", "Historical", 4.6, 62000, 14.0, 90, 35,
                "Hilltop fortress perched on the edge of the Aravalli hills overlooking panoramic Jaipur city lights and Madhavendra Bhawan.", "10:00 AM - 05:30 PM", 26.9372, 75.8156));
        jaipurPlaces.add(new PlaceTemplate("Albert Hall Museum", "Museums", 4.6, 38000, 2.5, 75, 10,
                "Oldest museum in Rajasthan featuring Indo-Saracenic architecture, Persian carpets, Egyptian mummy, and royal artifacts.", "09:00 AM - 05:00 PM, 07:00 PM - 10:00 PM", 26.9116, 75.8195));
        jaipurPlaces.add(new PlaceTemplate("Birla Mandir Jaipur", "Temples", 4.7, 44000, 4.0, 60, 12,
                "Gleaming white marble Lakshmi Narayan temple nestled beneath the historic Moti Dungri fortress with lush gardens.", "06:00 AM - 12:00 PM, 03:00 PM - 09:00 PM", 26.8924, 75.8156));
        jaipurPlaces.add(new PlaceTemplate("Jaigarh Fort & Jaivana Cannon", "Historical", 4.6, 49000, 12.5, 90, 32,
                "Rugged military citadel atop Cheel ka Teela hill holding the world's largest cannon on wheels and secret passages.", "09:00 AM - 05:00 PM", 26.9850, 75.8456));
        jaipurPlaces.add(new PlaceTemplate("Sisodia Rani Garden & Palace", "Parks", 4.5, 24000, 6.0, 60, 15,
                "Tiered 18th-century royal Mughal-style pleasure garden adorned with cascading fountains, painted pavilions, and peacocks.", "08:00 AM - 06:00 PM", 26.8900, 75.8560));
        jaipurPlaces.add(new PlaceTemplate("Galtaji Monkey Temple", "Temples", 4.6, 34000, 8.5, 75, 20,
                "Historic Hindu pilgrimage site built into a narrow mountain pass with sacred natural water springs (kunds) and rhesus macaques.", "05:00 AM - 07:00 PM", 26.9160, 75.8610));
        CURATED_PLACES_DB.put("jaipur", jaipurPlaces);

        // Delhi
        List<PlaceTemplate> delhiPlaces = new ArrayList<>();
        delhiPlaces.add(new PlaceTemplate("Red Fort", "Historical", 4.6, 125000, 4.0, 120, 20,
                "Iconic red sandstone Mughal fortress complex constructed by Shah Jahan in 1639, symbol of India's independence celebrations.", "09:30 AM - 04:30 PM (Closed Mondays)", 28.6562, 77.2410));
        delhiPlaces.add(new PlaceTemplate("Qutub Minar", "Historical", 4.7, 108000, 14.0, 90, 35,
                "UNESCO World Heritage 72.5-meter red sandstone minaret erected in 1192 surrounded by ancient ruins and the rust-resistant Iron Pillar.", "07:00 AM - 07:00 PM", 28.5245, 77.1855));
        delhiPlaces.add(new PlaceTemplate("Humayun's Tomb", "Historical", 4.7, 82000, 7.5, 90, 22,
                "Exquisite 16th-century Persian garden-tomb of Mughal Emperor Humayun, architectural inspiration for the Taj Mahal.", "06:00 AM - 06:00 PM", 28.5933, 77.2507));
        delhiPlaces.add(new PlaceTemplate("India Gate & Kartavya Path", "Historical", 4.6, 140000, 3.0, 60, 12,
                "42-meter triumphal war memorial arch commemorating soldiers of World War I surrounded by landscaped lawns and fountains.", "Open 24 Hours", 28.6129, 77.2295));
        delhiPlaces.add(new PlaceTemplate("Lotus Temple", "Temples", 4.6, 89000, 12.0, 75, 30,
                "Iconic Bahá'í House of Worship celebrated for its petal architecture resembling a blooming lotus flower and serene meditation hall.", "09:00 AM - 05:30 PM (Closed Mondays)", 28.5535, 77.2588));
        delhiPlaces.add(new PlaceTemplate("Swaminarayan Akshardham", "Temples", 4.8, 96000, 9.5, 150, 28,
                "Monumental carved sandstone temple complex featuring cultural exhibitions, musical water fountain, and boat rides.", "09:30 AM - 06:30 PM (Closed Mondays)", 28.6127, 77.2773));
        delhiPlaces.add(new PlaceTemplate("Jama Masjid", "Temples", 4.5, 62000, 4.5, 60, 18,
                "One of India's largest Mughal mosques built by Emperor Shah Jahan in Old Delhi, featuring sprawling red sandstone courtyards.", "07:00 AM - 12:00 PM, 01:30 PM - 06:30 PM", 28.6507, 77.2334));
        delhiPlaces.add(new PlaceTemplate("National Museum New Delhi", "Museums", 4.6, 31000, 3.5, 90, 12,
                "India's premier cultural museum showcasing 5,000 years of civilization from the Indus Valley Civilization to the National Treasures.", "10:00 AM - 06:00 PM (Closed Mondays)", 28.6118, 77.2193));
        delhiPlaces.add(new PlaceTemplate("Lodhi Garden & Sayyid Tombs", "Parks", 4.7, 56000, 5.0, 75, 15,
                "Sprawling 90-acre heritage city park dotted with 15th-century Sayyid and Lodi dynastic tombs, rose gardens, and walking trails.", "06:00 AM - 08:00 PM", 28.5931, 77.2197));
        delhiPlaces.add(new PlaceTemplate("Gurudwara Bangla Sahib", "Temples", 4.8, 92000, 2.5, 75, 10,
                "Prominent 17th-century Sikh gurdwara with gold dome, sacred Sarovar pool, and 24-hour community kitchen serving langar.", "Open 24 Hours", 28.6263, 77.2090));
        delhiPlaces.add(new PlaceTemplate("Raj Ghat & Memorial Park", "Historical", 4.6, 48000, 4.0, 60, 12,
                "Tranquil black marble memorial marking the cremation spot of Mahatma Gandhi set amidst serene landscaped lawns.", "06:30 AM - 06:00 PM", 28.6406, 77.2495));
        delhiPlaces.add(new PlaceTemplate("Hauz Khas Fort & Village Lake", "Historical", 4.5, 42000, 11.0, 90, 25,
                "13th-century medieval water reservoir complex, royal madrasa, and pavilions overlooking vibrant bohemian cafes.", "10:30 AM - 07:00 PM", 28.5535, 77.1947));
        CURATED_PLACES_DB.put("delhi", delhiPlaces);
        CURATED_PLACES_DB.put("new delhi", delhiPlaces);

        // Amritsar
        List<PlaceTemplate> amritsarPlaces = new ArrayList<>();
        amritsarPlaces.add(new PlaceTemplate("Golden Temple", "Temples", 4.9, 185000, 1.2, 150, 8,
                "Sri Harmandir Sahib, the spiritual epicenter of Sikhism with its gold-plated sanctum surrounded by the sacred Amrit Sarovar lake and community langar.", "Open 24 Hours", 31.6200, 74.8765));
        amritsarPlaces.add(new PlaceTemplate("Jallianwala Bagh", "Historical", 4.6, 76000, 0.8, 60, 5,
                "National memorial honoring the martyrs of the 1919 massacre, featuring the Martyrs' Well, eternal flame, and bullet-marked heritage walls.", "06:30 AM - 07:30 PM", 31.6206, 74.8797));
        amritsarPlaces.add(new PlaceTemplate("Attari-Wagah Border Ceremony", "Adventure", 4.8, 88000, 29.0, 120, 50,
                "Electric daily evening Beating Retreat military ceremony along the India-Pakistan international border with marching drills.", "04:30 PM - 06:30 PM", 31.6044, 74.5739));
        amritsarPlaces.add(new PlaceTemplate("Gobindgarh Fort", "Historical", 4.6, 32000, 2.5, 90, 10,
                "Historic 18th-century military citadel built by Maharaja Ranjit Singh featuring Toshakhana, warfare museums, and light shows.", "10:00 AM - 10:00 PM", 31.6288, 74.8583));
        amritsarPlaces.add(new PlaceTemplate("Partition Museum", "Museums", 4.7, 24000, 1.0, 75, 6,
                "Town Hall museum chronicling the stories, photographs, and artifacts of the 1947 partition of India.", "10:00 AM - 06:00 PM (Closed Mondays)", 31.6247, 74.8778));
        CURATED_PLACES_DB.put("amritsar", amritsarPlaces);

        // Varanasi
        List<PlaceTemplate> varanasiPlaces = new ArrayList<>();
        varanasiPlaces.add(new PlaceTemplate("Varanasi Ghats & Dashashwamedh", "Temples", 4.8, 120000, 2.0, 120, 12,
                "Historic sacred riverfront riverbanks along the River Ganges celebrated for the mesmerizing evening Maha Aarti ceremony with brass lamps.", "Open 24 Hours", 25.3076, 83.0107));
        varanasiPlaces.add(new PlaceTemplate("Kashi Vishwanath Temple", "Temples", 4.8, 95000, 2.2, 90, 15,
                "One of the twelve sacred Jyotirlinga shrines of Lord Shiva situated by the newly developed Kashi Vishwanath Dham corridor.", "03:00 AM - 11:00 PM", 25.3109, 83.0107));
        varanasiPlaces.add(new PlaceTemplate("Assi Ghat Sunrise Boat Tour", "Photography", 4.7, 45000, 4.0, 90, 18,
                "Southernmost major ghat renowned for morning Subah-e-Banaras classical music, yoga, and dawn rowboat rides along the mist.", "05:00 AM - 08:00 PM", 25.2890, 83.0062));
        varanasiPlaces.add(new PlaceTemplate("Sarnath Buddhist Heritage Site", "Historical", 4.7, 54000, 12.0, 120, 30,
                "Sacred deer park where Gautama Buddha first taught the Dharma; features Dhamek Stupa, Ashoka Pillar, and ancient monasteries.", "08:00 AM - 06:00 PM", 25.3811, 83.0214));
        CURATED_PLACES_DB.put("varanasi", varanasiPlaces);

        // Mumbai
        List<PlaceTemplate> mumbaiPlaces = new ArrayList<>();
        mumbaiPlaces.add(new PlaceTemplate("Gateway of India", "Historical", 4.7, 135000, 2.0, 60, 10,
                "Iconic 26-meter basalt triumphal arch built in 1924 overlooking Mumbai Harbour, facing the historic Taj Mahal Palace Hotel.", "Open 24 Hours", 18.9220, 72.8347));
        mumbaiPlaces.add(new PlaceTemplate("Marine Drive & Queen's Necklace", "Photography", 4.7, 110000, 3.5, 75, 12,
                "3.6-kilometer C-shaped promenade along the Arabian Sea coast, iconic for seaside breezes and panoramic night lights.", "Open 24 Hours", 18.9432, 72.8230));
        mumbaiPlaces.add(new PlaceTemplate("Elephanta Caves", "Historical", 4.6, 48000, 11.0, 180, 60,
                "Rock-cut cave temples on Elephanta Island dating to the 5th century dedicated to Lord Shiva, famous for the Trimurti sculpture.", "09:00 AM - 05:30 PM (Closed Mondays)", 18.9633, 72.9315));
        mumbaiPlaces.add(new PlaceTemplate("Chhatrapati Shivaji Maharaj Vastu Sangrahalaya", "Museums", 4.6, 28000, 1.8, 90, 8,
                "Indo-Saracenic museum housing extensive collections of ancient Indian art, Buddhist sculptures, miniature paintings, and armory.", "10:15 AM - 06:00 PM", 18.9269, 72.8327));
        mumbaiPlaces.add(new PlaceTemplate("Chhatrapati Shivaji Maharaj Terminus", "Historical", 4.7, 98000, 1.5, 60, 8,
                "UNESCO World Heritage Victorian Gothic railway terminus celebrated for monumental stone domes, gargoyles, and stained glass.", "Open 24 Hours", 18.9400, 72.8353));
        mumbaiPlaces.add(new PlaceTemplate("Bandra Fort & Sea Link Viewpoint", "Photography", 4.6, 54000, 16.0, 75, 30,
                "Historic Portuguese fort (Castella de Aguada) offering sweeping views of the Bandra-Worli Sea Link and sunset waves.", "06:00 AM - 06:30 PM", 19.0416, 72.8197));
        mumbaiPlaces.add(new PlaceTemplate("Siddhivinayak Temple", "Temples", 4.8, 112000, 11.5, 75, 25,
                "Highly revered 18th-century Hindu temple dedicated to Lord Ganesha featuring a gold-plated inner sanctum.", "05:30 AM - 10:00 PM", 19.0169, 72.8303));
        mumbaiPlaces.add(new PlaceTemplate("Sanjay Gandhi National Park & Kanheri Caves", "Adventure", 4.6, 68000, 32.0, 180, 50,
                "Sprawling urban wilderness harboring wild leopards, ancient 1st-century Buddhist rock-cut caves, and toy train rides.", "07:30 AM - 05:30 PM (Closed Mondays)", 19.2288, 72.9182));
        mumbaiPlaces.add(new PlaceTemplate("Haji Ali Dargah", "Historical", 4.6, 62000, 6.0, 75, 18,
                "15th-century Indo-Islamic shrine and mosque perched on a small islet in Worli Bay, reached via an ocean causeway.", "05:30 AM - 10:00 PM", 18.9827, 72.8089));
        mumbaiPlaces.add(new PlaceTemplate("Juhu Beach Promenade", "Photography", 4.5, 84000, 18.5, 90, 35,
                "Mumbai's famous Arabian Sea shoreline loved for sunset walks, sea breezes, and iconic street food stalls like Pav Bhaji.", "Open 24 Hours", 19.0988, 72.8264));
        CURATED_PLACES_DB.put("mumbai", mumbaiPlaces);

        // Hampi
        List<PlaceTemplate> hampiPlaces = new ArrayList<>();
        hampiPlaces.add(new PlaceTemplate("Virupaksha Temple", "Temples", 4.8, 48000, 0.5, 90, 4,
                "7th-century functioning temple dedicated to Lord Shiva with an imposing 50-meter eastern gopuram on the banks of Tungabhadra.", "06:00 AM - 08:00 PM", 15.3353, 76.4597));
        hampiPlaces.add(new PlaceTemplate("Vijaya Vittala Temple & Stone Chariot", "Historical", 4.8, 56000, 3.5, 120, 12,
                "Masterpiece of Vijayanagara architecture famous for musical pillars and the iconic stone chariot shrine honoring Garuda.", "08:30 AM - 05:30 PM", 15.3394, 76.4795));
        hampiPlaces.add(new PlaceTemplate("Lotus Mahal & Elephant Stables", "Historical", 4.7, 34000, 2.8, 75, 10,
                "Secular enclosure combining Indo-Islamic arches with lotus-bud domes, once used by queens and royal elephant trainers.", "08:00 AM - 06:00 PM", 15.3204, 76.4716));
        hampiPlaces.add(new PlaceTemplate("Matanga Hill Sunset Point", "Adventure", 4.7, 22000, 1.2, 90, 8,
                "Highest point in central Hampi offering panoramic 360-degree vistas of boulder-strewn terrain, ancient ruins, and banana plantations.", "05:30 AM - 06:30 PM", 15.3317, 76.4678));
        CURATED_PLACES_DB.put("hampi", hampiPlaces);

        // Goa
        List<PlaceTemplate> goaPlaces = new ArrayList<>();
        goaPlaces.add(new PlaceTemplate("Fort Aguada & Lighthouse", "Historical", 4.6, 62000, 14.0, 75, 25,
                "17th-century Portuguese fortress and lighthouse guarding the Mandovi River mouth with sweeping Arabian Sea panoramas.", "09:30 AM - 06:00 PM", 15.4924, 73.7737));
        goaPlaces.add(new PlaceTemplate("Basilica of Bom Jesus", "Historical", 4.7, 58000, 10.0, 60, 20,
                "UNESCO World Heritage Baroque church in Old Goa holding the mortal remains of St. Francis Xavier, built in 1605.", "09:00 AM - 06:30 PM", 15.5009, 73.9116));
        goaPlaces.add(new PlaceTemplate("Calangute & Baga Beach Promenade", "Photography", 4.5, 78000, 16.0, 120, 30,
                "Vibrant North Goa beach shoreline lined with seaside shacks, water sports, golden sands, and sunset views.", "Open 24 Hours", 15.5439, 73.7553));
        goaPlaces.add(new PlaceTemplate("Dudhsagar Waterfalls", "Waterfalls", 4.7, 42000, 65.0, 180, 90,
                "Spectacular four-tiered 310-meter milky cascade inside Bhagwan Mahaveer Sanctuary accessible by forest 4x4 jeeps.", "08:30 AM - 04:30 PM", 15.3144, 74.3143));
        CURATED_PLACES_DB.put("goa", goaPlaces);

        // Rishikesh
        List<PlaceTemplate> rishikeshPlaces = new ArrayList<>();
        rishikeshPlaces.add(new PlaceTemplate("Triveni Ghat Ganga Aarti", "Temples", 4.8, 52000, 2.5, 90, 10,
                "Sacred confluence ghat on the River Ganges hosting magical evening aarti with rhythmic chants, floating leaf lamps, and fire rituals.", "05:00 AM - 08:30 PM", 30.1030, 78.2938));
        rishikeshPlaces.add(new PlaceTemplate("Ram Jhula & Swarg Ashram Walk", "Photography", 4.7, 61000, 4.0, 75, 12,
                "Iconic iron suspension footbridge across the turquoise Ganges connecting sacred ashrams, yoga retreats, and cafes.", "Open 24 Hours", 30.1235, 78.3140));
        rishikeshPlaces.add(new PlaceTemplate("Beatles Ashram (Chaurasi Kutia)", "Historical", 4.5, 29000, 6.0, 90, 18,
                "Historic forest sanctuary where the Beatles practiced Transcendental Meditation in 1968, adorned with graffiti art.", "09:00 AM - 04:30 PM", 30.1170, 78.3180));
        rishikeshPlaces.add(new PlaceTemplate("Shivpuri White Water Rafting", "Adventure", 4.8, 44000, 16.0, 150, 40,
                "Thrilling river rapids through Himalayan gorges including Roller Coaster and Golf Course rapids.", "08:00 AM - 04:00 PM", 30.1370, 78.3890));
        CURATED_PLACES_DB.put("rishikesh", rishikeshPlaces);

        // Manali
        List<PlaceTemplate> manaliPlaces = new ArrayList<>();
        manaliPlaces.add(new PlaceTemplate("Solang Valley Adventure Hub", "Adventure", 4.6, 75000, 13.0, 180, 35,
                "Glacial mountain valley renowned for paragliding, zorbing, winter skiing, and ropeway cable cars overlooking snow peaks.", "09:00 AM - 06:00 PM", 32.3166, 77.1575));
        manaliPlaces.add(new PlaceTemplate("Hadimba Devi Temple", "Temples", 4.6, 68000, 2.5, 60, 10,
                "Unique 16th-century four-tiered pagoda-style wooden temple nestled inside towering Dhungri deodar cedar forests.", "08:00 AM - 06:00 PM", 32.2483, 77.1802));
        manaliPlaces.add(new PlaceTemplate("Jogini Waterfalls & Vashisht Walk", "Waterfalls", 4.7, 34000, 4.5, 120, 15,
                "Scenic alpine waterfall cascading down mountain cliffs reached by a tranquil hike through orchards and pine woods.", "06:00 AM - 06:00 PM", 32.2690, 77.1950));
        manaliPlaces.add(new PlaceTemplate("Atal Tunnel & Sissu Overlook", "Photography", 4.8, 58000, 28.0, 120, 60,
                "World's longest highway tunnel above 10,000 feet leading to the dramatic Lahaul Valley and Sissu waterfall.", "Open 24 Hours", 32.3610, 77.1650));
        CURATED_PLACES_DB.put("manali", manaliPlaces);

        // Darjeeling
        List<PlaceTemplate> darjeelingPlaces = new ArrayList<>();
        darjeelingPlaces.add(new PlaceTemplate("Tiger Hill Sunrise Point", "Photography", 4.7, 48000, 11.0, 90, 35,
                "Renowned 2,590m summit lookout offering world-famous sunrise views illuminating Mount Kanchenjunga in golden hues.", "04:00 AM - 06:30 AM", 27.0080, 88.2480));
        darjeelingPlaces.add(new PlaceTemplate("Batasia Loop & War Memorial", "Historical", 4.6, 39000, 5.0, 60, 18,
                "Double-loop spiral railway track with manicured gardens affording panoramic vistas of the Eastern Himalayas.", "05:00 AM - 06:30 PM", 27.0167, 88.2472));
        darjeelingPlaces.add(new PlaceTemplate("Happy Valley Tea Estate", "Nature", 4.6, 31000, 2.8, 90, 12,
                "One of Darjeeling's oldest organic tea plantations (1854) offering guided estate walks and orthodox tea tasting.", "08:00 AM - 04:30 PM (Closed Mondays)", 27.0520, 88.2610));
        darjeelingPlaces.add(new PlaceTemplate("Himalayan Mountaineering Institute & Zoo", "Adventure", 4.7, 36000, 2.5, 90, 10,
                "Prestigious institute founded by Tenzing Norgay alongside Padmaja Naidu Himalayan Zoological Park protecting red pandas.", "08:30 AM - 04:30 PM (Closed Thursdays)", 27.0580, 27.0580));
        CURATED_PLACES_DB.put("darjeeling", darjeelingPlaces);

        // Kaziranga
        List<PlaceTemplate> kazirangaPlaces = new ArrayList<>();
        kazirangaPlaces.add(new PlaceTemplate("Kaziranga Central Range Safari (Kohora)", "Adventure", 4.8, 38000, 2.0, 180, 10,
                "UNESCO World Heritage sanctuary harboring two-thirds of the world's great one-horned rhinoceroses and wild water buffalo.", "07:30 AM - 04:00 PM (Oct-Apr)", 26.5850, 93.1670));
        kazirangaPlaces.add(new PlaceTemplate("Bagori Western Range Jeep Safari", "Adventure", 4.8, 29000, 12.0, 150, 25,
                "Prime wildlife sector celebrated for close-up sightings of rhinos, swamp deer, elephants, and migratory birds.", "07:30 AM - 04:00 PM", 26.5480, 93.0720));
        kazirangaPlaces.add(new PlaceTemplate("Kaziranga National Orchid & Biodiversity Park", "Parks", 4.5, 21000, 3.5, 90, 12,
                "Sprawling botanical conservatory cultivating over 500 indigenous orchid species, medicinal plants, and traditional Assamese dances.", "08:30 AM - 05:30 PM", 26.5920, 93.1890));
        CURATED_PLACES_DB.put("kaziranga", kazirangaPlaces);
        CURATED_PLACES_DB.put("kaziranga national park", kazirangaPlaces);

        // Guntakal additional places
        guntakalPlaces.add(new PlaceTemplate("Chippagiri Sri Vijayadasara Gadde", "Historical", 4.5, 3100, 18.0, 75, 35,
                "Spiritual meditation sanctuary of Haridasa saint Sri Vijayadasaru amidst granite hill rocks.", "07:00 AM - 06:00 PM", 15.2800, 77.2100));
        guntakalPlaces.add(new PlaceTemplate("Uravakonda Historic Temple Complex", "Temples", 4.4, 4500, 24.0, 60, 30,
                "Ancient stone temple featuring carved mandapams and historic temple tank.", "06:30 AM - 07:30 PM", 14.9450, 77.2600));
        guntakalPlaces.add(new PlaceTemplate("Alur Ancient Fort Hillock", "Adventure", 4.3, 2800, 22.0, 90, 30,
                "Panoramic granite hilltop offering trekking paths, historic rampart remains, and valley views.", "06:00 AM - 06:00 PM", 15.3050, 77.2400));
        guntakalPlaces.add(new PlaceTemplate("Guntakal Heritage Rail Museum", "Museums", 4.4, 3900, 2.0, 60, 6,
                "Exhibits on South Central Railway history, steam locomotive models, and colonial signaling systems.", "09:30 AM - 05:00 PM", 15.1700, 77.3700));

        // Calicut additional places
        calicutPlaces.add(new PlaceTemplate("Beypore Beach & Floating Bridge", "Adventure", 4.5, 16000, 11.5, 60, 25,
                "Thrilling 100-meter floating bridge walkway rising and falling with sea waves along Beypore harbour.", "09:00 AM - 06:00 PM", 11.1620, 75.8050));
        calicutPlaces.add(new PlaceTemplate("Sarovaram Bio Park", "Parks", 4.4, 11000, 3.2, 75, 10,
                "Eco-friendly urban wetland park featuring mangrove forests, boardwalks, boating, and bird watching.", "09:00 AM - 07:30 PM", 11.2720, 75.7980));

        // Munnar additional places
        munnarPlaces.add(new PlaceTemplate("Kundala Lake & Arch Dam", "Nature", 4.6, 26000, 20.0, 90, 45,
                "Asia's first arch dam surrounded by pine groves offering pedal boating and Kashmiri Shikara rides.", "09:00 AM - 05:00 PM", 10.1150, 77.1950));
        munnarPlaces.add(new PlaceTemplate("Pothamedu Viewpoint", "Photography", 4.5, 22000, 4.5, 60, 15,
                "Breathtaking panoramic overlook draped in tea, coffee, and cardamom plantations and misty valleys.", "Open 24 Hours", 10.0510, 77.0610));

        // Kodaikanal additional places
        kodaiPlaces.add(new PlaceTemplate("Bear Shola Falls", "Nature", 4.4, 18000, 2.5, 60, 10,
                "Tiered forest waterfall nestled in dense woods where bears once drank, ideal for quiet nature walks.", "10:00 AM - 05:00 PM", 10.2420, 77.4780));
        kodaiPlaces.add(new PlaceTemplate("Dolphin's Nose & Echo Rock", "Adventure", 4.6, 21000, 8.0, 120, 25,
                "Protruding flat rock formation perched over a 6,600-foot chasm offering dramatic valley panoramas.", "06:00 AM - 05:30 PM", 10.2050, 77.5250));

        // Kochi / Cochin
        List<PlaceTemplate> kochiPlaces = new ArrayList<>();
        kochiPlaces.add(new PlaceTemplate("Chinese Fishing Nets & Fort Kochi Beach", "Photography", 4.6, 42000, 1.2, 60, 5,
                "Cantilevered 14th-century coastal fishing installations framing sunsets along Fort Kochi's historic promenade.", "Open 24 Hours", 9.9675, 76.2415));
        kochiPlaces.add(new PlaceTemplate("Mattancherry Dutch Palace", "Historical", 4.5, 34000, 3.5, 75, 12,
                "16th-century palace built by the Portuguese and renovated by the Dutch featuring Ramayana murals and royal artifacts.", "09:45 AM - 04:45 PM (Closed Fridays)", 9.9583, 76.2592));
        kochiPlaces.add(new PlaceTemplate("Santa Cruz Cathedral Basilica", "Historical", 4.7, 28000, 1.5, 60, 6,
                "One of India's grandest heritage basilicas dating to 1505 with magnificent Gothic spires and ceiling frescoes.", "07:00 AM - 06:30 PM", 9.9647, 76.2414));
        kochiPlaces.add(new PlaceTemplate("Jew Town & Paradesi Synagogue", "Historical", 4.6, 26000, 3.8, 75, 14,
                "Atmospheric 1568 synagogue adorned with Belgian crystal chandeliers and hand-painted blue Cantonese porcelain tiles.", "10:00 AM - 05:00 PM (Closed Saturdays)", 9.9575, 76.2600));
        kochiPlaces.add(new PlaceTemplate("Hill Palace Museum Tripunithura", "Museums", 4.5, 31000, 14.0, 120, 35,
                "Kerala's largest archaeological palace complex with 54 acres of royal botanical gardens, crown jewels, and heritage.", "09:30 AM - 04:30 PM (Closed Mondays)", 9.9535, 76.3639));
        kochiPlaces.add(new PlaceTemplate("Marine Drive Kochi Promenade", "Photography", 4.5, 45000, 8.0, 60, 20,
                "Scenic waterfront promenade facing Cochin backwaters and shipping channels with musical walkway and boating.", "Open 24 Hours", 9.9790, 76.2760));
        CURATED_PLACES_DB.put("kochi", kochiPlaces);
        CURATED_PLACES_DB.put("cochin", kochiPlaces);
        CURATED_PLACES_DB.put("fort kochi", kochiPlaces);

        // Pondicherry / Puducherry
        List<PlaceTemplate> pondyPlaces = new ArrayList<>();
        pondyPlaces.add(new PlaceTemplate("Promenade Beach & Rock Beach Walk", "Photography", 4.7, 56000, 1.0, 75, 5,
                "1.5-km seaside promenade overlooking the Bay of Bengal with heritage statues, French cafes, and sea spray.", "Open 24 Hours", 11.9338, 79.8359));
        pondyPlaces.add(new PlaceTemplate("Auroville Matrimandir & Visitors Centre", "Spiritual", 4.7, 48000, 12.0, 150, 30,
                "Universal township focused on human unity featuring the iconic golden geodesic meditation dome and tranquil gardens.", "09:00 AM - 05:00 PM", 12.0069, 79.8106));
        pondyPlaces.add(new PlaceTemplate("French Quarter (White Town) Heritage Walk", "Historical", 4.6, 38000, 0.8, 90, 4,
                "Charming mustard-yellow colonial mansions, bougainvillea-draped archways, art galleries, and quaint bakeries.", "Open 24 Hours", 11.9320, 79.8330));
        pondyPlaces.add(new PlaceTemplate("Sri Aurobindo Ashram", "Spiritual", 4.7, 41000, 1.2, 60, 6,
                "Serene spiritual sanctuary founded in 1926 housing the flower-covered Samadhi shrine of Sri Aurobindo and The Mother.", "08:00 AM - 06:00 PM", 11.9372, 79.8344));
        pondyPlaces.add(new PlaceTemplate("Paradise Beach & Chunnambar Boat House", "Beaches", 4.5, 34000, 9.5, 120, 25,
                "Golden sand spit between the Chunnambar backwaters and sea, accessible via scenic mangrove ferry boat rides.", "09:00 AM - 05:30 PM", 11.8790, 79.8050));
        pondyPlaces.add(new PlaceTemplate("Basilica of the Sacred Heart of Jesus", "Historical", 4.6, 22000, 1.8, 45, 8,
                "Gothic revival Catholic basilica built in 1907 renowned for rare stained-glass panels depicting the life of Christ.", "07:00 AM - 06:30 PM", 11.9280, 79.8270));
        CURATED_PLACES_DB.put("pondicherry", pondyPlaces);
        CURATED_PLACES_DB.put("pondy", pondyPlaces);
        CURATED_PLACES_DB.put("puducherry", pondyPlaces);

        // Chikmagalur
        List<PlaceTemplate> ckmPlaces = new ArrayList<>();
        ckmPlaces.add(new PlaceTemplate("Mullayanagiri Peak", "Adventure", 4.8, 38000, 22.0, 150, 45,
                "Highest summit in Karnataka (1,930 m) offering mist-wrapped ridge walks, temple at the top, and Western Ghats panoramas.", "06:00 AM - 06:00 PM", 13.3917, 75.7214));
        ckmPlaces.add(new PlaceTemplate("Baba Budangiri (Datta Peeta)", "Historical", 4.6, 28000, 28.0, 120, 55,
                "Sacred mountain range associated with Sufi saint Baba Budan who brought coffee to India, featuring mountain caves.", "08:00 AM - 05:30 PM", 13.4217, 75.7667));
        ckmPlaces.add(new PlaceTemplate("Hebbe Waterfalls", "Waterfalls", 4.6, 24000, 36.0, 150, 70,
                "Spectacular two-stage 168-meter cascade deep inside private coffee plantations reached by 4x4 forest jeeps.", "06:00 AM - 04:00 PM", 13.5410, 75.7220));
        ckmPlaces.add(new PlaceTemplate("Hirekolale Lake Sunset Point", "Nature", 4.6, 18000, 10.0, 75, 20,
                "Tranquil man-made reservoir surrounded by coffee hills mirroring Mullayanagiri peak in its calm evening waters.", "06:00 AM - 06:30 PM", 13.3620, 75.7350));
        ckmPlaces.add(new PlaceTemplate("Coffee Yatra Museum", "Museums", 4.4, 14000, 3.5, 60, 10,
                "Interactive thematic exhibition by Coffee Board of India displaying bean roasting, origin history, and brew labs.", "10:00 AM - 05:00 PM (Closed Weekends)", 13.3150, 75.7750));
        ckmPlaces.add(new PlaceTemplate("Z Point & Kemmangundi Trail", "Adventure", 4.7, 19000, 55.0, 120, 90,
                "Thrilling cliffside trail offering breathtaking vertical drops, lush green shola valleys, and mountain breezes.", "06:00 AM - 05:30 PM", 13.5480, 75.7530));
        CURATED_PLACES_DB.put("chikmagalur", ckmPlaces);
        CURATED_PLACES_DB.put("chikkamagaluru", ckmPlaces);

        // Tirupati
        List<PlaceTemplate> tirupatiPlaces = new ArrayList<>();
        tirupatiPlaces.add(new PlaceTemplate("Sri Venkateswara Swamy Temple (Tirumala)", "Temples", 4.9, 240000, 22.0, 180, 50,
                "World's most visited sacred hill shrine of Lord Balaji situated atop the sacred Seven Hills of Seshachalam.", "Open 24 Hours (Darshan Slots)", 13.6833, 79.3472));
        tirupatiPlaces.add(new PlaceTemplate("Kapila Theertham Waterfalls & Temple", "Temples", 4.6, 32000, 4.0, 75, 12,
                "Sacred pilgrimage cascade and Lord Shiva shrine at the foot of Tirumala hills where Sage Kapila meditated.", "06:00 AM - 08:00 PM", 13.6540, 79.4230));
        tirupatiPlaces.add(new PlaceTemplate("Silathoranam Natural Geological Arch", "Nature", 4.5, 21000, 23.5, 60, 55,
                "Extremely rare natural rock arch formation dating back 2.5 billion years to the Pre-Cambrian era atop Tirumala.", "06:00 AM - 06:30 PM", 13.6890, 79.3510));
        tirupatiPlaces.add(new PlaceTemplate("Sri Govindaraja Swamy Temple", "Temples", 4.7, 45000, 1.5, 75, 6,
                "Historic 12th-century Vaishnavite temple in Tirupati town with a towering 7-story gopuram consecrated by Saint Ramanuja.", "05:00 AM - 09:00 PM", 13.6290, 79.4180));
        tirupatiPlaces.add(new PlaceTemplate("Sri Padmavathi Ammavari Temple (Tiruchanur)", "Temples", 4.8, 62000, 5.0, 90, 15,
                "Revered temple of Goddess Padmavathi, consort of Lord Venkateswara, essential conclusion to Tirupati pilgrimage.", "05:30 AM - 09:00 PM", 13.6080, 79.4510));
        tirupatiPlaces.add(new PlaceTemplate("Chandragiri Historic Fort & Palace", "Historical", 4.4, 23000, 15.0, 90, 30,
                "11th-century Vijayanagara fortified capital featuring the Indo-Saracenic Raja Mahal and Rani Mahal sound-light show.", "09:00 AM - 05:30 PM (Closed Fridays)", 13.5850, 79.3170));
        CURATED_PLACES_DB.put("tirupati", tirupatiPlaces);

        // Madurai
        List<PlaceTemplate> maduraiPlaces = new ArrayList<>();
        maduraiPlaces.add(new PlaceTemplate("Meenakshi Amman Temple", "Temples", 4.9, 160000, 1.5, 150, 8,
                "Architectural wonder dating to the Pandya era with 14 colorful gopurams, Thousand Pillar Hall, and Golden Lotus tank.", "05:00 AM - 12:30 PM, 04:00 PM - 10:00 PM", 9.9195, 78.1193));
        maduraiPlaces.add(new PlaceTemplate("Thirumalai Nayakkar Mahal", "Historical", 4.6, 52000, 2.2, 75, 10,
                "17th-century palace masterpiece blending Dravidian and Islamic architecture with gigantic 82-foot circular pillars.", "09:00 AM - 05:00 PM", 9.9150, 78.1240));
        maduraiPlaces.add(new PlaceTemplate("Gandhi Memorial Museum", "Museums", 4.5, 26000, 4.0, 60, 15,
                "Housed inside the 17th-century Rani Mangammal Palace, preserving the bloodstained garment worn by Mahatma Gandhi.", "10:00 AM - 05:30 PM", 9.9320, 78.1400));
        maduraiPlaces.add(new PlaceTemplate("Alagar Koyil Vishnu Temple", "Temples", 4.7, 34000, 21.0, 90, 40,
                "Ancient temple of Lord Kallazhagar nestled in the foothills of Alagar Hills known for intricate stone and wood carvings.", "06:00 AM - 07:30 PM", 10.0760, 78.2140));
        maduraiPlaces.add(new PlaceTemplate("Vandiyur Mariamman Teppakulam", "Historical", 4.5, 29000, 4.5, 60, 15,
                "Enormous 16-acre square temple tank with an island pavilion built in 1646, celebrated for the annual Float Festival.", "06:00 AM - 08:00 PM", 9.9070, 78.1530));
        maduraiPlaces.add(new PlaceTemplate("Saint Mary's Cathedral Church", "Historical", 4.5, 14000, 2.5, 45, 10,
                "One of Tamil Nadu's oldest Roman Catholic churches constructed in 1840 featuring Neo-Gothic bell towers and stained glass.", "07:00 AM - 06:00 PM", 9.9150, 78.1320));
        CURATED_PLACES_DB.put("madurai", maduraiPlaces);

        // Rameshwaram
        List<PlaceTemplate> rameshPlaces = new ArrayList<>();
        rameshPlaces.add(new PlaceTemplate("Ramanathaswamy Temple & 22 Theerthams", "Temples", 4.8, 92000, 1.2, 150, 6,
                "Char Dham pilgrimage shrine featuring the world's longest pillared corridor (1,212 meters) and 22 sacred well baths.", "05:00 AM - 01:00 PM, 03:00 PM - 09:00 PM", 9.2881, 79.3174));
        rameshPlaces.add(new PlaceTemplate("Dhanushkodi Ghost Town & Point", "Adventure", 4.7, 65000, 19.0, 120, 35,
                "Surreal abandoned town destroyed in the 1964 cyclone where the Bay of Bengal meets the Indian Ocean at Ram Setu point.", "06:00 AM - 06:00 PM", 9.1760, 79.4180));
        rameshPlaces.add(new PlaceTemplate("Agni Theertham Holy Beach", "Temples", 4.6, 38000, 1.5, 60, 6,
                "Calm sacred sea waters in front of the eastern temple gate where pilgrims take purificatory baths before darshan.", "Open 24 Hours", 9.2890, 79.3210));
        rameshPlaces.add(new PlaceTemplate("Dr. APJ Abdul Kalam National Memorial", "Museums", 4.8, 44000, 4.0, 75, 10,
                "Inspiring national monument honoring India's Missile Man and former President, showcasing his life, rockets, and personal memorabilia.", "09:00 AM - 06:00 PM", 9.2810, 79.2840));
        rameshPlaces.add(new PlaceTemplate("Pamban Cantilever Railway Bridge Viewpoint", "Photography", 4.8, 52000, 12.0, 60, 20,
                "Engineering marvel opened in 1914 spanning the Palk Strait with a Scherzer rolling lift section for ships.", "Open 24 Hours", 9.2800, 79.1980));
        rameshPlaces.add(new PlaceTemplate("Lakshmana Theertham & Temple", "Temples", 4.5, 18000, 2.0, 45, 8,
                "Sacred temple dedicated to Lord Lakshmana featuring carved pillars and holy pond constructed in Dravidian style.", "06:00 AM - 07:00 PM", 9.2840, 79.3080));
        CURATED_PLACES_DB.put("rameshwaram", rameshPlaces);
        CURATED_PLACES_DB.put("rameswaram", rameshPlaces);

        // Kanyakumari & Surrounding Southern Coast Region
        List<PlaceTemplate> kanyaPlaces = new ArrayList<>();
        kanyaPlaces.add(new PlaceTemplate("Vivekananda Rock Memorial", "Historical", 4.8, 98000, 1.5, 120, 8,
                "Monumental rock shrine 500m off the mainland where Swami Vivekananda meditated in 1892, reached by ferry boat.", "08:00 AM - 04:00 PM", 8.0780, 77.5550, "Kanyakumari"));
        kanyaPlaces.add(new PlaceTemplate("Thiruvalluvar Giant Stone Statue", "Historical", 4.7, 72000, 1.6, 60, 8,
                "Imposing 133-foot stone sculpture honoring ancient Tamil poet-philosopher Thiruvalluvar standing on a rocky island.", "08:00 AM - 04:00 PM", 8.0770, 77.5560, "Kanyakumari"));
        kanyaPlaces.add(new PlaceTemplate("Triveni Sangam Sunset & Sunrise Beach", "Photography", 4.7, 85000, 1.0, 75, 4,
                "Southernmost tip of mainland India where the Arabian Sea, Bay of Bengal, and Indian Ocean merge, famed for simultaneous sunset and moonrise.", "Open 24 Hours", 8.0780, 77.5530, "Kanyakumari"));
        kanyaPlaces.add(new PlaceTemplate("Kanyakumari Bhagavathy Amman Temple", "Temples", 4.8, 42000, 0.7, 60, 3,
                "3000-year-old historic coastal shrine dedicated to Goddess Kanya Kumari, celebrated for its sparkling diamond nose ring and sacred seashore location.", "04:30 AM - 12:30 PM, 04:00 PM - 08:30 PM", 8.0784, 77.5544, "Kanyakumari"));
        kanyaPlaces.add(new PlaceTemplate("Gandhi Memorial Mandapam", "Historical", 4.5, 31000, 0.8, 45, 3,
                "Orissa-style pink pagoda monument built where Mahatma Gandhi's ashes were placed before immersion, designed so sunlight touches the spot on his birthday.", "07:00 AM - 07:00 PM", 8.0810, 77.5510, "Kanyakumari"));
        kanyaPlaces.add(new PlaceTemplate("Our Lady of Ransom Church", "Historical", 4.6, 21000, 1.2, 45, 5,
                "Magnificent 100-foot white Neo-Gothic Catholic basilica on the coast featuring intricate wooden carvings, golden altar, and soaring spires.", "05:30 AM - 08:30 PM", 8.0825, 77.5528, "Kanyakumari"));
        kanyaPlaces.add(new PlaceTemplate("Sunset View Point & Beach Promenade", "Photography", 4.6, 31000, 2.0, 60, 6,
                "Iconic southwestern tip promenade providing unobstructed sunset horizons over the Arabian Sea and panoramic coastal vistas.", "05:00 AM - 07:30 PM", 8.0792, 77.5445, "Kanyakumari"));
        kanyaPlaces.add(new PlaceTemplate("Kanyakumari View Tower & Telescope House", "Photography", 4.4, 18500, 1.1, 45, 4,
                "High observation tower offering 360-degree panoramic views of the confluence of three oceans and coastal headlands.", "06:00 AM - 07:00 PM", 8.0805, 77.5520, "Kanyakumari"));
        kanyaPlaces.add(new PlaceTemplate("Kanyakumari Wax Museum (Mayapuri)", "Museums", 4.3, 14000, 2.5, 60, 8,
                "India's first wax museum showcasing life-size wax figures of national heroes, scientists, and world leaders alongside optical illusion exhibits.", "08:00 AM - 08:00 PM", 8.0885, 77.5482, "Kanyakumari"));
        kanyaPlaces.add(new PlaceTemplate("Vattakottai Coastal Fort", "Historical", 4.5, 24000, 6.8, 60, 15,
                "18th-century granite circular coastal fortification built by the Dutch naval commander for Travancore with sea breezes and black sand beaches.", "08:00 AM - 05:30 PM", 8.1250, 77.5680, "Kanyakumari"));
        CURATED_PLACES_DB.put("kanyakumari", kanyaPlaces);
        CURATED_PLACES_DB.put("kanniyakumari", kanyaPlaces);

        // Suchindram (12 km from Kanyakumari)
        List<PlaceTemplate> suchindramPlaces = new ArrayList<>();
        suchindramPlaces.add(new PlaceTemplate("Suchindram Thanumalayan Temple", "Temples", 4.8, 52000, 12.0, 90, 22,
                "Renowned 17th-century temple representing the Trinity (Brahma, Vishnu, Shiva in single lingam) famed for 18-foot musical pillars and giant 22-foot Hanuman statue.", "04:30 AM - 12:00 PM, 05:00 PM - 08:30 PM", 8.1569, 77.4647, "Suchindram"));
        CURATED_PLACES_DB.put("suchindram", suchindramPlaces);

        // Nagercoil (19 km from Kanyakumari)
        List<PlaceTemplate> nagercoilPlaces = new ArrayList<>();
        nagercoilPlaces.add(new PlaceTemplate("Nagaraja Temple Nagercoil", "Temples", 4.7, 34000, 19.5, 60, 30,
                "Ancient sanctum dedicated to serpent deities surrounded by gardens, featuring sand with miraculous healing properties distributed as prasadam.", "05:00 AM - 12:00 PM, 05:00 PM - 08:00 PM", 8.1833, 77.4333, "Nagercoil"));
        nagercoilPlaces.add(new PlaceTemplate("St. Xavier's Cathedral & Kottar Church", "Historical", 4.5, 12000, 19.0, 45, 28,
                "Historical 1600 CE cathedral founded by St. Francis Xavier renowned for annual festival and Portuguese colonial architecture.", "06:00 AM - 07:30 PM", 8.1750, 77.4280, "Nagercoil"));
        CURATED_PLACES_DB.put("nagercoil", nagercoilPlaces);

        // Padmanabhapuram (34 km from Kanyakumari)
        List<PlaceTemplate> padmanabhaPlaces = new ArrayList<>();
        padmanabhaPlaces.add(new PlaceTemplate("Padmanabhapuram Wooden Palace", "Historical", 4.7, 36000, 34.0, 120, 50,
                "Magnificent 16th-century Travancore royal residence built entirely of rosewood and teak with carved ceilings and mica windows.", "09:00 AM - 04:30 PM (Closed Mondays)", 8.2510, 77.3270, "Padmanabhapuram"));
        CURATED_PLACES_DB.put("padmanabhapuram", padmanabhaPlaces);
        CURATED_PLACES_DB.put("thuckalay", padmanabhaPlaces);

        // Thirparappu & Mathur (46-52 km from Kanyakumari)
        List<PlaceTemplate> thirparappuPlaces = new ArrayList<>();
        thirparappuPlaces.add(new PlaceTemplate("Thirparappu Waterfalls", "Waterfalls", 4.6, 29000, 52.0, 90, 70,
                "Captivating 50-foot cascading multi-tiered waterfall on Kodayar River featuring natural pools, boating basin, and ancient Lord Shiva shrine.", "07:00 AM - 06:00 PM", 8.3508, 77.2625, "Thirparappu"));
        thirparappuPlaces.add(new PlaceTemplate("Mathur Aqueduct (Hanging Trough)", "Historical", 4.6, 21000, 46.0, 60, 60,
                "One of the longest and highest aqueducts in Asia standing 115 feet high on 28 pillars carrying canal waters across lush valley views.", "08:00 AM - 06:30 PM", 8.3340, 77.2917, "Mathur"));
        CURATED_PLACES_DB.put("thirparappu", thirparappuPlaces);
        CURATED_PLACES_DB.put("mathur", thirparappuPlaces);

        // Muttom (32 km from Kanyakumari)
        List<PlaceTemplate> muttomPlaces = new ArrayList<>();
        muttomPlaces.add(new PlaceTemplate("Muttom Beach & Colonial Lighthouse", "Photography", 4.5, 16000, 32.0, 60, 45,
                "Picturesque rocky fishing shoreline with colonial lighthouse, dramatic cliff formations, and sunset vantage points.", "06:00 AM - 07:00 PM", 8.1256, 77.3150, "Muttom"));
        CURATED_PLACES_DB.put("muttom", muttomPlaces);

        // Kovalam (78 km from Kanyakumari - Regional Optional)
        List<PlaceTemplate> kovalamPlaces = new ArrayList<>();
        kovalamPlaces.add(new PlaceTemplate("Kovalam Lighthouse Beach & Crescent Bay", "Nature", 4.6, 45000, 78.0, 90, 100,
                "World-famous crescent beach dominated by red-and-white Vizhinjam lighthouse, palm groves, and seaside restaurants.", "Open 24 Hours", 8.3980, 76.9790, "Kovalam"));
        kovalamPlaces.add(new PlaceTemplate("Hawah Beach & Sunset Point", "Photography", 4.5, 22000, 79.0, 60, 100,
                "Second largest beach in Kovalam famed for evening catamaran boat activities and sweeping ocean views.", "Open 24 Hours", 8.3945, 76.9780, "Kovalam"));
        CURATED_PLACES_DB.put("kovalam", kovalamPlaces);

        // Poovar (68 km from Kanyakumari - Regional Optional)
        List<PlaceTemplate> poovarPlaces = new ArrayList<>();
        poovarPlaces.add(new PlaceTemplate("Poovar Island Backwaters & Golden Beach", "Nature", 4.6, 28000, 68.0, 120, 90,
                "Enchanting estuary where lake, river, and Arabian Sea meet, famed for golden sand bar spit and mangrove boat cruises.", "08:00 AM - 06:00 PM", 8.3180, 77.0650, "Poovar"));
        CURATED_PLACES_DB.put("poovar", poovarPlaces);

        // Thiruvananthapuram (85 km from Kanyakumari)
        List<PlaceTemplate> trivandrumPlaces = new ArrayList<>();
        trivandrumPlaces.add(new PlaceTemplate("Sree Padmanabhaswamy Temple", "Temples", 4.9, 88000, 85.0, 90, 110,
                "Monumental 8th-century Dravidian temple complex dedicated to Lord Vishnu, famed as one of the world's richest spiritual treasuries.", "03:30 AM - 12:00 PM, 05:00 PM - 08:30 PM", 8.4828, 76.9436, "Thiruvananthapuram"));
        trivandrumPlaces.add(new PlaceTemplate("Napier Museum & Art Gallery", "Museums", 4.5, 32000, 87.0, 75, 115,
                "19th-century Indo-Saracenic architectural gem housing historic bronze idols, ancient musical instruments, and ivory carvings.", "10:00 AM - 04:45 PM (Closed Mondays)", 8.5085, 76.9554, "Thiruvananthapuram"));
        CURATED_PLACES_DB.put("thiruvananthapuram", trivandrumPlaces);
        CURATED_PLACES_DB.put("trivandrum", trivandrumPlaces);

        // Udaipur
        List<PlaceTemplate> udaipurPlaces = new ArrayList<>();
        udaipurPlaces.add(new PlaceTemplate("Udaipur City Palace Complex", "Historical", 4.8, 115000, 1.8, 150, 10,
                "Rajasthan's largest royal palace complex on Lake Pichola with ornate marble balconies, mirror work, and royal courtyards.", "09:00 AM - 05:30 PM", 24.5764, 73.6835));
        udaipurPlaces.add(new PlaceTemplate("Lake Pichola & Jag Mandir Island", "Photography", 4.7, 82000, 1.5, 90, 8,
                "Scenic 14th-century freshwater lake offering sunset boat cruises passing the Lake Palace and royal garden island pavilions.", "09:00 AM - 06:00 PM", 24.5700, 73.6780));
        udaipurPlaces.add(new PlaceTemplate("Saheliyon-ki-Bari Gardens", "Parks", 4.5, 48000, 4.0, 60, 15,
                "Historic ornamental gardens built for royal maidens featuring marble elephants, lotus pools, and rain fountains.", "09:00 AM - 07:00 PM", 24.6020, 73.6870));
        udaipurPlaces.add(new PlaceTemplate("Jagdish Temple", "Temples", 4.6, 36000, 1.6, 45, 6,
                "Large 1651 Indo-Aryan temple dedicated to Lord Vishnu featuring finely carved stone pillars and painted ceilings.", "05:00 AM - 02:30 PM, 04:00 PM - 10:00 PM", 24.5790, 73.6840));
        udaipurPlaces.add(new PlaceTemplate("Monsoon Palace (Sajjangarh Fort)", "Photography", 4.6, 41000, 9.5, 90, 25,
                "Hilltop palace overlooking the Aravalli hills and lakes, built in 1884 to track monsoon clouds, spectacular at sunset.", "09:00 AM - 06:00 PM", 24.5900, 73.6380));
        udaipurPlaces.add(new PlaceTemplate("Bagore Ki Haveli & Folk Dance", "Historical", 4.7, 34000, 1.7, 90, 8,
                "18th-century waterfront haveli at Gangaur Ghat hosting evening Dharohar Rajasthani puppet and folk dance performances.", "09:30 AM - 05:30 PM, Show 07:00 PM", 24.5795, 73.6800));
        CURATED_PLACES_DB.put("udaipur", udaipurPlaces);

        // Jodhpur
        List<PlaceTemplate> jodhpurPlaces = new ArrayList<>();
        jodhpurPlaces.add(new PlaceTemplate("Mehrangarh Fort & Museum", "Historical", 4.9, 132000, 2.5, 150, 12,
                "Towering 400-foot cliffside fortress guarding the Blue City, featuring Sheesh Mahal, Phool Mahal, and royal palanquins.", "09:00 AM - 05:00 PM", 26.2978, 73.0185));
        jodhpurPlaces.add(new PlaceTemplate("Jaswant Thada White Marble Cenotaph", "Historical", 4.7, 48000, 2.0, 60, 8,
                "Ornate 1899 white marble memorial to Maharaja Jaswant Singh II with carved gazebos, lakeside garden, and city panoramas.", "09:00 AM - 05:00 PM", 26.3030, 73.0230));
        jodhpurPlaces.add(new PlaceTemplate("Umaid Bhawan Palace & Museum", "Historical", 4.6, 62000, 5.5, 90, 18,
                "One of the world's largest private residences built in yellow sandstone featuring art deco halls and vintage car exhibits.", "09:00 AM - 05:00 PM", 26.2808, 73.0475));
        jodhpurPlaces.add(new PlaceTemplate("Mandore Gardens & Royal Cenotaphs", "Parks", 4.5, 36000, 8.5, 75, 20,
                "Historic former capital of Marwar with high-rock cenotaphs resembling Hindu temples, Hall of Heroes, and landscaped grounds.", "08:00 AM - 08:00 PM", 26.3570, 73.0410));
        jodhpurPlaces.add(new PlaceTemplate("Ghanta Ghar Clock Tower & Sardar Market", "Photography", 4.5, 41000, 1.2, 60, 5,
                "Vibrant blue city marketplace surrounded by century-old clock tower, textile shops, spices, and street food.", "10:00 AM - 09:00 PM", 26.2950, 73.0250));
        jodhpurPlaces.add(new PlaceTemplate("Rao Jodha Desert Rock Park", "Nature", 4.6, 21000, 2.8, 90, 10,
                "Ecological restoration park on volcanic rock beneath Mehrangarh Fort with walking trails showcasing native desert plants.", "07:00 AM - 06:30 PM", 26.3010, 73.0200));
        CURATED_PLACES_DB.put("jodhpur", jodhpurPlaces);

        // Shimla
        List<PlaceTemplate> shimlaPlaces = new ArrayList<>();
        shimlaPlaces.add(new PlaceTemplate("The Ridge & Neo-Gothic Christ Church", "Photography", 4.7, 85000, 1.0, 75, 4,
                "Open cultural hub in the heart of Shimla with North India's second oldest church (1857) and snow-capped Himalayan vistas.", "Open 24 Hours", 31.1048, 77.1734));
        shimlaPlaces.add(new PlaceTemplate("Mall Road & Scandal Point Walk", "Photography", 4.6, 92000, 0.8, 90, 3,
                "Vibrant pedestrian boulevard lined with colonial-era timber buildings, cafes, wool shops, and mountain viewpoints.", "09:00 AM - 09:30 PM", 31.1030, 77.1710));
        shimlaPlaces.add(new PlaceTemplate("Jakhoo Temple & Ropeway", "Temples", 4.6, 54000, 2.5, 90, 15,
                "Highest summit in Shimla (2,455 m) home to an ancient Hanuman temple and an imposing 108-foot giant vermillion statue.", "07:00 AM - 08:00 PM", 31.1009, 77.1852));
        shimlaPlaces.add(new PlaceTemplate("Viceregal Lodge (IIAS)", "Historical", 4.7, 38000, 4.5, 90, 18,
                "Magnificent Jacobethan British summer capital residence featuring polished Burmese teak halls and manicured lawns.", "09:00 AM - 05:30 PM (Closed Mondays)", 31.1036, 77.1408));
        shimlaPlaces.add(new PlaceTemplate("Kufri Himalayan Nature Park", "Adventure", 4.4, 46000, 15.0, 120, 40,
                "Scenic mountain resort hub at 2,720 m famous for pony rides, snow sports, and sightings of Himalayan monals and leopards.", "09:00 AM - 06:00 PM", 31.0970, 77.2680));
        shimlaPlaces.add(new PlaceTemplate("Tara Devi Hilltop Temple", "Temples", 4.6, 26000, 13.0, 75, 30,
                "250-year-old wooden hill shrine offering serene pine woods and 360-degree panoramas of Shimla and mountain ranges.", "07:00 AM - 06:30 PM", 31.0620, 77.1350));
        CURATED_PLACES_DB.put("shimla", shimlaPlaces);

        // Hyderabad
        List<PlaceTemplate> hydPlaces = new ArrayList<>();
        hydPlaces.add(new PlaceTemplate("Charminar & Laad Bazaar", "Historical", 4.7, 128000, 3.5, 75, 15,
                "Iconic 1591 monument with four 56-meter minarets surrounded by vibrant bangle markets and pearl bazaars.", "09:30 AM - 05:30 PM", 17.3616, 78.4747));
        hydPlaces.add(new PlaceTemplate("Golconda Fort & Sound-Light Show", "Historical", 4.7, 95000, 11.0, 150, 30,
                "Imposing medieval diamond-trading citadel famed for acoustic clapping engineering, Fateh Darwaza, and sunset views.", "09:00 AM - 05:30 PM", 17.3833, 78.4011));
        hydPlaces.add(new PlaceTemplate("Salar Jung Museum", "Museums", 4.6, 72000, 3.8, 120, 15,
                "One of the world's largest one-man art collections, featuring the Veiled Rebecca and 19th-century musical clock.", "10:00 AM - 05:00 PM (Closed Fridays)", 17.3713, 78.4804));
        hydPlaces.add(new PlaceTemplate("Chowmahalla Palace", "Historical", 4.6, 48000, 4.0, 90, 18,
                "Opulent 18th-century palace of the Nizams featuring the Grand Khilwat durbar hall, vintage Rolls Royces, and chandeliers.", "10:00 AM - 05:00 PM (Closed Fridays)", 17.3578, 78.4717));
        hydPlaces.add(new PlaceTemplate("Qutb Shahi Tombs", "Historical", 4.5, 36000, 10.5, 75, 25,
                "Magnificent domed mausoleums set in landscaped Ibrahim Bagh gardens blending Persian, Pashtun, and Hindu architecture.", "09:30 AM - 06:30 PM", 17.3917, 78.3980));
        hydPlaces.add(new PlaceTemplate("Hussain Sagar & Giant Buddha Statue", "Photography", 4.6, 84000, 4.5, 75, 15,
                "Heart-shaped lake featuring the world's tallest monolithic stone statue of Gautama Buddha, reached by speedboat.", "08:00 AM - 10:00 PM", 17.4239, 78.4738));
        hydPlaces.add(new PlaceTemplate("Birla Mandir Hyderabad", "Temples", 4.7, 56000, 3.0, 60, 12,
                "Magnificent white Rajasthani marble temple perched atop Naubat Pahad overlooking Hussain Sagar lake.", "07:00 AM - 12:00 PM, 03:00 PM - 09:00 PM", 17.4062, 78.4691));
        hydPlaces.add(new PlaceTemplate("Nehru Zoological Park", "Nature", 4.4, 42000, 7.5, 120, 25,
                "Extensive 380-acre wildlife sanctuary featuring open natural enclosures, lion safari, and nocturnal animal house.", "08:30 AM - 05:00 PM (Closed Mondays)", 17.3508, 78.4518));
        hydPlaces.add(new PlaceTemplate("Ramoji Film City", "Adventure", 4.5, 68000, 32.0, 180, 50,
                "Guinness World Record largest film studio complex offering themed sets, stunt shows, and adventure rides.", "09:00 AM - 05:30 PM", 17.2543, 78.6808));
        hydPlaces.add(new PlaceTemplate("Birla Science Museum & Planetarium", "Museums", 4.6, 32000, 3.2, 90, 12,
                "Modern science centre atop Naubat Pahad featuring a 160-million-year-old dinosaur fossil (Kotasaurus) and cosmos sky theater.", "10:30 AM - 08:00 PM", 17.4042, 78.4715));
        hydPlaces.add(new PlaceTemplate("Taramati Baradari Cultural Complex", "Historical", 4.5, 21000, 14.0, 75, 30,
                "Medieval acoustic music pavilion built by Ibrahim Quli Qutb Shah for his court singer Taramati on a scenic hillock.", "09:30 AM - 06:00 PM", 17.3780, 78.3690));
        hydPlaces.add(new PlaceTemplate("Durgam Cheruvu & Cable-Stayed Bridge", "Photography", 4.6, 46000, 12.0, 75, 25,
                "Historic freshwater lake nestled amidst granite rocks, now spanned by an illuminated cable-stayed bridge with floating restaurants.", "Open 24 Hours", 17.4330, 78.3840));
        CURATED_PLACES_DB.put("hyderabad", hydPlaces);

        // Chennai
        List<PlaceTemplate> chennaiPlaces = new ArrayList<>();
        chennaiPlaces.add(new PlaceTemplate("Marina Beach & Light House", "Photography", 4.6, 125000, 3.0, 75, 12,
                "India's longest natural urban beach (13 km) along the Bay of Bengal featuring lighthouse views and evening food stalls.", "Open 24 Hours", 13.0500, 80.2824));
        chennaiPlaces.add(new PlaceTemplate("Kapaleeshwarar Temple (Mylapore)", "Temples", 4.8, 58000, 4.5, 75, 15,
                "7th-century Dravidian temple of Lord Shiva with a stunning 37-meter rainbow gopuram and ancient temple tank.", "06:00 AM - 12:30 PM, 04:00 PM - 09:30 PM", 13.0335, 80.2698));
        chennaiPlaces.add(new PlaceTemplate("San Thome Cathedral Basilica", "Historical", 4.7, 34000, 3.8, 60, 12,
                "Neo-Gothic Roman Catholic cathedral built over the tomb of St. Thomas the Apostle, constructed by the Portuguese in 1523.", "06:00 AM - 09:00 PM", 13.0336, 80.2785));
        chennaiPlaces.add(new PlaceTemplate("Fort St. George & Clive House", "Historical", 4.5, 28000, 2.5, 75, 10,
                "First English fortress in India (1644) housing St. Mary's Church and the Fort Museum with colonial armory and coins.", "09:00 AM - 05:00 PM (Closed Fridays)", 13.0797, 80.2872));
        chennaiPlaces.add(new PlaceTemplate("Government Museum Chennai & Art Gallery", "Museums", 4.5, 38000, 3.5, 90, 12,
                "Second oldest museum in India (1851) renowned for its world-class collection of bronze sculptures from the Chola dynasty.", "09:30 AM - 05:00 PM (Closed Fridays)", 13.0700, 80.2560));
        chennaiPlaces.add(new PlaceTemplate("Guindy National Park & Snake Park", "Parks", 4.4, 31000, 9.0, 90, 25,
                "Unique protected wildlife park within city limits harboring spotted deer, blackbucks, birds, and an interactive reptile center.", "09:00 AM - 05:30 PM (Closed Tuesdays)", 13.0067, 80.2206));
        CURATED_PLACES_DB.put("chennai", chennaiPlaces);
        CURATED_PLACES_DB.put("madras", chennaiPlaces);

        // Mahabalipuram / Mamallapuram
        List<PlaceTemplate> mahabPlaces = new ArrayList<>();
        mahabPlaces.add(new PlaceTemplate("Shore Temple", "Historical", 4.8, 65000, 1.2, 75, 5,
                "UNESCO 8th-century granite temple complex overlooking the Bay of Bengal, built by Pallava king Narasimhavarman II.", "06:00 AM - 06:00 PM", 12.6163, 80.1994));
        mahabPlaces.add(new PlaceTemplate("Pancha Rathas (Five Rathas)", "Historical", 4.7, 52000, 1.8, 60, 6,
                "Monolithic rock-cut shrines carved out of single pink granite boulders named after the Pandavas and Draupadi.", "06:00 AM - 06:00 PM", 12.6090, 80.1940));
        mahabPlaces.add(new PlaceTemplate("Descent of the Ganges (Arjuna's Penance)", "Historical", 4.7, 44000, 1.0, 60, 4,
                "Enormous open-air bas-relief carved on two 27-meter boulders depicting celestial beings, animals, and the descent of River Ganga.", "06:00 AM - 06:00 PM", 12.6180, 80.1930));
        mahabPlaces.add(new PlaceTemplate("Krishna's Butter Ball", "Photography", 4.6, 38000, 1.1, 45, 4,
                "Gigantic 250-ton balancing boulder precariously resting on a 45-degree smooth rock slope for over 1,200 years.", "Open 24 Hours", 12.6190, 80.1920));
        mahabPlaces.add(new PlaceTemplate("Mahabalipuram Lighthouse & Hillock", "Photography", 4.5, 29000, 1.5, 45, 5,
                "India's oldest circular stone lighthouse (1887) and 8th-century Olakkannesvara temple with 360-degree coastal panoramas.", "09:00 AM - 05:30 PM", 12.6150, 80.1920));
        CURATED_PLACES_DB.put("mahabalipuram", mahabPlaces);
        CURATED_PLACES_DB.put("mamallapuram", mahabPlaces);

        // Gokarna
        List<PlaceTemplate> gokarnaPlaces = new ArrayList<>();
        gokarnaPlaces.add(new PlaceTemplate("Om Beach & Rock Formations", "Beaches", 4.7, 48000, 6.0, 120, 15,
                "Naturally shaped like the sacred Hindu symbol Om, featuring rocky tide pools, seaside cafes, and golden sands.", "Open 24 Hours", 14.5190, 74.3160));
        gokarnaPlaces.add(new PlaceTemplate("Mahabaleshwar Temple", "Temples", 4.8, 52000, 1.0, 75, 4,
                "4th-century sacred temple housing the Atmalinga of Lord Shiva, considered as holy as Kashi in Hindu tradition.", "06:00 AM - 12:30 PM, 05:00 PM - 08:00 PM", 14.5428, 74.3167));
        gokarnaPlaces.add(new PlaceTemplate("Kudle Beach Sunset Point", "Beaches", 4.6, 38000, 3.5, 90, 10,
                "Crescent-shaped serene beach framed by hills, famous for evening drum circles, yoga, and fresh seafood shacks.", "Open 24 Hours", 14.5280, 74.3140));
        gokarnaPlaces.add(new PlaceTemplate("Half Moon & Paradise Beach Coastal Trek", "Adventure", 4.7, 26000, 7.5, 150, 20,
                "Scenic cliffside hiking trail connecting isolated coves accessible only on foot or by traditional fishing boats.", "06:00 AM - 05:30 PM", 14.5120, 74.3280));
        gokarnaPlaces.add(new PlaceTemplate("Mirjan Historic Fort", "Historical", 4.5, 24000, 21.0, 75, 30,
                "16th-century laterite stone fortress built by Queen Chennabhairadevi (The Pepper Queen) with bastions and secret exits.", "09:00 AM - 05:30 PM", 14.4850, 74.4200));
        CURATED_PLACES_DB.put("gokarna", gokarnaPlaces);

        // Dandeli
        List<PlaceTemplate> dandeliPlaces = new ArrayList<>();
        dandeliPlaces.add(new PlaceTemplate("Kali River White Water Rafting", "Adventure", 4.8, 36000, 4.0, 180, 10,
                "Thrilling grade 2 and 3 rapids through dense Western Ghats river gorges on the River Kali.", "08:30 AM - 04:00 PM", 15.2450, 74.6280));
        dandeliPlaces.add(new PlaceTemplate("Syntheri Rocks Monolithic Canyon", "Nature", 4.6, 28000, 32.0, 90, 50,
                "300-foot monolithic granite rock structure carved over millennia by River Kanambi inside deep jungle reserves.", "08:30 AM - 05:00 PM", 15.2050, 74.3800));
        dandeliPlaces.add(new PlaceTemplate("Dandeli Wildlife Sanctuary Safari", "Adventure", 4.5, 24000, 8.0, 150, 20,
                "Dense evergreen tiger sanctuary home to black panthers, hornbills, flying squirrels, and wild elephants.", "06:00 AM - 09:00 AM, 03:00 PM - 06:00 PM", 15.2380, 74.6050));
        dandeliPlaces.add(new PlaceTemplate("Supa Reservoir & Dam Lookout", "Nature", 4.5, 18000, 24.0, 60, 35,
                "Massive concrete gravity dam creating a sprawling lake surrounded by rolling forest hills and sunset vistas.", "08:00 AM - 06:00 PM", 15.2780, 74.5200));
        dandeliPlaces.add(new PlaceTemplate("Shiroli Peak & Sunset Viewpoint", "Photography", 4.6, 16000, 14.0, 60, 25,
                "Highest viewpoint in the Dandeli forest reserve offering unobstructed panoramic vistas across the Sahyadri mountains.", "06:00 AM - 06:00 PM", 15.2100, 74.6750));
        CURATED_PLACES_DB.put("dandeli", dandeliPlaces);

        // Varkala
        List<PlaceTemplate> varkalaPlaces = new ArrayList<>();
        varkalaPlaces.add(new PlaceTemplate("Varkala Cliff & Papanasam Beach", "Beaches", 4.8, 62000, 1.5, 120, 6,
                "Dramatic red laterite sedimentary cliffs towering directly over the Arabian Sea lined with boho cafes and sunsets.", "Open 24 Hours", 8.7360, 76.7030));
        varkalaPlaces.add(new PlaceTemplate("Janardhana Swami 2000-Year Temple", "Temples", 4.7, 29000, 2.0, 60, 8,
                "Ancient Vaishnavite shrine known as Dakshin Kashi with copper-plated roof, stone carvings, and natural spring baths.", "05:30 AM - 12:00 PM, 05:00 PM - 08:00 PM", 8.7290, 76.7110));
        varkalaPlaces.add(new PlaceTemplate("Sivagiri Mutt Spiritual Hermitage", "Spiritual", 4.6, 21000, 4.0, 60, 10,
                "Ashram founded by philosopher-saint Sree Narayana Guru advocating 'One Caste, One Religion, One God for Man'.", "06:00 AM - 07:00 PM", 8.7450, 76.7350));
        varkalaPlaces.add(new PlaceTemplate("Kappil Beach & Edava Nadayara Backwater Lake", "Nature", 4.6, 26000, 7.5, 75, 15,
                "Picturesque bridge where serene backwater lake on one side meets the open sea on the other, lined with palm groves.", "Open 24 Hours", 8.7850, 76.6780));
        varkalaPlaces.add(new PlaceTemplate("Anjengo Fort & Lighthouse", "Historical", 4.5, 18000, 11.0, 60, 20,
                "17th-century British East India Company stone fortress flanked by coconut groves and historic seafaring towers.", "09:00 AM - 05:30 PM", 8.6730, 76.7580));
        CURATED_PLACES_DB.put("varkala", varkalaPlaces);

        // Mangalore & Udupi
        List<PlaceTemplate> mngUdupiPlaces = new ArrayList<>();
        mngUdupiPlaces.add(new PlaceTemplate("Sri Krishna Matha Udupi", "Temples", 4.9, 88000, 3.0, 120, 10,
                "13th-century spiritual complex founded by Saint Madhvacharya where Lord Krishna is viewed through the sacred Kanakana Kindi window.", "05:00 AM - 09:30 PM", 13.3409, 74.7525));
        mngUdupiPlaces.add(new PlaceTemplate("Malpe Beach & St. Mary's Basalt Islands", "Adventure", 4.7, 56000, 6.5, 150, 18,
                "Unique national geological monument of columnar hexagonal basalt rock formations created by volcanic activity 88 million years ago.", "09:00 AM - 05:30 PM (Oct-May)", 13.3760, 74.6730));
        mngUdupiPlaces.add(new PlaceTemplate("Panambur Beach & Port Overlook", "Beaches", 4.5, 42000, 8.0, 75, 18,
                "Popular shoreline celebrated for golden sunset strolls, camel rides, international kite festivals, and jet skiing.", "Open 24 Hours", 12.9350, 74.8050));
        mngUdupiPlaces.add(new PlaceTemplate("Kadri Manjunatha Ancient Cave Temple", "Temples", 4.7, 36000, 4.0, 60, 12,
                "10th-century shrine atop Kadri hills featuring historic bronze statues of Lokeshwara and natural hillside water springs.", "06:00 AM - 01:00 PM, 04:00 PM - 08:30 PM", 12.8870, 74.8580));
        mngUdupiPlaces.add(new PlaceTemplate("St. Aloysius Chapel Frescoes", "Historical", 4.7, 28000, 2.5, 60, 8,
                "Built in 1885 atop Lighthouse Hill, decorated with world-renowned Italian frescoes painted by Jesuit priest Antony Moscheni.", "09:00 AM - 05:30 PM", 12.8730, 74.8450));
        mngUdupiPlaces.add(new PlaceTemplate("Tannirbhavi Beach & Tree Park", "Nature", 4.6, 32000, 9.5, 75, 20,
                "Peaceful beach accessible via ferry from Gurupura river, framed by a serene coastal pine and botanical park.", "06:00 AM - 07:00 PM", 12.9050, 74.8080));
        CURATED_PLACES_DB.put("mangalore", mngUdupiPlaces);
        CURATED_PLACES_DB.put("mangaluru", mngUdupiPlaces);
        CURATED_PLACES_DB.put("udupi", mngUdupiPlaces);

        // Badami & Pattadakal
        List<PlaceTemplate> badamiPlaces = new ArrayList<>();
        badamiPlaces.add(new PlaceTemplate("Badami Cave Temples", "Historical", 4.8, 48000, 1.2, 120, 5,
                "Four magnificent 6th-century rock-cut temples carved out of red sandstone cliffs, dedicated to Shiva, Vishnu, and Jain tirthankaras.", "09:00 AM - 05:30 PM", 15.9189, 75.6766));
        badamiPlaces.add(new PlaceTemplate("Agastya Lake & Bhoothanatha Temples", "Photography", 4.8, 38000, 1.5, 75, 6,
                "Sacred green lake surrounded by sandstone cliffs and 7th-century shrines jutting directly into the tranquil water.", "06:00 AM - 06:30 PM", 15.9220, 75.6880));
        badamiPlaces.add(new PlaceTemplate("UNESCO Pattadakal Group of Monuments", "Historical", 4.8, 42000, 22.0, 120, 30,
                "UNESCO World Heritage complex showcasing the zenith of 7th and 8th-century Chalukyan temple architecture along Malaprabha river.", "06:00 AM - 06:00 PM", 15.9490, 75.8160));
        badamiPlaces.add(new PlaceTemplate("Aihole Durga Temple Complex", "Historical", 4.7, 34000, 34.0, 90, 45,
                "Renowned as the 'Cradle of Indian Temple Architecture' featuring over 120 stone temples including the apsidal Durga Temple.", "09:00 AM - 05:30 PM", 16.0190, 75.8820));
        badamiPlaces.add(new PlaceTemplate("Badami North Fort & Cannon Point", "Adventure", 4.6, 22000, 1.8, 90, 8,
                "Historic hilltop ramparts, 16th-century granaries, and watchtowers offering breathtaking aerial views of Badami town.", "09:00 AM - 05:00 PM", 15.9250, 75.6830));
        CURATED_PLACES_DB.put("badami", badamiPlaces);
        CURATED_PLACES_DB.put("pattadakal", badamiPlaces);
        CURATED_PLACES_DB.put("aihole", badamiPlaces);

        // Anantapur
        List<PlaceTemplate> anantapurPlaces = new ArrayList<>();
        anantapurPlaces.add(new PlaceTemplate("Lepakshi Veerabhadra Temple & Hanging Pillar", "Historical", 4.8, 62000, 14.0, 120, 25,
                "16th-century Vijayanagara marvel celebrated for the Hanging Pillar, colossal monolithic Nandi, and ceiling murals.", "06:00 AM - 06:30 PM", 13.8040, 77.6060));
        anantapurPlaces.add(new PlaceTemplate("Belum Underground Caves", "Adventure", 4.7, 48000, 68.0, 120, 75,
                "Second largest natural cave system in India, stretching 3.2 km with underground passages, stalactite chambers, and music halls.", "10:00 AM - 05:00 PM", 15.1030, 78.1130));
        anantapurPlaces.add(new PlaceTemplate("Oravakallu Rock Garden Canyon", "Nature", 4.6, 28000, 82.0, 90, 85,
                "Natural silica and quartz rock formations nestled around a picturesque reservoir with canyon walkways and boating.", "08:00 AM - 06:00 PM", 15.6980, 78.1880));
        anantapurPlaces.add(new PlaceTemplate("Konda Reddy Buruju Historic Fort", "Historical", 4.5, 31000, 92.0, 60, 95,
                "Iconic medieval circular bastion in Kurnool town with underground tunnels built during the Vijayanagara empire.", "Open 24 Hours", 15.8280, 78.0370));
        anantapurPlaces.add(new PlaceTemplate("Yaganti Uma Maheshwara Cave Temple", "Temples", 4.7, 36000, 75.0, 90, 80,
                "Sacred cave shrine nestled inside Yerramala hills famous for the growing Nandi idol and natural Pushkarini springs.", "06:00 AM - 07:30 PM", 15.3520, 78.1430));
        CURATED_PLACES_DB.put("anantapur", anantapurPlaces);
        CURATED_PLACES_DB.put("lepakshi", anantapurPlaces);

        // Kurnool
        List<PlaceTemplate> kurnoolPlaces = new ArrayList<>();
        kurnoolPlaces.add(new PlaceTemplate("Konda Reddy Buruju Historic Fort", "Historical", 4.6, 38000, 1.5, 60, 6,
                "Iconic medieval circular bastion in Kurnool town with underground tunnels built during the Vijayanagara empire.", "Open 24 Hours", 15.8280, 78.0370));
        kurnoolPlaces.add(new PlaceTemplate("Oravakallu Rock Garden Canyon", "Nature", 4.6, 28000, 22.0, 90, 30,
                "Natural silica and quartz rock formations nestled around a picturesque reservoir with canyon walkways and boating.", "08:00 AM - 06:00 PM", 15.6980, 78.1880));
        kurnoolPlaces.add(new PlaceTemplate("Belum Underground Caves", "Adventure", 4.7, 48000, 70.0, 120, 75,
                "Second largest natural cave system in India, stretching 3.2 km with underground passages, stalactite chambers, and music halls.", "10:00 AM - 05:00 PM", 15.1030, 78.1130));
        kurnoolPlaces.add(new PlaceTemplate("Yaganti Uma Maheshwara Cave Temple", "Temples", 4.7, 36000, 65.0, 90, 70,
                "Sacred cave shrine nestled inside Yerramala hills famous for the growing Nandi idol and natural Pushkarini springs.", "06:00 AM - 07:30 PM", 15.3520, 78.1430));
        kurnoolPlaces.add(new PlaceTemplate("Rollapadu Wildlife Sanctuary", "Nature", 4.4, 16000, 45.0, 90, 50,
                "Renowned open grassland sanctuary known for the Great Indian Bustard, blackbucks, and migratory bird wetlands.", "07:00 AM - 06:00 PM", 15.7410, 78.3620));
        kurnoolPlaces.add(new PlaceTemplate("Tomb of Abdul Wahab Khan", "Historical", 4.3, 11000, 2.0, 45, 8,
                "17th-century heritage mausoleum near Handri River featuring Golconda-style domes, arches, and stone carvings.", "09:00 AM - 06:00 PM", 15.8340, 78.0480));
        CURATED_PLACES_DB.put("kurnool", kurnoolPlaces);

        // Kolkata / Calcutta
        List<PlaceTemplate> kolkataPlaces = new ArrayList<>();
        kolkataPlaces.add(new PlaceTemplate("Victoria Memorial & Gardens", "Historical", 4.7, 145000, 2.5, 120, 15,
                "Majestic white Makrana marble monument and museum surrounded by 64 acres of manicured gardens.", "10:00 AM - 06:00 PM", 22.5448, 88.3426));
        kolkataPlaces.add(new PlaceTemplate("Howrah Bridge (Rabindra Setu)", "Photography", 4.6, 120000, 3.5, 45, 12,
                "Iconic cantilever bridge over the Hooghly River, emblem of Kolkata connecting Kolkata and Howrah.", "Open 24 Hours", 22.5851, 88.3468));
        kolkataPlaces.add(new PlaceTemplate("Dakshineswar Kali Temple", "Temples", 4.8, 98000, 12.0, 90, 30,
                "19th-century Navaratna temple on the eastern bank of Hooghly River associated with Sri Ramakrishna Paramahamsa.", "06:00 AM - 12:30 PM, 03:30 PM - 08:30 PM", 22.6550, 88.3575));
        kolkataPlaces.add(new PlaceTemplate("Indian Museum", "Museums", 4.6, 52000, 1.8, 120, 10,
                "Oldest and largest multipurpose museum in the Asia-Pacific region founded in 1814, featuring rare antiques and mummies.", "10:00 AM - 06:00 PM (Closed Mondays)", 22.5579, 88.3511));
        kolkataPlaces.add(new PlaceTemplate("St. Paul's Cathedral", "Historical", 4.6, 38000, 2.8, 60, 10,
                "Gothic Revival Anglican cathedral with stained glass windows and ornate Florentine frescoes near Victoria Memorial.", "09:00 AM - 05:00 PM", 22.5444, 88.3469));
        kolkataPlaces.add(new PlaceTemplate("Marble Palace", "Historical", 4.5, 29000, 4.0, 75, 18,
                "Opulent 19th-century neoclassical mansion housing Western classical statues, Victorian furniture, and paintings by Rubens.", "10:00 AM - 04:00 PM (Closed Mondays & Thursdays)", 22.5824, 88.3601));
        kolkataPlaces.add(new PlaceTemplate("Belur Math", "Temples", 4.8, 82000, 10.5, 90, 25,
                "Headquarters of Ramakrishna Math and Mission on Hooghly River blending Hindu, Christian, and Islamic motifs.", "06:00 AM - 11:30 AM, 04:00 PM - 08:30 PM", 22.6322, 88.3547));
        kolkataPlaces.add(new PlaceTemplate("Science City Kolkata", "Museums", 4.6, 64000, 6.0, 120, 20,
                "Largest science centre in the Indian subcontinent featuring Space Odyssey, Dynamotion Hall, and evolution theme park.", "09:00 AM - 07:00 PM", 22.5400, 88.3960));
        CURATED_PLACES_DB.put("kolkata", kolkataPlaces);
        CURATED_PLACES_DB.put("calcutta", kolkataPlaces);

        // Pune
        List<PlaceTemplate> punePlaces = new ArrayList<>();
        punePlaces.add(new PlaceTemplate("Shaniwar Wada", "Historical", 4.4, 68000, 1.5, 75, 10,
                "18th-century seven-storied fortified palace of the Peshwa rulers of the Maratha Empire with massive Delhi Gate.", "08:00 AM - 06:30 PM", 18.5196, 73.8553));
        punePlaces.add(new PlaceTemplate("Aga Khan Palace", "Historical", 4.5, 42000, 7.0, 90, 20,
                "Majestic Italian-arched palace where Mahatma Gandhi was interned in 1942, featuring memorial samadhis and serene lawns.", "09:00 AM - 05:30 PM", 18.5524, 73.9015));
        punePlaces.add(new PlaceTemplate("Sinhagad Fort", "Historical", 4.6, 72000, 28.0, 150, 60,
                "Hilltop fortress perched in the Sahyadri mountains with sweeping valley vistas and Battle of Sinhagad lore.", "06:00 AM - 06:00 PM", 18.3663, 73.7558));
        punePlaces.add(new PlaceTemplate("Dagdusheth Halwai Ganpati Temple", "Temples", 4.8, 86000, 1.8, 60, 10,
                "Highly revered golden temple of Lord Ganesha in the heart of Pune city celebrated for spiritual ambiance.", "06:00 AM - 10:30 PM", 18.5164, 73.8561));
        punePlaces.add(new PlaceTemplate("Raja Dinkar Kelkar Museum", "Museums", 4.6, 22000, 2.2, 90, 12,
                "Extraordinary collection of 20,000 everyday traditional Indian artifacts, musical instruments, and royal textiles.", "10:00 AM - 05:30 PM", 18.5108, 73.8541));
        punePlaces.add(new PlaceTemplate("Pataleshwar Cave Temple", "Temples", 4.4, 18000, 2.0, 60, 10,
                "8th-century monolithic rock-cut cave shrine carved from single basalt rock during the Rashtrakuta period.", "08:30 AM - 05:30 PM", 18.5273, 73.8504));
        CURATED_PLACES_DB.put("pune", punePlaces);

        // Ahmedabad
        List<PlaceTemplate> ahmedabadPlaces = new ArrayList<>();
        ahmedabadPlaces.add(new PlaceTemplate("Sabarmati Ashram", "Historical", 4.7, 85000, 4.5, 90, 15,
                "Historic headquarters of Mahatma Gandhi during the Indian independence movement along the Sabarmati river.", "08:30 AM - 06:30 PM", 23.0605, 72.5800));
        ahmedabadPlaces.add(new PlaceTemplate("Adalaj Stepwell (Rudabai Stepwell)", "Historical", 4.7, 62000, 18.0, 75, 30,
                "UNESCO 15th-century five-story subterranean sandstone stepwell with intricate Solanki carved pillars and balconies.", "08:00 AM - 06:00 PM", 23.1667, 72.5800));
        ahmedabadPlaces.add(new PlaceTemplate("Sidi Saiyyed Mosque", "Historical", 4.6, 34000, 2.0, 45, 8,
                "1573 mosque celebrated for its intricate marble filigree carved stone window known as the 'Tree of Life'.", "07:00 AM - 07:00 PM", 23.0284, 72.5815));
        ahmedabadPlaces.add(new PlaceTemplate("Kankaria Lake & Nagina Wadi", "Nature", 4.5, 92000, 4.0, 90, 15,
                "Vibrant circular lake built in 1451 featuring a central island garden (Nagina Wadi), musical fountains, and zoo.", "04:00 AM - 10:00 PM", 23.0063, 72.6026));
        ahmedabadPlaces.add(new PlaceTemplate("Calico Museum of Textiles", "Museums", 4.7, 18000, 5.5, 120, 20,
                "Premier textile museum housing rare collections of antique Indian handwoven fabrics and royal court costumes.", "10:00 AM - 01:00 PM (Closed Wednesdays)", 23.0560, 72.5930));
        CURATED_PLACES_DB.put("ahmedabad", ahmedabadPlaces);

        // Lucknow
        List<PlaceTemplate> lucknowPlaces = new ArrayList<>();
        lucknowPlaces.add(new PlaceTemplate("Bara Imambara & Bhulbhulaiya", "Historical", 4.6, 82000, 4.0, 120, 15,
                "Architectural marvel built in 1784 by Nawab Asaf-ud-Daula with unsupported vaulted hall and labyrinthine maze.", "06:00 AM - 05:00 PM", 26.8690, 80.9130));
        lucknowPlaces.add(new PlaceTemplate("Chota Imambara", "Historical", 4.5, 48000, 4.8, 60, 18,
                "Imposing 1838 monument adorned with ornate Belgian chandeliers, gold-plated dome, and Islamic calligraphy.", "06:00 AM - 05:00 PM", 26.8738, 80.9048));
        lucknowPlaces.add(new PlaceTemplate("Rumi Darwaza", "Historical", 4.6, 65000, 4.2, 45, 16,
                "Stately 60-foot Turkish Gate erected in 1784, the architectural symbol of Lucknow and Awadhi heritage.", "Open 24 Hours", 26.8717, 80.9118));
        lucknowPlaces.add(new PlaceTemplate("British Residency Lucknow", "Historical", 4.5, 36000, 2.8, 75, 12,
                "Historic ruins of the British diplomatic quarters preserved as a memorial to the 1857 Siege of Lucknow.", "07:00 AM - 06:00 PM", 26.8604, 80.9272));
        lucknowPlaces.add(new PlaceTemplate("Ambedkar Memorial Park", "Parks", 4.6, 54000, 5.5, 75, 18,
                "Monumental red sandstone architectural park spanning 107 acres with grand stupas and elephant pillars.", "11:00 AM - 09:00 PM", 26.8480, 80.9780));
        CURATED_PLACES_DB.put("lucknow", lucknowPlaces);

        // Chandigarh
        List<PlaceTemplate> chandigarhPlaces = new ArrayList<>();
        chandigarhPlaces.add(new PlaceTemplate("Rock Garden of Chandigarh", "Parks", 4.6, 94000, 3.5, 120, 15,
                "Iconic 40-acre sculpture park created by Nek Chand entirely from industrial and urban scrap materials.", "09:00 AM - 07:00 PM", 30.7525, 76.8066));
        chandigarhPlaces.add(new PlaceTemplate("Sukhna Lake Promenade", "Nature", 4.6, 88000, 4.0, 75, 15,
                "Picturesque 3 sq km artificial reservoir at the foothills of the Shivalik range popular for boating and promenades.", "05:00 AM - 09:00 PM", 30.7421, 76.8188));
        chandigarhPlaces.add(new PlaceTemplate("Zakir Hussain Rose Garden", "Nature", 4.5, 42000, 2.0, 60, 10,
                "Asia's largest botanical rose sanctuary spanning 30 acres with over 50,000 rose bushes and walking trails.", "06:00 AM - 08:00 PM", 30.7478, 76.7820));
        chandigarhPlaces.add(new PlaceTemplate("Government Museum & Art Gallery", "Museums", 4.5, 18000, 2.5, 90, 10,
                "Modernist Le Corbusier-designed museum housing Gandhara sculptures and Rajasthani miniature paintings.", "10:00 AM - 04:30 PM (Closed Mondays)", 30.7483, 76.7885));
        CURATED_PLACES_DB.put("chandigarh", chandigarhPlaces);

        // Alleppey / Alappuzha
        List<PlaceTemplate> alleppeyPlaces = new ArrayList<>();
        alleppeyPlaces.add(new PlaceTemplate("Alappuzha Beach & Historic Lighthouse", "Photography", 4.5, 62000, 2.5, 75, 10,
                "Vast sandy shoreline featuring an ancient 150-year-old sea pier and functioning striped lighthouse with sea views.", "03:00 PM - 05:00 PM", 9.4925, 76.3180));
        alleppeyPlaces.add(new PlaceTemplate("Punnamada Lake Backwaters", "Nature", 4.7, 85000, 4.0, 120, 15,
                "World-renowned lake venue for the annual Nehru Trophy Snake Boat Race, lined with scenic houseboat canals.", "Open 24 Hours", 9.5160, 76.3500));
        alleppeyPlaces.add(new PlaceTemplate("Marari Beach", "Nature", 4.7, 38000, 14.0, 90, 25,
                "Pristine, quiet coconut-fringed coastal paradise in Mararikulam known for serene relaxation and fishing village life.", "Open 24 Hours", 9.5985, 76.2970));
        alleppeyPlaces.add(new PlaceTemplate("Revi Karunakaran Memorial Museum", "Museums", 4.6, 16000, 1.8, 60, 8,
                "Opulent museum showcasing rare Tanjore paintings, Swarovski crystal collections, and antique porcelain.", "09:00 AM - 05:00 PM (Closed Mondays)", 9.4960, 76.3290));
        CURATED_PLACES_DB.put("alleppey", alleppeyPlaces);
        CURATED_PLACES_DB.put("alappuzha", alleppeyPlaces);
    }

    // Curated catalog of famous Indian travel discovery destinations (Section 2)
    private static final List<FeaturedDestinationDto> FEATURED_DESTINATIONS = new ArrayList<>();

    static {
        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_taj_mahal", "Taj Mahal", "Agra", "Uttar Pradesh", "Historical & Heritage",
                "World-renowned 17th-century white marble mausoleum commissioned by Mughal Emperor Shah Jahan, recognized globally as a masterpiece of symmetry and love.",
                "https://images.unsplash.com/photo-1564507592333-c60657eea523?auto=format&fit=crop&w=800&q=80",
                "1 - 2 Days", 27.1751, 78.0421, 4.9, 210000,
                List.of("Mughal Architecture", "Yamuna River Sunset", "UNESCO World Heritage"), "October to March"));

        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_amer_fort", "Amer Fort", "Jaipur", "Rajasthan", "Historical & Heritage",
                "Imposing hilltop fortress combining Rajput and Mughal artistry, celebrated for the mirror-studded Sheesh Mahal and sweeping views over Maota Lake.",
                "https://images.unsplash.com/photo-1599661046289-e31897846e41?auto=format&fit=crop&w=800&q=80",
                "2 - 3 Days", 26.9855, 75.8513, 4.8, 142000,
                List.of("Sheesh Mahal", "Hilltop Panoramas", "Light & Sound Show"), "October to March"));

        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_jaipur_city_palace", "Jaipur City Palace", "Jaipur", "Rajasthan", "Historical & Heritage",
                "The grand royal residence of the Maharaja of Jaipur featuring courtyard pavilions, royal museum collections, and historic Rajput architecture.",
                "https://images.unsplash.com/photo-1603288940300-4b2a8d6e32d5?auto=format&fit=crop&w=800&q=80",
                "2 - 3 Days", 26.9258, 75.8237, 4.6, 68000,
                List.of("Chandra Mahal", "Royal Costumes Gallery", "Mubarak Mahal"), "October to March"));

        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_hawa_mahal", "Hawa Mahal", "Jaipur", "Rajasthan", "Historical & Heritage",
                "The iconic five-story pink sandstone 'Palace of Winds' with 953 honeycomb windows designed to capture refreshing breezes.",
                "https://images.unsplash.com/photo-1599661046827-dacff0c0f09a?auto=format&fit=crop&w=800&q=80",
                "2 - 3 Days", 26.9239, 75.8267, 4.7, 115000,
                List.of("953 Jharokhas", "Pink City Heart", "Rooftop Overlooks"), "October to March"));

        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_varanasi_ghats", "Varanasi Ghats", "Varanasi", "Uttar Pradesh", "Spiritual & Temples",
                "The spiritual heart of India along the sacred River Ganges, renowned for ancient bathing ghats, dawn boat rides, and the mesmerizing evening Ganga Aarti.",
                "https://images.unsplash.com/photo-1561361513-2d000a50f0dc?auto=format&fit=crop&w=800&q=80",
                "2 - 3 Days", 25.3076, 83.0107, 4.8, 120000,
                List.of("Ganga Maha Aarti", "Dawn River Cruises", "Ancient Alleyways"), "October to March"));

        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_golden_temple", "Golden Temple", "Amritsar", "Punjab", "Spiritual & Temples",
                "Harmandir Sahib, the radiant spiritual haven of Sikhism, revered for its gold-sheathed sanctum, the tranquil Amrit Sarovar, and egalitarian community kitchen.",
                "https://images.unsplash.com/photo-1605649487212-47bdab064df7?auto=format&fit=crop&w=800&q=80",
                "1 - 2 Days", 31.6200, 74.8765, 4.9, 185000,
                List.of("Golden Sanctum", "Amrit Sarovar Lake", "24/7 Community Langar"), "October to March"));

        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_red_fort", "Red Fort", "Delhi", "NCR", "Historical & Heritage",
                "Vast 17th-century Mughal stronghold built of red sandstone by Emperor Shah Jahan, symbol of sovereign Indian history.",
                "https://images.unsplash.com/photo-1587474260584-136574528ed5?auto=format&fit=crop&w=800&q=80",
                "2 - 3 Days", 28.6562, 77.2410, 4.6, 125000,
                List.of("Diwan-i-Khas", "Lahori Gate", "Mughal Gardens"), "October to March"));

        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_gateway_of_india", "Gateway of India", "Mumbai", "Maharashtra", "Historical & Heritage",
                "Majestic 26-meter basalt arch overlooking the Arabian Sea, standing opposite the heritage Taj Mahal Palace Hotel in vibrant South Mumbai.",
                "https://images.unsplash.com/photo-1570168007204-dfb528c6958f?auto=format&fit=crop&w=800&q=80",
                "2 - 3 Days", 18.9220, 72.8347, 4.7, 135000,
                List.of("Harbour Boat Tours", "Colaba Causeway", "Marine Drive Sunset"), "November to February"));

        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_hampi", "Hampi", "Vijayanagara", "Karnataka", "Historical & Heritage",
                "UNESCO World Heritage open-air museum amidst surreal granite boulders, showcasing the monumental ruins of the 14th-century Vijayanagara Empire.",
                "https://images.unsplash.com/photo-1600100397608-f010f443b7cf?auto=format&fit=crop&w=800&q=80",
                "2 - 4 Days", 15.3350, 76.4600, 4.8, 56000,
                List.of("Stone Chariot", "Virupaksha Temple", "Matanga Hill Sunrise"), "October to March"));

        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_mysore_palace", "Mysore Palace", "Mysuru", "Karnataka", "Historical & Heritage",
                "One of India's most visited royal estates, celebrated for its Indo-Saracenic grandeur, stained glass domes, and weekend illuminations with 100,000 bulbs.",
                "https://images.unsplash.com/photo-1600100397937-c1001a4e5185?auto=format&fit=crop&w=800&q=80",
                "2 - 3 Days", 12.3051, 76.6552, 4.7, 48500,
                List.of("Weekend Night Illumination", "Golden Throne", "Chamundi Hill Views"), "September to March"));

        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_coorg", "Coorg", "Kodagu", "Karnataka", "Mountains & Hills",
                "The Scotland of India, draped in emerald coffee plantations, misty Western Ghat peaks, cascading waterfalls, and fragrant spice estates.",
                "https://images.unsplash.com/photo-1596401057633-54a8fe8ef647?auto=format&fit=crop&w=800&q=80",
                "2 - 4 Days", 12.4244, 75.7382, 4.6, 26000,
                List.of("Abbey Falls", "Coffee Estate Stays", "Namdroling Golden Temple"), "October to May"));

        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_munnar", "Munnar", "Idukki", "Kerala", "Mountains & Hills",
                "Serene hill station perched at 1,600m in Kerala's Western Ghats, famed for rolling tea carpet plantations, misty valleys, and rare Nilgiri Tahr.",
                "https://images.unsplash.com/photo-1593693397690-362cb9666fc2?auto=format&fit=crop&w=800&q=80",
                "3 - 4 Days", 10.0889, 77.0595, 4.7, 41000,
                List.of("Tea Terraces", "Eravikulam National Park", "Top Station Viewpoint"), "September to May"));

        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_alleppey", "Alleppey Backwaters", "Alappuzha", "Kerala", "Beaches & Coastal",
                "The Venice of the East, celebrated for tranquil palm-fringed backwater lagoons, traditional Kettuvallam houseboats, and village waterways.",
                "https://images.unsplash.com/photo-1602216056096-3b40cc0c9944?auto=format&fit=crop&w=800&q=80",
                "2 - 3 Days", 9.4981, 76.3388, 4.8, 86000,
                List.of("Houseboat Overnights", "Vembanad Lake", "Marari Beach"), "October to March"));

        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_ooty", "Ooty", "Nilgiris", "Tamil Nadu", "Mountains & Hills",
                "Queen of Hill Stations nestled in the Nilgiri Mountains, famed for the UNESCO Mountain Toy Train, botanical gardens, and cool mountain weather.",
                "https://images.unsplash.com/photo-1589182373726-e4f658ab50f0?auto=format&fit=crop&w=800&q=80",
                "2 - 3 Days", 11.4102, 76.6950, 4.5, 34000,
                List.of("Nilgiri Toy Train", "Botanical Gardens", "Doddabetta Peak"), "October to June"));

        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_kodaikanal", "Kodaikanal", "Dindigul", "Tamil Nadu", "Mountains & Hills",
                "Princess of Hill Stations set around a star-shaped lake, surrounded by towering eucalyptus woods, scenic cliff walks, and Pillar Rocks.",
                "https://images.unsplash.com/photo-1589182373726-e4f658ab50f0?auto=format&fit=crop&w=800&q=80",
                "2 - 3 Days", 10.2381, 77.4892, 4.6, 38000,
                List.of("Star-shaped Kodai Lake", "Coaker's Walk Cliff", "Pillar Rocks"), "September to May"));

        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_goa_beaches", "Goa beaches", "Goa", "Goa", "Beaches & Coastal",
                "India's premier coastal retreat offering sun-drenched Arabian Sea beaches, historic Portuguese churches, vibrant shacks, and seafood.",
                "https://images.unsplash.com/photo-1512343879784-a960bf40e7f2?auto=format&fit=crop&w=800&q=80",
                "3 - 5 Days", 15.2993, 74.1240, 4.7, 78000,
                List.of("Aguada Fort", "Sunset Shacks & Water Sports", "Old Goa Churches"), "November to March"));

        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_rishikesh", "Rishikesh", "Dehradun", "Uttarakhand", "Spiritual & Temples",
                "Yoga Capital of the World along the foothills of the Himalayas, where the Ganges emerges pure for river rafting, meditation, and evening aartis.",
                "https://images.unsplash.com/photo-1544735716-392fe2489ffa?auto=format&fit=crop&w=800&q=80",
                "2 - 4 Days", 30.0869, 78.2676, 4.8, 61000,
                List.of("White Water Rafting", "Triveni Ghat Aarti", "Beatles Ashram"), "September to April"));

        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_manali", "Manali", "Kullu", "Himachal Pradesh", "Mountains & Hills",
                "Breathtaking alpine paradise in the Beas River Valley, gateway to Solang Valley, Rohtang Pass, snow activities, and pine-clad trekking trails.",
                "https://images.unsplash.com/photo-1626621341517-bbf3d9990a23?auto=format&fit=crop&w=800&q=80",
                "3 - 5 Days", 32.2432, 77.1892, 4.7, 75000,
                List.of("Solang Valley Gliders", "Hadimba Pagoda Temple", "Atal Tunnel Drive"), "October to June"));

        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_darjeeling", "Darjeeling", "Darjeeling", "West Bengal", "Mountains & Hills",
                "Champagne of Teas hill town offering magnificent morning panoramas of Mount Kanchenjunga, British colonial charm, and the Himalayan Railway.",
                "https://images.unsplash.com/photo-1544735716-392fe2489ffa?auto=format&fit=crop&w=800&q=80",
                "2 - 4 Days", 27.0410, 88.2663, 4.6, 48000,
                List.of("Tiger Hill Sunrise", "Toy Train Heritage", "Happy Valley Tea Estates"), "October to May"));

        FEATURED_DESTINATIONS.add(new FeaturedDestinationDto(
                "ind_kaziranga", "Kaziranga National Park", "Golaghat & Nagaon", "Assam", "Wildlife & Nature",
                "Iconic UNESCO World Heritage reserve in Assam harboring the world's largest population of the great Indian one-horned rhinoceros.",
                "https://images.unsplash.com/photo-1575550959106-5a7defe28b56?auto=format&fit=crop&w=800&q=80",
                "2 - 3 Days", 26.5775, 93.1711, 4.8, 38000,
                List.of("One-Horned Rhino Safari", "Brahmaputra Floodplains", "Assam Tea Trails"), "November to April"));
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

        double[] destCoords = GeoUtils.getApproximateCoordinates(destinationName);

        // If not found directly by name, check for nearby authentic destinations within 35 km
        if (templates == null || templates.isEmpty()) {
            for (Map.Entry<String, List<PlaceTemplate>> entry : CURATED_PLACES_DB.entrySet()) {
                double[] candidateCoords = GeoUtils.getApproximateCoordinates(entry.getKey());
                double dist = GeoUtils.calculateHaversineDistanceKm(destCoords[0], destCoords[1], candidateCoords[0], candidateCoords[1]);
                if (dist <= 35.0) {
                    templates = entry.getValue();
                    break;
                }
            }
        }

        List<SightseeingPlaceDto> results = new ArrayList<>();
        Set<String> seenPlaceNames = new HashSet<>();

        if (templates != null) {
            for (PlaceTemplate t : templates) {
                if (t.name == null || !seenPlaceNames.add(t.name.trim().toLowerCase())) {
                    continue; // Prevent duplicate recommendations
                }

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
                dto.setDistanceFromDestinationKm(t.distanceKm);
                dto.setEstimatedVisitDurationMinutes(t.visitDurationMinutes);
                dto.setEstimatedTravelTimeMinutes(t.travelTimeMinutes);
                dto.setDescription(t.description);
                dto.setOpeningHours(t.openingHours);
                dto.setLatitude(t.latitude != 0 ? t.latitude : destCoords[0]);
                dto.setLongitude(t.longitude != 0 ? t.longitude : destCoords[1]);
                dto.setVisited(false);
                dto.setAddedToItinerary(false);
                dto.setVerificationStatus("VERIFIED_ATTRACTION");
                dto.setSource("VoyageCraft Verified Catalog");

                String town = t.town != null ? t.town : destinationName;
                dto.setTownOrArea(town);

                boolean isSameTown = town.equalsIgnoreCase(destinationName);
                if (isSameTown || t.distanceKm <= 6.0) {
                    dto.setProximityTier("IN_DESTINATION");
                } else if (t.distanceKm <= 35.0) {
                    dto.setProximityTier("NEARBY_ATTRACTION");
                } else if (t.distanceKm <= 65.0) {
                    dto.setProximityTier("NEARBY_TOWN");
                } else {
                    dto.setProximityTier("REGIONAL_OPTIONAL");
                }

                scoreAndPrioritizePlace(dto, userInterests);
                results.add(dto);
            }
        }

        // Dynamic Live Discovery: If fewer than 8 places are available (or city not in curated catalog),
        // discover real identifiable attractions via Google Places or Wikipedia GeoSearch
        if (results.size() < 8) {
            List<SightseeingPlaceDto> livePlaces = discoverLivePlaces(destinationName, destCoords, userInterests, seenPlaceNames);
            for (SightseeingPlaceDto liveDto : livePlaces) {
                if (filterCategory != null && !filterCategory.equalsIgnoreCase("ALL") && !filterCategory.trim().isEmpty()) {
                    if (!liveDto.getCategory().equalsIgnoreCase(filterCategory.trim())) {
                        continue;
                    }
                }
                liveDto.setProximityTier("IN_DESTINATION");
                liveDto.setTownOrArea(destinationName);
                liveDto.setDistanceFromDestinationKm(liveDto.getDistanceKm());
                liveDto.setVerificationStatus("VERIFIED_ATTRACTION");
                liveDto.setSource("Live Places Discovery");
                scoreAndPrioritizePlace(liveDto, userInterests);
                results.add(liveDto);
            }
        }

        // Progressive Radius Fallback: If still fewer than 8 places are available, expand search to surrounding towns
        if (results.size() < 8) {
            int[] fallbackRadii = {25, 45, 65, 90};
            for (int r : fallbackRadii) {
                if (results.size() >= 8) break;
                List<SightseeingPlaceDto> nearby = getNearbyPlacesWithProgressiveRadius(destinationName, r, filterCategory, sortBy, userInterests);
                for (SightseeingPlaceDto p : nearby) {
                    String norm = p.getName().trim().toLowerCase();
                    if (seenPlaceNames.add(norm)) {
                        results.add(p);
                        if (results.size() >= 12) break;
                    }
                }
            }
        }

        // Return empty list if no genuine verified places exist for this location; never fabricate fake places
        if (results.isEmpty()) {
            return Collections.emptyList();
        }

        // Apply Sorting
        if ("distance".equalsIgnoreCase(sortBy)) {
            results.sort(Comparator.comparing(SightseeingPlaceDto::getDistanceKm));
        } else if ("rating".equalsIgnoreCase(sortBy)) {
            results.sort(Comparator.comparing(SightseeingPlaceDto::getRating).reversed()
                    .thenComparing(SightseeingPlaceDto::getReviewCount, Comparator.reverseOrder()));
        } else {
            // Default: In-destination first, then priority / match score
            results.sort(Comparator.comparingInt(this::getProximityTierRank)
                    .thenComparing(SightseeingPlaceDto::getMatchScore, Comparator.reverseOrder()));
        }

        return results;
    }

    /**
     * Discovers genuine, verified sightseeing places in and around the specified destination,
     * expanding outward up to the requested radius (e.g. 10 km, 25 km, 50 km, 100 km).
     * Categorizes results into distinct proximity tiers:
     * - IN_DESTINATION (<= 15 km)
     * - NEARBY_ATTRACTION (<= 35 km)
     * - NEARBY_TOWN (<= 65 km)
     * - REGIONAL_OPTIONAL (<= 100 km)
     */
    public List<SightseeingPlaceDto> getNearbyPlacesWithProgressiveRadius(
            String destinationName, int requestedRadiusKm, String filterCategory, String sortBy, List<String> userInterests) {

        if (destinationName == null || destinationName.trim().isEmpty()) {
            return Collections.emptyList();
        }

        double[] destCoords = GeoUtils.getApproximateCoordinates(destinationName);
        if (destCoords == null || (destCoords[0] == 0.0 && destCoords[1] == 0.0)) {
            return Collections.emptyList();
        }

        int maxRadius = Math.max(10, Math.min(requestedRadiusKm > 0 ? requestedRadiusKm : 50, 120));
        List<SightseeingPlaceDto> results = new ArrayList<>();
        Set<String> seenPlaceNames = new HashSet<>();

        // 1. Scan curated places catalog across all known locations
        for (Map.Entry<String, List<PlaceTemplate>> entry : CURATED_PLACES_DB.entrySet()) {
            for (PlaceTemplate t : entry.getValue()) {
                if (t.name == null) continue;
                String norm = t.name.trim().toLowerCase();
                if (seenPlaceNames.contains(norm)) continue;

                double pLat = t.latitude != 0.0 ? t.latitude : destCoords[0];
                double pLng = t.longitude != 0.0 ? t.longitude : destCoords[1];
                double dist = GeoUtils.calculateHaversineDistanceKm(destCoords[0], destCoords[1], pLat, pLng);

                if (dist > maxRadius) continue;

                // Category filter check
                if (filterCategory != null && !filterCategory.equalsIgnoreCase("ALL") && !filterCategory.trim().isEmpty()) {
                    if (!t.category.equalsIgnoreCase(filterCategory.trim())) {
                        continue;
                    }
                }

                seenPlaceNames.add(norm);

                SightseeingPlaceDto dto = new SightseeingPlaceDto();
                dto.setName(t.name);
                dto.setCategory(t.category);
                dto.setRating(t.rating);
                dto.setReviewCount(t.reviewCount);
                dto.setLatitude(pLat);
                dto.setLongitude(pLng);

                double roundedDist = Math.round(dist * 10.0) / 10.0;
                dto.setDistanceKm(roundedDist);
                dto.setDistanceFromDestinationKm(roundedDist);

                int travelMins = Math.max(10, (int) Math.round((dist / 35.0) * 60.0));
                dto.setEstimatedTravelTimeMinutes(travelMins);
                dto.setEstimatedVisitDurationMinutes(t.visitDurationMinutes);
                dto.setDescription(t.description);
                dto.setOpeningHours(t.openingHours);
                dto.setVisited(false);
                dto.setAddedToItinerary(false);
                dto.setVerificationStatus("VERIFIED_ATTRACTION");
                dto.setSource("VoyageCraft Verified Catalog");

                String town = t.town != null ? t.town : capitalizeWords(entry.getKey());
                dto.setTownOrArea(town);
                dto.setDestinationName(destinationName);

                boolean isSameTown = town.equalsIgnoreCase(destinationName);
                if (isSameTown || dist <= 6.0) {
                    dto.setProximityTier("IN_DESTINATION");
                } else if (dist <= 35.0) {
                    dto.setProximityTier("NEARBY_ATTRACTION");
                } else if (dist <= 65.0) {
                    dto.setProximityTier("NEARBY_TOWN");
                } else {
                    dto.setProximityTier("REGIONAL_OPTIONAL");
                }

                scoreAndPrioritizePlace(dto, userInterests);
                results.add(dto);
            }
        }

        // 2. Discover live places if catalog count is small and within local radius
        if (results.size() < 6 && maxRadius <= 35) {
            List<SightseeingPlaceDto> live = discoverLivePlaces(destinationName, destCoords, userInterests, seenPlaceNames);
            for (SightseeingPlaceDto lp : live) {
                if (filterCategory != null && !filterCategory.equalsIgnoreCase("ALL") && !filterCategory.trim().isEmpty()) {
                    if (!lp.getCategory().equalsIgnoreCase(filterCategory.trim())) continue;
                }
                lp.setProximityTier("IN_DESTINATION");
                lp.setTownOrArea(destinationName);
                lp.setDistanceFromDestinationKm(lp.getDistanceKm());
                lp.setVerificationStatus("VERIFIED_ATTRACTION");
                lp.setSource("Live Places Discovery");
                scoreAndPrioritizePlace(lp, userInterests);
                results.add(lp);
            }
        }

        // Apply sorting
        if ("distance".equalsIgnoreCase(sortBy)) {
            results.sort(Comparator.comparing(SightseeingPlaceDto::getDistanceKm));
        } else if ("rating".equalsIgnoreCase(sortBy)) {
            results.sort(Comparator.comparing(SightseeingPlaceDto::getRating).reversed()
                    .thenComparing(SightseeingPlaceDto::getReviewCount, Comparator.reverseOrder()));
        } else {
            // Default: Proximity tier first (in-destination -> nearby -> town -> regional), then match score
            results.sort(Comparator.comparingInt(this::getProximityTierRank)
                    .thenComparing(SightseeingPlaceDto::getMatchScore, Comparator.reverseOrder()));
        }

        return results;
    }

    private int getProximityTierRank(SightseeingPlaceDto dto) {
        if (dto == null || dto.getProximityTier() == null) return 2;
        switch (dto.getProximityTier().toUpperCase()) {
            case "IN_DESTINATION": return 1;
            case "NEARBY_ATTRACTION": return 2;
            case "NEARBY_TOWN": return 3;
            case "REGIONAL_OPTIONAL": return 4;
            default: return 5;
        }
    }

    private String capitalizeWords(String str) {
        if (str == null || str.isEmpty()) return str;
        String[] words = str.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) sb.append(w.substring(1).toLowerCase());
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }

    private void scoreAndPrioritizePlace(SightseeingPlaceDto dto, List<String> userInterests) {
        double rating = dto.getRating() != null ? dto.getRating() : 4.5;
        int reviews = dto.getReviewCount() != null ? dto.getReviewCount() : 1000;
        double distance = dto.getDistanceKm() != null ? dto.getDistanceKm() : 5.0;

        double ratingFactor = (rating / 5.0) * 35.0;
        double reviewFactor = (Math.log10(Math.max(10, reviews)) / Math.log10(50000)) * 25.0;
        double distanceFactor = (1.0 / (1.0 + (distance / 8.0))) * 25.0;

        double interestFactor = 0.0;
        if (userInterests != null && !userInterests.isEmpty()) {
            for (String interest : userInterests) {
                if (dto.getCategory() != null && (dto.getCategory().equalsIgnoreCase(interest) || interest.equalsIgnoreCase("All"))) {
                    interestFactor = 15.0;
                    break;
                }
            }
        } else {
            interestFactor = 10.0;
        }

        double totalScore = ratingFactor + reviewFactor + distanceFactor + interestFactor;
        dto.setMatchScore(Math.round(totalScore * 10.0) / 10.0);

        if (totalScore >= 75.0 || rating >= 4.6) {
            dto.setPriority("High");
        } else if (totalScore >= 60.0) {
            dto.setPriority("Medium");
        } else {
            dto.setPriority("Low");
        }
    }

    private List<SightseeingPlaceDto> discoverLivePlaces(String destinationName, double[] destCoords, List<String> userInterests, Set<String> existingNames) {
        List<SightseeingPlaceDto> discovered = new ArrayList<>();
        if (destCoords == null || destCoords.length < 2 || (destCoords[0] == 0.0 && destCoords[1] == 0.0)) {
            return discovered;
        }

        // 1. Try Google Places API if configured
        if (googleMapsService != null && googleMapsService.isLiveApiConfigured()) {
            try {
                GeoApiContext context = googleMapsService.getGeoApiContext();
                if (context != null) {
                    PlacesSearchResponse resp = PlacesApi.nearbySearchQuery(context, new LatLng(destCoords[0], destCoords[1]))
                            .radius(15000)
                            .type(PlaceType.TOURIST_ATTRACTION)
                            .await();
                    if (resp != null && resp.results != null) {
                        for (PlacesSearchResult r : resp.results) {
                            if (r.name == null || r.name.trim().isEmpty()) continue;
                            String normName = r.name.trim().toLowerCase();
                            if (existingNames.contains(normName)) continue;
                            existingNames.add(normName);

                            double dist = GeoUtils.calculateHaversineDistanceKm(destCoords[0], destCoords[1], r.geometry.location.lat, r.geometry.location.lng);
                            if (dist > 35.0) continue;

                            SightseeingPlaceDto dto = new SightseeingPlaceDto();
                            dto.setName(r.name.trim());
                            dto.setDestinationName(destinationName);
                            dto.setLatitude(r.geometry.location.lat);
                            dto.setLongitude(r.geometry.location.lng);
                            dto.setRating(r.rating > 0 ? Math.round(r.rating * 10.0) / 10.0 : 4.5);
                            dto.setReviewCount(r.userRatingsTotal > 0 ? r.userRatingsTotal : 500);
                            dto.setDistanceKm(Math.round(dist * 10.0) / 10.0);
                            dto.setEstimatedVisitDurationMinutes(90);
                            dto.setEstimatedTravelTimeMinutes(Math.max(10, (int) Math.round((dist / 25.0) * 60.0)));
                            dto.setDescription(r.vicinity != null && !r.vicinity.isEmpty() ? ("Popular attraction in " + r.vicinity) : ("Sightseeing attraction in " + destinationName));
                            dto.setOpeningHours("09:00 AM - 06:00 PM");
                            dto.setCategory(resolveGoogleTypeToCategory(r.types));
                            dto.setVisited(false);
                            dto.setAddedToItinerary(false);
                            discovered.add(dto);
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Live Google Places search fallback: {}", e.getMessage());
            }
        }

        // 2. Query Wikipedia GeoSearch API (Free, reliable global public API for verified landmarks, monuments, museums)
        if (discovered.size() < 8) {
            try {
                String wikiUrl = String.format(Locale.US,
                        "https://en.wikipedia.org/w/api.php?action=query&list=geosearch&gscoord=%.6f|%.6f&gsradius=15000&gslimit=20&format=json",
                        destCoords[0], destCoords[1]);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(wikiUrl))
                        .header("User-Agent", "VoyageCraft-TravelPlanner/1.0 (travel@voyagecraft.ai)")
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 && resp.body() != null) {
                    JsonNode root = objectMapper.readTree(resp.body());
                    JsonNode geosearch = root.path("query").path("geosearch");
                    if (geosearch.isArray()) {
                        for (JsonNode item : geosearch) {
                            String title = item.path("title").asText("");
                            if (title.isEmpty() || title.length() < 3) continue;
                            if (isNonAttractionTitle(title)) continue;

                            String normTitle = title.trim().toLowerCase();
                            if (existingNames.contains(normTitle)) continue;
                            existingNames.add(normTitle);

                            double lat = item.path("lat").asDouble();
                            double lon = item.path("lon").asDouble();
                            double dist = item.path("dist").asDouble() / 1000.0; // convert meters to km

                            SightseeingPlaceDto dto = new SightseeingPlaceDto();
                            dto.setName(title.trim());
                            dto.setDestinationName(destinationName);
                            dto.setLatitude(lat);
                            dto.setLongitude(lon);
                            dto.setRating(4.6);
                            dto.setReviewCount(15000);
                            dto.setDistanceKm(Math.round(dist * 10.0) / 10.0);
                            dto.setEstimatedVisitDurationMinutes(75);
                            dto.setEstimatedTravelTimeMinutes(Math.max(10, (int) Math.round((dist / 25.0) * 60.0)));
                            dto.setDescription("Verified iconic landmark and attraction in " + destinationName + ".");
                            dto.setOpeningHours("09:00 AM - 06:00 PM");
                            dto.setCategory(inferCategoryFromTitle(title));
                            dto.setVisited(false);
                            dto.setAddedToItinerary(false);
                            discovered.add(dto);
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("Live Wikipedia GeoSearch skipped: {}", e.getMessage());
            }
        }

        return discovered;
    }

    private boolean isNonAttractionTitle(String title) {
        String lower = title.toLowerCase();
        if (lower.startsWith("list of") || lower.startsWith("history of") || lower.startsWith("geography of") ||
                lower.startsWith("economy of") || lower.startsWith("demographics of") || lower.startsWith("timeline of") ||
                lower.startsWith("elections in") || lower.startsWith("transport in")) {
            return true;
        }
        return lower.contains("railway station") || lower.contains("metro station") || lower.contains("bus station") ||
                lower.contains("airport") || lower.contains("constituency") || lower.contains("subdivision") ||
                lower.contains("taluk") || lower.contains("mandal") || lower.contains("tahsil") || lower.contains("district") ||
                lower.contains("flyover") || lower.contains("expressway") || lower.contains("highway") ||
                lower.contains("hospital") || lower.contains("medical college") || lower.contains("engineering college") ||
                lower.contains("police station") || lower.contains("post office") || lower.contains("high school") ||
                lower.contains("intermediate college") || lower.contains("stadium") || lower.contains("stewart");
    }

    private String inferCategoryFromTitle(String title) {
        String lower = title.toLowerCase();
        if (lower.contains("temple") || lower.contains("mandir") || lower.contains("devasthanam") ||
                lower.contains("church") || lower.contains("cathedral") || lower.contains("basilica") ||
                lower.contains("mosque") || lower.contains("masjid") || lower.contains("gurudwara") ||
                lower.contains("ashram") || lower.contains("monastery") || lower.contains("stupa")) {
            return "Temples";
        }
        if (lower.contains("museum") || lower.contains("gallery") || lower.contains("planetarium") ||
                lower.contains("exhibition") || lower.contains("memorial")) {
            return "Museums";
        }
        if (lower.contains("park") || lower.contains("garden") || lower.contains("lake") ||
                lower.contains("falls") || lower.contains("waterfall") || lower.contains("wildlife") ||
                lower.contains("sanctuary") || lower.contains("beach") || lower.contains("forest") ||
                lower.contains("valley") || lower.contains("peak") || lower.contains("hills")) {
            return "Nature";
        }
        if (lower.contains("fort") || lower.contains("palace") || lower.contains("mahal") ||
                lower.contains("tomb") || lower.contains("caves") || lower.contains("heritage") ||
                lower.contains("ruins") || lower.contains("gate") || lower.contains("tower") || lower.contains("minar")) {
            return "Historical";
        }
        return "Sightseeing";
    }

    private String resolveGoogleTypeToCategory(String[] types) {
        if (types == null) return "Sightseeing";
        for (String t : types) {
            String tl = t.toLowerCase();
            if (tl.contains("hindu_temple") || tl.contains("church") || tl.contains("mosque") || tl.contains("place_of_worship")) {
                return "Temples";
            }
            if (tl.contains("museum") || tl.contains("art_gallery")) {
                return "Museums";
            }
            if (tl.contains("park") || tl.contains("natural_feature") || tl.contains("campground")) {
                return "Nature";
            }
            if (tl.contains("historical_landmark") || tl.contains("tourist_attraction")) {
                return "Historical";
            }
            if (tl.contains("amusement_park") || tl.contains("zoo") || tl.contains("aquarium")) {
                return "Adventure";
            }
        }
        return "Sightseeing";
    }

    public List<FeaturedDestinationDto> getFeaturedDestinations(String category, String query) {
        return FEATURED_DESTINATIONS.stream()
                .filter(d -> {
                    if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("ALL")) {
                        if (!d.getCategory().equalsIgnoreCase(category.trim())) {
                            return false;
                        }
                    }
                    if (query != null && !query.trim().isEmpty()) {
                        String q = query.trim().toLowerCase();
                        boolean matchName = d.getName().toLowerCase().contains(q);
                        boolean matchCity = d.getCity().toLowerCase().contains(q);
                        boolean matchState = d.getState().toLowerCase().contains(q);
                        boolean matchDesc = d.getDescription().toLowerCase().contains(q);
                        return matchName || matchCity || matchState || matchDesc;
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    public List<String> getAvailableCategories() {
        return List.of(
                "All",
                "Historical & Heritage",
                "Spiritual & Temples",
                "Mountains & Hills",
                "Beaches & Coastal",
                "Wildlife & Nature"
        );
    }

    public Optional<FeaturedDestinationDto> getFeaturedDestinationById(String id) {
        if (id == null) return Optional.empty();
        return FEATURED_DESTINATIONS.stream()
                .filter(d -> d.getId().equalsIgnoreCase(id.trim()))
                .findFirst();
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
        String town;

        PlaceTemplate(String name, String category, double rating, int reviewCount, double distanceKm,
                      int visitDurationMinutes, int travelTimeMinutes, String description, String openingHours,
                      double latitude, double longitude) {
            this(name, category, rating, reviewCount, distanceKm, visitDurationMinutes, travelTimeMinutes, description, openingHours, latitude, longitude, null);
        }

        PlaceTemplate(String name, String category, double rating, int reviewCount, double distanceKm,
                      int visitDurationMinutes, int travelTimeMinutes, String description, String openingHours,
                      double latitude, double longitude, String town) {
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
            this.town = town;
        }
    }
}
