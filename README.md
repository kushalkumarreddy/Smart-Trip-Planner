# VoyageCraft — AI Smart Travel Planner & Route Optimizer

VoyageCraft is a modern, consumer-ready travel planning platform designed to make journey planning effortless, collaborative, and accurate. Whether typing a natural language prompt like *"Plan a 4-day family trip from Bengaluru to Coorg with waterfalls and relaxed driving"* or crafting a custom multi-destination road trip, VoyageCraft calculates real road driving distances, optimizes route sequences, schedules realistic day-by-day itineraries, and lets you plan collaboratively with travel companions.

---

## 1. Key Features

1. **Google Maps Style Place Autocomplete**
   - Live search suggestions as you type destinations and hotels with instant response times.
   - Rich dropdown cards featuring location pins, highlighted matching text, and location hints.
   - Full keyboard navigation (`↑`, `↓`, `Enter`, `Escape`) and outside-click dismissal.

2. **Accurate Road Highway Distances (OSRM Routing Engine)**
   - High-precision highway routing powered by Open Source Routing Machine (OSRM) and verified coordinate geocoding.
   - Point-to-point road distances match real highway journeys (e.g. Bengaluru to Mysore = exact 143.5 km).
   - Accurate segment-by-segment and cumulative journey metrics.

3. **AI-Powered Natural Language Trip Planner**
   - Describe trips in plain words: *"Plan a 3-day trip from Bengaluru to Wayanad with coffee plantations and scenic viewpoints"*.
   - Automatically detects duration, origin, destinations, travel style, travel pace, and interests.
   - Recommends handpicked destinations and hotel stays based on preferences.
   - Generates a day-by-day itinerary with meals, activities, and transit buffers.
   - **Single Day Regeneration**: Refresh any specific day's schedule without rebuilding the rest of your trip.

4. **Smart Route Optimization (TSP Solver)**
   - Evaluates all possible destination sequences using exact Traveling Salesperson Problem (TSP) algorithms.
   - Shows transparent distance and driving time savings before applying any changes.
   - Preserves user original route until explicitly approved.
   - Re-optimizes schedule and route segments seamlessly upon approval.

5. **Collaborative Trip Planning & Place Voting**
   - Share trips with friends and family using a secure invite link (`?invite=shareCode`).
   - Role-based permissions: `OWNER`, `EDITOR`, and `VIEWER`.
   - Crew members can submit attraction ideas, vote on suggestions, and accept top ideas directly into the itinerary.

6. **Clean, Travel-Lover Dashboard**
   - Wanderlust-themed dashboard with passport statistics, total kilometers journeyed, and destinations explored.
   - Journey countdowns and visual progress indicators for ongoing trips.

7. **Flexible Stop Reordering**
   - Easily swap destination stops up or down on the creation form or directly within the live itinerary timeline.

8. **Per-Destination Hotel Stays**
   - Hotels and accommodations are tied to each specific stop rather than a single hotel at the beginning of the trip.
   - Automatically schedules morning departure and evening check-in at the destination's hotel.

9. **Sightseeing Discovery & Ranking**
   - Curated sightseeing spots categorized into Historical, Nature, Waterfalls, Temples, Adventure, Food, and Photography.
   - Filter by category and sort by popularity, rating, or distance.

---

## 2. Technology Stack

- **Backend**: Java 21, Spring Boot 3.3.4 (Spring MVC, REST APIs, Spring Data JPA, Spring Security)
- **Geospatial & Routing**: OSRM (Open Source Routing Machine), Photon/OSM Geocoding, Google Maps Java Services client
- **Database**: MySQL 8.0+ with automatic schema migrations (`ddl-auto=update`)
- **Frontend**: Responsive HTML5, Vanilla JavaScript, Bootstrap 5.3, Bootstrap Icons
- **Interactive Maps**: Dual-mode Leaflet OpenStreetMap engine + Google Maps JavaScript API

---

## 3. Database Setup (MySQL)

Create the MySQL database:
```sql
CREATE DATABASE IF NOT EXISTS trip_planner CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Verify your credentials in `src/main/resources/application.properties`:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/trip_planner?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=root
```
*(Or set the `DB_PASSWORD` environment variable).*

---

## 4. Running the Application

In the root directory, start the Spring Boot application:

```powershell
# Using the bundled Maven wrapper:
.\mvnw.cmd spring-boot:run
```

Once started, open your web browser at:
- **Website URL**: `http://localhost:8080/`
- **Default Account**:
  - **Email**: `traveler@example.com`
  - **Password**: `travel123`
  *(Or create a new account via the Sign Up page).*

---

## 5. API Reference

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/trips` | Create a new trip with per-destination hotels & optimized route |
| `POST` | `/api/trips/ai-plan` | Generate a structured trip from a natural language prompt |
| `POST` | `/api/trips/preview-optimize` | Calculate potential distance & time savings for a route |
| `POST` | `/api/trips/{id}/optimize` | Apply recommended TSP route order |
| `POST` | `/api/trips/{id}/reorder` | Reorder destination stops by ID list |
| `POST` | `/api/trips/{id}/days/{day}/regenerate` | Regenerate a single day's itinerary |
| `GET`  | `/api/places/autocomplete` | Google Maps style place search suggestions |
| `POST` | `/api/trips/{id}/collaborators` | Invite a collaborator with role permissions |
| `DELETE` | `/api/trips/{id}/collaborators/{collabId}` | Remove a collaborator |
| `POST` | `/api/trips/{id}/suggestions` | Submit a place suggestion |
| `POST` | `/api/trips/{id}/suggestions/{sugId}/vote` | Upvote a suggested place |
| `POST` | `/api/trips/{id}/suggestions/{sugId}/accept` | Accept a suggestion into the itinerary |
| `PUT`  | `/api/places/{id}/visited` | Toggle attraction visited state |
| `DELETE` | `/api/trips/{id}` | Delete a trip |
| `GET`  | `/api/trips/{id}/round-trip` | Calculate outbound and highway return distance |
| `POST` | `/api/trips/preview-round-trip` | Preview round-trip calculation without saving |

---

## ⚡ Single-Click Launchers

You can start VoyageCraft in a single click using either:

1. **Desktop Shortcut:** Double-click the **`VoyageCraft`** icon on your Windows Desktop.
2. **Project Folder Batch Launcher:** Double-click [`run-voyagecraft.bat`](run-voyagecraft.bat) located in the project root folder.

Both launchers will automatically:
- Verify the local environment and dependencies.
- Launch the Spring Boot server (`mvnw.cmd spring-boot:run`).
- Open your default web browser directly to `http://localhost:8080`.

