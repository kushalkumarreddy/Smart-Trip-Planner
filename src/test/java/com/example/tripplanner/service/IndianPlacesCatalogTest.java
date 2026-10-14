package com.example.tripplanner.service;

import com.example.tripplanner.dto.FeaturedDestinationDto;
import com.example.tripplanner.util.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Famous Indian Places & Catalog Verification Tests")
class IndianPlacesCatalogTest {

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService();
    }

    @Test
    @DisplayName("Catalog contains representative variety of 20 famous Indian destinations across all required categories")
    void testFeaturedIndianDestinationsCatalog() {
        List<FeaturedDestinationDto> featured = recommendationService.getFeaturedDestinations(null, null);

        assertThat(featured).isNotEmpty();
        assertThat(featured.size()).isGreaterThanOrEqualTo(20);

        // Verify required iconic Indian destinations are present
        assertThat(featured).extracting(FeaturedDestinationDto::getName)
                .contains(
                        "Taj Mahal",
                        "Amer Fort",
                        "Jaipur City Palace",
                        "Hawa Mahal",
                        "Varanasi Ghats",
                        "Golden Temple",
                        "Red Fort",
                        "Gateway of India",
                        "Hampi",
                        "Mysore Palace",
                        "Coorg",
                        "Munnar",
                        "Alleppey Backwaters",
                        "Ooty",
                        "Kodaikanal",
                        "Goa beaches",
                        "Rishikesh",
                        "Manali",
                        "Darjeeling",
                        "Kaziranga National Park"
                );
    }

    @Test
    @DisplayName("Every destination has verified coordinates, realistic rating, and non-empty metadata")
    void testDestinationMetadataValidity() {
        List<FeaturedDestinationDto> featured = recommendationService.getFeaturedDestinations(null, null);

        for (FeaturedDestinationDto dest : featured) {
            assertThat(dest.getId()).isNotBlank();
            assertThat(dest.getName()).isNotBlank();
            assertThat(dest.getState()).isNotBlank();
            assertThat(dest.getCategory()).isNotBlank();
            assertThat(dest.getDescription()).isNotBlank();
            assertThat(dest.getRating()).isBetween(4.0, 5.0);

            // Coordinates must be strictly within Indian subcontinent geographical bounds (Lat 8°N to 36°N, Lng 68°E to 98°E)
            assertThat(dest.getLatitude()).isBetween(8.0, 36.0);
            assertThat(dest.getLongitude()).isBetween(68.0, 98.0);
        }
    }

    @Test
    @DisplayName("Categories filter returns only matching Indian destinations")
    void testFilterByCategory() {
        List<FeaturedDestinationDto> mountains = recommendationService.getFeaturedDestinations("Mountains & Hills", null);
        assertThat(mountains).isNotEmpty();
        assertThat(mountains).allMatch(d -> "Mountains & Hills".equalsIgnoreCase(d.getCategory()));
        assertThat(mountains).extracting(FeaturedDestinationDto::getName)
                .contains("Manali", "Munnar", "Ooty");

        List<FeaturedDestinationDto> beaches = recommendationService.getFeaturedDestinations("Beaches & Coastal", null);
        assertThat(beaches).isNotEmpty();
        assertThat(beaches).allMatch(d -> "Beaches & Coastal".equalsIgnoreCase(d.getCategory()));
        assertThat(beaches).extracting(FeaturedDestinationDto::getName).contains("Goa beaches", "Alleppey Backwaters");

        List<FeaturedDestinationDto> heritage = recommendationService.getFeaturedDestinations("Historical & Heritage", null);
        assertThat(heritage).isNotEmpty();
        assertThat(heritage).allMatch(d -> "Historical & Heritage".equalsIgnoreCase(d.getCategory()));
        assertThat(heritage).extracting(FeaturedDestinationDto::getName).contains("Hampi", "Amer Fort", "Mysore Palace");
    }

    @Test
    @DisplayName("Distinct places with similar names or regions resolve distinct verified coordinates")
    void testDistinctPlacesResolveDistinctCoordinates() {
        double[] amerFort = GeoUtils.getApproximateCoordinates("Amer Fort, Jaipur");
        double[] cityPalace = GeoUtils.getApproximateCoordinates("Jaipur City Palace, Jaipur");

        assertThat(amerFort).isNotNull();
        assertThat(cityPalace).isNotNull();
        // Amer Fort and City Palace are ~10km apart in Jaipur; lat/lng must not be identical
        assertThat(amerFort[0]).isNotEqualTo(cityPalace[0]);
        assertThat(amerFort[1]).isNotEqualTo(cityPalace[1]);
    }

    @Test
    @DisplayName("Unknown location lookup safely falls back to standard center without throwing exception")
    void testUnknownLocationLookupSafelyHandled() {
        double[] coords = GeoUtils.getApproximateCoordinates("NonExistentPlaceXYZ987");
        assertThat(coords).isNotNull();
        assertThat(coords).hasSize(2);
    }
}
