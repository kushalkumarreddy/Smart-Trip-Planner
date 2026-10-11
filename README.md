# TripPlanner - Full-Stack Trip Planner & Route Optimization System

A production-ready full-stack web application designed for real-world travel planning. The user inputs their desired destinations, and the system automatically calculates segment distances, routes, nearby sightseeing recommendations, travel times based on transportation modes, and generates a realistic, practical day-by-day itinerary with visited progress tracking and interactive maps.

---

## 1. System Architecture & Tech Stack

### Backend
- **Java 21**
- **Spring Boot 3.3.4** (Spring MVC, REST APIs, Spring Data JPA, Spring Security)
- **Maven 3.9+** (with bundled `mvnw.cmd` wrapper script)
- **Google Maps Services Java Client** (`com.google.maps:google-maps-services:2.2.0`)
- **BCrypt Password Hashing** for user security

### Frontend
- **HTML5 & Vanilla JavaScript** (modular, reactive DOM updates)
- **Bootstrap 5.3 & Bootstrap Icons 1.11**
- **Custom CSS Design System** (typography, responsive grids, interactive timeline connectors, metric KPI cards)
- **Interactive Dual Map Engine** (Google Maps JavaScript API + Leaflet OpenStreetMap fallback)

### Database
- **MySQL 8.0+** with relational schema, foreign key constraints, and auto-generated indexes

---

## 2. Key Features

1. **Dynamic Multi-Destination Input**: Add or remove destinations on the fly with sequence numbering and Places Autocomplete.
2. **Two Types of Distance Display**:
   - *Destination-to-Destination Distance*: Point-to-point road distance & transit time (e.g. `Bengaluru → Mysore = 164 km, 3 hr 14 min`).
   - *Total Trip Distance & Duration*: Comprehensive route distance and total travel time (e.g. `470.8 km, 9 hr 35 min`).
3. **Transportation Mode Selection**:
   - **Car**: High-speed highway routes.
   - **Bike**: Two-wheeler road profiles.
   - **Public Transport**: Transit routing with connection buffers.
   - **Walking**: Pedestrian paths and realistic walking pace.
4. **Smart Route Order (TSP Optimization)**:
   - Evaluates destination order using Traveling Salesperson Problem (TSP) nearest-neighbor and 2-opt swap algorithms.
   - Calculates potential distance (km) and time savings if a more efficient route exists.
   - Provides a `[ Use Recommended Route ]` button to reorder without altering user intent arbitrarily.
5. **Ranked Sightseeing Recommendations**:
   - Curated & dynamic sightseeing spots for destinations.
   - **Multi-Factor Ranking Formula**:
     $$\text{Score} = \left(\frac{\text{Rating}}{5.0} \times 35\right) + \left(\frac{\log_{10}(\text{Reviews})}{\log_{10}(50000)} \times 25\right) + \left(\frac{1}{1 + \text{Dist}/8} \times 25\right) + \text{InterestBonus}$$
   - Filter by categories: *Historical, Nature, Waterfalls, Temples, Museums, Adventure, Parks, Photography*.
   - Sort by: *Popularity, Top Rating, Nearest Distance*.
   - One-click `[ + Add to Trip ]` which recalculates routes and schedule dynamically.
6. **Practical Schedule Generator**:
   - Respects user's daily start (e.g. `08:00 AM`) and end times.
   - Includes 15–30 minute transit/parking buffers between activities.
   - Inserts realistic meal breaks (Lunch `12:30 PM - 01:30 PM`, Dinner).
   - Handles hotel/accommodation as daily morning departure and evening check-in points.
   - **Travel Fatigue Warnings**: Flags days containing $> 7$ hours of transit as `⚠️ Long Day` with actionable suggestions.
7. **Visited Progress Checklist**:
   - Real-time `☐ Mark Visited` / `☑ Visited` toggle.
   - Dynamic progress bar showing `X / Y Places Visited` and `% Completed`.
8. **Interactive Map & Google Maps Navigation**:
   - Color-coded markers (Blue for unvisited, Emerald Green for visited).
   - Route polyline connecting all stops.
   - 1-Click `[ Open in Google Maps ]` deep link generating official multi-waypoint navigation URLs.

---

## 3. Project Directory Structure

```text
trip-planner/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com.example.tripplanner/
│   │   │       ├── config/               # SecurityConfig, Spring beans
│   │   │       ├── controller/           # WebController, TripApiController, etc.
│   │   │       ├── dto/                  # TripRequestDto, RouteSummaryDto, etc.
│   │   │       ├── entity/               # User, Trip, Destination, Place, ItineraryItem
│   │   │       ├── exception/            # GlobalExceptionHandler, Custom Exceptions
│   │   │       ├── repository/           # Spring Data JPA Repositories
│   │   │       ├── service/              # TripService, RouteService, ItineraryService, etc.
│   │   │       ├── util/                 # GeoUtils, TimeUtils
│   │   │       └── TripPlannerApplication.java
│   │   │
│   │   └── resources/
│   │       ├── static/
│   │       │   ├── css/style.css         # Modern travel design system
│   │       │   └── js/app.js             # Client routing, maps & visited logic
│   │       ├── templates/
│   │       │   ├── index.html            # Landing home page
│   │       │   ├── login.html            # Sign in page
│   │       │   ├── register.html         # User registration
│   │       │   ├── dashboard.html        # Saved trips overview & stats
│   │       │   ├── create-trip.html      # Trip creation form
│   │       │   └── trip-details.html     # Results, timeline, map & recommendations
│   │       ├── application.properties    # DB and Google Maps configuration
│   │       └── schema.sql                # Complete MySQL DDL creation script
│
├── mvnw.cmd                              # Local Maven runner script
├── pom.xml                               # Dependencies and build settings
└── README.md                             # Documentation
```

---

## 4. Database Setup (MySQL)

### Step 1: Create Database
Connect to your local MySQL 8.0 server:
```bash
mysql -u root -p
```
Run the database creation script:
```sql
CREATE DATABASE IF NOT EXISTS trip_planner CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

*(Note: Spring Boot Hibernate is configured with `spring.jpa.hibernate.ddl-auto=update`, which automatically provisions all tables, constraints, and relationships upon startup. You can also manually inspect or run `src/main/resources/schema.sql` if desired).*

### Step 2: Configure Credentials in `application.properties`
Open `src/main/resources/application.properties` and verify your credentials:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/trip_planner?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=root
```
*(Or set the environment variable `DB_PASSWORD`)*.

---

## 5. Google Maps Platform API Setup Guide

The application is built to run **100% out of the box** using its built-in realistic geospatial calculation engine. To activate live Google Cloud Platform Maps services:

### Step 1: Create Google Cloud Project
1. Go to the [Google Cloud Console](https://console.cloud.google.com/).
2. Click **Create Project** (e.g. `Trip-Planner-App`).
3. Make sure billing is enabled for your Google Cloud account.

### Step 2: Enable Required APIs
Navigate to **APIs & Services > Library** and enable:
1. **Maps JavaScript API** (Interactive browser map)
2. **Places API** (Autocomplete & location lookups)
3. **Directions API** (Real-time road routes & transit polylines)
4. **Distance Matrix API** (Point-to-point distance matrices)
5. **Geocoding API** (Address coordinate conversion)

### Step 3: Create & Restrict API Keys
To protect your key from unauthorized usage:
1. Go to **APIs & Services > Credentials** and click **Create Credentials > API Key**.
2. **For Frontend Maps JS Key**: Restrict by **Application restriction** -> **HTTP referrers (web sites)** -> add `localhost:8080/*`.
3. **For Backend Server Key**: Restrict by **API restriction** -> Select only *Directions API, Distance Matrix API, Geocoding API, Places API*.

### Step 4: Add Key to Application
Add the key in `src/main/resources/application.properties`:
```properties
google.maps.api-key=YOUR_GOOGLE_CLOUD_API_KEY
google.maps.enabled=true
```
*(Or set the system environment variable `GOOGLE_MAPS_API_KEY=YOUR_KEY`)*.

---

## 6. Running the Application

In the root of the project, run:

```powershell
# Using the bundled Maven script:
.\mvnw.cmd spring-boot:run

# Or with system Maven:
mvn spring-boot:run
```

Once started, open your browser:
- **Application URL**: `http://localhost:8080/`
- **Pre-configured Evaluation Account**:
  - **Email**: `traveler@example.com`
  - **Password**: `travel123`
  *(You can also register a new account or click "Fill Demo Credentials" on the login page).*

---

## 7. REST API Endpoints Reference

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/trips` | Creates a new trip, computes route matrix & generates practical schedule |
| `GET` | `/api/trips` | Retrieves all trips saved by the current user |
| `GET` | `/api/trips/{id}` | Retrieves full trip details, metrics, day schedules & recommendations |
| `DELETE` | `/api/trips/{id}` | Deletes a trip |
| `POST` | `/api/trips/{id}/optimize` | Applies TSP 2-opt recommended route sequence to save travel km |
| `POST` | `/api/trips/{id}/destinations/{destId}/places` | Adds a sightseeing place to itinerary & recalculates total distance |
| `PUT` | `/api/places/{id}/visited` | Toggles visited status (`true`/`false`) & returns updated progress % |
| `PUT` | `/api/destinations/{id}/visited` | Toggles destination visited status |
| `GET` | `/api/destinations/{id}/nearby-places` | Returns ranked sightseeing spots with category & sort filters |
