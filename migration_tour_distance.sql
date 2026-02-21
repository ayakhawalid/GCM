-- Add total_distance_meters to tours (sum of consecutive POI-to-POI distances for the tour route).
USE gcm_db;

ALTER TABLE tours ADD COLUMN total_distance_meters DOUBLE NULL AFTER estimated_duration_minutes;
