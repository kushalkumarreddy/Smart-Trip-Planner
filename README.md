# VoyageCraft — AI Smart Travel Planner & Route Optimizer

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Build Status](https://img.shields.io/badge/Tests-77%20Passing-success.svg)](TEST_REPORT.md)
[![Security Hardened](https://img.shields.io/badge/Security-OWASP%20Hardened-blue.svg)](SECURITY_REVIEW.md)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

VoyageCraft is a modern, consumer-ready travel planning platform designed to make journey planning effortless, collaborative, and accurate. Whether typing a natural language prompt like *"Plan a 4-day family trip from Bengaluru to Coorg with waterfalls and relaxed driving"* or crafting a custom multi-destination road trip, VoyageCraft calculates real road driving distances, optimizes route sequences, schedules realistic day-by-day itineraries, and lets you plan collaboratively with travel companions.

---

## 📑 Documentation Quick Links

- [🚀 Production Deployment Guide](DEPLOYMENT_GUIDE.md) — Comprehensive setup, cloud hosting, Docker, and MySQL 8.0 guide.
- [🛡️ Application Security Review](SECURITY_REVIEW.md) — Threat modeling, authorization matrix, and CSRF hardening.
- [🧪 Automated Test Report](TEST_REPORT.md) — Full breakdown of the 77 unit and integration tests.
- [📋 Project Code Audit](PROJECT_AUDIT.md) — Remediation matrix and status of all audit findings.

---

## 1. Key Features

### 🚀 Guest Trip Planning (No Login Required)
- **Instant Journey Creation**: Plan trips, select start times (default 8:00 AM), reorder destinations, and discover smart stops immediately without creating an account.
- **Session & Local Persistence**: Guest itineraries persist safely during browser sessions and page reloads.
- **One-Click Account Save**: Convert active guest itineraries directly into permanent saved account trips upon signing in.

### 🌟 Categorized Trip Dashboard
- **Upcoming, Ongoing & Completed Categories**: Trips automatically categorize according to their travel calendar dates with accessible tabs and live counts.
- **Real-Time Client Search**: Instant debounced search by trip name or destination.
- **Multi-Field Sorting**: Sort dynamically by Departure Date, Recently Created, Oldest First, Alphabetical Name, or Duration.
- **Pagination & Skeletons**: Clean 6-card pagination with responsive loading skeletons and custom empty states for all categories.
- **Safe Deletion Modal**: Accessible confirmation modal preventing accidental trip loss, reinforced with CSRF token verification.

### 🗺️ Interactive Itinerary & Map Synchronization
- **Bidirectional Map-Timeline Sync**:
  - Selecting an itinerary stop pans the map directly to that stop and triggers the popup.
  - Clicking a map marker highlights the destination card in the sequence bar and scrolls smoothly to the matching timeline activity.
- **HTML5 Drag-and-Drop Reordering**: Grab handles (`bi-grip-vertical`) allow direct reordering of stops with instant route recalculation.
- **Keyboard Accessible Reordering**: Accessible Move Earlier (`←`) and Move Later (`→`) buttons equipped with `aria-label` and `aria-live` screen reader announcements.
- **Safe Undo & Restoration**: Instant "Undo Reorder" button plus deep trip snapshot history and version diffs via `TripVersionService`.
- **Custom Floating Map Controls**: Fit Route to bounds, Zoom In, Zoom Out, and verified highway polyline overlay badge.

### 🛣️ Accurate Highway Routing & AI Planning
- **OSRM Road Route Engine**: Calculates true point-to-point highway distances and drive times (e.g. Bengaluru to Mysore = exact 143.5 km).
- **TSP Route Optimization Solver**: Evaluates all destination permutations and provides preview savings before applying.
- **Natural Language AI Itinerary Builder**: Parses plain-text trip requests into structured multi-day schedules with meals, buffer times, and activities.
- **Single-Day Schedule Regeneration**: Refresh any individual day's itinerary without changing the rest of your trip.
- **Smart Corridor Stops**: Discover scenic viewpoints, fuel stations, and cafes along highway corridors.

---

## 2. Technology Stack

- **Backend**: Java 21, Spring Boot 3.3.4 (Spring MVC, Spring Data JPA, Spring Security, Validation)
- **Database**: MySQL 8.0+ with HikariCP connection pooling and Flyway migrations
- **Routing & Geocoding**: OSRM (Open Source Routing Machine), Overpass OSM API, Google Maps Platform
- **Frontend**: Thymeleaf, HTML5, Vanilla JavaScript, CSS3 Design System, Bootstrap 5.3, Bootstrap Icons
- **Interactive Maps**: Leaflet 1.9 + OpenStreetMap tiles with Google Maps navigation fallback
- **Containerization**: Multi-stage Dockerfile (Java 21 eclipse-temurin) & Docker Compose

---

## 3. Quickstart & Local Setup

### Prerequisites
- Java 21 JDK
- MySQL 8.0+ running locally on port `3306`

### 1. Database Setup
```sql
CREATE DATABASE IF NOT EXISTS trip_planner CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. Configure Environment
Copy `.env.example` to `.env` and fill in credentials:
```bash
cp .env.example .env
```

### 3. Run Application
Using the bundled Maven wrapper:
- **Windows**:
  ```cmd
  .\mvnw.cmd spring-boot:run
  ```
- **Linux / macOS**:
  ```bash
  ./mvnw spring-boot:run
  ```

Open your browser at `http://localhost:8080`.

### 4. Pre-seeded Evaluation Account
- **Email**: `traveler@example.com`
- **Password**: `travel123`

---

## 4. Docker & Multi-Container Execution

### Run complete stack via Docker Compose (App + MySQL 8.0):
```bash
docker compose up -d --build
```
- App: `http://localhost:8080`
- MySQL: `localhost:3306` (Data persisted in `mysql_data` volume)

---

## 5. Running Automated Tests

Run the complete 70-test suite:
```cmd
.\mvnw.cmd clean test
```
All tests execute offline and pass in under 12 seconds with zero failures.

---

## 6. REST API Reference

| Method | Endpoint | Description | Security |
|---|---|---|---|
| `GET` | `/api/trips` | List all trips for current authenticated user | Authenticated |
| `POST` | `/api/trips` | Create a new trip with destinations & route | Authenticated + CSRF |
| `GET` | `/api/trips/{id}` | Get full trip details with day itineraries | Authenticated (Owner / Crew) |
| `POST` | `/api/trips/{id}/reorder` | Reorder stops by ID list or apply TSP | Authenticated (EDITOR) + CSRF |
| `POST` | `/api/trips/{id}/versions/{v}/restore` | Restore past itinerary snapshot | Authenticated (EDITOR) + CSRF |
| `PUT` | `/api/places/{id}/visited` | Toggle attraction visited state | Authenticated (EDITOR) + CSRF |
| `DELETE` | `/api/trips/{id}` | Delete a trip | Authenticated (Owner) + CSRF |
| `GET` | `/api/places/autocomplete` | Live Google Maps style place search | Public |
