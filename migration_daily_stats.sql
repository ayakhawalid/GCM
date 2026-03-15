-- Activity Reports: daily statistics table (Phase 10).
-- Safe to run multiple times (CREATE TABLE IF NOT EXISTS).
-- Use this if you prefer running migrations manually instead of relying on DAO init.
USE gcm_db;
CREATE TABLE IF NOT EXISTS daily_stats (
  stat_date   DATE NOT NULL,
  city_id     INT NOT NULL,
  maps_count  INT NOT NULL DEFAULT 0,
  one_time_purchases INT NOT NULL DEFAULT 0,
  subscriptions      INT NOT NULL DEFAULT 0,
  renewals            INT NOT NULL DEFAULT 0,
  views              INT NOT NULL DEFAULT 0,
  downloads           INT NOT NULL DEFAULT 0,
  PRIMARY KEY (stat_date, city_id)
);
