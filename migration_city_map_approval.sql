-- City and Map approval: only content manager can approve; until then visible only to creator.
USE gcm_db;

ALTER TABLE cities
  ADD COLUMN approved TINYINT(1) NOT NULL DEFAULT 0 AFTER price,
  ADD COLUMN created_by INT NULL AFTER approved;
-- Optional: FK to users if you have referential integrity
-- ALTER TABLE cities ADD CONSTRAINT fk_cities_created_by FOREIGN KEY (created_by) REFERENCES users(id);

ALTER TABLE maps
  ADD COLUMN approved TINYINT(1) NOT NULL DEFAULT 0 AFTER short_description,
  ADD COLUMN created_by INT NULL AFTER approved;

CREATE INDEX idx_cities_approved ON cities(approved);
CREATE INDEX idx_maps_approved ON maps(approved);

-- Approve all existing cities and maps so they remain visible.
UPDATE cities SET approved = 1 WHERE approved = 0;
UPDATE maps SET approved = 1 WHERE approved = 0;
