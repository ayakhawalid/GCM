-- ============================================================
-- GCM System Database Schema (consolidated)
-- ============================================================
-- Includes all migrations: city/map approval, map_pois approved/linked_by_user_id,
-- tour distance (no duration), maps.tour_id for tour route maps.
-- Run this file for a full reset with schema + seed data.
-- ============================================================
CREATE DATABASE IF NOT EXISTS gcm_db;
USE gcm_db;
-- ============================================================
-- DROP existing tables (for clean reset)
-- ============================================================
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS ticket_messages;
DROP TABLE IF EXISTS support_tickets;
DROP TABLE IF EXISTS faq_entries;
DROP TABLE IF EXISTS pricing_requests;
DROP TABLE IF EXISTS view_events;
DROP TABLE IF EXISTS download_events;
DROP TABLE IF EXISTS subscriptions;
DROP TABLE IF EXISTS purchases;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS approvals;
DROP TABLE IF EXISTS tour_stops;
DROP TABLE IF EXISTS tours;
DROP TABLE IF EXISTS poi_distances;
DROP TABLE IF EXISTS map_pois;
DROP TABLE IF EXISTS pois;
DROP TABLE IF EXISTS map_versions;
DROP TABLE IF EXISTS maps;
DROP TABLE IF EXISTS customers;
DROP TABLE IF EXISTS employees;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS cities;
SET FOREIGN_KEY_CHECKS = 1;
-- ============================================================
-- 1. CITIES TABLE
-- ============================================================
CREATE TABLE cities (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    price DOUBLE NOT NULL DEFAULT 0.0,
    approved TINYINT(1) NOT NULL DEFAULT 0,
    created_by INT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_cities_name (name),
    INDEX idx_cities_approved (approved)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- 2. MAPS TABLE
-- ============================================================
CREATE TABLE maps (
    id INT AUTO_INCREMENT PRIMARY KEY,
    city_id INT NOT NULL,
    name VARCHAR(200) NOT NULL,
    short_description VARCHAR(500),
    approved TINYINT(1) NOT NULL DEFAULT 0,
    created_by INT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE,
    INDEX idx_maps_city_id (city_id),
    INDEX idx_maps_name (name),
    INDEX idx_maps_approved (approved)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- 3. MAP_VERSIONS TABLE (for versioning support)
-- ============================================================
CREATE TABLE map_versions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    map_id INT NOT NULL,
    version_number INT NOT NULL DEFAULT 1,
    status ENUM('DRAFT', 'PENDING', 'APPROVED', 'REJECTED') DEFAULT 'DRAFT',
    description_text TEXT,
    created_by INT,
    approved_by INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP NULL,
    rejection_reason VARCHAR(500),
    FOREIGN KEY (map_id) REFERENCES maps(id) ON DELETE CASCADE,
    INDEX idx_map_versions_map_id (map_id),
    INDEX idx_map_versions_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- 4. POIS (Points of Interest) TABLE
-- ============================================================
CREATE TABLE pois (
    id INT AUTO_INCREMENT PRIMARY KEY,
    city_id INT NOT NULL,
    name VARCHAR(200) NOT NULL,
    location VARCHAR(100),
    -- lat,lng or location description
    latitude DOUBLE NULL,
    longitude DOUBLE NULL,
    category VARCHAR(50),
    -- Beach, Museum, Restaurant, Historic, Park, etc.
    short_explanation VARCHAR(500),
    is_accessible BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE,
    -- Indexes for fast POI search
    INDEX idx_pois_city_id (city_id),
    INDEX idx_pois_name (name),
    INDEX idx_pois_category (category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- 5. MAP_POIS Junction Table (Many-to-Many)
-- ============================================================
-- A POI can appear in multiple maps (e.g., a beach POI in both
-- "Beaches of Haifa" and "Complete Haifa Guide" maps)
CREATE TABLE map_pois (
    map_id INT NOT NULL,
    poi_id INT NOT NULL,
    display_order INT DEFAULT 0,
    approved TINYINT NOT NULL DEFAULT 1,
    linked_by_user_id INT NULL,
    PRIMARY KEY (map_id, poi_id),
    FOREIGN KEY (map_id) REFERENCES maps(id) ON DELETE CASCADE,
    FOREIGN KEY (poi_id) REFERENCES pois(id) ON DELETE CASCADE,
    INDEX idx_map_pois_poi_id (poi_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- 6. TOURS TABLE
-- ============================================================
CREATE TABLE tours (
    id INT AUTO_INCREMENT PRIMARY KEY,
    city_id INT NOT NULL,
    name VARCHAR(200) NOT NULL,
    general_description TEXT,
    total_distance_meters DOUBLE NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE,
    INDEX idx_tours_city_id (city_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- 7. TOUR_STOPS TABLE
-- ============================================================
CREATE TABLE tour_stops (
    id INT AUTO_INCREMENT PRIMARY KEY,
    tour_id INT NOT NULL,
    poi_id INT NOT NULL,
    stop_order INT NOT NULL,
    notes VARCHAR(500),
    FOREIGN KEY (tour_id) REFERENCES tours(id) ON DELETE CASCADE,
    FOREIGN KEY (poi_id) REFERENCES pois(id) ON DELETE CASCADE,
    INDEX idx_tour_stops_tour_id (tour_id),
    UNIQUE KEY unique_tour_stop_order (tour_id, stop_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- 7b. POI_DISTANCES (distances between POIs for tour planning)
-- ============================================================
CREATE TABLE poi_distances (
    poi_id_a INT NOT NULL,
    poi_id_b INT NOT NULL,
    distance_meters DOUBLE NOT NULL,
    PRIMARY KEY (poi_id_a, poi_id_b),
    FOREIGN KEY (poi_id_a) REFERENCES pois(id) ON DELETE CASCADE,
    FOREIGN KEY (poi_id_b) REFERENCES pois(id) ON DELETE CASCADE,
    INDEX idx_poi_distances_b (poi_id_b)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- maps.tour_id: dedicated tour route map per tour (migration_tour_maps)
ALTER TABLE maps ADD COLUMN tour_id INT NULL AFTER created_by;
ALTER TABLE maps ADD CONSTRAINT fk_maps_tour FOREIGN KEY (tour_id) REFERENCES tours(id) ON DELETE CASCADE;
-- ============================================================
-- 8. USERS TABLE
-- ============================================================
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) UNIQUE,
    password_hash VARCHAR(255),
    role ENUM(
        'ANONYMOUS',
        'CUSTOMER',
        'CONTENT_EDITOR',
        'CONTENT_MANAGER',
        'COMPANY_MANAGER',
        'SUPPORT_AGENT'
    ) DEFAULT 'CUSTOMER',
    phone VARCHAR(20),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NULL,
    INDEX idx_users_username (username),
    INDEX idx_users_email (email),
    INDEX idx_users_role (role)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- 9. CUSTOMERS TABLE (extends users)
-- ============================================================
CREATE TABLE customers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL UNIQUE,
    payment_token VARCHAR(100),
    -- Mock payment token
    card_last4 VARCHAR(4),
    -- Last 4 digits of card
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- 10. EMPLOYEES TABLE (extends users)
-- ============================================================
CREATE TABLE employees (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL UNIQUE,
    department VARCHAR(50),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- SEED DATA: 20 world cities + 5 Israel cities (Haifa, Jerusalem, TA, Netanya, Akko);
-- each city: 3 maps (tour map A + normal B + normal C), 5 POIs per map, 1 tour mixing POIs from A+B
-- ============================================================

INSERT INTO cities (id, name, description, price, approved, created_by) VALUES
    (1, 'New York City, USA', 'Major US metropolis - finance, culture, and iconic landmarks.', 250.0, 1, NULL),
    (2, 'London, UK', 'Historic capital - royalty, museums, and the Thames.', 220.0, 1, NULL),
    (3, 'Paris, France', 'Art, cuisine, and world-famous monuments.', 230.0, 1, NULL),
    (4, 'Berlin, Germany', 'History, museums, and vibrant neighborhoods.', 210.0, 1, NULL),
    (5, 'Tokyo, Japan', 'Ultra-modern city with deep traditional roots.', 280.0, 1, NULL),
    (6, 'Sydney, Australia', 'Harbour city - beaches, icons, and outdoor life.', 240.0, 1, NULL),
    (7, 'Rome, Italy', 'The Eternal City - ancient ruins and Baroque splendor.', 205.0, 1, NULL),
    (8, 'Moscow, Russia', 'Capital of Russia - Kremlin, squares, and culture.', 215.0, 1, NULL),
    (9, 'Cairo, Egypt', 'Megacity gateway to ancient Egyptian heritage.', 195.0, 1, NULL),
    (10, 'Sao Paulo, Brazil', 'Brazil''s largest city - business, culture, and parks.', 200.0, 1, NULL),
    (11, 'Los Angeles, USA', 'Entertainment capital - coast, hills, and studios.', 245.0, 1, NULL),
    (12, 'Beijing, China', 'Imperial heritage and modern Olympic legacy.', 225.0, 1, NULL),
    (13, 'Mumbai, India', 'Coastal economic hub - colonial-era icons and Bollywood.', 210.0, 1, NULL),
    (14, 'Istanbul, Turkiye', 'Two continents - mosques, bazaars, and the Bosphorus.', 225.0, 1, NULL),
    (15, 'Mexico City, Mexico', 'High-altitude capital - Aztec roots and vibrant plazas.', 200.0, 1, NULL),
    (16, 'Toronto, Canada', 'Canada''s largest city - Lake Ontario waterfront and diversity.', 215.0, 1, NULL),
    (17, 'Johannesburg, South Africa', 'Economic heart of South Africa - history and urban energy.', 190.0, 1, NULL),
    (18, 'Bangkok, Thailand', 'Temples, markets, and legendary street life.', 205.0, 1, NULL),
    (19, 'Singapore', 'City-state - futuristic skyline and lush gardens.', 260.0, 1, NULL),
    (20, 'Dubai, UAE', 'Desert metropolis - iconic towers and luxury coastline.', 270.0, 1, NULL),
    (21, 'Haifa', 'Bay city: Baha''i terraces, German Colony, beaches, and port.', 150.0, 1, NULL),
    (22, 'Jerusalem', 'Holy sites, Old City gates, museums, and markets.', 180.0, 1, NULL),
    (23, 'Tel Aviv', 'Coastal culture: museums, squares, markets, and the port.', 200.0, 1, NULL),
    (24, 'Netanya', 'Mediterranean resort city: cliffs, beaches, and promenades.', 130.0, 1, NULL),
    (25, 'Akko (Acre)', 'UNESCO Crusader port: Old City, walls, and maritime heritage.', 140.0, 1, NULL);

-- Reset AUTO_INCREMENT after explicit city ids
ALTER TABLE cities AUTO_INCREMENT = 26;

-- One tour per city (tour id = city id)
INSERT INTO tours (id, city_id, name, general_description, total_distance_meters) VALUES
    (1, 1, 'New York City Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in New York City.', NULL),
    (2, 2, 'London Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in London.', NULL),
    (3, 3, 'Paris Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Paris.', NULL),
    (4, 4, 'Berlin Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Berlin.', NULL),
    (5, 5, 'Tokyo Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Tokyo.', NULL),
    (6, 6, 'Sydney Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Sydney.', NULL),
    (7, 7, 'Rome Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Rome.', NULL),
    (8, 8, 'Moscow Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Moscow.', NULL),
    (9, 9, 'Cairo Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Cairo.', NULL),
    (10, 10, 'Sao Paulo Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Sao Paulo.', NULL),
    (11, 11, 'Los Angeles Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Los Angeles.', NULL),
    (12, 12, 'Beijing Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Beijing.', NULL),
    (13, 13, 'Mumbai Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Mumbai.', NULL),
    (14, 14, 'Istanbul Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Istanbul.', NULL),
    (15, 15, 'Mexico City Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Mexico City.', NULL),
    (16, 16, 'Toronto Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Toronto.', NULL),
    (17, 17, 'Johannesburg Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Johannesburg.', NULL),
    (18, 18, 'Bangkok Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Bangkok.', NULL),
    (19, 19, 'Singapore Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Singapore.', NULL),
    (20, 20, 'Dubai Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Dubai.', NULL),
    (21, 21, 'Haifa Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Haifa.', NULL),
    (22, 22, 'Jerusalem Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Jerusalem.', NULL),
    (23, 23, 'Tel Aviv Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Tel Aviv.', NULL),
    (24, 24, 'Netanya Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Netanya.', NULL),
    (25, 25, 'Akko Highlights Tour', 'Mixed route across both maps - landmarks, transit, and greenspace in Akko (Acre).', NULL);
ALTER TABLE tours AUTO_INCREMENT = 26;

-- Three maps per city: Map A = tour route (tour_id set); Map B = normal (POIs 6-10);
-- Map C = normal duplicate of A's POI set so every tour-map POI also appears on a non-tour map.
INSERT INTO maps (id, city_id, name, short_description, approved, created_by, tour_id) VALUES
    (1, 1, 'New York City - Landmarks & Core (Map A)', 'Five major POIs: first half of New York City curated set.', 1, NULL, 1),
    (2, 1, 'New York City - Transit, Parks & More (Map B)', 'Five more POIs: second half of New York City curated set.', 1, NULL, NULL),
    (3, 2, 'London - Landmarks & Core (Map A)', 'Five major POIs: first half of London curated set.', 1, NULL, 2),
    (4, 2, 'London - Transit, Parks & More (Map B)', 'Five more POIs: second half of London curated set.', 1, NULL, NULL),
    (5, 3, 'Paris - Landmarks & Core (Map A)', 'Five major POIs: first half of Paris curated set.', 1, NULL, 3),
    (6, 3, 'Paris - Transit, Parks & More (Map B)', 'Five more POIs: second half of Paris curated set.', 1, NULL, NULL),
    (7, 4, 'Berlin - Landmarks & Core (Map A)', 'Five major POIs: first half of Berlin curated set.', 1, NULL, 4),
    (8, 4, 'Berlin - Transit, Parks & More (Map B)', 'Five more POIs: second half of Berlin curated set.', 1, NULL, NULL),
    (9, 5, 'Tokyo - Landmarks & Core (Map A)', 'Five major POIs: first half of Tokyo curated set.', 1, NULL, 5),
    (10, 5, 'Tokyo - Transit, Parks & More (Map B)', 'Five more POIs: second half of Tokyo curated set.', 1, NULL, NULL),
    (11, 6, 'Sydney - Landmarks & Core (Map A)', 'Five major POIs: first half of Sydney curated set.', 1, NULL, 6),
    (12, 6, 'Sydney - Transit, Parks & More (Map B)', 'Five more POIs: second half of Sydney curated set.', 1, NULL, NULL),
    (13, 7, 'Rome - Landmarks & Core (Map A)', 'Five major POIs: first half of Rome curated set.', 1, NULL, 7),
    (14, 7, 'Rome - Transit, Parks & More (Map B)', 'Five more POIs: second half of Rome curated set.', 1, NULL, NULL),
    (15, 8, 'Moscow - Landmarks & Core (Map A)', 'Five major POIs: first half of Moscow curated set.', 1, NULL, 8),
    (16, 8, 'Moscow - Transit, Parks & More (Map B)', 'Five more POIs: second half of Moscow curated set.', 1, NULL, NULL),
    (17, 9, 'Cairo - Landmarks & Core (Map A)', 'Five major POIs: first half of Cairo curated set.', 1, NULL, 9),
    (18, 9, 'Cairo - Transit, Parks & More (Map B)', 'Five more POIs: second half of Cairo curated set.', 1, NULL, NULL),
    (19, 10, 'Sao Paulo - Landmarks & Core (Map A)', 'Five major POIs: first half of Sao Paulo curated set.', 1, NULL, 10),
    (20, 10, 'Sao Paulo - Transit, Parks & More (Map B)', 'Five more POIs: second half of Sao Paulo curated set.', 1, NULL, NULL),
    (21, 11, 'Los Angeles - Landmarks & Core (Map A)', 'Five major POIs: first half of Los Angeles curated set.', 1, NULL, 11),
    (22, 11, 'Los Angeles - Transit, Parks & More (Map B)', 'Five more POIs: second half of Los Angeles curated set.', 1, NULL, NULL),
    (23, 12, 'Beijing - Landmarks & Core (Map A)', 'Five major POIs: first half of Beijing curated set.', 1, NULL, 12),
    (24, 12, 'Beijing - Transit, Parks & More (Map B)', 'Five more POIs: second half of Beijing curated set.', 1, NULL, NULL),
    (25, 13, 'Mumbai - Landmarks & Core (Map A)', 'Five major POIs: first half of Mumbai curated set.', 1, NULL, 13),
    (26, 13, 'Mumbai - Transit, Parks & More (Map B)', 'Five more POIs: second half of Mumbai curated set.', 1, NULL, NULL),
    (27, 14, 'Istanbul - Landmarks & Core (Map A)', 'Five major POIs: first half of Istanbul curated set.', 1, NULL, 14),
    (28, 14, 'Istanbul - Transit, Parks & More (Map B)', 'Five more POIs: second half of Istanbul curated set.', 1, NULL, NULL),
    (29, 15, 'Mexico City - Landmarks & Core (Map A)', 'Five major POIs: first half of Mexico City curated set.', 1, NULL, 15),
    (30, 15, 'Mexico City - Transit, Parks & More (Map B)', 'Five more POIs: second half of Mexico City curated set.', 1, NULL, NULL),
    (31, 16, 'Toronto - Landmarks & Core (Map A)', 'Five major POIs: first half of Toronto curated set.', 1, NULL, 16),
    (32, 16, 'Toronto - Transit, Parks & More (Map B)', 'Five more POIs: second half of Toronto curated set.', 1, NULL, NULL),
    (33, 17, 'Johannesburg - Landmarks & Core (Map A)', 'Five major POIs: first half of Johannesburg curated set.', 1, NULL, 17),
    (34, 17, 'Johannesburg - Transit, Parks & More (Map B)', 'Five more POIs: second half of Johannesburg curated set.', 1, NULL, NULL),
    (35, 18, 'Bangkok - Landmarks & Core (Map A)', 'Five major POIs: first half of Bangkok curated set.', 1, NULL, 18),
    (36, 18, 'Bangkok - Transit, Parks & More (Map B)', 'Five more POIs: second half of Bangkok curated set.', 1, NULL, NULL),
    (37, 19, 'Singapore - Landmarks & Core (Map A)', 'Five major POIs: first half of Singapore curated set.', 1, NULL, 19),
    (38, 19, 'Singapore - Transit, Parks & More (Map B)', 'Five more POIs: second half of Singapore curated set.', 1, NULL, NULL),
    (39, 20, 'Dubai - Landmarks & Core (Map A)', 'Five major POIs: first half of Dubai curated set.', 1, NULL, 20),
    (40, 20, 'Dubai - Transit, Parks & More (Map B)', 'Five more POIs: second half of Dubai curated set.', 1, NULL, NULL),
    (41, 21, 'Haifa - Landmarks & Core (Map A)', 'Five major POIs: first half of Haifa curated set.', 1, NULL, 21),
    (42, 21, 'Haifa - Transit, Parks & More (Map B)', 'Five more POIs: second half of Haifa curated set.', 1, NULL, NULL),
    (43, 22, 'Jerusalem - Landmarks & Core (Map A)', 'Five major POIs: first half of Jerusalem curated set.', 1, NULL, 22),
    (44, 22, 'Jerusalem - Transit, Parks & More (Map B)', 'Five more POIs: second half of Jerusalem curated set.', 1, NULL, NULL),
    (45, 23, 'Tel Aviv - Landmarks & Core (Map A)', 'Five major POIs: first half of Tel Aviv curated set.', 1, NULL, 23),
    (46, 23, 'Tel Aviv - Transit, Parks & More (Map B)', 'Five more POIs: second half of Tel Aviv curated set.', 1, NULL, NULL),
    (47, 24, 'Netanya - Landmarks & Core (Map A)', 'Five major POIs: first half of Netanya curated set.', 1, NULL, 24),
    (48, 24, 'Netanya - Transit, Parks & More (Map B)', 'Five more POIs: second half of Netanya curated set.', 1, NULL, NULL),
    (49, 25, 'Akko (Acre) - Landmarks & Core (Map A)', 'Five major POIs: first half of Akko curated set.', 1, NULL, 25),
    (50, 25, 'Akko (Acre) - Transit, Parks & More (Map B)', 'Five more POIs: second half of Akko curated set.', 1, NULL, NULL),
    (51, 1, 'New York City - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (52, 2, 'London - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (53, 3, 'Paris - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (54, 4, 'Berlin - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (55, 5, 'Tokyo - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (56, 6, 'Sydney - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (57, 7, 'Rome - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (58, 8, 'Moscow - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (59, 9, 'Cairo - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (60, 10, 'Sao Paulo - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (61, 11, 'Los Angeles - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (62, 12, 'Beijing - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (63, 13, 'Mumbai - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (64, 14, 'Istanbul - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (65, 15, 'Mexico City - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (66, 16, 'Toronto - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (67, 17, 'Johannesburg - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (68, 18, 'Bangkok - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (69, 19, 'Singapore - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (70, 20, 'Dubai - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (71, 21, 'Haifa - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (72, 22, 'Jerusalem - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (73, 23, 'Tel Aviv - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (74, 24, 'Netanya - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL),
    (75, 25, 'Akko (Acre) - Landmarks & Core (normal map)', 'Normal catalog map: same five POIs as the tour route map (Map A).', 1, NULL, NULL);
ALTER TABLE maps AUTO_INCREMENT = 76;

INSERT INTO pois (id, city_id, name, location, category, short_explanation, is_accessible) VALUES
    (1, 1, 'Times Square', '40.7580,-73.9855', 'Entertainment', 'Neon heart of Midtown.', TRUE),
    (2, 1, 'Central Park', '40.7829,-73.9654', 'Park', 'Vast urban park and recreation.', TRUE),
    (3, 1, 'Statue of Liberty', '40.6892,-74.0445', 'Landmark', 'Icon of freedom in NY Harbor.', TRUE),
    (4, 1, 'Empire State Building', '40.7484,-73.9857', 'Landmark', 'Art deco observation classic.', TRUE),
    (5, 1, 'Brooklyn Bridge', '40.7061,-73.9969', 'Landmark', 'Historic suspension bridge.', TRUE),
    (6, 1, 'Rockefeller Center', '40.7587,-73.9787', 'Landmark', 'Midtown plaza and ice rink.', TRUE),
    (7, 1, 'Grand Central Terminal', '40.7527,-73.9772', 'Transport', 'Beaux-arts rail landmark.', TRUE),
    (8, 1, 'One World Trade Center', '40.7127,-74.0134', 'Landmark', 'Downtown skyline anchor.', TRUE),
    (9, 1, 'Times Square-42 St Subway Station', '40.7553,-73.9870', 'Transport', 'Major subway interchange.', TRUE),
    (10, 1, 'JFK International Airport', '40.6413,-73.7781', 'Transport', 'Main international air gateway.', TRUE),
    (11, 2, 'Big Ben', '51.5007,-0.1246', 'Landmark', 'Clock tower at Parliament.', TRUE),
    (12, 2, 'London Eye', '51.5033,-0.1195', 'Entertainment', 'Thames observation wheel.', TRUE),
    (13, 2, 'Tower of London', '51.5081,-0.0759', 'Historic', 'Crown jewels fortress.', TRUE),
    (14, 2, 'Tower Bridge', '51.5055,-0.0754', 'Landmark', 'Victorian bascule bridge.', TRUE),
    (15, 2, 'Buckingham Palace', '51.5014,-0.1419', 'Royal', 'Monarch''s London residence.', TRUE),
    (16, 2, 'Trafalgar Square', '51.5080,-0.1281', 'Plaza', 'National Gallery steps plaza.', TRUE),
    (17, 2, 'St Paul''s Cathedral', '51.5138,-0.0984', 'Religious', 'Domed Anglican cathedral.', TRUE),
    (18, 2, 'King''s Cross Station', '51.5308,-0.1238', 'Transport', 'Major rail head.', TRUE),
    (19, 2, 'Hyde Park', '51.5073,-0.1657', 'Park', 'Royal park central London.', TRUE),
    (20, 2, 'Heathrow Airport', '51.4700,-0.4543', 'Transport', 'Primary London airport.', TRUE),
    (21, 3, 'Eiffel Tower', '48.8584,2.2945', 'Landmark', 'Iron lattice tower.', TRUE),
    (22, 3, 'Louvre Museum', '48.8606,2.3376', 'Museum', 'World-class art museum.', TRUE),
    (23, 3, 'Notre-Dame Cathedral', '48.8530,2.3499', 'Religious', 'Medieval cathedral.', TRUE),
    (24, 3, 'Arc de Triomphe', '48.8738,2.2950', 'Landmark', 'Triumphal arch.', TRUE),
    (25, 3, 'Sacré-Cœur Basilica', '48.8867,2.3431', 'Religious', 'Hilltop basilica Montmartre.', TRUE),
    (26, 3, 'Gare du Nord', '48.8809,2.3553', 'Transport', 'Busy rail station.', TRUE),
    (27, 3, 'Jardin du Luxembourg', '48.8462,2.3372', 'Park', 'Formal palace gardens.', TRUE),
    (28, 3, 'Place de la Concorde', '48.8656,2.3211', 'Plaza', 'Major public square.', TRUE),
    (29, 3, 'Champs-Elysees (central)', '48.8698,2.3073', 'Street', 'Grand avenue.', TRUE),
    (30, 3, 'Charles de Gaulle Airport', '49.0097,2.5479', 'Transport', 'Main Paris airport.', TRUE),
    (31, 4, 'Brandenburg Gate', '52.5163,13.3777', 'Landmark', 'Neoclassical city gate.', TRUE),
    (32, 4, 'Reichstag Building', '52.5186,13.3762', 'Government', 'Historic parliament dome.', TRUE),
    (33, 4, 'Berlin TV Tower (Fernsehturm)', '52.5208,13.4094', 'Landmark', 'Alexanderplatz tower views.', TRUE),
    (34, 4, 'Berlin Central Station (Hbf)', '52.5251,13.3694', 'Transport', 'Main railway hub.', TRUE),
    (35, 4, 'Checkpoint Charlie', '52.5076,13.3904', 'Historic', 'Cold War crossing point.', TRUE),
    (36, 4, 'East Side Gallery', '52.5050,13.4394', 'Historic', 'Murals on Berlin Wall remnant.', TRUE),
    (37, 4, 'Alexanderplatz', '52.5219,13.4132', 'Plaza', 'Central square east Berlin.', TRUE),
    (38, 4, 'Museum Island (center)', '52.5169,13.4010', 'Museum', 'UNESCO museum ensemble.', TRUE),
    (39, 4, 'Tempelhofer Feld', '52.4730,13.4020', 'Park', 'Former airfield public park.', TRUE),
    (40, 4, 'Berlin Tegel (former airport)', '52.5597,13.2877', 'Transport', 'Reference point former TXL.', TRUE),
    (41, 5, 'Tokyo Station', '35.6812,139.7671', 'Transport', 'Brick-front rail terminus.', TRUE),
    (42, 5, 'Shibuya Crossing', '35.6595,139.7005', 'Landmark', 'Famous scramble crossing.', TRUE),
    (43, 5, 'Tokyo Skytree', '35.7100,139.8107', 'Landmark', 'Tallest tower Japan.', TRUE),
    (44, 5, 'Senso-ji Temple', '35.7148,139.7967', 'Religious', 'Asakusa Buddhist temple.', TRUE),
    (45, 5, 'Tokyo Tower', '35.6586,139.7454', 'Landmark', 'Orange lattice tower.', TRUE),
    (46, 5, 'Meiji Jingu Shrine', '35.6764,139.6993', 'Religious', 'Forest Shinto shrine.', TRUE),
    (47, 5, 'Ueno Park (center)', '35.7156,139.7745', 'Park', 'Museums and cherry trees.', TRUE),
    (48, 5, 'Shinjuku Gyoen', '35.6852,139.7100', 'Park', 'Imperial garden oasis.', TRUE),
    (49, 5, 'Roppongi Hills', '35.6605,139.7293', 'Entertainment', 'Mori tower district.', TRUE),
    (50, 5, 'Haneda Airport', '35.5494,139.7798', 'Transport', 'Major Tokyo airport.', TRUE),
    (51, 6, 'Sydney Opera House', '33.8568,151.2153', 'Landmark', 'Sails on the harbour.', TRUE),
    (52, 6, 'Sydney Harbour Bridge', '33.8523,151.2108', 'Landmark', 'Coathanger steel arch.', TRUE),
    (53, 6, 'Circular Quay', '33.8611,151.2109', 'Transport', 'Ferries and harbour front.', TRUE),
    (54, 6, 'Bondi Beach', '33.8915,151.2767', 'Beach', 'Famous surf beach.', TRUE),
    (55, 6, 'Darling Harbour', '33.8728,151.2006', 'Entertainment', 'Waterfront dining precinct.', TRUE),
    (56, 6, 'Royal Botanic Garden (center)', '33.8642,151.2166', 'Park', 'Harbour-side gardens.', TRUE),
    (57, 6, 'The Rocks', '33.8599,151.2070', 'Historic', 'Colonial laneways.', TRUE),
    (58, 6, 'Central Station', '33.8830,151.2065', 'Transport', 'Main rail station.', TRUE),
    (59, 6, 'Taronga Zoo Wharf', '33.8457,151.2413', 'Transport', 'Ferry to zoo.', TRUE),
    (60, 6, 'Sydney Airport', '33.9399,151.1753', 'Transport', 'Kingsford Smith airport.', TRUE),
    (61, 7, 'Colosseum', '41.8902,12.4922', 'Historic', 'Ancient amphitheatre.', TRUE),
    (62, 7, 'Roman Forum (center)', '41.8925,12.4853', 'Historic', 'Heart of ancient Rome.', TRUE),
    (63, 7, 'Trevi Fountain', '41.9009,12.4833', 'Landmark', 'Baroque wishing fountain.', TRUE),
    (64, 7, 'Pantheon', '41.8986,12.4769', 'Historic', 'Domed Roman temple.', TRUE),
    (65, 7, 'St Peter''s Basilica', '41.9022,12.4539', 'Religious', 'Vatican major basilica.', TRUE),
    (66, 7, 'Piazza Navona', '41.8992,12.4731', 'Plaza', 'Baroque square fountains.', TRUE),
    (67, 7, 'Termini Station', '41.9010,12.5018', 'Transport', 'Central rail hub.', TRUE),
    (68, 7, 'Spanish Steps', '41.9059,12.4823', 'Landmark', 'Trinita dei Monti stairs.', TRUE),
    (69, 7, 'Circus Maximus', '41.8861,12.4852', 'Historic', 'Ancient chariot circuit.', TRUE),
    (70, 7, 'Ciampino Airport', '41.7999,12.5949', 'Transport', 'Secondary Rome airport.', TRUE),
    (71, 8, 'Red Square (center)', '55.7539,37.6208', 'Plaza', 'Historic central square.', TRUE),
    (72, 8, 'Kremlin (Spasskaya Tower)', '55.7525,37.6231', 'Historic', 'Fortified complex.', TRUE),
    (73, 8, 'St Basil''s Cathedral', '55.7525,37.6230', 'Religious', 'Colorful onion domes.', TRUE),
    (74, 8, 'Bolshoi Theatre', '55.7601,37.6186', 'Cultural', 'Opera and ballet house.', TRUE),
    (75, 8, 'GUM Department Store', '55.7549,37.6216', 'Shopping', 'Arcaded retail facing square.', TRUE),
    (76, 8, 'Moscow State University (main)', '55.7033,37.5301', 'Education', 'Stalinist skyscraper campus.', TRUE),
    (77, 8, 'Gorky Park', '55.7299,37.6036', 'Park', 'Riverside recreation.', TRUE),
    (78, 8, 'Kievsky Railway Station', '55.7431,37.5650', 'Transport', 'Rail terminus.', TRUE),
    (79, 8, 'VDNKh (main entrance)', '55.8298,37.6339', 'Entertainment', 'Exhibition park gates.', TRUE),
    (80, 8, 'Sheremetyevo Airport', '55.9726,37.4146', 'Transport', 'Major Moscow airport.', TRUE),
    (81, 9, 'Tahrir Square', '30.0444,31.2357', 'Plaza', 'Downtown focal square.', TRUE),
    (82, 9, 'Egyptian Museum', '30.0460,31.2336', 'Museum', 'Antiquities collection.', TRUE),
    (83, 9, 'Cairo Citadel', '30.0299,31.2617', 'Historic', 'Medieval fortifications.', TRUE),
    (84, 9, 'Khan el-Khalili Bazaar', '30.0478,31.2625', 'Market', 'Historic souk.', TRUE),
    (85, 9, 'Al-Azhar Mosque', '30.0477,31.2620', 'Religious', 'Fatimid mosque.', TRUE),
    (86, 9, 'Giza Pyramids (Great Pyramid)', '29.9792,31.1342', 'Historic', 'Ancient wonder plateau.', TRUE),
    (87, 9, 'Cairo Tower', '30.0459,31.2243', 'Landmark', 'Nile-side tower views.', TRUE),
    (88, 9, 'Ramses Railway Station', '30.0635,31.2461', 'Transport', 'Central rail.', TRUE),
    (89, 9, 'Al-Azhar Park (center)', '30.0429,31.2685', 'Park', 'Hilltop green space.', TRUE),
    (90, 9, 'Cairo International Airport', '30.1219,31.4056', 'Transport', 'Main airport.', TRUE),
    (91, 10, 'Paulista Avenue (MASP)', '23.5614,-46.6559', 'Street', 'Museum and skyline.', TRUE),
    (92, 10, 'Ibirapuera Park (center)', '23.5874,-46.6576', 'Park', 'Major city park.', TRUE),
    (93, 10, 'Sao Paulo Cathedral (Se)', '23.5503,-46.6342', 'Religious', 'Neo-Gothic cathedral.', TRUE),
    (94, 10, 'Luz Station', '23.5363,-46.6339', 'Transport', 'Historic rail station.', TRUE),
    (95, 10, 'Municipal Market', '23.5411,-46.6296', 'Market', 'Gourmet produce hall.', TRUE),
    (96, 10, 'Pinacoteca do Estado', '23.5342,-46.6339', 'Museum', 'Art museum Luz.', TRUE),
    (97, 10, 'Allianz Parque Stadium', '23.5276,-46.6784', 'Entertainment', 'Football arena.', TRUE),
    (98, 10, 'Morumbi Stadium', '23.5990,-46.7208', 'Entertainment', 'Large football venue.', TRUE),
    (99, 10, 'Congonhas Airport', '23.6261,-46.6566', 'Transport', 'Domestic airport.', TRUE),
    (100, 10, 'Tiete Bus Terminal', '23.5185,-46.6250', 'Transport', 'Major bus hub.', TRUE),
    (101, 11, 'Hollywood Sign', '34.1341,-118.3215', 'Landmark', 'Hillside cinema icon.', TRUE),
    (102, 11, 'Griffith Observatory', '34.1184,-118.3004', 'Museum', 'Planetarium and city views.', TRUE),
    (103, 11, 'Santa Monica Pier', '34.0100,-118.4963', 'Entertainment', 'Pacific Park pier.', TRUE),
    (104, 11, 'Hollywood Walk of Fame', '34.1019,-118.3269', 'Entertainment', 'Terrazzo star plaques.', TRUE),
    (105, 11, 'LAX Airport', '33.9416,-118.4085', 'Transport', 'Main LA airport.', TRUE),
    (106, 11, 'Walt Disney Concert Hall', '34.0553,-118.2498', 'Cultural', 'Gehry concert hall.', TRUE),
    (107, 11, 'The Getty Center', '34.0780,-118.4741', 'Museum', 'Hilltop art campus.', TRUE),
    (108, 11, 'Dodger Stadium', '34.0739,-118.2400', 'Entertainment', 'Historic baseball ballpark.', TRUE),
    (109, 11, 'Crypto.com Arena', '34.0430,-118.2673', 'Entertainment', 'Downtown sports venue.', TRUE),
    (110, 11, 'Union Station', '34.0561,-118.2365', 'Transport', 'Mission revival terminal.', TRUE),
    (111, 12, 'Tiananmen Square (center)', '39.9055,116.3976', 'Plaza', 'Vast central square.', TRUE),
    (112, 12, 'Forbidden City (Meridian Gate)', '39.9163,116.3972', 'Historic', 'Imperial palace south gate.', TRUE),
    (113, 12, 'Temple of Heaven', '39.8822,116.4065', 'Historic', 'Ming ritual complex.', TRUE),
    (114, 12, 'Summer Palace (Longevity Hill)', '39.9996,116.2755', 'Historic', 'Lake palace gardens.', TRUE),
    (115, 12, 'Beijing National Stadium', '39.9917,116.3907', 'Landmark', 'Bird''s Nest Olympic stadium.', TRUE),
    (116, 12, 'National Aquatics Center', '39.9929,116.3960', 'Landmark', 'Water Cube.', TRUE),
    (117, 12, 'Wangfujing Street (center)', '39.9143,116.4110', 'Shopping', 'Pedestrian retail strip.', TRUE),
    (118, 12, 'Beijing South Railway Station', '39.8650,116.3789', 'Transport', 'HSR terminus.', TRUE),
    (119, 12, 'Beijing Capital Airport', '40.0799,116.6031', 'Transport', 'PEK international.', TRUE),
    (120, 12, '798 Art District', '39.9841,116.4970', 'Cultural', 'Factory gallery zone.', TRUE),
    (121, 13, 'Chhatrapati Shivaji Terminus', '18.9402,72.8353', 'Historic', 'UNESCO Victoria Terminus.', TRUE),
    (122, 13, 'Gateway of India', '18.9220,72.8347', 'Landmark', 'Harbour triumphal arch.', TRUE),
    (123, 13, 'Marine Drive', '18.9430,72.8238', 'Street', 'Queen''s Necklace promenade.', TRUE),
    (124, 13, 'Siddhivinayak Temple', '19.0169,72.8305', 'Religious', 'Ganesha temple.', TRUE),
    (125, 13, 'Haji Ali Dargah', '18.9823,72.8087', 'Religious', 'Coastal mosque tomb.', TRUE),
    (126, 13, 'Bandra-Worli Sea Link (mid)', '19.0311,72.8074', 'Landmark', 'Cable-stay bridge.', TRUE),
    (127, 13, 'Juhu Beach (center)', '19.1024,72.8265', 'Beach', 'Popular western beach.', TRUE),
    (128, 13, 'CSMIA Airport (T2)', '19.0961,72.8747', 'Transport', 'Main Mumbai airport.', TRUE),
    (129, 13, 'Film City (Goregaon)', '19.1643,72.8798', 'Entertainment', 'Studio complex.', TRUE),
    (130, 13, 'Sanjay Gandhi National Park', '19.2169,72.9106', 'Park', 'Northern forest reserve.', TRUE),
    (131, 14, 'Hagia Sophia', '41.0086,28.9802', 'Historic', 'Great mosque museum.', TRUE),
    (132, 14, 'Blue Mosque', '41.0054,28.9768', 'Religious', 'Sultan Ahmed Mosque.', TRUE),
    (133, 14, 'Topkapi Palace (main gate)', '41.0115,28.9833', 'Historic', 'Ottoman palace.', TRUE),
    (134, 14, 'Galata Tower', '41.0257,28.9744', 'Landmark', 'Genoese tower.', TRUE),
    (135, 14, 'Taksim Square', '41.0369,28.9850', 'Plaza', 'Modern city hub.', TRUE),
    (136, 14, 'Grand Bazaar', '41.0106,28.9680', 'Market', 'Historic covered market.', TRUE),
    (137, 14, 'Spice Bazaar', '41.0164,28.9702', 'Market', 'Egyptian Bazaar.', TRUE),
    (138, 14, 'Dolmabahçe Palace', '41.0390,29.0005', 'Historic', 'Bosphorus palace.', TRUE),
    (139, 14, '15 July Martyrs Bridge (mid)', '41.0450,29.0280', 'Landmark', 'Bosphorus bridge.', TRUE),
    (140, 14, 'Istanbul Airport', '41.2621,28.7426', 'Transport', 'IST mega-hub.', TRUE),
    (141, 15, 'Zocalo (Plaza de la Constitucion)', '19.4326,-99.1332', 'Plaza', 'Main square CDMX.', TRUE),
    (142, 15, 'Metropolitan Cathedral', '19.4341,-99.1339', 'Religious', 'Central cathedral.', TRUE),
    (143, 15, 'Palacio de Bellas Artes', '19.4352,-99.1412', 'Cultural', 'Art nouveau theatre.', TRUE),
    (144, 15, 'Ángel de la Independencia', '19.4270,-99.1677', 'Landmark', 'Reform column.', TRUE),
    (145, 15, 'Chapultepec Castle', '19.4204,-99.1819', 'Historic', 'Hilltop museum.', TRUE),
    (146, 15, 'Museo Nacional de Antropología', '19.4250,-99.1860', 'Museum', 'Anthropology flagship.', TRUE),
    (147, 15, 'Basilica of Guadalupe', '19.4840,-99.1180', 'Religious', 'Major pilgrimage site.', TRUE),
    (148, 15, 'Frida Kahlo Museum', '19.3553,-99.1628', 'Museum', 'Casa Azul Coyoacán.', TRUE),
    (149, 15, 'Estadio Azteca', '19.3029,-99.1505', 'Entertainment', 'Legendary stadium.', TRUE),
    (150, 15, 'Mexico City Airport', '19.4361,-99.0719', 'Transport', 'AICM terminal area.', TRUE),
    (151, 16, 'CN Tower', '43.6426,-79.3871', 'Landmark', 'Communications tower.', TRUE),
    (152, 16, 'Rogers Centre', '43.6414,-79.3894', 'Entertainment', 'Retractable roof stadium.', TRUE),
    (153, 16, 'Union Station', '43.6456,-79.3807', 'Transport', 'Historic rail hub.', TRUE),
    (154, 16, 'Nathan Phillips Square', '43.6525,-79.3839', 'Plaza', 'City hall forecourt.', TRUE),
    (155, 16, 'Royal Ontario Museum', '43.6677,-79.3948', 'Museum', 'ROM crystal wing.', TRUE),
    (156, 16, 'Art Gallery of Ontario', '43.6536,-79.3925', 'Museum', 'AGO downtown.', TRUE),
    (157, 16, 'Yonge-Dundas Square', '43.6561,-79.3802', 'Plaza', 'Neon intersection.', TRUE),
    (158, 16, 'High Park (center)', '43.6465,-79.4637', 'Park', 'Large west-end park.', TRUE),
    (159, 16, 'Billy Bishop Airport', '43.6287,-79.3962', 'Transport', 'Island city airport.', TRUE),
    (160, 16, 'Toronto Zoo', '43.8177,-79.1859', 'Entertainment', 'Scarborough wildlife park.', TRUE),
    (161, 17, 'Nelson Mandela Square', '26.1076,28.0567', 'Plaza', 'Sandton public square.', TRUE),
    (162, 17, 'Apartheid Museum', '26.2379,28.0100', 'Museum', 'Apartheid history.', TRUE),
    (163, 17, 'Constitution Hill', '26.1897,28.0422', 'Historic', 'Old fort precinct.', TRUE),
    (164, 17, 'Johannesburg Zoo', '26.1757,28.0374', 'Entertainment', 'Urban zoo.', TRUE),
    (165, 17, 'Carlton Centre', '26.2045,28.0467', 'Landmark', 'Downtown skyscraper.', TRUE),
    (166, 17, 'Gold Reef City', '26.2371,28.0124', 'Entertainment', 'Mine-themed park.', TRUE),
    (167, 17, 'FNB Stadium', '26.2348,27.9826', 'Entertainment', 'Soccer City arena.', TRUE),
    (168, 17, 'OR Tambo Airport', '26.1337,28.2420', 'Transport', 'Main airport.', TRUE),
    (169, 17, 'Wits University (main)', '26.1880,28.0265', 'Education', 'Braamfontein campus.', TRUE),
    (170, 17, 'Maboneng Precinct', '26.2033,28.0617', 'Entertainment', 'Arts district.', TRUE),
    (171, 18, 'Grand Palace', '13.7500,100.4913', 'Historic', 'Royal compound.', TRUE),
    (172, 18, 'Wat Pho', '13.7466,100.4930', 'Religious', 'Reclining Buddha.', TRUE),
    (173, 18, 'Wat Arun', '13.7437,100.4889', 'Religious', 'Temple of Dawn.', TRUE),
    (174, 18, 'Khao San Road (center)', '13.7597,100.4977', 'Entertainment', 'Backpacker strip.', TRUE),
    (175, 18, 'MBK Center', '13.7446,100.5296', 'Shopping', 'MBK mall.', TRUE),
    (176, 18, 'Lumphini Park (center)', '13.7300,100.5410', 'Park', 'Central green lung.', TRUE),
    (177, 18, 'Chatuchak Weekend Market', '13.7996,100.5530', 'Market', 'Vast weekend bazaar.', TRUE),
    (178, 18, 'Victory Monument', '13.7629,100.5383', 'Landmark', 'Traffic circle obelisk.', TRUE),
    (179, 18, 'Suvarnabhumi Airport', '13.6900,100.7501', 'Transport', 'BKK international.', TRUE),
    (180, 18, 'Don Mueang Airport', '13.9126,100.6069', 'Transport', 'Secondary airport.', TRUE),
    (181, 19, 'Marina Bay Sands', '1.2834,103.8607', 'Landmark', 'Sky park resort.', TRUE),
    (182, 19, 'Merlion Park', '1.2868,103.8545', 'Landmark', 'Symbol fountain.', TRUE),
    (183, 19, 'Gardens by the Bay', '1.2816,103.8636', 'Park', 'Supertree Grove.', TRUE),
    (184, 19, 'Orchard Road (central)', '1.3040,103.8318', 'Shopping', 'Retail boulevard.', TRUE),
    (185, 19, 'Singapore Botanic Gardens', '1.3138,103.8159', 'Park', 'UNESCO gardens.', TRUE),
    (186, 19, 'Singapore Zoo', '1.4043,103.7930', 'Entertainment', 'Wildlife park.', TRUE),
    (187, 19, 'Sentosa (Resorts World)', '1.2565,103.8219', 'Entertainment', 'Island resort.', TRUE),
    (188, 19, 'Clarke Quay', '1.2915,103.8463', 'Entertainment', 'Riverside nightlife.', TRUE),
    (189, 19, 'Changi Airport (T3)', '1.3571,103.9886', 'Transport', 'Major hub.', TRUE),
    (190, 19, 'Esplanade Theatres', '1.2894,103.8550', 'Cultural', 'Durian-shaped arts centre.', TRUE),
    (191, 20, 'Burj Khalifa', '25.1972,55.2744', 'Landmark', 'World''s tallest tower.', TRUE),
    (192, 20, 'Dubai Mall', '25.1985,55.2796', 'Shopping', 'Mega retail mall.', TRUE),
    (193, 20, 'Dubai Fountain', '25.1965,55.2759', 'Entertainment', 'Choreographed water show.', TRUE),
    (194, 20, 'Burj Al Arab', '25.1412,55.1853', 'Landmark', 'Sail-shaped hotel.', TRUE),
    (195, 20, 'Palm Jumeirah (Atlantis)', '25.1311,55.1178', 'Entertainment', 'Palm resort tip.', TRUE),
    (196, 20, 'Dubai Marina (center)', '25.0801,55.1402', 'Neighborhood', 'High-rise waterfront.', TRUE),
    (197, 20, 'Mall of the Emirates', '25.1180,55.2007', 'Shopping', 'Ski Dubai mall.', TRUE),
    (198, 20, 'Dubai Creek (Al Seef)', '25.2584,55.3045', 'Historic', 'Heritage waterfront.', TRUE),
    (199, 20, 'Dubai International Airport', '25.2532,55.3657', 'Transport', 'DXB hubs.', TRUE),
    (200, 20, 'Dubai Frame', '25.2371,55.3004', 'Landmark', 'Golden observation frame.', TRUE),
    (201, 21, 'Baha''i Gardens (upper terrace viewpoint)', '32.8150,34.9860', 'Religious', 'Upper terrace views of the terraced gardens.', TRUE),
    (202, 21, 'German Colony (center)', '32.8196,34.9983', 'Historic', 'Templar-era neighborhood center.', TRUE),
    (203, 21, 'Haifa Port (main gate area)', '32.8420,35.0030', 'Landmark', 'Active port and waterfront.', TRUE),
    (204, 21, 'Haifa University', '32.7620,35.0194', 'Education', 'Mount Carmel campus.', TRUE),
    (205, 21, 'Stella Maris Monastery', '32.8300,34.9525', 'Religious', 'Carmelite monastery above the bay.', TRUE),
    (206, 21, 'Haifa Central / HaShmona Railway Station', '32.8125,35.0034', 'Transport', 'Downtown rail hub.', TRUE),
    (207, 21, 'Rambam Health Care Campus', '32.8282,34.9855', 'Hospital', 'Major medical center.', TRUE),
    (208, 21, 'Carmel Center (Merkaz HaCarmel)', '32.8039,34.9879', 'Neighborhood', 'Central Carmel shopping and dining.', TRUE),
    (209, 21, 'Dado Beach (center)', '32.7955,34.9585', 'Beach', 'Popular city beach.', TRUE),
    (210, 21, 'Haifa Bay Mall (Cinemall)', '32.8070,35.0135', 'Shopping', 'Bay-side retail and cinema.', TRUE),
    (211, 22, 'Western Wall (Kotel)', '31.7767,35.2345', 'Religious', 'Holiest Jewish prayer site.', TRUE),
    (212, 22, 'Dome of the Rock', '31.7780,35.2358', 'Religious', 'Iconic Islamic shrine.', TRUE),
    (213, 22, 'Church of the Holy Sepulchre', '31.7784,35.2292', 'Religious', 'Christian holy sites complex.', TRUE),
    (214, 22, 'Jaffa Gate', '31.7762,35.2254', 'Historic', 'Old City western entry.', TRUE),
    (215, 22, 'Damascus Gate', '31.7830,35.2300', 'Historic', 'Old City northern gate.', TRUE),
    (216, 22, 'Mahane Yehuda Market (center)', '31.7840,35.2119', 'Market', 'Famous food market (the Shuk).', TRUE),
    (217, 22, 'Central Bus Station', '31.7902,35.2033', 'Transport', 'Main intercity bus terminal.', TRUE),
    (218, 22, 'Israel Museum (main entrance)', '31.7733,35.2034', 'Museum', 'National collections and campus.', TRUE),
    (219, 22, 'Yad Vashem (main entrance plaza)', '31.7741,35.1755', 'Museum', 'Holocaust remembrance center.', TRUE),
    (220, 22, 'Mount of Olives Viewpoint', '31.7788,35.2432', 'Viewpoint', 'Panoramic Old City views.', TRUE),
    (221, 23, 'Rabin Square', '32.0810,34.7805', 'Plaza', 'Central civic square.', TRUE),
    (222, 23, 'Tel Aviv Museum of Art', '32.0773,34.7894', 'Museum', 'Modern and contemporary art.', TRUE),
    (223, 23, 'Azrieli Center (round tower)', '32.0740,34.7923', 'Landmark', 'Triangular towers complex.', TRUE),
    (224, 23, 'Habima Square', '32.0724,34.7805', 'Cultural', 'National theater precinct.', TRUE),
    (225, 23, 'Carmel Market (Shuk HaCarmel)', '32.0680,34.7687', 'Market', 'Vibrant street market.', TRUE),
    (226, 23, 'Old Tel Aviv Port (Namal)', '32.1006,34.7744', 'Entertainment', 'Restored port promenade.', TRUE),
    (227, 23, 'Charles Clore Park (center)', '32.0635,34.7606', 'Park', 'Waterfront green belt.', TRUE),
    (228, 23, 'Tel Aviv HaShalom Railway Station', '32.0748,34.7944', 'Transport', 'Major Sarona-area station.', TRUE),
    (229, 23, 'Dizengoff Center', '32.0757,34.7749', 'Shopping', 'City-center mall.', TRUE),
    (230, 23, 'Tel Aviv University (main gate)', '32.1133,34.8044', 'Education', 'Ramat Aviv campus entrance.', TRUE),
    (231, 24, 'Netanya Amphitheater / Cliff Promenade (center)', '32.3329,34.8506', 'Entertainment', 'Cliff-top promenade and venue.', TRUE),
    (232, 24, 'Herzl Street Pedestrian Zone', '32.3322,34.8566', 'Street', 'Downtown walking zone.', TRUE),
    (233, 24, 'Netanya Beach Elevator', '32.3313,34.8498', 'Landmark', 'Cliff elevator to beach.', TRUE),
    (234, 24, 'Poleg Beach (center)', '32.2787,34.8308', 'Beach', 'South Netanya beach.', TRUE),
    (235, 24, 'Netanya Railway Station', '32.3169,34.8704', 'Transport', 'Coastal rail stop.', TRUE),
    (236, 24, 'Ir Yamim Mall', '32.2762,34.8428', 'Shopping', 'Southern neighborhood retail.', TRUE),
    (237, 24, 'Independence Square (Kikar HaAtzmaut)', '32.3323,34.8551', 'Plaza', 'Central city square.', TRUE),
    (238, 24, 'Sapir Business and Technology Park', '32.3105,34.8785', 'Business', 'High-tech office zone.', TRUE),
    (239, 24, 'Wingate Institute', '32.2625,34.8384', 'Sports', 'National sports institute landmark.', TRUE),
    (240, 24, 'Lagoon Beach', '32.3132,34.8427', 'Beach', 'Protected lagoon swimming.', TRUE),
    (241, 25, 'Old City of Akko (central square)', '32.9211,35.0703', 'Historic', 'Crusader-era urban core.', TRUE),
    (242, 25, 'Akko Port', '32.9201,35.0709', 'Landmark', 'Historic fishing and marina port.', TRUE),
    (243, 25, 'Knights'' Halls (Hospitaller Fortress entrance)', '32.9219,35.0717', 'Historic', 'Crusader fortress complex.', TRUE),
    (244, 25, 'Al-Jazzar Mosque', '32.9218,35.0711', 'Religious', 'Ottoman-era mosque.', TRUE),
    (245, 25, 'Akko city walls', '32.9226,35.0697', 'Historic', 'Fortified sea and land walls.', TRUE),
    (246, 25, 'Akko Railway Station', '32.9233,35.0814', 'Transport', 'Rail access to the city.', TRUE),
    (247, 25, 'Akko Beach', '32.9187,35.0675', 'Beach', 'Mediterranean city beach.', TRUE),
    (248, 25, 'Tunisian Synagogue (Or Torah)', '32.9282,35.0761', 'Religious', 'Ornate synagogue (Or Torah).', TRUE),
    (249, 25, 'Acre Lighthouse', '32.9192,35.0688', 'Landmark', 'Harbor navigation beacon.', TRUE),
    (250, 25, 'Treasures in the Walls Museum', '32.9240,35.0703', 'Museum', 'Museum in historic ramparts.', TRUE);
ALTER TABLE pois AUTO_INCREMENT = 251;

SET SQL_SAFE_UPDATES = 0;
UPDATE pois
SET latitude = CAST(TRIM(SUBSTRING_INDEX(location, ',', 1)) AS DECIMAL(10,6)),
    longitude = CAST(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(location, ',', 2), ',', -1)) AS DECIMAL(10,6))
WHERE location REGEXP '^-?[0-9]+[.]?[0-9]*,[ ]*-?[0-9]+[.]?[0-9]*$';
SET SQL_SAFE_UPDATES = 1;

-- Map A: POIs 1-5 per city; Map B: POIs 6-10; Map C (ids 51-75): same POIs 1-5 as A on a normal (non-tour) map
INSERT INTO map_pois (map_id, poi_id, display_order, approved, linked_by_user_id) VALUES
    (1, 1, 1, 1, NULL),
    (1, 2, 2, 1, NULL),
    (1, 3, 3, 1, NULL),
    (1, 4, 4, 1, NULL),
    (1, 5, 5, 1, NULL),
    (2, 6, 1, 1, NULL),
    (2, 7, 2, 1, NULL),
    (2, 8, 3, 1, NULL),
    (2, 9, 4, 1, NULL),
    (2, 10, 5, 1, NULL),
    (3, 11, 1, 1, NULL),
    (3, 12, 2, 1, NULL),
    (3, 13, 3, 1, NULL),
    (3, 14, 4, 1, NULL),
    (3, 15, 5, 1, NULL),
    (4, 16, 1, 1, NULL),
    (4, 17, 2, 1, NULL),
    (4, 18, 3, 1, NULL),
    (4, 19, 4, 1, NULL),
    (4, 20, 5, 1, NULL),
    (5, 21, 1, 1, NULL),
    (5, 22, 2, 1, NULL),
    (5, 23, 3, 1, NULL),
    (5, 24, 4, 1, NULL),
    (5, 25, 5, 1, NULL),
    (6, 26, 1, 1, NULL),
    (6, 27, 2, 1, NULL),
    (6, 28, 3, 1, NULL),
    (6, 29, 4, 1, NULL),
    (6, 30, 5, 1, NULL),
    (7, 31, 1, 1, NULL),
    (7, 32, 2, 1, NULL),
    (7, 33, 3, 1, NULL),
    (7, 34, 4, 1, NULL),
    (7, 35, 5, 1, NULL),
    (8, 36, 1, 1, NULL),
    (8, 37, 2, 1, NULL),
    (8, 38, 3, 1, NULL),
    (8, 39, 4, 1, NULL),
    (8, 40, 5, 1, NULL),
    (9, 41, 1, 1, NULL),
    (9, 42, 2, 1, NULL),
    (9, 43, 3, 1, NULL),
    (9, 44, 4, 1, NULL),
    (9, 45, 5, 1, NULL),
    (10, 46, 1, 1, NULL),
    (10, 47, 2, 1, NULL),
    (10, 48, 3, 1, NULL),
    (10, 49, 4, 1, NULL),
    (10, 50, 5, 1, NULL),
    (11, 51, 1, 1, NULL),
    (11, 52, 2, 1, NULL),
    (11, 53, 3, 1, NULL),
    (11, 54, 4, 1, NULL),
    (11, 55, 5, 1, NULL),
    (12, 56, 1, 1, NULL),
    (12, 57, 2, 1, NULL),
    (12, 58, 3, 1, NULL),
    (12, 59, 4, 1, NULL),
    (12, 60, 5, 1, NULL),
    (13, 61, 1, 1, NULL),
    (13, 62, 2, 1, NULL),
    (13, 63, 3, 1, NULL),
    (13, 64, 4, 1, NULL),
    (13, 65, 5, 1, NULL),
    (14, 66, 1, 1, NULL),
    (14, 67, 2, 1, NULL),
    (14, 68, 3, 1, NULL),
    (14, 69, 4, 1, NULL),
    (14, 70, 5, 1, NULL),
    (15, 71, 1, 1, NULL),
    (15, 72, 2, 1, NULL),
    (15, 73, 3, 1, NULL),
    (15, 74, 4, 1, NULL),
    (15, 75, 5, 1, NULL),
    (16, 76, 1, 1, NULL),
    (16, 77, 2, 1, NULL),
    (16, 78, 3, 1, NULL),
    (16, 79, 4, 1, NULL),
    (16, 80, 5, 1, NULL),
    (17, 81, 1, 1, NULL),
    (17, 82, 2, 1, NULL),
    (17, 83, 3, 1, NULL),
    (17, 84, 4, 1, NULL),
    (17, 85, 5, 1, NULL),
    (18, 86, 1, 1, NULL),
    (18, 87, 2, 1, NULL),
    (18, 88, 3, 1, NULL),
    (18, 89, 4, 1, NULL),
    (18, 90, 5, 1, NULL),
    (19, 91, 1, 1, NULL),
    (19, 92, 2, 1, NULL),
    (19, 93, 3, 1, NULL),
    (19, 94, 4, 1, NULL),
    (19, 95, 5, 1, NULL),
    (20, 96, 1, 1, NULL),
    (20, 97, 2, 1, NULL),
    (20, 98, 3, 1, NULL),
    (20, 99, 4, 1, NULL),
    (20, 100, 5, 1, NULL),
    (21, 101, 1, 1, NULL),
    (21, 102, 2, 1, NULL),
    (21, 103, 3, 1, NULL),
    (21, 104, 4, 1, NULL),
    (21, 105, 5, 1, NULL),
    (22, 106, 1, 1, NULL),
    (22, 107, 2, 1, NULL),
    (22, 108, 3, 1, NULL),
    (22, 109, 4, 1, NULL),
    (22, 110, 5, 1, NULL),
    (23, 111, 1, 1, NULL),
    (23, 112, 2, 1, NULL),
    (23, 113, 3, 1, NULL),
    (23, 114, 4, 1, NULL),
    (23, 115, 5, 1, NULL),
    (24, 116, 1, 1, NULL),
    (24, 117, 2, 1, NULL),
    (24, 118, 3, 1, NULL),
    (24, 119, 4, 1, NULL),
    (24, 120, 5, 1, NULL),
    (25, 121, 1, 1, NULL),
    (25, 122, 2, 1, NULL),
    (25, 123, 3, 1, NULL),
    (25, 124, 4, 1, NULL),
    (25, 125, 5, 1, NULL),
    (26, 126, 1, 1, NULL),
    (26, 127, 2, 1, NULL),
    (26, 128, 3, 1, NULL),
    (26, 129, 4, 1, NULL),
    (26, 130, 5, 1, NULL),
    (27, 131, 1, 1, NULL),
    (27, 132, 2, 1, NULL),
    (27, 133, 3, 1, NULL),
    (27, 134, 4, 1, NULL),
    (27, 135, 5, 1, NULL),
    (28, 136, 1, 1, NULL),
    (28, 137, 2, 1, NULL),
    (28, 138, 3, 1, NULL),
    (28, 139, 4, 1, NULL),
    (28, 140, 5, 1, NULL),
    (29, 141, 1, 1, NULL),
    (29, 142, 2, 1, NULL),
    (29, 143, 3, 1, NULL),
    (29, 144, 4, 1, NULL),
    (29, 145, 5, 1, NULL),
    (30, 146, 1, 1, NULL),
    (30, 147, 2, 1, NULL),
    (30, 148, 3, 1, NULL),
    (30, 149, 4, 1, NULL),
    (30, 150, 5, 1, NULL),
    (31, 151, 1, 1, NULL),
    (31, 152, 2, 1, NULL),
    (31, 153, 3, 1, NULL),
    (31, 154, 4, 1, NULL),
    (31, 155, 5, 1, NULL),
    (32, 156, 1, 1, NULL),
    (32, 157, 2, 1, NULL),
    (32, 158, 3, 1, NULL),
    (32, 159, 4, 1, NULL),
    (32, 160, 5, 1, NULL),
    (33, 161, 1, 1, NULL),
    (33, 162, 2, 1, NULL),
    (33, 163, 3, 1, NULL),
    (33, 164, 4, 1, NULL),
    (33, 165, 5, 1, NULL),
    (34, 166, 1, 1, NULL),
    (34, 167, 2, 1, NULL),
    (34, 168, 3, 1, NULL),
    (34, 169, 4, 1, NULL),
    (34, 170, 5, 1, NULL),
    (35, 171, 1, 1, NULL),
    (35, 172, 2, 1, NULL),
    (35, 173, 3, 1, NULL),
    (35, 174, 4, 1, NULL),
    (35, 175, 5, 1, NULL),
    (36, 176, 1, 1, NULL),
    (36, 177, 2, 1, NULL),
    (36, 178, 3, 1, NULL),
    (36, 179, 4, 1, NULL),
    (36, 180, 5, 1, NULL),
    (37, 181, 1, 1, NULL),
    (37, 182, 2, 1, NULL),
    (37, 183, 3, 1, NULL),
    (37, 184, 4, 1, NULL),
    (37, 185, 5, 1, NULL),
    (38, 186, 1, 1, NULL),
    (38, 187, 2, 1, NULL),
    (38, 188, 3, 1, NULL),
    (38, 189, 4, 1, NULL),
    (38, 190, 5, 1, NULL),
    (39, 191, 1, 1, NULL),
    (39, 192, 2, 1, NULL),
    (39, 193, 3, 1, NULL),
    (39, 194, 4, 1, NULL),
    (39, 195, 5, 1, NULL),
    (40, 196, 1, 1, NULL),
    (40, 197, 2, 1, NULL),
    (40, 198, 3, 1, NULL),
    (40, 199, 4, 1, NULL),
    (40, 200, 5, 1, NULL),
    (41, 201, 1, 1, NULL),
    (41, 202, 2, 1, NULL),
    (41, 203, 3, 1, NULL),
    (41, 204, 4, 1, NULL),
    (41, 205, 5, 1, NULL),
    (42, 206, 1, 1, NULL),
    (42, 207, 2, 1, NULL),
    (42, 208, 3, 1, NULL),
    (42, 209, 4, 1, NULL),
    (42, 210, 5, 1, NULL),
    (43, 211, 1, 1, NULL),
    (43, 212, 2, 1, NULL),
    (43, 213, 3, 1, NULL),
    (43, 214, 4, 1, NULL),
    (43, 215, 5, 1, NULL),
    (44, 216, 1, 1, NULL),
    (44, 217, 2, 1, NULL),
    (44, 218, 3, 1, NULL),
    (44, 219, 4, 1, NULL),
    (44, 220, 5, 1, NULL),
    (45, 221, 1, 1, NULL),
    (45, 222, 2, 1, NULL),
    (45, 223, 3, 1, NULL),
    (45, 224, 4, 1, NULL),
    (45, 225, 5, 1, NULL),
    (46, 226, 1, 1, NULL),
    (46, 227, 2, 1, NULL),
    (46, 228, 3, 1, NULL),
    (46, 229, 4, 1, NULL),
    (46, 230, 5, 1, NULL),
    (47, 231, 1, 1, NULL),
    (47, 232, 2, 1, NULL),
    (47, 233, 3, 1, NULL),
    (47, 234, 4, 1, NULL),
    (47, 235, 5, 1, NULL),
    (48, 236, 1, 1, NULL),
    (48, 237, 2, 1, NULL),
    (48, 238, 3, 1, NULL),
    (48, 239, 4, 1, NULL),
    (48, 240, 5, 1, NULL),
    (49, 241, 1, 1, NULL),
    (49, 242, 2, 1, NULL),
    (49, 243, 3, 1, NULL),
    (49, 244, 4, 1, NULL),
    (49, 245, 5, 1, NULL),
    (50, 246, 1, 1, NULL),
    (50, 247, 2, 1, NULL),
    (50, 248, 3, 1, NULL),
    (50, 249, 4, 1, NULL),
    (50, 250, 5, 1, NULL),
    (51, 1, 1, 1, NULL),
    (51, 2, 2, 1, NULL),
    (51, 3, 3, 1, NULL),
    (51, 4, 4, 1, NULL),
    (51, 5, 5, 1, NULL),
    (52, 11, 1, 1, NULL),
    (52, 12, 2, 1, NULL),
    (52, 13, 3, 1, NULL),
    (52, 14, 4, 1, NULL),
    (52, 15, 5, 1, NULL),
    (53, 21, 1, 1, NULL),
    (53, 22, 2, 1, NULL),
    (53, 23, 3, 1, NULL),
    (53, 24, 4, 1, NULL),
    (53, 25, 5, 1, NULL),
    (54, 31, 1, 1, NULL),
    (54, 32, 2, 1, NULL),
    (54, 33, 3, 1, NULL),
    (54, 34, 4, 1, NULL),
    (54, 35, 5, 1, NULL),
    (55, 41, 1, 1, NULL),
    (55, 42, 2, 1, NULL),
    (55, 43, 3, 1, NULL),
    (55, 44, 4, 1, NULL),
    (55, 45, 5, 1, NULL),
    (56, 51, 1, 1, NULL),
    (56, 52, 2, 1, NULL),
    (56, 53, 3, 1, NULL),
    (56, 54, 4, 1, NULL),
    (56, 55, 5, 1, NULL),
    (57, 61, 1, 1, NULL),
    (57, 62, 2, 1, NULL),
    (57, 63, 3, 1, NULL),
    (57, 64, 4, 1, NULL),
    (57, 65, 5, 1, NULL),
    (58, 71, 1, 1, NULL),
    (58, 72, 2, 1, NULL),
    (58, 73, 3, 1, NULL),
    (58, 74, 4, 1, NULL),
    (58, 75, 5, 1, NULL),
    (59, 81, 1, 1, NULL),
    (59, 82, 2, 1, NULL),
    (59, 83, 3, 1, NULL),
    (59, 84, 4, 1, NULL),
    (59, 85, 5, 1, NULL),
    (60, 91, 1, 1, NULL),
    (60, 92, 2, 1, NULL),
    (60, 93, 3, 1, NULL),
    (60, 94, 4, 1, NULL),
    (60, 95, 5, 1, NULL),
    (61, 101, 1, 1, NULL),
    (61, 102, 2, 1, NULL),
    (61, 103, 3, 1, NULL),
    (61, 104, 4, 1, NULL),
    (61, 105, 5, 1, NULL),
    (62, 111, 1, 1, NULL),
    (62, 112, 2, 1, NULL),
    (62, 113, 3, 1, NULL),
    (62, 114, 4, 1, NULL),
    (62, 115, 5, 1, NULL),
    (63, 121, 1, 1, NULL),
    (63, 122, 2, 1, NULL),
    (63, 123, 3, 1, NULL),
    (63, 124, 4, 1, NULL),
    (63, 125, 5, 1, NULL),
    (64, 131, 1, 1, NULL),
    (64, 132, 2, 1, NULL),
    (64, 133, 3, 1, NULL),
    (64, 134, 4, 1, NULL),
    (64, 135, 5, 1, NULL),
    (65, 141, 1, 1, NULL),
    (65, 142, 2, 1, NULL),
    (65, 143, 3, 1, NULL),
    (65, 144, 4, 1, NULL),
    (65, 145, 5, 1, NULL),
    (66, 151, 1, 1, NULL),
    (66, 152, 2, 1, NULL),
    (66, 153, 3, 1, NULL),
    (66, 154, 4, 1, NULL),
    (66, 155, 5, 1, NULL),
    (67, 161, 1, 1, NULL),
    (67, 162, 2, 1, NULL),
    (67, 163, 3, 1, NULL),
    (67, 164, 4, 1, NULL),
    (67, 165, 5, 1, NULL),
    (68, 171, 1, 1, NULL),
    (68, 172, 2, 1, NULL),
    (68, 173, 3, 1, NULL),
    (68, 174, 4, 1, NULL),
    (68, 175, 5, 1, NULL),
    (69, 181, 1, 1, NULL),
    (69, 182, 2, 1, NULL),
    (69, 183, 3, 1, NULL),
    (69, 184, 4, 1, NULL),
    (69, 185, 5, 1, NULL),
    (70, 191, 1, 1, NULL),
    (70, 192, 2, 1, NULL),
    (70, 193, 3, 1, NULL),
    (70, 194, 4, 1, NULL),
    (70, 195, 5, 1, NULL),
    (71, 201, 1, 1, NULL),
    (71, 202, 2, 1, NULL),
    (71, 203, 3, 1, NULL),
    (71, 204, 4, 1, NULL),
    (71, 205, 5, 1, NULL),
    (72, 211, 1, 1, NULL),
    (72, 212, 2, 1, NULL),
    (72, 213, 3, 1, NULL),
    (72, 214, 4, 1, NULL),
    (72, 215, 5, 1, NULL),
    (73, 221, 1, 1, NULL),
    (73, 222, 2, 1, NULL),
    (73, 223, 3, 1, NULL),
    (73, 224, 4, 1, NULL),
    (73, 225, 5, 1, NULL),
    (74, 231, 1, 1, NULL),
    (74, 232, 2, 1, NULL),
    (74, 233, 3, 1, NULL),
    (74, 234, 4, 1, NULL),
    (74, 235, 5, 1, NULL),
    (75, 241, 1, 1, NULL),
    (75, 242, 2, 1, NULL),
    (75, 243, 3, 1, NULL),
    (75, 244, 4, 1, NULL),
    (75, 245, 5, 1, NULL);

-- Tour order mixes both maps: POI 1,6,2,7,3,8,4,9,5,10 (randomized-style interleaving)
INSERT INTO tour_stops (tour_id, poi_id, stop_order, notes) VALUES
    (1, 1, 1, 'Stop 1 - mixed map sequence'),
    (1, 6, 2, 'Stop 2 - mixed map sequence'),
    (1, 2, 3, 'Stop 3 - mixed map sequence'),
    (1, 7, 4, 'Stop 4 - mixed map sequence'),
    (1, 3, 5, 'Stop 5 - mixed map sequence'),
    (1, 8, 6, 'Stop 6 - mixed map sequence'),
    (1, 4, 7, 'Stop 7 - mixed map sequence'),
    (1, 9, 8, 'Stop 8 - mixed map sequence'),
    (1, 5, 9, 'Stop 9 - mixed map sequence'),
    (1, 10, 10, 'Stop 10 - mixed map sequence'),
    (2, 11, 1, 'Stop 1 - mixed map sequence'),
    (2, 16, 2, 'Stop 2 - mixed map sequence'),
    (2, 12, 3, 'Stop 3 - mixed map sequence'),
    (2, 17, 4, 'Stop 4 - mixed map sequence'),
    (2, 13, 5, 'Stop 5 - mixed map sequence'),
    (2, 18, 6, 'Stop 6 - mixed map sequence'),
    (2, 14, 7, 'Stop 7 - mixed map sequence'),
    (2, 19, 8, 'Stop 8 - mixed map sequence'),
    (2, 15, 9, 'Stop 9 - mixed map sequence'),
    (2, 20, 10, 'Stop 10 - mixed map sequence'),
    (3, 21, 1, 'Stop 1 - mixed map sequence'),
    (3, 26, 2, 'Stop 2 - mixed map sequence'),
    (3, 22, 3, 'Stop 3 - mixed map sequence'),
    (3, 27, 4, 'Stop 4 - mixed map sequence'),
    (3, 23, 5, 'Stop 5 - mixed map sequence'),
    (3, 28, 6, 'Stop 6 - mixed map sequence'),
    (3, 24, 7, 'Stop 7 - mixed map sequence'),
    (3, 29, 8, 'Stop 8 - mixed map sequence'),
    (3, 25, 9, 'Stop 9 - mixed map sequence'),
    (3, 30, 10, 'Stop 10 - mixed map sequence'),
    (4, 31, 1, 'Stop 1 - mixed map sequence'),
    (4, 36, 2, 'Stop 2 - mixed map sequence'),
    (4, 32, 3, 'Stop 3 - mixed map sequence'),
    (4, 37, 4, 'Stop 4 - mixed map sequence'),
    (4, 33, 5, 'Stop 5 - mixed map sequence'),
    (4, 38, 6, 'Stop 6 - mixed map sequence'),
    (4, 34, 7, 'Stop 7 - mixed map sequence'),
    (4, 39, 8, 'Stop 8 - mixed map sequence'),
    (4, 35, 9, 'Stop 9 - mixed map sequence'),
    (4, 40, 10, 'Stop 10 - mixed map sequence'),
    (5, 41, 1, 'Stop 1 - mixed map sequence'),
    (5, 46, 2, 'Stop 2 - mixed map sequence'),
    (5, 42, 3, 'Stop 3 - mixed map sequence'),
    (5, 47, 4, 'Stop 4 - mixed map sequence'),
    (5, 43, 5, 'Stop 5 - mixed map sequence'),
    (5, 48, 6, 'Stop 6 - mixed map sequence'),
    (5, 44, 7, 'Stop 7 - mixed map sequence'),
    (5, 49, 8, 'Stop 8 - mixed map sequence'),
    (5, 45, 9, 'Stop 9 - mixed map sequence'),
    (5, 50, 10, 'Stop 10 - mixed map sequence'),
    (6, 51, 1, 'Stop 1 - mixed map sequence'),
    (6, 56, 2, 'Stop 2 - mixed map sequence'),
    (6, 52, 3, 'Stop 3 - mixed map sequence'),
    (6, 57, 4, 'Stop 4 - mixed map sequence'),
    (6, 53, 5, 'Stop 5 - mixed map sequence'),
    (6, 58, 6, 'Stop 6 - mixed map sequence'),
    (6, 54, 7, 'Stop 7 - mixed map sequence'),
    (6, 59, 8, 'Stop 8 - mixed map sequence'),
    (6, 55, 9, 'Stop 9 - mixed map sequence'),
    (6, 60, 10, 'Stop 10 - mixed map sequence'),
    (7, 61, 1, 'Stop 1 - mixed map sequence'),
    (7, 66, 2, 'Stop 2 - mixed map sequence'),
    (7, 62, 3, 'Stop 3 - mixed map sequence'),
    (7, 67, 4, 'Stop 4 - mixed map sequence'),
    (7, 63, 5, 'Stop 5 - mixed map sequence'),
    (7, 68, 6, 'Stop 6 - mixed map sequence'),
    (7, 64, 7, 'Stop 7 - mixed map sequence'),
    (7, 69, 8, 'Stop 8 - mixed map sequence'),
    (7, 65, 9, 'Stop 9 - mixed map sequence'),
    (7, 70, 10, 'Stop 10 - mixed map sequence'),
    (8, 71, 1, 'Stop 1 - mixed map sequence'),
    (8, 76, 2, 'Stop 2 - mixed map sequence'),
    (8, 72, 3, 'Stop 3 - mixed map sequence'),
    (8, 77, 4, 'Stop 4 - mixed map sequence'),
    (8, 73, 5, 'Stop 5 - mixed map sequence'),
    (8, 78, 6, 'Stop 6 - mixed map sequence'),
    (8, 74, 7, 'Stop 7 - mixed map sequence'),
    (8, 79, 8, 'Stop 8 - mixed map sequence'),
    (8, 75, 9, 'Stop 9 - mixed map sequence'),
    (8, 80, 10, 'Stop 10 - mixed map sequence'),
    (9, 81, 1, 'Stop 1 - mixed map sequence'),
    (9, 86, 2, 'Stop 2 - mixed map sequence'),
    (9, 82, 3, 'Stop 3 - mixed map sequence'),
    (9, 87, 4, 'Stop 4 - mixed map sequence'),
    (9, 83, 5, 'Stop 5 - mixed map sequence'),
    (9, 88, 6, 'Stop 6 - mixed map sequence'),
    (9, 84, 7, 'Stop 7 - mixed map sequence'),
    (9, 89, 8, 'Stop 8 - mixed map sequence'),
    (9, 85, 9, 'Stop 9 - mixed map sequence'),
    (9, 90, 10, 'Stop 10 - mixed map sequence'),
    (10, 91, 1, 'Stop 1 - mixed map sequence'),
    (10, 96, 2, 'Stop 2 - mixed map sequence'),
    (10, 92, 3, 'Stop 3 - mixed map sequence'),
    (10, 97, 4, 'Stop 4 - mixed map sequence'),
    (10, 93, 5, 'Stop 5 - mixed map sequence'),
    (10, 98, 6, 'Stop 6 - mixed map sequence'),
    (10, 94, 7, 'Stop 7 - mixed map sequence'),
    (10, 99, 8, 'Stop 8 - mixed map sequence'),
    (10, 95, 9, 'Stop 9 - mixed map sequence'),
    (10, 100, 10, 'Stop 10 - mixed map sequence'),
    (11, 101, 1, 'Stop 1 - mixed map sequence'),
    (11, 106, 2, 'Stop 2 - mixed map sequence'),
    (11, 102, 3, 'Stop 3 - mixed map sequence'),
    (11, 107, 4, 'Stop 4 - mixed map sequence'),
    (11, 103, 5, 'Stop 5 - mixed map sequence'),
    (11, 108, 6, 'Stop 6 - mixed map sequence'),
    (11, 104, 7, 'Stop 7 - mixed map sequence'),
    (11, 109, 8, 'Stop 8 - mixed map sequence'),
    (11, 105, 9, 'Stop 9 - mixed map sequence'),
    (11, 110, 10, 'Stop 10 - mixed map sequence'),
    (12, 111, 1, 'Stop 1 - mixed map sequence'),
    (12, 116, 2, 'Stop 2 - mixed map sequence'),
    (12, 112, 3, 'Stop 3 - mixed map sequence'),
    (12, 117, 4, 'Stop 4 - mixed map sequence'),
    (12, 113, 5, 'Stop 5 - mixed map sequence'),
    (12, 118, 6, 'Stop 6 - mixed map sequence'),
    (12, 114, 7, 'Stop 7 - mixed map sequence'),
    (12, 119, 8, 'Stop 8 - mixed map sequence'),
    (12, 115, 9, 'Stop 9 - mixed map sequence'),
    (12, 120, 10, 'Stop 10 - mixed map sequence'),
    (13, 121, 1, 'Stop 1 - mixed map sequence'),
    (13, 126, 2, 'Stop 2 - mixed map sequence'),
    (13, 122, 3, 'Stop 3 - mixed map sequence'),
    (13, 127, 4, 'Stop 4 - mixed map sequence'),
    (13, 123, 5, 'Stop 5 - mixed map sequence'),
    (13, 128, 6, 'Stop 6 - mixed map sequence'),
    (13, 124, 7, 'Stop 7 - mixed map sequence'),
    (13, 129, 8, 'Stop 8 - mixed map sequence'),
    (13, 125, 9, 'Stop 9 - mixed map sequence'),
    (13, 130, 10, 'Stop 10 - mixed map sequence'),
    (14, 131, 1, 'Stop 1 - mixed map sequence'),
    (14, 136, 2, 'Stop 2 - mixed map sequence'),
    (14, 132, 3, 'Stop 3 - mixed map sequence'),
    (14, 137, 4, 'Stop 4 - mixed map sequence'),
    (14, 133, 5, 'Stop 5 - mixed map sequence'),
    (14, 138, 6, 'Stop 6 - mixed map sequence'),
    (14, 134, 7, 'Stop 7 - mixed map sequence'),
    (14, 139, 8, 'Stop 8 - mixed map sequence'),
    (14, 135, 9, 'Stop 9 - mixed map sequence'),
    (14, 140, 10, 'Stop 10 - mixed map sequence'),
    (15, 141, 1, 'Stop 1 - mixed map sequence'),
    (15, 146, 2, 'Stop 2 - mixed map sequence'),
    (15, 142, 3, 'Stop 3 - mixed map sequence'),
    (15, 147, 4, 'Stop 4 - mixed map sequence'),
    (15, 143, 5, 'Stop 5 - mixed map sequence'),
    (15, 148, 6, 'Stop 6 - mixed map sequence'),
    (15, 144, 7, 'Stop 7 - mixed map sequence'),
    (15, 149, 8, 'Stop 8 - mixed map sequence'),
    (15, 145, 9, 'Stop 9 - mixed map sequence'),
    (15, 150, 10, 'Stop 10 - mixed map sequence'),
    (16, 151, 1, 'Stop 1 - mixed map sequence'),
    (16, 156, 2, 'Stop 2 - mixed map sequence'),
    (16, 152, 3, 'Stop 3 - mixed map sequence'),
    (16, 157, 4, 'Stop 4 - mixed map sequence'),
    (16, 153, 5, 'Stop 5 - mixed map sequence'),
    (16, 158, 6, 'Stop 6 - mixed map sequence'),
    (16, 154, 7, 'Stop 7 - mixed map sequence'),
    (16, 159, 8, 'Stop 8 - mixed map sequence'),
    (16, 155, 9, 'Stop 9 - mixed map sequence'),
    (16, 160, 10, 'Stop 10 - mixed map sequence'),
    (17, 161, 1, 'Stop 1 - mixed map sequence'),
    (17, 166, 2, 'Stop 2 - mixed map sequence'),
    (17, 162, 3, 'Stop 3 - mixed map sequence'),
    (17, 167, 4, 'Stop 4 - mixed map sequence'),
    (17, 163, 5, 'Stop 5 - mixed map sequence'),
    (17, 168, 6, 'Stop 6 - mixed map sequence'),
    (17, 164, 7, 'Stop 7 - mixed map sequence'),
    (17, 169, 8, 'Stop 8 - mixed map sequence'),
    (17, 165, 9, 'Stop 9 - mixed map sequence'),
    (17, 170, 10, 'Stop 10 - mixed map sequence'),
    (18, 171, 1, 'Stop 1 - mixed map sequence'),
    (18, 176, 2, 'Stop 2 - mixed map sequence'),
    (18, 172, 3, 'Stop 3 - mixed map sequence'),
    (18, 177, 4, 'Stop 4 - mixed map sequence'),
    (18, 173, 5, 'Stop 5 - mixed map sequence'),
    (18, 178, 6, 'Stop 6 - mixed map sequence'),
    (18, 174, 7, 'Stop 7 - mixed map sequence'),
    (18, 179, 8, 'Stop 8 - mixed map sequence'),
    (18, 175, 9, 'Stop 9 - mixed map sequence'),
    (18, 180, 10, 'Stop 10 - mixed map sequence'),
    (19, 181, 1, 'Stop 1 - mixed map sequence'),
    (19, 186, 2, 'Stop 2 - mixed map sequence'),
    (19, 182, 3, 'Stop 3 - mixed map sequence'),
    (19, 187, 4, 'Stop 4 - mixed map sequence'),
    (19, 183, 5, 'Stop 5 - mixed map sequence'),
    (19, 188, 6, 'Stop 6 - mixed map sequence'),
    (19, 184, 7, 'Stop 7 - mixed map sequence'),
    (19, 189, 8, 'Stop 8 - mixed map sequence'),
    (19, 185, 9, 'Stop 9 - mixed map sequence'),
    (19, 190, 10, 'Stop 10 - mixed map sequence'),
    (20, 191, 1, 'Stop 1 - mixed map sequence'),
    (20, 196, 2, 'Stop 2 - mixed map sequence'),
    (20, 192, 3, 'Stop 3 - mixed map sequence'),
    (20, 197, 4, 'Stop 4 - mixed map sequence'),
    (20, 193, 5, 'Stop 5 - mixed map sequence'),
    (20, 198, 6, 'Stop 6 - mixed map sequence'),
    (20, 194, 7, 'Stop 7 - mixed map sequence'),
    (20, 199, 8, 'Stop 8 - mixed map sequence'),
    (20, 195, 9, 'Stop 9 - mixed map sequence'),
    (20, 200, 10, 'Stop 10 - mixed map sequence'),
    (21, 201, 1, 'Stop 1 - mixed map sequence'),
    (21, 206, 2, 'Stop 2 - mixed map sequence'),
    (21, 202, 3, 'Stop 3 - mixed map sequence'),
    (21, 207, 4, 'Stop 4 - mixed map sequence'),
    (21, 203, 5, 'Stop 5 - mixed map sequence'),
    (21, 208, 6, 'Stop 6 - mixed map sequence'),
    (21, 204, 7, 'Stop 7 - mixed map sequence'),
    (21, 209, 8, 'Stop 8 - mixed map sequence'),
    (21, 205, 9, 'Stop 9 - mixed map sequence'),
    (21, 210, 10, 'Stop 10 - mixed map sequence'),
    (22, 211, 1, 'Stop 1 - mixed map sequence'),
    (22, 216, 2, 'Stop 2 - mixed map sequence'),
    (22, 212, 3, 'Stop 3 - mixed map sequence'),
    (22, 217, 4, 'Stop 4 - mixed map sequence'),
    (22, 213, 5, 'Stop 5 - mixed map sequence'),
    (22, 218, 6, 'Stop 6 - mixed map sequence'),
    (22, 214, 7, 'Stop 7 - mixed map sequence'),
    (22, 219, 8, 'Stop 8 - mixed map sequence'),
    (22, 215, 9, 'Stop 9 - mixed map sequence'),
    (22, 220, 10, 'Stop 10 - mixed map sequence'),
    (23, 221, 1, 'Stop 1 - mixed map sequence'),
    (23, 226, 2, 'Stop 2 - mixed map sequence'),
    (23, 222, 3, 'Stop 3 - mixed map sequence'),
    (23, 227, 4, 'Stop 4 - mixed map sequence'),
    (23, 223, 5, 'Stop 5 - mixed map sequence'),
    (23, 228, 6, 'Stop 6 - mixed map sequence'),
    (23, 224, 7, 'Stop 7 - mixed map sequence'),
    (23, 229, 8, 'Stop 8 - mixed map sequence'),
    (23, 225, 9, 'Stop 9 - mixed map sequence'),
    (23, 230, 10, 'Stop 10 - mixed map sequence'),
    (24, 231, 1, 'Stop 1 - mixed map sequence'),
    (24, 236, 2, 'Stop 2 - mixed map sequence'),
    (24, 232, 3, 'Stop 3 - mixed map sequence'),
    (24, 237, 4, 'Stop 4 - mixed map sequence'),
    (24, 233, 5, 'Stop 5 - mixed map sequence'),
    (24, 238, 6, 'Stop 6 - mixed map sequence'),
    (24, 234, 7, 'Stop 7 - mixed map sequence'),
    (24, 239, 8, 'Stop 8 - mixed map sequence'),
    (24, 235, 9, 'Stop 9 - mixed map sequence'),
    (24, 240, 10, 'Stop 10 - mixed map sequence'),
    (25, 241, 1, 'Stop 1 - mixed map sequence'),
    (25, 246, 2, 'Stop 2 - mixed map sequence'),
    (25, 242, 3, 'Stop 3 - mixed map sequence'),
    (25, 247, 4, 'Stop 4 - mixed map sequence'),
    (25, 243, 5, 'Stop 5 - mixed map sequence'),
    (25, 248, 6, 'Stop 6 - mixed map sequence'),
    (25, 244, 7, 'Stop 7 - mixed map sequence'),
    (25, 249, 8, 'Stop 8 - mixed map sequence'),
    (25, 245, 9, 'Stop 9 - mixed map sequence'),
    (25, 250, 10, 'Stop 10 - mixed map sequence');

-- ============================================================
-- TEST USERS with SIMPLE passwords for easy testing
-- Username = role name, Password = 1234
-- ============================================================
INSERT INTO users (username, email, password_hash, role, is_active)
VALUES (
        'customer1',
        'customer1@gcm.com',
        '1234',
        'CUSTOMER',
        TRUE
    ),
    (
        'employee1',
        'employee1@gcm.com',
        '1234',
        'CONTENT_EDITOR',
        TRUE
    ),
    (
        'manager1',
        'manager1@gcm.com',
        '1234',
        'CONTENT_MANAGER',
        TRUE
    );
-- Customer record for 'customer1' user (id=1)
INSERT INTO customers (user_id, payment_token, card_last4)
VALUES (1, 'tok_visa_mock_123456', '4242');
-- Employee records for employee1(id=2) and manager(id=3)
INSERT INTO employees (user_id, department)
VALUES (2, 'Content'),
    (3, 'Management');
-- ============================================================
-- 11. APPROVALS TABLE (Phase 3)
-- ============================================================
CREATE TABLE IF NOT EXISTS approvals (
    id INT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    -- MAP_VERSION, PRICING_REQUEST
    entity_id INT NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
    reason VARCHAR(500),
    approved_by INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE
    SET NULL,
        INDEX idx_approvals_status (status),
        INDEX idx_approvals_entity (entity_type, entity_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- 12. AUDIT_LOG TABLE (Phase 3)
-- ============================================================
CREATE TABLE IF NOT EXISTS audit_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    action VARCHAR(100) NOT NULL,
    -- MAP_UPDATED, VERSION_APPROVED, etc.
    actor INT,
    -- User ID who performed action
    entity_type VARCHAR(50),
    -- MAP_VERSION, CITY, etc.
    entity_id INT,
    details_json TEXT,
    -- JSON with additional details
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (actor) REFERENCES users(id) ON DELETE
    SET NULL,
        INDEX idx_audit_actor (actor),
        INDEX idx_audit_entity (entity_type, entity_id),
        INDEX idx_audit_action (action)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- 13. NOTIFICATIONS TABLE (Phase 3)
-- ============================================================
CREATE TABLE IF NOT EXISTS notifications (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    channel ENUM('IN_APP', 'EMAIL', 'SMS') DEFAULT 'IN_APP',
    title VARCHAR(200) NOT NULL,
    body TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,
    is_read BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_notifications_user (user_id),
    INDEX idx_notifications_read (is_read)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- 14. PURCHASES TABLE (Phase 5)
-- ============================================================
CREATE TABLE IF NOT EXISTS purchases (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    city_id INT NOT NULL,
    map_version_id INT,
    -- Version downloaded at time of purchase
    price_paid DOUBLE NOT NULL,
    purchased_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE,
    INDEX idx_purchases_user (user_id),
    INDEX idx_purchases_city (city_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- 15. SUBSCRIPTIONS TABLE (Phase 5)
-- ============================================================
CREATE TABLE IF NOT EXISTS subscriptions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    city_id INT NOT NULL,
    months INT NOT NULL DEFAULT 1,
    price_paid DOUBLE NOT NULL,
    start_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_date TIMESTAMP NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE,
    INDEX idx_subscriptions_user (user_id),
    INDEX idx_subscriptions_active (is_active, end_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- 16. DOWNLOAD_EVENTS TABLE (Phase 5)
-- ============================================================
CREATE TABLE IF NOT EXISTS download_events (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    city_id INT NOT NULL,
    map_version_id INT,
    downloaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- 17. VIEW_EVENTS TABLE (Phase 5)
-- ============================================================
CREATE TABLE IF NOT EXISTS view_events (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    city_id INT NOT NULL,
    map_id INT NOT NULL,
    viewed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE,
    FOREIGN KEY (map_id) REFERENCES maps(id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- PHASE 5 SEED DATA
-- ============================================================
-- Customer purchased New York City one-time
INSERT INTO purchases (user_id, city_id, price_paid)
VALUES (1, 1, 250.00);
-- Customer subscription to London, UK (active)
INSERT INTO subscriptions (
        user_id,
        city_id,
        months,
        price_paid,
        start_date,
        end_date,
        is_active
    )
VALUES (
        1,
        2,
        6,
        220.00,
        DATE_SUB(NOW(), INTERVAL 1 MONTH),
        DATE_ADD(NOW(), INTERVAL 5 MONTH),
        TRUE
    );
-- Customer subscription to Paris, France (expired)
INSERT INTO subscriptions (
        user_id,
        city_id,
        months,
        price_paid,
        start_date,
        end_date,
        is_active
    )
VALUES (
        1,
        3,
        3,
        230.00,
        DATE_SUB(NOW(), INTERVAL 4 MONTH),
        DATE_SUB(NOW(), INTERVAL 1 MONTH),
        FALSE
    );
-- ============================================================
-- DATABASE INFO UPDATE
-- ============================================================
-- Added Purchase tables
-- Added 1 purchase and 2 subscriptions for 'customer1'
-- ============================================================
-- ============================================================
-- 18. PRICING_REQUESTS TABLE (Phase 8)
-- ============================================================
CREATE TABLE IF NOT EXISTS pricing_requests (
    id INT AUTO_INCREMENT PRIMARY KEY,
    city_id INT NOT NULL,
    current_price DOUBLE NOT NULL,
    proposed_price DOUBLE NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
    reason VARCHAR(500),
    rejection_reason VARCHAR(500),
    created_by INT NOT NULL,
    approved_by INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    FOREIGN KEY (city_id) REFERENCES cities(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by) REFERENCES users(id),
    FOREIGN KEY (approved_by) REFERENCES users(id),
    INDEX idx_pricing_status (status),
    INDEX idx_pricing_city (city_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- Add Company Manager user for testing Phase 8
INSERT INTO users (username, email, password_hash, role, is_active)
VALUES (
        'comanager1',
        'comanager1@gcm.com',
        '1234',
        'COMPANY_MANAGER',
        TRUE
    );
INSERT INTO employees (user_id, department)
VALUES (4, 'Management');
-- ============================================================
-- Phase 8 Pricing added
-- pricing_requests table created
-- comanager user added (COMPANY_MANAGER role)
-- ============================================================
-- ============================================================
-- 19. SUPPORT_TICKETS TABLE (Phase 9)
-- ============================================================
CREATE TABLE IF NOT EXISTS support_tickets (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    subject VARCHAR(200) NOT NULL,
    status ENUM('OPEN', 'BOT_RESPONDED', 'ESCALATED', 'CLOSED') DEFAULT 'OPEN',
    priority ENUM('LOW', 'MEDIUM', 'HIGH') DEFAULT 'MEDIUM',
    assigned_agent_id INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (assigned_agent_id) REFERENCES users(id) ON DELETE
    SET NULL,
        INDEX idx_tickets_user (user_id),
        INDEX idx_tickets_status (status),
        INDEX idx_tickets_agent (assigned_agent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- 20. TICKET_MESSAGES TABLE (Phase 9)
-- ============================================================
CREATE TABLE IF NOT EXISTS ticket_messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    ticket_id INT NOT NULL,
    sender_type ENUM('CUSTOMER', 'BOT', 'AGENT') NOT NULL,
    sender_id INT,
    message TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticket_id) REFERENCES support_tickets(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE
    SET NULL,
        INDEX idx_messages_ticket (ticket_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- 21. FAQ_ENTRIES TABLE (Bot knowledge base - Phase 9)
-- ============================================================
CREATE TABLE IF NOT EXISTS faq_entries (
    id INT AUTO_INCREMENT PRIMARY KEY,
    keywords VARCHAR(500) NOT NULL,
    question VARCHAR(300) NOT NULL,
    answer TEXT NOT NULL,
    category VARCHAR(50),
    usage_count INT DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- ============================================================
-- Add Support Agent user for testing
-- ============================================================
INSERT INTO users (username, email, password_hash, role, is_active)
VALUES (
        'agent1',
        'agent1@gcm.com',
        '1234',
        'SUPPORT_AGENT',
        TRUE
    );
INSERT INTO employees (user_id, department)
VALUES (
        (
            SELECT id
            FROM users
            WHERE username = 'agent1'
        ),
        'Support'
    );
-- ============================================================
-- Seed FAQ entries for bot responses
-- ============================================================
INSERT INTO faq_entries (keywords, question, answer, category)
VALUES (
        'purchase,buy,subscription,price,cost,pay',
        'How do I purchase a map?',
        'Go to Browse Catalog, select a city, and click Purchase. You can choose one-time download ($X) or subscription ($X/month).',
        'Purchasing'
    ),
    (
        'download,offline,save,export',
        'Can I download maps for offline use?',
        'Yes! After purchasing, go to My Purchases and click Download to save maps for offline use.',
        'Downloads'
    ),
    (
        'refund,cancel,money,return,chargeback',
        'How do I request a refund?',
        'Refunds are processed within 48 hours. Please provide your order details and an agent will assist you.',
        'Billing'
    ),
    (
        'password,login,access,forgot,reset,cant',
        'I forgot my password',
        'Use the Forgot Password link on the login screen. A reset email will be sent to your registered address.',
        'Account'
    ),
    (
        'poi,location,place,find,search,where',
        'How do I find a specific POI?',
        'Use the search feature in Browse Catalog. You can search by POI name, category, or city.',
        'Search'
    ),
    (
        'tour,route,guide,walk,navigate',
        'How do tours work?',
        'Tours are pre-planned routes connecting multiple POIs. Open a map and select the Tour tab to see available tours.',
        'Tours'
    ),
    (
        'subscription,expire,renew,extend,expiry',
        'How do I renew my subscription?',
        'Go to My Purchases, find your subscription, and click Renew. You can extend for 1-6 months.',
        'Subscription'
    ),
    (
        'update,new,version,latest,refresh',
        'How do I get map updates?',
        'If you have an active subscription, go to My Purchases and click Download to get the latest version.',
        'Updates'
    );
-- ============================================================
-- Phase 9 Support System added
-- support_tickets, ticket_messages, faq_entries tables created
-- 'agent1' user added (SUPPORT_AGENT role)
-- FAQ entries seeded for bot responses
-- ============================================================
-- ============================================================
-- CONSOLIDATED MIGRATIONS (no need to run separate .sql files)
-- ============================================================
-- migration_city_map_approval: cities.approved, created_by; maps.approved, created_by
-- migration_map_pois_approved: map_pois.approved
-- migration_map_pois_linked_by_user: map_pois.linked_by_user_id
-- migration_poi_coords_and_distances: pois.latitude, longitude (already in base); poi_distances table
-- migration_tour_remove_duration: tours use total_distance_meters only (no duration columns)
-- migration_tour_maps: maps.tour_id FK to tours(id)
-- ============================================================

-- ============================================================
-- MISSING MIGRATIONS + TOP-UP DUMMY DATA (idempotent)
-- Ensures:
-- 1) daily_stats table exists
-- 2) demo role users exist: customer/editor/manager/company-manager/agent
-- (City catalog: 20 world cities + 5 Israel cities (25 total), 3 maps per city (5 POIs each), mixed 10-stop tours - see SEED DATA.
--  Regenerate geography via scripts/gen_world_cities_seed.py if needed.)
-- ============================================================

-- Missing migration: daily_stats
CREATE TABLE IF NOT EXISTS daily_stats (
  stat_date DATE NOT NULL,
  city_id INT NOT NULL,
  maps_count INT NOT NULL DEFAULT 0,
  one_time_purchases INT NOT NULL DEFAULT 0,
  subscriptions INT NOT NULL DEFAULT 0,
  renewals INT NOT NULL DEFAULT 0,
  views INT NOT NULL DEFAULT 0,
  downloads INT NOT NULL DEFAULT 0,
  PRIMARY KEY (stat_date, city_id)
);

DROP PROCEDURE IF EXISTS seed_topup_20_cities_and_roles;
DELIMITER //
CREATE PROCEDURE seed_topup_20_cities_and_roles()
BEGIN
    -- Rename old *_demo users if they exist and target names are free
    IF EXISTS (SELECT 1 FROM users WHERE username = 'customer_demo')
       AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'customer1') THEN
        UPDATE users
        SET username = 'customer1', email = 'customer1@gcm.com'
        WHERE username = 'customer_demo';
    END IF;
    IF EXISTS (SELECT 1 FROM users WHERE username = 'editor_demo')
       AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'employee2') THEN
        UPDATE users
        SET username = 'employee2', email = 'employee2@gcm.com'
        WHERE username = 'editor_demo';
    END IF;
    IF EXISTS (SELECT 1 FROM users WHERE username = 'editor1')
       AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'employee2') THEN
        UPDATE users
        SET username = 'employee2', email = 'employee2@gcm.com'
        WHERE username = 'editor1';
    END IF;
    IF EXISTS (SELECT 1 FROM users WHERE username = 'manager_demo')
       AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'manager1') THEN
        UPDATE users
        SET username = 'manager1', email = 'manager1@gcm.com'
        WHERE username = 'manager_demo';
    END IF;
    IF EXISTS (SELECT 1 FROM users WHERE username = 'comanager_demo')
       AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'comanager1') THEN
        UPDATE users
        SET username = 'comanager1', email = 'comanager1@gcm.com'
        WHERE username = 'comanager_demo';
    END IF;
    IF EXISTS (SELECT 1 FROM users WHERE username = 'agent_demo')
       AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'agent1') THEN
        UPDATE users
        SET username = 'agent1', email = 'agent1@gcm.com'
        WHERE username = 'agent_demo';
    END IF;

    -- Ensure role users exist (password = 1234 for demo/testing)
    IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'customer1') THEN
        INSERT INTO users (username, email, password_hash, role, is_active)
        VALUES ('customer1', 'customer1@gcm.com', '1234', 'CUSTOMER', TRUE);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'customer2') THEN
        INSERT INTO users (username, email, password_hash, role, is_active)
        VALUES ('customer2', 'customer2@gcm.com', '1234', 'CUSTOMER', TRUE);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'customer3') THEN
        INSERT INTO users (username, email, password_hash, role, is_active)
        VALUES ('customer3', 'customer3@gcm.com', '1234', 'CUSTOMER', TRUE);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'customer4') THEN
        INSERT INTO users (username, email, password_hash, role, is_active)
        VALUES ('customer4', 'customer4@gcm.com', '1234', 'CUSTOMER', TRUE);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'customer5') THEN
        INSERT INTO users (username, email, password_hash, role, is_active)
        VALUES ('customer5', 'customer5@gcm.com', '1234', 'CUSTOMER', TRUE);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM customers c
        JOIN users u ON u.id = c.user_id
        WHERE u.username = 'customer1'
    ) THEN
        INSERT INTO customers (user_id, payment_token, card_last4)
        VALUES ((SELECT id FROM users WHERE username = 'customer1' LIMIT 1), 'tok_customer1', '1111');
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM customers c JOIN users u ON u.id = c.user_id WHERE u.username = 'customer2'
    ) THEN
        INSERT INTO customers (user_id, payment_token, card_last4)
        VALUES ((SELECT id FROM users WHERE username = 'customer2' LIMIT 1), 'tok_customer2', '2222');
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM customers c JOIN users u ON u.id = c.user_id WHERE u.username = 'customer3'
    ) THEN
        INSERT INTO customers (user_id, payment_token, card_last4)
        VALUES ((SELECT id FROM users WHERE username = 'customer3' LIMIT 1), 'tok_customer3', '3333');
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM customers c JOIN users u ON u.id = c.user_id WHERE u.username = 'customer4'
    ) THEN
        INSERT INTO customers (user_id, payment_token, card_last4)
        VALUES ((SELECT id FROM users WHERE username = 'customer4' LIMIT 1), 'tok_customer4', '4444');
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM customers c JOIN users u ON u.id = c.user_id WHERE u.username = 'customer5'
    ) THEN
        INSERT INTO customers (user_id, payment_token, card_last4)
        VALUES ((SELECT id FROM users WHERE username = 'customer5' LIMIT 1), 'tok_customer5', '5555');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'employee2') THEN
        INSERT INTO users (username, email, password_hash, role, is_active)
        VALUES ('employee2', 'employee2@gcm.com', '1234', 'CONTENT_EDITOR', TRUE);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM employees e JOIN users u ON u.id = e.user_id WHERE u.username = 'employee2'
    ) THEN
        INSERT INTO employees (user_id, department)
        VALUES ((SELECT id FROM users WHERE username = 'employee2' LIMIT 1), 'Content');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'manager1') THEN
        INSERT INTO users (username, email, password_hash, role, is_active)
        VALUES ('manager1', 'manager1@gcm.com', '1234', 'CONTENT_MANAGER', TRUE);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM employees e JOIN users u ON u.id = e.user_id WHERE u.username = 'manager1'
    ) THEN
        INSERT INTO employees (user_id, department)
        VALUES ((SELECT id FROM users WHERE username = 'manager1' LIMIT 1), 'Management');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'comanager1') THEN
        INSERT INTO users (username, email, password_hash, role, is_active)
        VALUES ('comanager1', 'comanager1@gcm.com', '1234', 'COMPANY_MANAGER', TRUE);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM employees e JOIN users u ON u.id = e.user_id WHERE u.username = 'comanager1'
    ) THEN
        INSERT INTO employees (user_id, department)
        VALUES ((SELECT id FROM users WHERE username = 'comanager1' LIMIT 1), 'Executive');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'agent1') THEN
        INSERT INTO users (username, email, password_hash, role, is_active)
        VALUES ('agent1', 'agent1@gcm.com', '1234', 'SUPPORT_AGENT', TRUE);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM employees e JOIN users u ON u.id = e.user_id WHERE u.username = 'agent1'
    ) THEN
        INSERT INTO employees (user_id, department)
        VALUES ((SELECT id FROM users WHERE username = 'agent1' LIMIT 1), 'Support');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'agent2') THEN
        INSERT INTO users (username, email, password_hash, role, is_active)
        VALUES ('agent2', 'agent2@gcm.com', '1234', 'SUPPORT_AGENT', TRUE);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM employees e JOIN users u ON u.id = e.user_id WHERE u.username = 'agent2'
    ) THEN
        INSERT INTO employees (user_id, department)
        VALUES ((SELECT id FROM users WHERE username = 'agent2' LIMIT 1), 'Support');
    END IF;
END //
DELIMITER ;

CALL seed_topup_20_cities_and_roles();
DROP PROCEDURE IF EXISTS seed_topup_20_cities_and_roles;

-- ============================================================
-- Additional SQL from database_update.sql
-- ============================================================
CREATE TABLE IF NOT EXISTS subscription_reminders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    subscription_id INT NOT NULL,
    reminder_type VARCHAR(20) NOT NULL,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_reminder (subscription_id, reminder_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;