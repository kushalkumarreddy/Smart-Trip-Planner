-- =========================================================
-- VoyageCraft - AI Smart Travel Planner Database Schema
-- Migration V1: Initial Database Schema (MySQL 8.0+)
-- =========================================================

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(120) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) DEFAULT 'ROLE_USER',
    theme_preference VARCHAR(30) DEFAULT 'system',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Trips Table
CREATE TABLE IF NOT EXISTS trips (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    trip_name VARCHAR(150) NOT NULL,
    start_location VARCHAR(200) NOT NULL,
    start_lat DOUBLE,
    start_lng DOUBLE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    transport_mode VARCHAR(50) DEFAULT 'CAR',
    travel_style VARCHAR(50) DEFAULT 'BALANCED',
    travelers_count INT DEFAULT 1,
    daily_start_time VARCHAR(20) DEFAULT '08:00',
    daily_end_time VARCHAR(20) DEFAULT '20:00',
    hotel_location VARCHAR(255),
    hotel_lat DOUBLE,
    hotel_lng DOUBLE,
    budget_range VARCHAR(50),
    preferred_interests TEXT,
    total_distance DOUBLE DEFAULT 0.0,
    total_travel_time_minutes INT DEFAULT 0,
    share_code VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_trip_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Destinations Table (Waypoints in the Trip)
CREATE TABLE IF NOT EXISTS destinations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    latitude DOUBLE,
    longitude DOUBLE,
    sequence_order INT NOT NULL,
    stay_duration_days INT DEFAULT 1,
    stay_duration_nights INT DEFAULT 0,
    arrival_date DATE,
    departure_date DATE,
    arrival_time VARCHAR(20),
    departure_time VARCHAR(20),
    hotel_name VARCHAR(255),
    hotel_address VARCHAR(500),
    hotel_lat DOUBLE,
    hotel_lng DOUBLE,
    check_in_date DATE,
    check_out_date DATE,
    check_in_time VARCHAR(20),
    check_out_time VARCHAR(20),
    rooms_count INT DEFAULT 1,
    guests_count INT DEFAULT 1,
    booking_reference VARCHAR(100),
    hotel_notes TEXT,
    distance_from_previous DOUBLE DEFAULT 0.0,
    travel_time_from_previous INT DEFAULT 0,
    visited BOOLEAN DEFAULT FALSE,
    visited_at TIMESTAMP NULL,
    notes TEXT,
    CONSTRAINT fk_destination_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Places Table (Sightseeing Spots & POIs)
CREATE TABLE IF NOT EXISTS places (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    destination_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(100) DEFAULT 'Historical',
    rating DOUBLE DEFAULT 4.5,
    review_count INT DEFAULT 100,
    latitude DOUBLE,
    longitude DOUBLE,
    distance_km DOUBLE DEFAULT 0.0,
    estimated_visit_duration_minutes INT DEFAULT 90,
    estimated_travel_time_minutes INT DEFAULT 15,
    description TEXT,
    opening_hours VARCHAR(100) DEFAULT '09:00 AM - 06:00 PM',
    photo_url VARCHAR(500),
    google_place_id VARCHAR(100),
    priority VARCHAR(50) DEFAULT 'Medium',
    visited BOOLEAN DEFAULT FALSE,
    visited_at TIMESTAMP NULL,
    added_to_itinerary BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_place_destination FOREIGN KEY (destination_id) REFERENCES destinations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Itineraries Table (Day-by-Day Schedule)
CREATE TABLE IF NOT EXISTS itineraries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    day_number INT NOT NULL,
    schedule_date DATE,
    start_time VARCHAR(20) NOT NULL,
    end_time VARCHAR(20) NOT NULL,
    activity VARCHAR(255) NOT NULL,
    location VARCHAR(255) NOT NULL,
    activity_type VARCHAR(50) DEFAULT 'SIGHTSEEING',
    place_id BIGINT NULL,
    travel_distance_km DOUBLE DEFAULT 0.0,
    travel_time_minutes INT DEFAULT 0,
    duration_minutes INT DEFAULT 60,
    rating DOUBLE NULL,
    visited BOOLEAN DEFAULT FALSE,
    locked BOOLEAN DEFAULT FALSE,
    notes TEXT,
    latitude DOUBLE NULL,
    longitude DOUBLE NULL,
    category VARCHAR(100) NULL,
    CONSTRAINT fk_itinerary_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    CONSTRAINT fk_itinerary_place FOREIGN KEY (place_id) REFERENCES places(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Trip Collaborators Table
CREATE TABLE IF NOT EXISTS trip_collaborators (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    user_name VARCHAR(100) NOT NULL,
    user_email VARCHAR(120),
    role VARCHAR(50) DEFAULT 'EDITOR',
    invited_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_collab_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Trip Suggestions Table
CREATE TABLE IF NOT EXISTS trip_suggestions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    place_name VARCHAR(200) NOT NULL,
    category VARCHAR(100) DEFAULT 'Sightseeing',
    suggested_by_name VARCHAR(100) NOT NULL,
    votes_count INT DEFAULT 1,
    notes TEXT,
    status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_suggestion_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Trip Checklists Table (Dedicated Travel Preparation Checklist)
CREATE TABLE IF NOT EXISTS trip_checklists (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    category VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    notes TEXT,
    completed BOOLEAN DEFAULT FALSE,
    essential BOOLEAN DEFAULT FALSE,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_checklist_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. Trip Versions Table (Trip Version History & Snapshots)
CREATE TABLE IF NOT EXISTS trip_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    change_description VARCHAR(255) NOT NULL,
    change_source VARCHAR(50) DEFAULT 'USER_ACTION',
    created_by_name VARCHAR(100),
    created_by_email VARCHAR(120),
    snapshot_data LONGTEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_version_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. Performance Indexes
CREATE INDEX idx_trips_user ON trips(user_id);
CREATE INDEX idx_destinations_trip ON destinations(trip_id);
CREATE INDEX idx_places_destination ON places(destination_id);
CREATE INDEX idx_itineraries_trip ON itineraries(trip_id);
CREATE INDEX idx_collaborators_trip ON trip_collaborators(trip_id);
CREATE INDEX idx_suggestions_trip ON trip_suggestions(trip_id);
CREATE INDEX idx_checklists_trip ON trip_checklists(trip_id);
CREATE INDEX idx_versions_trip ON trip_versions(trip_id);
