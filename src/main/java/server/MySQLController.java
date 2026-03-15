package server;

import common.City;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class MySQLController {

    /**
     * Authenticates a user against the database.
     * 
     * @param username The username to check
     * @param password The password to verify (plain text for testing)
     * @return String array: [0]=role, [1]=isActive ("true"/"false"), or null if
     *         authentication fails
     */
    public static String[] authenticateUser(String username, String password) {
        String query = "SELECT role, is_active FROM users WHERE username = ? AND password_hash = ?";

        try {
            Connection conn = DBConnector.getConnection();
            if (conn == null) {
                System.out.println("Database connection failed during authentication");
                return null;
            }

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String role = rs.getString("role");
                boolean isActive = rs.getBoolean("is_active");
                System.out.println("User authenticated: " + username + ", Role: " + role);
                return new String[] { role, String.valueOf(isActive) };
            } else {
                System.out.println("Authentication failed for user: " + username);
                return null;
            }
        } catch (SQLException e) {
            System.out.println("Error during authentication:");
            e.printStackTrace();
            return null;
        }
    }

    public static ArrayList<City> getAllCities() {
        ArrayList<City> cities = new ArrayList<>();
        String query = "SELECT * FROM cities";

        try {
            Connection conn = DBConnector.getConnection();
            if (conn == null) {
                System.out.println("Database connection failed");
                return cities;
            }

            PreparedStatement stmt = conn.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                // Convert SQL row to Java Object
                City c = new City(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getDouble("price"));
                cities.add(c);
            }
            System.out.println("Retrieved " + cities.size() + " cities from database");
        } catch (SQLException e) {
            System.out.println("Error getting cities:");
            e.printStackTrace();
        }
        return cities;
    }

    public static ArrayList<common.Map> getMapsForCity(int cityId) {
        ArrayList<common.Map> maps = new ArrayList<>();
        String query = "SELECT * FROM maps WHERE city_id = ?";

        try {
            Connection conn = DBConnector.getConnection();
            if (conn == null)
                return maps;

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setInt(1, cityId); // Put the ID into the query
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                common.Map m = new common.Map(
                        rs.getInt("id"),
                        rs.getString("description"),
                        rs.getInt("city_id"));
                maps.add(m);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return maps;
    }

    public static boolean updateCityPrice(int cityId, double newPrice) {
        String query = "UPDATE cities SET price = ? WHERE id = ?";
        try {
            Connection conn = DBConnector.getConnection();
            if (conn == null)
                return false;

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setDouble(1, newPrice); // Set the new price
            stmt.setInt(2, cityId); // Set the City ID

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0; // Returns true if it worked

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Quick test to make sure this works
    public static void main(String[] args) {
        ArrayList<City> list = getAllCities();
        System.out.println("Found cities: " + list);

        // Test authentication
        String[] result = authenticateUser("admin", "admin123");
        if (result != null) {
            System.out.println("Auth test: Role=" + result[0] + ", Subscribed=" + result[1]);
        } else {
            System.out.println("Auth test: FAILED");
        }
    }
}