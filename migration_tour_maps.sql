-- Add tour_id to maps so a map can be the dedicated "tour route" map for a tour.
USE gcm_db;

ALTER TABLE maps ADD COLUMN tour_id INT NULL;
ALTER TABLE maps ADD CONSTRAINT fk_maps_tour FOREIGN KEY (tour_id) REFERENCES tours(id) ON DELETE CASCADE;
