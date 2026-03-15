-- Remove duration columns from tours and tour_stops; use distance only.
-- Compatible with MySQL 5.7 and 8.x.
USE gcm_db;

DELIMITER //
-- Add column only if it does not exist
DROP PROCEDURE IF EXISTS migrate_tour_add_distance//
CREATE PROCEDURE migrate_tour_add_distance()
BEGIN
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tours' AND COLUMN_NAME = 'total_distance_meters') = 0 THEN
    ALTER TABLE tours ADD COLUMN total_distance_meters DOUBLE NULL;
  END IF;
END//
-- Drop column only if it exists
DROP PROCEDURE IF EXISTS migrate_tour_drop_duration//
CREATE PROCEDURE migrate_tour_drop_duration(IN p_table VARCHAR(64), IN p_column VARCHAR(64))
BEGIN
  IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column) > 0 THEN
    SET @sql = CONCAT('ALTER TABLE `', p_table, '` DROP COLUMN `', p_column, '`');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//
DELIMITER ;

CALL migrate_tour_add_distance();
CALL migrate_tour_drop_duration('tours', 'estimated_duration_minutes');
CALL migrate_tour_drop_duration('tour_stops', 'recommended_duration_minutes');

DROP PROCEDURE IF EXISTS migrate_tour_add_distance;
DROP PROCEDURE IF EXISTS migrate_tour_drop_duration;
