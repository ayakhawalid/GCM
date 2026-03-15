-- Draft POIs: show only to the user who created the draft (linked_by_user_id).
-- So the employee sees their own draft POIs in the map editor; others do not.
USE gcm_db;

DROP PROCEDURE IF EXISTS add_linked_by_user_if_missing;
DELIMITER //
CREATE PROCEDURE add_linked_by_user_if_missing()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'gcm_db' AND TABLE_NAME = 'map_pois' AND COLUMN_NAME = 'linked_by_user_id'
  ) THEN
    ALTER TABLE map_pois ADD COLUMN linked_by_user_id INT NULL AFTER approved;
  END IF;
END //
DELIMITER ;
CALL add_linked_by_user_if_missing();
DROP PROCEDURE add_linked_by_user_if_missing;
