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
-- SEED DATA
-- ============================================================
-- Cities (approved=1 so they appear in catalog)
INSERT INTO cities (name, description, price, approved, created_by)
VALUES (
        'Haifa',
        'Beautiful port city on the Mediterranean coast with the famous Bahai Gardens',
        150.00, 1, NULL
    ),
    (
        'Tel Aviv',
        'The vibrant city that never sleeps - beaches, nightlife, and tech hub',
        200.00, 1, NULL
    ),
    (
        'Jerusalem',
        'The holy city with rich history and religious significance',
        180.00, 1, NULL
    ),
    (
        'New York',
        'The Big Apple - world capital of finance, culture, and diversity',
        250.00, 1, NULL
    ),
    (
        'London',
        'Historic capital of England with royal heritage and modern attractions',
        220.00, 1, NULL
    ),
    (
        'Paris',
        'The city of lights and love - art, cuisine, and romance',
        230.00, 1, NULL
    ),
    (
        'Tokyo',
        'Futuristic metropolis blending ancient traditions with cutting-edge technology',
        280.00, 1, NULL
    ),
    (
        'Sakhnin',
        'Historic Arab city in the Galilee known for culture, olive oil, and football',
        120.00, 1, NULL
    );
-- Maps for Haifa (city_id = 1) (approved=1)
INSERT INTO maps (city_id, name, short_description, approved, created_by)
VALUES (
        1,
        'Haifa Bay Area',
        'Explore the stunning bay area with beaches and port attractions',
        1, NULL
    ),
    (
        1,
        'Carmel Center',
        'Discover the heart of Haifa on Mount Carmel',
        1, NULL
    ),
    (
        1,
        'Bahai Gardens Tour',
        'Navigate the famous UNESCO World Heritage Bahai Gardens',
        1, NULL
    ),
    (
        1,
        'Haifa Beach Guide',
        'Complete guide to all beaches in Haifa',
        1, NULL
    );
-- Maps for Tel Aviv (city_id = 2)
INSERT INTO maps (city_id, name, short_description, approved, created_by)
VALUES (
        2,
        'Rothschild Boulevard',
        'Walk through the historic Bauhaus architecture',
        1, NULL
    ),
    (
        2,
        'Jaffa Old City',
        'Ancient port city with artists quarters and flea market',
        1, NULL
    ),
    (
        2,
        'Tel Aviv Beaches',
        'All the beaches from north to south',
        1, NULL
    ),
    (
        2,
        'Dizengoff Area',
        'Shopping and cafe culture in the city center',
        1, NULL
    );
-- Maps for Jerusalem (city_id = 3)
INSERT INTO maps (city_id, name, short_description, approved, created_by)
VALUES (
        3,
        'Old City Complete',
        'Navigate all four quarters of the Old City',
        1, NULL
    ),
    (
        3,
        'Western Wall Area',
        'The holiest site in Judaism and surroundings',
        1, NULL
    ),
    (
        3,
        'Mount of Olives',
        'Historic cemetery and panoramic views',
        1, NULL
    );
-- Maps for New York (city_id = 4)
INSERT INTO maps (city_id, name, short_description, approved, created_by)
VALUES (
        4,
        'Manhattan Downtown',
        'Financial district and 9/11 Memorial',
        1, NULL
    ),
    (
        4,
        'Central Park',
        'Complete guide to the iconic urban park',
        1, NULL
    ),
    (
        4,
        'Times Square Area',
        'Broadway theaters and bright lights',
        1, NULL
    );
-- Maps for London (city_id = 5)
INSERT INTO maps (city_id, name, short_description, approved, created_by)
VALUES (
        5,
        'Westminster',
        'Parliament, Big Ben, and royal landmarks',
        1, NULL
    ),
    (
        5,
        'Tower of London',
        'Historic fortress and Crown Jewels',
        1, NULL
    );
-- Maps for Paris (city_id = 6)
INSERT INTO maps (city_id, name, short_description, approved, created_by)
VALUES (
        6,
        'Paris Highlights',
        'Essential sights: Eiffel Tower, Louvre, and the Seine',
        1, NULL
    ),
    (
        6,
        'Montmartre & Sacré-Cœur',
        'Artistic hilltop neighborhood and basilica',
        1, NULL
    );
-- Maps for Tokyo (city_id = 7)
INSERT INTO maps (city_id, name, short_description, approved, created_by)
VALUES (
        7,
        'Tokyo Central',
        'Shinjuku, Shibuya, and Harajuku districts',
        1, NULL
    ),
    (
        7,
        'Traditional Tokyo',
        'Asakusa, Senso-ji, and Sumida River',
        1, NULL
    );
-- Maps for Sakhnin (city_id = 8)
INSERT INTO maps (city_id, name, short_description, approved, created_by)
VALUES (
        8,
        'Sakhnin Old City',
        'Historic center, market, and cultural sites',
        1, NULL
    );
-- POIs for Haifa
INSERT INTO pois (
        city_id,
        name,
        location,
        category,
        short_explanation,
        is_accessible
    )
VALUES (
        1,
        'Bahai Gardens',
        '32.8140,34.9870',
        'Religious',
        'UNESCO World Heritage site with stunning terraced gardens',
        TRUE
    ),
    (
        1,
        'Bat Galim Beach',
        '32.8260,34.9640',
        'Beach',
        'Popular beach with promenade and restaurants',
        TRUE
    ),
    (
        1,
        'Dado Beach',
        '32.8050,34.9620',
        'Beach',
        'Family-friendly beach with calm waters',
        TRUE
    ),
    (
        1,
        'German Colony',
        '32.8190,34.9880',
        'Historic',
        'Historic neighborhood with cafes and Templar architecture',
        TRUE
    ),
    (
        1,
        'Haifa Port',
        '32.8220,35.0000',
        'Landmark',
        'Active commercial port with views and history',
        FALSE
    ),
    (
        1,
        'Stella Maris',
        '32.8260,34.9710',
        'Religious',
        'Carmelite monastery with beautiful views',
        TRUE
    ),
    (
        1,
        'Wadi Nisnas',
        '32.8180,34.9940',
        'Cultural',
        'Arab neighborhood famous for street art and food',
        TRUE
    );
-- POIs for Tel Aviv
INSERT INTO pois (
        city_id,
        name,
        location,
        category,
        short_explanation,
        is_accessible
    )
VALUES (
        2,
        'Gordon Beach',
        '32.0830,34.7680',
        'Beach',
        'Central beach with sports facilities',
        TRUE
    ),
    (
        2,
        'Carmel Market',
        '32.0670,34.7690',
        'Market',
        'Vibrant outdoor market with local produce and food',
        FALSE
    ),
    (
        2,
        'Habima Square',
        '32.0720,34.7790',
        'Cultural',
        'Cultural complex with theater and art venues',
        TRUE
    ),
    (
        2,
        'Jaffa Clock Tower',
        '32.0520,34.7520',
        'Historic',
        'Ottoman-era landmark in old Jaffa',
        TRUE
    ),
    (
        2,
        'Neve Tzedek',
        '32.0600,34.7650',
        'Historic',
        'First Jewish neighborhood with boutiques and cafes',
        TRUE
    ),
    (
        2,
        'Frishman Beach',
        '32.0790,34.7670',
        'Beach',
        'Popular beach near the city center',
        TRUE
    ),
    (
        2,
        'Port of Tel Aviv',
        '32.0970,34.7700',
        'Entertainment',
        'Renovated port with restaurants and nightlife',
        TRUE
    );
-- POIs for Jerusalem
INSERT INTO pois (
        city_id,
        name,
        location,
        category,
        short_explanation,
        is_accessible
    )
VALUES (
        3,
        'Western Wall',
        '31.7767,35.2345',
        'Religious',
        'Most sacred site in Judaism for prayer',
        TRUE
    ),
    (
        3,
        'Church of the Holy Sepulchre',
        '31.7785,35.2296',
        'Religious',
        'Christian holy site marking crucifixion and burial of Jesus',
        FALSE
    ),
    (
        3,
        'Dome of the Rock',
        '31.7781,35.2354',
        'Religious',
        'Iconic Islamic shrine on Temple Mount',
        FALSE
    ),
    (
        3,
        'Mahane Yehuda Market',
        '31.7850,35.2120',
        'Market',
        'Famous food market known as The Shuk',
        TRUE
    ),
    (
        3,
        'Tower of David',
        '31.7764,35.2281',
        'Historic',
        'Ancient citadel with museum and light show',
        TRUE
    ),
    (
        3,
        'Mount of Olives Viewpoint',
        '31.7780,35.2450',
        'Viewpoint',
        'Panoramic view of Old City and Temple Mount',
        TRUE
    );
-- POIs for New York
INSERT INTO pois (
        city_id,
        name,
        location,
        category,
        short_explanation,
        is_accessible
    )
VALUES (
        4,
        'Statue of Liberty',
        '40.6892,-74.0445',
        'Landmark',
        'Iconic symbol of freedom and democracy',
        TRUE
    ),
    (
        4,
        'Central Park',
        '40.7829,-73.9654',
        'Park',
        'Urban oasis with lakes, bridges, and gardens',
        TRUE
    ),
    (
        4,
        'Times Square',
        '40.7580,-73.9855',
        'Entertainment',
        'Bright lights and Broadway theaters',
        TRUE
    ),
    (
        4,
        'Brooklyn Bridge',
        '40.7061,-73.9969',
        'Landmark',
        'Historic suspension bridge with walkway',
        TRUE
    ),
    (
        4,
        'Empire State Building',
        '40.7484,-73.9857',
        'Landmark',
        'Art Deco skyscraper with observation deck',
        TRUE
    ),
    (
        4,
        '9/11 Memorial',
        '40.7115,-74.0134',
        'Memorial',
        'Tribute to victims of September 11 attacks',
        TRUE
    );
-- POIs for London
INSERT INTO pois (
        city_id,
        name,
        location,
        category,
        short_explanation,
        is_accessible
    )
VALUES (
        5,
        'Big Ben',
        '51.5007,-0.1246',
        'Landmark',
        'Iconic clock tower at Houses of Parliament',
        TRUE
    ),
    (
        5,
        'Tower Bridge',
        '51.5055,-0.0754',
        'Landmark',
        'Victorian bridge with glass walkway',
        TRUE
    ),
    (
        5,
        'Buckingham Palace',
        '51.5014,-0.1419',
        'Royal',
        'Official residence of the British monarch',
        TRUE
    ),
    (
        5,
        'British Museum',
        '51.5194,-0.1270',
        'Museum',
        'World-famous museum with free admission',
        TRUE
    ),
    (
        5,
        'London Eye',
        '51.5033,-0.1196',
        'Entertainment',
        'Giant observation wheel on the Thames',
        TRUE
    );
-- POIs for Paris (city_id = 6)
INSERT INTO pois (city_id, name, location, category, short_explanation, is_accessible)
VALUES
    (6, 'Eiffel Tower', '48.8584,2.2945', 'Landmark', 'Iconic iron lattice tower and symbol of Paris', TRUE),
    (6, 'Louvre Museum', '48.8606,2.3376', 'Museum', 'World''s largest art museum and historic monument', TRUE),
    (6, 'Notre-Dame Cathedral', '48.8530,2.3499', 'Religious', 'Medieval Catholic cathedral on the Île de la Cité', TRUE),
    (6, 'Arc de Triomphe', '48.8738,2.2950', 'Landmark', 'Triumphal arch honoring those who fought for France', TRUE),
    (6, 'Sacré-Cœur', '48.8867,2.3431', 'Religious', 'Basilica on Montmartre with panoramic views', TRUE),
    (6, 'Champs-Élysées', '48.8698,2.3078', 'Landmark', 'Famous avenue with shops, cafes, and theaters', TRUE);
-- POIs for Tokyo (city_id = 7)
INSERT INTO pois (city_id, name, location, category, short_explanation, is_accessible)
VALUES
    (7, 'Shibuya Crossing', '35.6595,139.7004', 'Landmark', 'World''s busiest pedestrian crossing', TRUE),
    (7, 'Senso-ji Temple', '35.7148,139.7967', 'Religious', 'Ancient Buddhist temple in Asakusa', TRUE),
    (7, 'Tokyo Skytree', '35.7101,139.8107', 'Landmark', 'Tallest structure in Japan with observation decks', TRUE),
    (7, 'Meiji Shrine', '35.6764,139.6993', 'Religious', 'Shinto shrine surrounded by forest in Shibuya', TRUE),
    (7, 'Tsukiji Outer Market', '35.6654,139.7707', 'Market', 'Famous food and fish market (outer market only)', TRUE),
    (7, 'Imperial Palace', '35.6852,139.7528', 'Historic', 'Emperor''s residence and East Gardens', TRUE);
-- POIs for Sakhnin (city_id = 8)
INSERT INTO pois (city_id, name, location, category, short_explanation, is_accessible)
VALUES
    (8, 'Sakhnin Old Mosque', '32.8642,35.2972', 'Religious', 'Historic mosque in the old city center', TRUE),
    (8, 'Sakhnin Market', '32.8635,35.2980', 'Market', 'Traditional market with local produce and crafts', TRUE),
    (8, 'Doha Stadium', '32.8680,35.2950', 'Entertainment', 'Home of Bnei Sakhnin football club', TRUE),
    (8, 'Olive Press Museum', '32.8610,35.3020', 'Cultural', 'Heritage site showcasing olive oil production', TRUE);
-- Backfill latitude/longitude from location (lat,lng format) for seed POIs
SET SQL_SAFE_UPDATES = 0;
UPDATE pois
SET latitude = CAST(TRIM(SUBSTRING_INDEX(location, ',', 1)) AS DECIMAL(10,6)),
    longitude = CAST(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(location, ',', 2), ',', -1)) AS DECIMAL(10,6))
WHERE location REGEXP '^-?[0-9]+[.]?[0-9]*,[ ]*-?[0-9]+[.]?[0-9]*$';
SET SQL_SAFE_UPDATES = 1;

-- Map-POI Associations (linking POIs to maps; approved=1, linked_by_user_id=NULL)
-- Haifa Bay Area map includes port and beaches
INSERT INTO map_pois (map_id, poi_id, display_order, approved, linked_by_user_id)
VALUES (1, 5, 1, 1, NULL),
    (1, 2, 2, 1, NULL),
    (1, 3, 3, 1, NULL);
-- Carmel Center map
INSERT INTO map_pois (map_id, poi_id, display_order, approved, linked_by_user_id)
VALUES (2, 4, 1, 1, NULL),
    (2, 1, 2, 1, NULL),
    (2, 6, 3, 1, NULL);
-- Bahai Gardens Tour
INSERT INTO map_pois (map_id, poi_id, display_order, approved, linked_by_user_id)
VALUES (3, 1, 1, 1, NULL),
    (3, 4, 2, 1, NULL),
    (3, 7, 3, 1, NULL);
-- Haifa Beach Guide
INSERT INTO map_pois (map_id, poi_id, display_order, approved, linked_by_user_id)
VALUES (4, 2, 1, 1, NULL),
    (4, 3, 2, 1, NULL);
-- Tel Aviv Rothschild map
INSERT INTO map_pois (map_id, poi_id, display_order, approved, linked_by_user_id)
VALUES (5, 10, 1, 1, NULL),
    (5, 12, 2, 1, NULL);
-- Jaffa Old City map
INSERT INTO map_pois (map_id, poi_id, display_order, approved, linked_by_user_id)
VALUES (6, 11, 1, 1, NULL),
    (6, 14, 2, 1, NULL);
-- Tel Aviv Beaches map
INSERT INTO map_pois (map_id, poi_id, display_order, approved, linked_by_user_id)
VALUES (7, 8, 1, 1, NULL),
    (7, 13, 2, 1, NULL);
-- Jerusalem Old City map
INSERT INTO map_pois (map_id, poi_id, display_order, approved, linked_by_user_id)
VALUES (9, 15, 1, 1, NULL),
    (9, 16, 2, 1, NULL),
    (9, 17, 3, 1, NULL),
    (9, 19, 4, 1, NULL);
-- New York Central Park map
INSERT INTO map_pois (map_id, poi_id, display_order, approved, linked_by_user_id)
VALUES (13, 22, 1, 1, NULL);
-- Times Square Area map
INSERT INTO map_pois (map_id, poi_id, display_order, approved, linked_by_user_id)
VALUES (14, 23, 1, 1, NULL),
    (14, 25, 2, 1, NULL);
-- London Westminster map
INSERT INTO map_pois (map_id, poi_id, display_order, approved, linked_by_user_id)
VALUES (15, 27, 1, 1, NULL),
    (15, 29, 2, 1, NULL),
    (15, 31, 3, 1, NULL);
-- Paris Highlights map (map 17: POIs 32 Eiffel, 33 Louvre, 34 Notre-Dame, 35 Arc, 36 Sacré-Cœur, 37 Champs-Élysées)
INSERT INTO map_pois (map_id, poi_id, display_order, approved, linked_by_user_id)
VALUES (17, 32, 1, 1, NULL),
    (17, 33, 2, 1, NULL),
    (17, 34, 3, 1, NULL),
    (17, 35, 4, 1, NULL),
    (17, 37, 5, 1, NULL);
-- Montmartre & Sacré-Cœur map (map 18)
INSERT INTO map_pois (map_id, poi_id, display_order, approved, linked_by_user_id)
VALUES (18, 36, 1, 1, NULL);
-- Tokyo Central map (map 19: POIs 38 Shibuya, 39 Senso-ji, 40 Skytree, 41 Meiji, 42 Tsukiji, 43 Imperial)
INSERT INTO map_pois (map_id, poi_id, display_order, approved, linked_by_user_id)
VALUES (19, 38, 1, 1, NULL),
    (19, 41, 2, 1, NULL);
-- Traditional Tokyo map (map 20)
INSERT INTO map_pois (map_id, poi_id, display_order, approved, linked_by_user_id)
VALUES (20, 39, 1, 1, NULL),
    (20, 40, 2, 1, NULL),
    (20, 43, 3, 1, NULL);
-- Sakhnin Old City map (map 21: POIs 44-47)
INSERT INTO map_pois (map_id, poi_id, display_order, approved, linked_by_user_id)
VALUES (21, 44, 1, 1, NULL),
    (21, 45, 2, 1, NULL),
    (21, 46, 3, 1, NULL),
    (21, 47, 4, 1, NULL);
-- Sample Tours
INSERT INTO tours (
        city_id,
        name,
        general_description,
        total_distance_meters
    )
VALUES (
        1,
        'Haifa Highlights',
        'See the best of Haifa in half a day',
        NULL
    ),
    (
        2,
        'Tel Aviv Beach Walk',
        'Coastal walk from Gordon Beach to Jaffa',
        NULL
    ),
    (
        3,
        'Jerusalem Holy Sites',
        'Visit the three major holy sites',
        NULL
    ),
    (
        6,
        'Paris Essentials',
        'Eiffel Tower, Louvre, and the Seine',
        NULL
    ),
    (
        7,
        'Tokyo Mix',
        'From Shibuya to Senso-ji: modern and traditional',
        NULL
    ),
    (
        8,
        'Sakhnin Culture Walk',
        'Old city, market, and olive heritage',
        NULL
    );
-- Tour stops for Haifa Highlights (total_distance_meters recomputed when POI distances exist)
INSERT INTO tour_stops (
        tour_id,
        poi_id,
        stop_order,
        notes
    )
VALUES (1, 1, 1, 'Start at the top of Bahai Gardens'),
    (1, 4, 2, 'Coffee break in German Colony'),
    (1, 2, 3, 'End with beach time at Bat Galim');
-- Tour stops for Paris Essentials (tour_id=4; POIs 32,33,34)
INSERT INTO tour_stops (tour_id, poi_id, stop_order, notes)
VALUES (4, 32, 1, 'Start at Eiffel Tower'),
    (4, 33, 2, 'Louvre Museum'),
    (4, 34, 3, 'Notre-Dame');
-- Tour stops for Tokyo Mix (tour_id=5; POIs 38,39,41)
INSERT INTO tour_stops (tour_id, poi_id, stop_order, notes)
VALUES (5, 38, 1, 'Shibuya Crossing'),
    (5, 39, 2, 'Senso-ji Temple'),
    (5, 41, 3, 'Meiji Shrine');
-- Tour stops for Sakhnin Culture Walk (tour_id=6; POIs 44,45,46,47)
INSERT INTO tour_stops (tour_id, poi_id, stop_order, notes)
VALUES (6, 44, 1, 'Old Mosque'),
    (6, 45, 2, 'Market'),
    (6, 46, 3, 'Doha Stadium'),
    (6, 47, 4, 'Olive Press Museum');
-- ============================================================
-- TEST USERS with SIMPLE passwords for easy testing
-- Username = role name, Password = 1234
-- ============================================================
INSERT INTO users (username, email, password_hash, role, is_active)
VALUES (
        'customer',
        'customer@gcm.com',
        '1234',
        'CUSTOMER',
        TRUE
    ),
    (
        'employee',
        'employee@gcm.com',
        '1234',
        'CONTENT_EDITOR',
        TRUE
    ),
    (
        'manager',
        'manager@gcm.com',
        '1234',
        'CONTENT_MANAGER',
        TRUE
    );
-- Customer record for 'customer' user (id=1)
INSERT INTO customers (user_id, payment_token, card_last4)
VALUES (1, 'tok_visa_mock_123456', '4242');
-- Employee records for employee(id=2) and manager(id=3)
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
-- Customer purchased Haifa one-time
INSERT INTO purchases (user_id, city_id, price_paid)
VALUES (1, 1, 150.00);
-- Customer subscription to Tel Aviv (active)
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
        180.00,
        DATE_SUB(NOW(), INTERVAL 1 MONTH),
        DATE_ADD(NOW(), INTERVAL 5 MONTH),
        TRUE
    );
-- Customer subscription to Jerusalem (expired)
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
        162.00,
        DATE_SUB(NOW(), INTERVAL 4 MONTH),
        DATE_SUB(NOW(), INTERVAL 1 MONTH),
        FALSE
    );
-- ============================================================
-- DATABASE INFO UPDATE
-- ============================================================
-- Added Purchase tables
-- Added 1 purchase and 2 subscriptions for 'customer'
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
        'comanager',
        'comanager@gcm.com',
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
        'agent',
        'agent@gcm.com',
        '1234',
        'SUPPORT_AGENT',
        TRUE
    );
INSERT INTO employees (user_id, department)
VALUES (
        (
            SELECT id
            FROM users
            WHERE username = 'agent'
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
-- 'agent' user added (SUPPORT_AGENT role)
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