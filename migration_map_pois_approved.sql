-- Only count/show POIs that are approved (not draft) in catalog.
-- When editor saves changes (draft), map_pois rows get approved=0 until manager approves.
-- Safe to run multiple times: adds column only if it does not exist.
USE gcm_db;

-- TINYINT without display width to avoid deprecation warning (MySQL 8.0.17+)
DROP PROCEDURE IF EXISTS add_map_pois_approved_if_missing;
DELIMITER //
CREATE PROCEDURE add_map_pois_approved_if_missing()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'gcm_db' AND TABLE_NAME = 'map_pois' AND COLUMN_NAME = 'approved'
  ) THEN
    ALTER TABLE map_pois ADD COLUMN approved TINYINT NOT NULL DEFAULT 1 AFTER display_order;
  END IF;
END //
DELIMITER ;
CALL add_map_pois_approved_if_missing();
DROP PROCEDURE add_map_pois_approved_if_missing;
