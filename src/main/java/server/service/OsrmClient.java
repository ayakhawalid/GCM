package server.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Client for OSRM public API to compute shortest road distance between two points.
 * Uses driving profile; distance returned in meters.
 */
public class OsrmClient {

    private static final String OSRM_BASE = "https://router.project-osrm.org/route/v1/driving";
    private static final int TIMEOUT_SECONDS = 10;

    private final HttpClient httpClient;

    public OsrmClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Get shortest road distance in meters between two (lat, lon) points.
     * OSRM expects coordinates as longitude,latitude.
     *
     * @param lat1 latitude of first point
     * @param lon1 longitude of first point
     * @param lat2 latitude of second point
     * @param lon2 longitude of second point
     * @return distance in meters, or null if request failed or no route found
     */
    public Double getDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        // OSRM format: {lon},{lat};{lon},{lat}
        String coords = lon1 + "," + lat1 + ";" + lon2 + "," + lat2;
        String url = OSRM_BASE + "/" + coords + "?overview=false";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return null;
            }
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!"Ok".equals(root.has("code") ? root.get("code").getAsString() : "")) {
                return null;
            }
            if (!root.has("routes") || !root.get("routes").isJsonArray()) {
                return null;
            }
            JsonElement routes = root.get("routes");
            if (routes.getAsJsonArray().isEmpty()) {
                return null;
            }
            JsonObject route = routes.getAsJsonArray().get(0).getAsJsonObject();
            if (!route.has("distance")) {
                return null;
            }
            return route.get("distance").getAsDouble();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static final OsrmClient INSTANCE = new OsrmClient();

    /** Shared instance for server-side use. */
    public static OsrmClient getInstance() {
        return INSTANCE;
    }
}
