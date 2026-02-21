-- Only count/show POIs that are approved (not draft) in catalog.
-- When editor saves changes (draft), map_pois rows get approved=0 until manager approves.
USE gcm_db;

ALTER TABLE map_pois ADD COLUMN approved TINYINT(1) NOT NULL DEFAULT 1 AFTER display_order;
