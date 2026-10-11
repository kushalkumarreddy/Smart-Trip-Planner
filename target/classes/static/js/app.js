/**
 * Trip Planner & Route Optimization Frontend Engine
 * Dynamic destination inputs, route rendering, map controls, and visited tracking
 */

let tripMap = null;
let mapMarkers = [];
let mapPolyline = null;
let isGoogleMapsActive = false;

// 1. Dynamic Destination Input Management
function addDestinationInput(value = "") {
    const container = document.getElementById("destinationsContainer");
    if (!container) return;

    const rowCount = container.querySelectorAll(".destination-row").length + 1;
    const row = document.createElement("div");
    row.className = "destination-row";
    row.innerHTML = `
        <span class="destination-number">${rowCount}</span>
        <input type="text" name="destinationNames" class="form-control destination-input" 
               placeholder="e.g. Destination (e.g. Mysore, Coorg, Ooty...)" 
               value="${value}" required list="placesDatalist">
        <button type="button" class="btn btn-outline-danger btn-sm" onclick="removeDestinationInput(this)">
            <i class="bi bi-trash"></i>
        </button>
    `;

    container.appendChild(row);
    renumberDestinations();

    // Attach autocomplete if Google Places is ready
    const newInput = row.querySelector(".destination-input");
    attachPlacesAutocomplete(newInput);
}

function removeDestinationInput(button) {
    const container = document.getElementById("destinationsContainer");
    if (!container) return;

    const rows = container.querySelectorAll(".destination-row");
    if (rows.length <= 1) {
        alert("At least one destination is required for planning your trip.");
        return;
    }

    button.closest(".destination-row").remove();
    renumberDestinations();
}

function renumberDestinations() {
    const container = document.getElementById("destinationsContainer");
    if (!container) return;

    const rows = container.querySelectorAll(".destination-row");
    rows.forEach((row, index) => {
        const badge = row.querySelector(".destination-number");
        if (badge) badge.textContent = index + 1;
    });
}

// 2. Google Places Autocomplete Integration
function attachPlacesAutocomplete(inputElement) {
    if (!inputElement) return;
    if (window.google && window.google.maps && window.google.maps.places) {
        try {
            const autocomplete = new google.maps.places.Autocomplete(inputElement, {
                types: ["(cities)"]
            });
            autocomplete.addListener("place_changed", function () {
                const place = autocomplete.getPlace();
                if (place && place.name) {
                    inputElement.value = place.name;
                }
            });
        } catch (e) {
            console.warn("Could not attach Google Autocomplete to input", e);
        }
    }
}

// 3. Map Initialization (Dual-Mode: Google Maps + Leaflet Fallback)
function initTripMap(mapElementId, locations = []) {
    const mapElement = document.getElementById(mapElementId);
    if (!mapElement) return;

    // Default center (South India / Bengaluru region)
    let centerLat = 12.9716;
    let centerLng = 77.5946;

    if (locations.length > 0 && locations[0].lat && locations[0].lng) {
        centerLat = locations[0].lat;
        centerLng = locations[0].lng;
    }

    // Check if Google Maps JS API is ready
    if (window.google && window.google.maps && window.googleMapsApiKeyAvailable) {
        initGoogleMap(mapElement, locations, centerLat, centerLng);
    } else {
        // Fallback to Leaflet OpenStreetMap
        initLeafletMap(mapElementId, locations, centerLat, centerLng);
    }
}

function initGoogleMap(mapElement, locations, centerLat, centerLng) {
    isGoogleMapsActive = true;
    tripMap = new google.maps.Map(mapElement, {
        center: { lat: centerLat, lng: centerLng },
        zoom: 8,
        mapTypeControl: true,
        streetViewControl: false,
        styles: [
            { featureType: "poi", elementType: "labels", stylers: [{ visibility: "off" }] }
        ]
    });

    const bounds = new google.maps.LatLngBounds();
    const pathCoordinates = [];

    locations.forEach((loc, index) => {
        if (!loc.lat || !loc.lng) return;

        const pos = { lat: loc.lat, lng: loc.lng };
        pathCoordinates.push(pos);
        bounds.extend(pos);

        const marker = new google.maps.Marker({
            position: pos,
            map: tripMap,
            title: loc.name,
            label: {
                text: `${index + 1}`,
                color: "#ffffff",
                fontWeight: "bold"
            },
            icon: {
                path: google.maps.SymbolPath.CIRCLE,
                scale: 12,
                fillColor: loc.isVisited ? "#10b981" : "#2563eb",
                fillOpacity: 1,
                strokeWeight: 2,
                strokeColor: "#ffffff"
            }
        });

        const infoWindow = new google.maps.InfoWindow({
            content: `
                <div style="padding: 6px; font-family: sans-serif;">
                    <strong style="font-size: 14px;">${loc.name}</strong><br>
                    <span style="font-size: 12px; color: #64748b;">Stop ${index + 1}</span><br>
                    ${loc.isVisited ? '<span style="color:#10b981; font-weight:bold;">☑ Visited</span>' : '<span style="color:#64748b;">☐ Unvisited</span>'}
                </div>
            `
        });

        marker.addListener("click", () => infoWindow.open(tripMap, marker));
        mapMarkers.push(marker);
    });

    if (pathCoordinates.length > 1) {
        mapPolyline = new google.maps.Polyline({
            path: pathCoordinates,
            geodesic: true,
            strokeColor: "#2563eb",
            strokeOpacity: 0.8,
            strokeWeight: 4
        });
        mapPolyline.setMap(tripMap);
        tripMap.fitBounds(bounds);
    }
}

function initLeafletMap(mapElementId, locations, centerLat, centerLng) {
    if (typeof L === "undefined") {
        console.warn("Leaflet library not loaded.");
        return;
    }

    tripMap = L.map(mapElementId).setView([centerLat, centerLng], 8);

    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
        maxZoom: 18
    }).addTo(tripMap);

    const latLngs = [];

    locations.forEach((loc, index) => {
        if (!loc.lat || !loc.lng) return;

        latLngs.push([loc.lat, loc.lng]);

        const markerColor = loc.isVisited ? "#10b981" : "#2563eb";
        const customIcon = L.divIcon({
            className: "custom-leaflet-marker",
            html: `
                <div style="background-color: ${markerColor}; color: white; border-radius: 50%; 
                            width: 26px; height: 26px; display: flex; align-items: center; 
                            justify-content: center; font-weight: bold; font-size: 12px; 
                            border: 2px solid white; box-shadow: 0 2px 4px rgba(0,0,0,0.3);">
                    ${index + 1}
                </div>
            `,
            iconSize: [26, 26],
            iconAnchor: [13, 13]
        });

        const marker = L.marker([loc.lat, loc.lng], { icon: customIcon }).addTo(tripMap);
        marker.bindPopup(`
            <div style="padding: 4px; font-family: 'Plus Jakarta Sans', sans-serif;">
                <h6 style="margin: 0 0 4px; font-weight: 700;">${loc.name}</h6>
                <div style="font-size: 12px; color: #64748b;">Stop #${index + 1}</div>
                <div style="margin-top: 4px; font-size: 12px;">
                    ${loc.isVisited ? '<span style="color:#10b981; font-weight:bold;">☑ Visited</span>' : '<span style="color:#64748b;">☐ Not Visited</span>'}
                </div>
            </div>
        `);
        mapMarkers.push(marker);
    });

    if (latLngs.length > 1) {
        mapPolyline = L.polyline(latLngs, {
            color: "#2563eb",
            weight: 4,
            opacity: 0.85,
            dashArray: "6, 8"
        }).addTo(tripMap);
        tripMap.fitBounds(mapPolyline.getBounds(), { padding: [40, 40] });
    }
}

// 4. Visited Checkbox Toggle for Sightseeing Places & Destinations
async function togglePlaceVisited(placeId, checkboxElement) {
    const isChecked = checkboxElement.checked;
    const itemCard = checkboxElement.closest(".timeline-content");

    try {
        const response = await fetch(`/api/places/${placeId}/visited`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ visited: isChecked })
        });

        if (!response.ok) throw new Error("Failed to update status");

        const progress = await response.json();
        updateProgressDisplay(progress);

        // Update card styling
        if (itemCard) {
            if (isChecked) {
                itemCard.classList.add("is-visited");
            } else {
                itemCard.classList.remove("is-visited");
            }
        }

        // Update corresponding timeline dot if present
        const timelineItem = checkboxElement.closest(".timeline-item");
        if (timelineItem) {
            const dot = timelineItem.querySelector(".timeline-dot");
            if (dot) {
                if (isChecked) {
                    dot.classList.add("visited");
                    dot.innerHTML = '<i class="bi bi-check"></i>';
                } else {
                    dot.classList.remove("visited");
                    dot.innerHTML = '<i class="bi bi-geo-alt"></i>';
                }
            }
        }
    } catch (err) {
        console.error("Error updating place visited:", err);
        checkboxElement.checked = !isChecked; // revert
        alert("Could not update visited status. Please try again.");
    }
}

async function toggleDestinationVisited(destinationId, checkboxElement) {
    const isChecked = checkboxElement.checked;
    try {
        const response = await fetch(`/api/destinations/${destinationId}/visited`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ visited: isChecked })
        });

        if (!response.ok) throw new Error("Failed to update status");

        const progress = await response.json();
        updateProgressDisplay(progress);
    } catch (err) {
        console.error("Error updating destination visited:", err);
        checkboxElement.checked = !isChecked;
    }
}

function updateProgressDisplay(progress) {
    const progressBar = document.getElementById("tripProgressBar");
    const progressText = document.getElementById("tripProgressText");
    const visitedCountText = document.getElementById("visitedPlacesCountText");

    if (progressBar) {
        progressBar.style.width = `${progress.percentage}%`;
        progressBar.setAttribute("aria-valuenow", progress.percentage);
    }
    if (progressText) {
        progressText.textContent = `${progress.percentage}% Completed`;
    }
    if (visitedCountText) {
        visitedCountText.textContent = `${progress.visitedPlaces}/${progress.totalPlaces}`;
    }
}

// 5. Smart Route Optimization (Apply TSP Recommended Order)
async function applyOptimizedRoute(tripId) {
    if (!confirm("Would you like to reorganize your trip using the recommended shortest route? This will recalculate schedule timings and distances.")) {
        return;
    }

    const btn = document.getElementById("btnApplyOptimization");
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Optimizing Route...';
    }

    try {
        const response = await fetch(`/api/trips/${tripId}/optimize`, {
            method: "POST"
        });

        if (!response.ok) throw new Error("Optimization failed");
        window.location.reload();
    } catch (err) {
        console.error("Optimization failed:", err);
        alert("Failed to apply optimized route. Please try again.");
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = '<i class="bi bi-magic me-1"></i> Use Recommended Route';
        }
    }
}

// 6. Add Sightseeing Place to Trip
function addPlaceItem(tripId, destinationId, placeDataBtn) {
    const d = placeDataBtn.dataset;
    const placeDto = {
        name: d.name,
        category: d.category || "Historical",
        rating: parseFloat(d.rating) || 4.5,
        reviewCount: parseInt(d.reviews) || 100,
        distanceKm: parseFloat(d.distance) || 2.0,
        estimatedVisitDurationMinutes: parseInt(d.duration) || 90,
        description: d.description || "",
        latitude: parseFloat(d.lat) || 0.0,
        longitude: parseFloat(d.lng) || 0.0,
        addedToItinerary: true
    };
    sendAddPlaceRequest(tripId, destinationId, placeDto, placeDataBtn);
}

async function sendAddPlaceRequest(tripId, destinationId, placeDto, placeDataBtn) {
    placeDataBtn.disabled = true;
    placeDataBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Adding...';

    try {
        const response = await fetch(`/api/trips/${tripId}/destinations/${destinationId}/places`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(placeDto)
        });

        if (!response.ok) throw new Error("Failed to add place");

        placeDataBtn.className = "btn btn-sm btn-success w-100";
        placeDataBtn.innerHTML = '<i class="bi bi-check-circle me-1"></i> Added to Itinerary';

        setTimeout(() => {
            window.location.reload();
        }, 500);
    } catch (err) {
        console.error("Error adding place to trip:", err);
        alert("Could not add place to itinerary.");
        placeDataBtn.disabled = false;
        placeDataBtn.innerHTML = '<i class="bi bi-plus-circle me-1"></i> Add to Trip';
    }
}

// 7. Sightseeing Categories Filter & Sorter
function filterPlacesByCategory(category, clickedElement) {
    // Highlight pill
    document.querySelectorAll(".filter-pill").forEach(el => el.classList.remove("active"));
    if (clickedElement) clickedElement.classList.add("active");

    const cards = document.querySelectorAll(".recommendation-item");
    cards.forEach(card => {
        const itemCat = card.getAttribute("data-category") || "";
        if (category === "ALL" || itemCat.toLowerCase() === category.toLowerCase()) {
            card.style.display = "";
        } else {
            card.style.display = "none";
        }
    });
}

function sortSightseeingPlaces(sortCriteria) {
    const container = document.getElementById("recommendationsDeck");
    if (!container) return;

    const items = Array.from(container.querySelectorAll(".recommendation-item"));

    items.sort((a, b) => {
        if (sortCriteria === "distance") {
            const distA = parseFloat(a.getAttribute("data-distance") || "0");
            const distB = parseFloat(b.getAttribute("data-distance") || "0");
            return distA - distB;
        } else if (sortCriteria === "rating") {
            const ratingA = parseFloat(a.getAttribute("data-rating") || "0");
            const ratingB = parseFloat(b.getAttribute("data-rating") || "0");
            return ratingB - ratingA;
        } else {
            // Popularity
            const scoreA = parseFloat(a.getAttribute("data-score") || "0");
            const scoreB = parseFloat(b.getAttribute("data-score") || "0");
            return scoreB - scoreA;
        }
    });

    items.forEach(item => container.appendChild(item));
}

// 8. View Mode Switcher (List View / Map View / Split View)
function setViewMode(mode) {
    const listCol = document.getElementById("itineraryColumn");
    const mapCol = document.getElementById("mapColumn");
    const btnSplit = document.getElementById("btnViewSplit");
    const btnList = document.getElementById("btnViewList");
    const btnMap = document.getElementById("btnViewMap");

    [btnSplit, btnList, btnMap].forEach(b => { if (b) b.classList.remove("active"); });

    if (mode === "list") {
        if (listCol) { listCol.className = "col-12"; listCol.style.display = "block"; }
        if (mapCol) { mapCol.style.display = "none"; }
        if (btnList) btnList.classList.add("active");
    } else if (mode === "map") {
        if (listCol) { listCol.style.display = "none"; }
        if (mapCol) { mapCol.className = "col-12"; mapCol.style.display = "block"; }
        if (btnMap) btnMap.classList.add("active");
        if (tripMap && typeof tripMap.invalidateSize === "function") {
            tripMap.invalidateSize();
        }
    } else {
        // Split view
        if (listCol) { listCol.className = "col-lg-7 mb-4"; listCol.style.display = "block"; }
        if (mapCol) { mapCol.className = "col-lg-5 mb-4"; mapCol.style.display = "block"; }
        if (btnSplit) btnSplit.classList.add("active");
        if (tripMap && typeof tripMap.invalidateSize === "function") {
            tripMap.invalidateSize();
        }
    }
}

// Global initialization on DOM ready
document.addEventListener("DOMContentLoaded", () => {
    // Attach autocomplete to existing start and destination fields
    const startInput = document.getElementById("startLocationInput");
    if (startInput) attachPlacesAutocomplete(startInput);

    document.querySelectorAll(".destination-input").forEach(attachPlacesAutocomplete);
});
