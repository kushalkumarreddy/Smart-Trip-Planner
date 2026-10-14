package com.example.tripplanner.service;

import com.example.tripplanner.dto.FeaturedDestinationDto;
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

        // Alleppey
        List<PlaceTemplate> alleppeyPlaces = new ArrayList<>();
        alleppeyPlaces.add(new PlaceTemplate("Alleppey Backwaters Houseboat Cruise", "Nature", 4.8, 86000, 2.0, 180, 8,
                "Serene network of interconnected canals, lagoons, and Vembanad Lake traversed on traditional Kettuvallam houseboats.", "07:00 AM - 06:00 PM", 9.4981, 76.3388));
        alleppeyPlaces.add(new PlaceTemplate("Alappuzha Beach & Ancient Sea Pier", "Photography", 4.5, 42000, 3.5, 75, 12,
                "Atmospheric sandy shoreline featuring a 150-year-old historic pier extending into the Arabian Sea and a striped lighthouse.", "Open 24 Hours", 9.4925, 76.3180));
        alleppeyPlaces.add(new PlaceTemplate("Marari Beach", "Photography", 4.6, 29000, 14.0, 90, 25,
                "Quiet pristine white sand beach framed by coconut palm groves and traditional fishing villages, ideal for relaxation.", "Open 24 Hours", 9.6010, 76.2970));
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

        // If not found directly by name, check for nearby authentic destinations within 35 km
        if (templates == null || templates.isEmpty()) {
            double[] coords = GeoUtils.getApproximateCoordinates(destinationName);
            for (Map.Entry<String, List<PlaceTemplate>> entry : CURATED_PLACES_DB.entrySet()) {
                double[] candidateCoords = GeoUtils.getApproximateCoordinates(entry.getKey());
                double dist = GeoUtils.calculateHaversineDistanceKm(coords[0], coords[1], candidateCoords[0], candidateCoords[1]);
                if (dist <= 35.0) {
                    templates = entry.getValue();
                    break;
                }
            }
        }

        // Return empty list if no genuine verified places exist for this location; never fabricate fake places
        if (templates == null || templates.isEmpty()) {
            return Collections.emptyList();
        }

        double[] destCoords = GeoUtils.getApproximateCoordinates(destinationName);

        // Convert templates to DTOs and score them, preventing duplicates
        List<SightseeingPlaceDto> results = new ArrayList<>();
        Set<String> seenPlaceNames = new HashSet<>();

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
            dto.setEstimatedVisitDurationMinutes(t.visitDurationMinutes);
            dto.setEstimatedTravelTimeMinutes(t.travelTimeMinutes);
            dto.setDescription(t.description);
            dto.setOpeningHours(t.openingHours);
            dto.setLatitude(t.latitude != 0 ? t.latitude : destCoords[0]);
            dto.setLongitude(t.longitude != 0 ? t.longitude : destCoords[1]);
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
