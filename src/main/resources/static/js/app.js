/**
 * VoyageCraft - AI Smart Travel Planner Frontend Engine
 * Handles Google Maps-style place autocomplete, route rendering, map controls,
 * visited tracking, route optimization, and collaboration helpers.
 */

let tripMap = null;
let mapMarkers = [];
let mapPolyline = null;
let isGoogleMapsActive = false;

// =========================================================
// 0. Security & CSRF Token Interceptor
// Ensures all state-changing requests pass Spring Security CSRF verification
// =========================================================
function getCsrfToken() {
    const metaToken = document.querySelector('meta[name="_csrf"]');
    if (metaToken && metaToken.content) return metaToken.content;
    const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : '';
}

function getCsrfHeaderName() {
    const metaHeader = document.querySelector('meta[name="_csrf_header"]');
    if (metaHeader && metaHeader.content) return metaHeader.content;
    return 'X-XSRF-TOKEN';
}

(function initCsrfFetchInterceptor() {
    const originalFetch = window.fetch;
    window.fetch = function(url, options) {
        options = options || {};
        const method = (options.method || 'GET').toUpperCase();
        if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
            options.headers = options.headers || {};
            const token = getCsrfToken();
            const headerName = getCsrfHeaderName();
            if (token) {
                if (options.headers instanceof Headers) {
                    if (!options.headers.has(headerName) && !options.headers.has('X-XSRF-TOKEN') && !options.headers.has('X-CSRF-TOKEN')) {
                        options.headers.set(headerName, token);
                    }
                } else if (Array.isArray(options.headers)) {
                    options.headers.push([headerName, token]);
                } else {
                    if (!options.headers[headerName] && !options.headers['X-XSRF-TOKEN'] && !options.headers['X-CSRF-TOKEN']) {
                        options.headers[headerName] = token;
                    }
                }
            }
        }
        return originalFetch(url, options);
    };
})();

// =========================================================
// 1. Google Maps-Style Custom Place Autocomplete Engine
// =========================================================

/**
 * Attaches real-time place suggestions with Google Maps-like dropdown UI
 * to any input element. Works out of the box with zero external API key required.
 */
function attachPlaceAutocomplete(inputElement) {
    if (!inputElement || inputElement.dataset.autocompleteAttached === "true") {
        return;
    }
    inputElement.dataset.autocompleteAttached = "true";

    // Ensure input is contained in an .autocomplete-wrapper
    let wrapper = inputElement.closest(".autocomplete-wrapper");
    if (!wrapper) {
        wrapper = document.createElement("div");
        wrapper.className = "autocomplete-wrapper";
        inputElement.parentNode.insertBefore(wrapper, inputElement);
        wrapper.appendChild(inputElement);
    }

    // Create dropdown container
    const dropdown = document.createElement("div");
    dropdown.className = "autocomplete-dropdown";
    wrapper.appendChild(dropdown);

    let debounceTimer = null;
    let activeIndex = -1;
    let currentAbortController = null;
    let isSelecting = false;
    let lastQuery = "";

    // Input typing listener
    inputElement.addEventListener("input", function (e) {
        // If the input event was triggered programmatically during selection, ignore it
        if (isSelecting) {
            return;
        }

        clearTimeout(debounceTimer);
        if (currentAbortController) {
            currentAbortController.abort();
            currentAbortController = null;
        }

        const query = this.value.trim();
        lastQuery = query;

        if (query.length < 2) {
            // User cleared or shortened query; remove stale coordinates
            delete this.dataset.placeId;
            delete this.dataset.lat;
            delete this.dataset.lng;
            closeDropdown();
            return;
        }

        // Show subtle loading state
        dropdown.innerHTML = `
            <div class="p-2 text-muted small text-center autocomplete-loading">
                <span class="spinner-border spinner-border-sm me-1 text-primary" role="status"></span>
                <span>Searching places...</span>
            </div>
        `;
        dropdown.classList.add("show");

        debounceTimer = setTimeout(async () => {
            const controller = new AbortController();
            currentAbortController = controller;

            try {
                const response = await fetch(`/api/places/autocomplete?query=${encodeURIComponent(query)}&limit=6`, {
                    signal: controller.signal
                });

                if (controller.signal.aborted || inputElement.value.trim() !== query) {
                    return; // Stale or cancelled response
                }

                if (!response.ok) {
                    dropdown.innerHTML = `
                        <div class="p-2 text-danger small text-center">
                            <i class="bi bi-exclamation-circle me-1"></i> Unable to search places
                        </div>
                    `;
                    return;
                }

                const suggestions = await response.json();
                if (controller.signal.aborted || inputElement.value.trim() !== query) {
                    return; // Stale query check
                }
                renderDropdown(suggestions, query);
            } catch (err) {
                if (err.name === "AbortError") {
                    return; // Safely ignore user-cancelled requests
                }
                console.error("Autocomplete fetch error:", err);
                if (inputElement.value.trim() === query) {
                    dropdown.innerHTML = `
                        <div class="p-2 text-muted small text-center">
                            <i class="bi bi-wifi-off me-1"></i> Search service unavailable
                        </div>
                    `;
                }
            } finally {
                if (currentAbortController === controller) {
                    currentAbortController = null;
                }
            }
        }, 180);
    });

    // Keyboard navigation (ArrowDown, ArrowUp, Enter, Escape)
    inputElement.addEventListener("keydown", function (e) {
        const items = dropdown.querySelectorAll(".autocomplete-item");
        if (!dropdown.classList.contains("show") || items.length === 0) return;

        if (e.key === "ArrowDown") {
            e.preventDefault();
            activeIndex = (activeIndex + 1) % items.length;
            updateActiveItem(items);
        } else if (e.key === "ArrowUp") {
            e.preventDefault();
            activeIndex = (activeIndex - 1 + items.length) % items.length;
            updateActiveItem(items);
        } else if (e.key === "Enter") {
            if (activeIndex >= 0 && activeIndex < items.length) {
                e.preventDefault();
                selectItem(items[activeIndex]);
            }
        } else if (e.key === "Escape") {
            e.preventDefault();
            closeDropdown();
        }
    });

    // Focus listener
    inputElement.addEventListener("focus", function () {
        if (dropdown.children.length > 0 && this.value.trim().length >= 2 && !dropdown.classList.contains("show")) {
            // Only re-show if dropdown already has rendered items matching current value
            const currentVal = this.value.trim();
            if (currentVal === lastQuery) {
                dropdown.classList.add("show");
            }
        }
    });

    function renderDropdown(items, query) {
        dropdown.innerHTML = "";
        activeIndex = -1;

        if (!items || items.length === 0) {
            dropdown.innerHTML = `
                <div class="p-2 text-muted small text-center">
                    <i class="bi bi-geo me-1"></i> No matching locations found
                </div>
            `;
            dropdown.classList.add("show");
            return;
        }

        items.forEach(item => {
            const row = document.createElement("div");
            row.className = "autocomplete-item";
            row.dataset.placeName = item.name || "";
            if (item.placeId) row.dataset.placeId = item.placeId;
            if (item.lat !== undefined && item.lat !== null) row.dataset.lat = item.lat;
            if (item.lng !== undefined && item.lng !== null) row.dataset.lng = item.lng;

            const highlightedName = highlightMatch(item.name, query);
            const subtext = escapeHtml(item.description || item.category || "Destination");

            row.innerHTML = `
                <div class="autocomplete-pin">
                    <i class="bi bi-geo-alt-fill"></i>
                </div>
                <div class="autocomplete-text">
                    <div class="autocomplete-main">${highlightedName}</div>
                    <div class="autocomplete-sub">${subtext}</div>
                </div>
            `;

            row.addEventListener("mousedown", (e) => {
                e.preventDefault(); // Prevents input blur before click
                e.stopPropagation();
                selectItem(row);
            });

            dropdown.appendChild(row);
        });

        dropdown.classList.add("show");
    }

    function selectItem(itemElement) {
        clearTimeout(debounceTimer);
        if (currentAbortController) {
            currentAbortController.abort();
            currentAbortController = null;
        }

        const placeName = itemElement.dataset.placeName;
        const placeId = itemElement.dataset.placeId;
        const lat = itemElement.dataset.lat;
        const lng = itemElement.dataset.lng;

        if (placeName) {
            isSelecting = true;
            inputElement.value = placeName;
            lastQuery = placeName.trim();

            if (placeId) inputElement.dataset.placeId = placeId;
            if (lat) inputElement.dataset.lat = lat;
            if (lng) inputElement.dataset.lng = lng;

            // Immediately close and empty dropdown to eliminate stale UI & race conditions
            closeDropdown();

            // Dispatch events safely so external listeners update without re-triggering search
            inputElement.dispatchEvent(new Event("input", { bubbles: true }));
            inputElement.dispatchEvent(new Event("change", { bubbles: true }));
            isSelecting = false;
        } else {
            closeDropdown();
        }
    }

    function updateActiveItem(items) {
        items.forEach((item, idx) => {
            if (idx === activeIndex) {
                item.classList.add("active");
                item.scrollIntoView({ block: "nearest" });
            } else {
                item.classList.remove("active");
            }
        });
    }

    function closeDropdown() {
        if (currentAbortController) {
            currentAbortController.abort();
            currentAbortController = null;
        }
        dropdown.classList.remove("show");
        dropdown.innerHTML = "";
        activeIndex = -1;
    }
}

// Global click outside to dismiss all autocomplete dropdowns and clear stale suggestions
document.addEventListener("click", (e) => {
    if (!e.target.closest(".autocomplete-wrapper")) {
        document.querySelectorAll(".autocomplete-dropdown").forEach(d => {
            d.classList.remove("show");
            d.innerHTML = "";
        });
    }
});

// Alias for backwards compatibility
window.attachPlaceAutocomplete = attachPlaceAutocomplete;
window.attachPlacesAutocomplete = attachPlaceAutocomplete;

function highlightMatch(text, query) {
    if (!text || !query) return escapeHtml(text || "");
    const escapedQuery = query.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const regex = new RegExp(`(${escapedQuery})`, "gi");
    return escapeHtml(text).replace(regex, "<strong>$1</strong>");
}

function escapeHtml(str) {
    if (!str) return "";
    return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

// =========================================================
// 2. Map Initialization (Dual-Engine: Google Maps + Leaflet)
// =========================================================

function initTripMap(mapElementId, locations = []) {
    const mapElement = document.getElementById(mapElementId);
    if (!mapElement) return;

    // Default center (Bengaluru region)
    let centerLat = 12.9716;
    let centerLng = 77.5946;

    if (locations.length > 0 && locations[0].lat && locations[0].lng) {
        centerLat = locations[0].lat;
        centerLng = locations[0].lng;
    }

    if (window.google && window.google.maps && window.googleMapsApiKeyAvailable) {
        initGoogleMap(mapElement, locations, centerLat, centerLng);
    } else {
        initLeafletMap(mapElementId, locations, centerLat, centerLng);
    }
}

function initGoogleMap(mapElement, locations, centerLat, centerLng) {
    isGoogleMapsActive = true;
    tripMap = new google.maps.Map(mapElement, {
        center: { lat: centerLat, lng: centerLng },
        zoom: 8,
        mapTypeControl: true,
        streetViewControl: false
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
                <div style="padding: 6px; font-family: 'Plus Jakarta Sans', sans-serif;">
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
            strokeOpacity: 0.85,
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

// =========================================================
// 3. Visited Progress Tracker
// =========================================================

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

        if (itemCard) {
            if (isChecked) {
                itemCard.classList.add("is-visited");
            } else {
                itemCard.classList.remove("is-visited");
            }
        }

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
        checkboxElement.checked = !isChecked;
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

// =========================================================
// 4. Smart Route Optimization (Apply TSP Sequence)
// =========================================================

async function applyOptimizedRoute(tripId) {
    if (!confirm("Reorganize your journey using the recommended shortest highway route? This will update your travel schedule and eliminate backtracking.")) {
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

// =========================================================
// 5. Delete Trip Handler
// =========================================================

async function deleteTripItem(tripId) {
    if (!confirm("Are you sure you want to delete this trip itinerary? This action cannot be undone.")) {
        return;
    }

    try {
        const response = await fetch(`/api/trips/${tripId}`, {
            method: "DELETE"
        });

        if (!response.ok) throw new Error("Failed to delete trip");
        window.location.reload();
    } catch (err) {
        console.error("Error deleting trip:", err);
        alert("Could not delete trip. Please try again.");
    }
}

// =========================================================
// 6. Sightseeing & Itinerary Add Place
// =========================================================

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

// =========================================================
// 7. Sightseeing Categories Filter & Sorter
// =========================================================

function filterPlacesByCategory(category, clickedElement) {
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
            const scoreA = parseFloat(a.getAttribute("data-score") || "0");
            const scoreB = parseFloat(b.getAttribute("data-score") || "0");
            return scoreB - scoreA;
        }
    });

    items.forEach(item => container.appendChild(item));
}

// =========================================================
// 8. View Mode Switcher
// =========================================================

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
        if (listCol) { listCol.className = "col-lg-7 mb-4"; listCol.style.display = "block"; }
        if (mapCol) { mapCol.className = "col-lg-5 mb-4"; mapCol.style.display = "block"; }
        if (btnSplit) btnSplit.classList.add("active");
        if (tripMap && typeof tripMap.invalidateSize === "function") {
            tripMap.invalidateSize();
        }
    }
}

// =========================================================
// 9. Global DOM Ready Auto-Init & Image Fallbacks
// =========================================================

/**
 * Robust image fallback handler that prevents broken image icons
 * and renders high-quality theme-matched vector illustrations.
 */
function handleImageFallback(img, category) {
    if (!img || img.dataset.fallbackApplied === "true") return;
    img.dataset.fallbackApplied = "true";

    const cat = (category || img.getAttribute("data-category") || "").toLowerCase();
    let fallbackSrc = "/images/placeholder-travel.svg";

    if (cat.includes("heritage") || cat.includes("culture") || cat.includes("palace") || cat.includes("fort") || cat.includes("historical")) {
        fallbackSrc = "/images/fallback-heritage.svg";
    } else if (cat.includes("beach") || cat.includes("coast") || cat.includes("island") || cat.includes("sea")) {
        fallbackSrc = "/images/fallback-beach.svg";
    } else if (cat.includes("waterfall") || cat.includes("stream") || cat.includes("lake") || cat.includes("river")) {
        fallbackSrc = "/images/fallback-waterfall.svg";
    } else if (cat.includes("temple") || cat.includes("spiritual") || cat.includes("pilgrim") || cat.includes("shrine") || cat.includes("religious")) {
        fallbackSrc = "/images/fallback-temple.svg";
    } else if (cat.includes("nature") || cat.includes("wildlife") || cat.includes("hill") || cat.includes("mountain") || cat.includes("forest") || cat.includes("valley")) {
        fallbackSrc = "/images/fallback-nature.svg";
    }

    img.src = fallbackSrc;
    img.classList.add("img-fallback-loaded");
}

window.handleImageFallback = handleImageFallback;

document.addEventListener("DOMContentLoaded", () => {
    // Automatically attach autocomplete to all relevant inputs
    const startInput = document.getElementById("startLocationInput");
    if (startInput) attachPlaceAutocomplete(startInput);

    document.querySelectorAll(".destination-input").forEach(attachPlaceAutocomplete);
    document.querySelectorAll(".destination-hotel-input").forEach(attachPlaceAutocomplete);

    // Scan for existing images and ensure fallback safety
    document.querySelectorAll("img").forEach(img => {
        img.addEventListener("error", function () {
            handleImageFallback(this);
        });
    });

    // Initialize Theme
    if (window.themeManager) {
        window.themeManager.init();
    }
});

// =========================================================
// 10. Theme Management System (Requirement 5)
// Light, Dark, and System preference with persistence & zero-flash
// =========================================================

const themeManager = {
    STORAGE_KEY: "voyagecraft_theme",

    getStoredTheme() {
        return localStorage.getItem(this.STORAGE_KEY) || "system";
    },

    getSystemTheme() {
        return window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
    },

    getEffectiveTheme(theme) {
        return theme === "system" ? this.getSystemTheme() : theme;
    },

    setTheme(theme) {
        if (!["light", "dark", "system"].includes(theme)) theme = "system";
        localStorage.setItem(this.STORAGE_KEY, theme);
        const effective = this.getEffectiveTheme(theme);
        document.documentElement.setAttribute("data-theme", effective);
        this.updateThemeUI(theme);

        // Persist theme to user profile if logged in
        fetch("/api/config/theme", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ theme: theme })
        }).catch(() => {
            // Silently ignore if guest
        });
    },

    updateThemeUI(theme) {
        const iconEl = document.querySelector(".theme-icon-active");
        const nameEl = document.querySelector(".theme-name-active");
        if (!iconEl && !nameEl) return;

        if (theme === "dark") {
            if (iconEl) iconEl.className = "bi bi-moon-stars-fill theme-icon-active text-info";
            if (nameEl) nameEl.textContent = "Dark";
        } else if (theme === "light") {
            if (iconEl) iconEl.className = "bi bi-sun-fill theme-icon-active text-warning";
            if (nameEl) nameEl.textContent = "Light";
        } else {
            if (iconEl) iconEl.className = "bi bi-circle-half theme-icon-active";
            if (nameEl) nameEl.textContent = "Auto";
        }
    },

    init() {
        const theme = this.getStoredTheme();
        const effective = this.getEffectiveTheme(theme);
        document.documentElement.setAttribute("data-theme", effective);
        this.updateThemeUI(theme);

        if (window.matchMedia) {
            window.matchMedia("(prefers-color-scheme: dark)").addEventListener("change", (e) => {
                if (this.getStoredTheme() === "system") {
                    document.documentElement.setAttribute("data-theme", e.matches ? "dark" : "light");
                }
            });
        }
    }
};

window.themeManager = themeManager;

// =========================================================
// 12. Stay Duration & Day Movement Helpers (Requirement 1 & 2)
// =========================================================

async function updateDestinationDuration(tripId, destinationId, days, nights) {
    if (!days || days < 1) days = 1;
    if (nights === undefined || nights === null || nights < 0) nights = Math.max(0, days - 1);

    try {
        const res = await fetch(`/api/trips/${tripId}/destinations/${destinationId}/duration`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                stayDurationDays: parseInt(days),
                stayDurationNights: parseInt(nights)
            })
        });

        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.message || "Failed to update stay duration");
        }

        window.location.reload();
    } catch (err) {
        console.error("Duration update error:", err);
        alert(err.message || "Could not update stay duration.");
    }
}

async function moveActivityItem(tripId, itemId, targetDay) {
    if (!targetDay || targetDay < 1) return;

    try {
        const res = await fetch(`/api/trips/${tripId}/move-activity`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                itemId: parseInt(itemId),
                targetDayNumber: parseInt(targetDay)
            })
        });

        if (!res.ok) {
            const err = await res.json();
            throw new Error(err.message || "Failed to move activity");
        }

        window.location.reload();
    } catch (err) {
        console.error("Move activity error:", err);
        alert(err.message || "Could not move activity to specified day.");
    }
}

window.updateDestinationDuration = updateDestinationDuration;
window.moveActivityItem = moveActivityItem;


