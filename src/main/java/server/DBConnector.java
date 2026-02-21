package server;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Database connection manager using HikariCP connection pool.
 * 
 * Phase 12: Multi-user concurrency support with connection pooling.
 * - Thread-safe connection pool
 * - Automatic connection lifecycle management
 * - Configurable pool size and timeouts
 */
public class DBConnector {

    // Database configuration
    private static final String URL = "jdbc:mysql://localhost:3306/gcm_db?serverTimezone=Asia/Jerusalem";
    private static final String USER = "root";
    private static final String PASS = "root";

    // Pool configuration
    private static final int MAX_POOL_SIZE = 10;
    private static final int MIN_IDLE = 2;
    private static final long CONNECTION_TIMEOUT_MS = 30000;
    private static final long IDLE_TIMEOUT_MS = 600000;
    private static final long MAX_LIFETIME_MS = 1800000;

    // HikariCP data source (connection pool)
    private static HikariDataSource dataSource;
    private static boolean poolInitialized = false;

    /**
     * Initialize the connection pool.
     * Called automatically on first connection request.
     */
    private static synchronized void initializePool() {
        if (poolInitialized) {
            return;
        }

        try {
            HikariConfig config = new HikariConfig();

            // Database connection settings
            config.setJdbcUrl(URL);
            config.setUsername(USER);
            config.setPassword(PASS);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // Pool size settings
            config.setMaximumPoolSize(MAX_POOL_SIZE);
            config.setMinimumIdle(MIN_IDLE);

            // Timeout settings
            config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
            config.setIdleTimeout(IDLE_TIMEOUT_MS);
            config.setMaxLifetime(MAX_LIFETIME_MS);

            // Pool name for monitoring
            config.setPoolName("GCM-DB-Pool");

            // Connection test query
            config.setConnectionTestQuery("SELECT 1");

            // Performance optimizations
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            dataSource = new HikariDataSource(config);
            poolInitialized = true;

            System.out.println("╔══════════════════════════════════════════════════════════╗");
            System.out.println("║        DATABASE CONNECTION POOL INITIALIZED              ║");
            System.out.println("╠══════════════════════════════════════════════════════════╣");
            System.out.println("║  Pool: " + config.getPoolName() + "                                        ║");
            System.out.println("║  Max connections: " + MAX_POOL_SIZE + "                                     ║");
            System.out.println("║  Min idle: " + MIN_IDLE + "                                              ║");
            System.out.println("╚══════════════════════════════════════════════════════════╝");

        } catch (Exception e) {
            System.err.println("Failed to initialize connection pool: " + e.getMessage());
            e.printStackTrace();
            poolInitialized = false;
        }
    }

    /**
     * Get a database connection from the pool.
     * Connection MUST be closed after use to return it to the pool.
     * Use try-with-resources pattern.
     * 
     * @return Database connection from pool
     */
    public static Connection getConnection() {
        if (!poolInitialized) {
            initializePool();
        }

        if (dataSource == null) {
            System.err.println("Database Connection Failed! Pool not initialized.");
            System.err.println("  1. Check if MySQL is running");
            System.err.println("  2. Check if database 'gcm_db' exists");
            System.err.println("  3. Check credentials in DBConnector");
            return null;
        }

        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            System.err.println("Failed to get connection from pool: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get pool statistics for monitoring.
     * 
     * @return String with pool stats
     */
    public static String getPoolStats() {
        if (dataSource == null) {
            return "Pool not initialized";
        }
        return String.format(
                "Pool[active=%d, idle=%d, total=%d, waiting=%d]",
                dataSource.getHikariPoolMXBean().getActiveConnections(),
                dataSource.getHikariPoolMXBean().getIdleConnections(),
                dataSource.getHikariPoolMXBean().getTotalConnections(),
                dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection());
    }

    /**
     * Close the connection pool.
     * Call this on server shutdown for graceful cleanup.
     */
    public static synchronized void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            System.out.println("Closing database connection pool...");
            dataSource.close();
            poolInitialized = false;
            System.out.println("✓ Database connection pool closed");
        }
    }

    /**
     * Test connection to database.
     */
    public static void main(String[] args) {
        Connection conn = getConnection();
        if (conn != null) {
            System.out.println("SUCCESS: Connected to Database!");
            System.out.println(getPoolStats());
            try {
                conn.close(); // Return to pool
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        closePool();
    }
}