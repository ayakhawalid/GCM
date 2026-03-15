-- Run this on an existing gcm_db to add latitude/longitude and poi_distances.
USE gcm_db;

-- If columns already exist, skip these two lines or run ALTER only once.
ALTER TABLE pois ADD COLUMN latitude DOUBLE NULL AFTER location;
ALTER TABLE pois ADD COLUMN longitude DOUBLE NULL AFTER latitude;

-- Backfill lat/lon from location where it looks like "num,num"
-- (Disable safe updates so UPDATE without key in WHERE is allowed.)
SET SQL_SAFE_UPDATES = 0;
UPDATE pois
SET latitude = CAST(TRIM(SUBSTRING_INDEX(location, ',', 1)) AS DECIMAL(10,6)),
    longitude = CAST(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(location, ',', 2), ',', -1)) AS DECIMAL(10,6))
WHERE location REGEXP '^-?[0-9]+[.]?[0-9]*,[ ]*-?[0-9]+[.]?[0-9]*$'
  AND (latitude IS NULL OR longitude IS NULL);
SET SQL_SAFE_UPDATES = 1;

CREATE TABLE IF NOT EXISTS poi_distances (
    poi_id_a INT NOT NULL,
    poi_id_b INT NOT NULL,
    distance_meters DOUBLE NOT NULL,
    PRIMARY KEY (poi_id_a, poi_id_b),
    FOREIGN KEY (poi_id_a) REFERENCES pois(id) ON DELETE CASCADE,
    FOREIGN KEY (poi_id_b) REFERENCES pois(id) ON DELETE CASCADE,
    INDEX idx_poi_distances_b (poi_id_b)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- MySQL 8.0.12+ supports IF NOT EXISTS for ALTER ADD COLUMN; older MySQL may error on ADD COLUMN IF NOT EXISTS.
-- If you get syntax error, run instead:
-- ALTER TABLE pois ADD COLUMN latitude DOUBLE NULL AFTER location;
-- ALTER TABLE pois ADD COLUMN longitude DOUBLE NULL AFTER latitude;
