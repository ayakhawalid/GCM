package server.dao;

import common.DailyStat;
import server.DBConnector;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DailyStatsDAO {

    static {
        createTable();
    }

    private static void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS daily_stats (" +
                "stat_date DATE NOT NULL, " +
                "city_id INT NOT NULL, " +
                "maps_count INT NOT NULL DEFAULT 0, " +
                "one_time_purchases INT NOT NULL DEFAULT 0, " +
                "subscriptions INT NOT NULL DEFAULT 0, " +
                "renewals INT NOT NULL DEFAULT 0, " +
                "views INT NOT NULL DEFAULT 0, " +
                "downloads INT NOT NULL DEFAULT 0, " +
                "PRIMARY KEY (stat_date, city_id)" +
                ")";
        try (Connection conn = DBConnector.getConnection();
             Statement stmt = conn != null ? conn.createStatement() : null) {
            if (stmt != null) stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("DailyStatsDAO: Error creating daily_stats table: " + e.getMessage());
        }
    }

    // Metrics that can be incremented
    public enum Metric {
        MAPS_COUNT("maps_count"),
        PURCHASE_ONE_TIME("one_time_purchases"),
        PURCHASE_SUBSCRIPTION("subscriptions"),
        RENEWAL("renewals"),
        VIEW("views"),
        DOWNLOAD("downloads");

        private final String colName;

        Metric(String colName) {
            this.colName = colName;
        }

        public String getColumnName() {
            return colName;
        }
    }

    /**
     * Increment a specific metric for the current date and given city.
     * Uses ON DUPLICATE KEY UPDATE to handle non-existent rows atomically.
     */
    public static void increment(int cityId, Metric metric) {
        String col = metric.getColumnName();
        // date, city_id are key
        // if row exists, increment col. if not, insert row with col=1 (others default
        // 0)
        String query = "INSERT INTO daily_stats (stat_date, city_id, " + col + ") VALUES (?, ?, 1) " +
                "ON DUPLICATE KEY UPDATE " + col + " = " + col + " + 1";

        try {
            Connection conn = DBConnector.getConnection();
            if (conn == null)
                return;

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setDate(1, Date.valueOf(LocalDate.now())); // Current date
            stmt.setInt(2, cityId);

            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Error incrementing stat " + metric + ": " + e.getMessage());
            // Since table might not exist in early dev, fail silently or log
            e.printStackTrace();
        }
    }

    /**
     * Get stats for a date range and specific city.
     * If cityId is null, likely not supported by this method or we aggregate (but
     * requirement says per city or all).
     * This method returns a list of DailyStats (one per day).
     */
    public static List<DailyStat> getStats(LocalDate from, LocalDate to, Integer cityId) {
        List<DailyStat> results = new ArrayList<>();
        String query = "SELECT * FROM daily_stats WHERE stat_date BETWEEN ? AND ?";

        if (cityId != null) {
            query += " AND city_id = ?";
        } else {
            // If fetching for ALL cities, we might still want per-city rows,
            // OR we might want an aggregate.
            // Requirement: "Report for all cities." probably means aggregate or breakdown?
            // "Must show per city" implies list of cities?
            // "Report for specific city" -> show that city's stats.

            // Let's assume we return raw rows and client or service aggregates.
            // Or we sort by date.
            query += " ORDER BY stat_date, city_id";
        }

        try {
            Connection conn = DBConnector.getConnection();
            if (conn == null)
                return results;

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setDate(1, Date.valueOf(from));
            stmt.setDate(2, Date.valueOf(to));

            if (cityId != null) {
                stmt.setInt(3, cityId);
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                DailyStat stat = new DailyStat(
                        rs.getDate("stat_date").toLocalDate(),
                        rs.getInt("city_id"),
                        rs.getInt("maps_count"),
                        rs.getInt("one_time_purchases"),
                        rs.getInt("subscriptions"),
                        rs.getInt("renewals"),
                        rs.getInt("views"),
                        rs.getInt("downloads"));
                results.add(stat);
            }

        } catch (SQLException e) {
            System.out.println("Error getting stats: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }

    /**
     * Aggregate stats for ALL cities in range (Summed up).
     * Returns a list of DailyStat where cityId is 0 or -1 (globally aggregated per
     * day).
     */
    public static List<DailyStat> getGlobalStatsPerDay(LocalDate from, LocalDate to) {
        List<DailyStat> results = new ArrayList<>();
        String query = "SELECT stat_date, " +
                "SUM(maps_count) as maps_count, " +
                "SUM(one_time_purchases) as one_time_purchases, " +
                "SUM(subscriptions) as subscriptions, " +
                "SUM(renewals) as renewals, " +
                "SUM(views) as views, " +
                "SUM(downloads) as downloads " +
                "FROM daily_stats " +
                "WHERE stat_date BETWEEN ? AND ? " +
                "GROUP BY stat_date " +
                "ORDER BY stat_date";

        try {
            Connection conn = DBConnector.getConnection();
            if (conn == null)
                return results;

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setDate(1, Date.valueOf(from));
            stmt.setDate(2, Date.valueOf(to));

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                DailyStat stat = new DailyStat(
                        rs.getDate("stat_date").toLocalDate(),
                        0, // Global ID
                        rs.getInt("maps_count"),
                        rs.getInt("one_time_purchases"),
                        rs.getInt("subscriptions"),
                        rs.getInt("renewals"),
                        rs.getInt("views"),
                        rs.getInt("downloads"));
                results.add(stat);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return results;
    }

    /**
     * Per-city totals over a date range for "all cities" report (histogram grouped by city).
     * Returns one DailyStat per city with summed metrics; date is set to from for display.
     */
    public static List<DailyStat> getPerCityTotals(LocalDate from, LocalDate to) {
        List<DailyStat> results = new ArrayList<>();
        String query = "SELECT city_id, " +
                "SUM(maps_count) as maps_count, " +
                "SUM(one_time_purchases) as one_time_purchases, " +
                "SUM(subscriptions) as subscriptions, " +
                "SUM(renewals) as renewals, " +
                "SUM(views) as views, " +
                "SUM(downloads) as downloads " +
                "FROM daily_stats " +
                "WHERE stat_date BETWEEN ? AND ? AND city_id > 0 " +
                "GROUP BY city_id " +
                "ORDER BY city_id";
        try (Connection conn = DBConnector.getConnection()) {
            if (conn == null) return results;
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setDate(1, Date.valueOf(from));
                stmt.setDate(2, Date.valueOf(to));
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    results.add(new DailyStat(
                            from,
                            rs.getInt("city_id"),
                            rs.getInt("maps_count"),
                            rs.getInt("one_time_purchases"),
                            rs.getInt("subscriptions"),
                            rs.getInt("renewals"),
                            rs.getInt("views"),
                            rs.getInt("downloads")));
                }
            }
        } catch (SQLException e) {
            System.out.println("Error getting per-city stats: " + e.getMessage());
            e.printStackTrace();
        }
        return results;
    }
}
