package server;

import common.City;
import common.MessageType;
import common.Request;
import common.Response;
import ocsf.server.AbstractServer;
import ocsf.server.ConnectionToClient;
import server.handler.MapEditHandler;
import server.handler.SearchHandler;
import server.handler.ApprovalHandler;
import server.handler.AuthHandler;
import server.handler.PurchaseHandler;
import server.handler.CustomerHandler;
import server.handler.NotificationHandler;
import server.handler.PricingHandler;
import server.handler.SupportHandler;
import server.scheduler.SubscriptionScheduler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GCM Server - Main server class handling client connections.
 * 
 * Phase 12: Multi-user concurrency with thread pool.
 * Phase 13: Session cleanup on client disconnect.
 * Phase 16: Proper thread management with named threads.
 */
public class GCMServer extends AbstractServer {

    // Thread pool for request handling (Phase 12)
    private static final int THREAD_POOL_SIZE = 10;
    private final ExecutorService requestExecutor;

    public GCMServer(int port) {
        super(port);

        // Create named thread pool for request handling (Phase 16)
        this.requestExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE, new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "GCM-RequestHandler-" + threadNumber.getAndIncrement());
                t.setDaemon(false);
                return t;
            }
        });
    }

    @Override
    protected void handleMessageFromClient(Object msg, ConnectionToClient client) {
        // Process in thread pool for concurrent request handling (Phase 12)
        requestExecutor.submit(() -> processClientMessage(msg, client));
    }

    /**
     * Process a client message (runs in thread pool).
     */
    private void processClientMessage(Object msg, ConnectionToClient client) {
        String clientId = getClientId(client);
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println("[" + Thread.currentThread().getName() + "] Message from: " + clientId);

        try {
            // ==================== NEW PROTOCOL (Request/Response) ====================
            if (msg instanceof Request) {
                Request request = (Request) msg;
                System.out.println("Processing Request: " + request.getType());

                try {
                    Response response = dispatchRequest(request, clientId);
                    System.out.println("Sending Response: " + (response.isOk() ? "OK" : "ERROR"));
                    // ObjectOutputStream is not thread-safe: only one thread may send to a client at a time
                    synchronized (client) {
                        client.sendToClient(response);
                    }
                } catch (Exception e) {
                    System.out.println("!!! EXCEPTION in request handling or send: " + e.getMessage());
                    e.printStackTrace();
                }
                return;
            }

            // ==================== LEGACY PROTOCOL (String commands) ====================
            if (msg instanceof String) {
                synchronized (client) {
                    handleLegacyMessage((String) msg, client);
                }
                return;
            }

            System.out.println("Unknown message type: " + msg.getClass().getName());

        } catch (IOException e) {
            System.out.println("Error sending response to client: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get unique client identifier for session tracking.
     */
    private String getClientId(ConnectionToClient client) {
        return client.getInetAddress().getHostAddress() + ":" + client.hashCode();
    }

    /**
     * Dispatch a Request to the appropriate handler.
     * Phase 13: Pass clientId for session-connection linking.
     */
    private Response dispatchRequest(Request request, String clientId) {
        MessageType type = request.getType();

        // Search handlers (no authentication required)
        if (SearchHandler.canHandle(type)) {
            return SearchHandler.handle(request);
        }

        // Map editing handlers
        if (MapEditHandler.canHandle(type)) {
            return MapEditHandler.handle(request);
        }

        // Version approval handlers (Phase 3)
        if (ApprovalHandler.canHandle(type)) {
            return ApprovalHandler.handle(request);
        }

        // Authentication handlers (Phase 4)
        // Special handling to link session to connection
        if (AuthHandler.canHandle(type)) {
            Response response = AuthHandler.handle(request);

            // If login successful, link session to connection (Phase 13)
            if (type == MessageType.LOGIN && response.isOk() && request.getSessionToken() == null) {
                // Session token is inside the LoginResponse payload
                Object payload = response.getPayload();
                if (payload instanceof common.dto.LoginResponse) {
                    String newToken = ((common.dto.LoginResponse) payload).getSessionToken();
                    if (newToken != null) {
                        SessionManager.getInstance().setSessionConnection(newToken, clientId);
                    }
                }
            }
            return response;
        }

        // Purchase handlers (Phase 5)
        if (PurchaseHandler.canHandle(type)) {
            return PurchaseHandler.handle(request);
        }

        // Customer handlers (Phase 6)
        if (CustomerHandler.canHandle(type)) {
            return CustomerHandler.handle(request);
        }

        // Notification handlers (Phase 7)
        if (NotificationHandler.canHandle(type)) {
            return NotificationHandler.handle(request);
        }

        // Pricing handlers (Phase 8)
        if (PricingHandler.canHandle(type)) {
            return PricingHandler.handle(request);
        }

        // Support handlers (Phase 9)
        if (SupportHandler.canHandle(type)) {
            return SupportHandler.handle(request);
        }

        // Report handlers (Phase 10)
        if (server.handler.ReportHandler.canHandle(type)) {
            return server.handler.ReportHandler.handle(request);
        }

        // Legacy handlers (for backward compatibility)
        if (type == MessageType.LEGACY_GET_CITIES) {
            ArrayList<City> cities = MySQLController.getAllCities();
            return Response.success(request, cities);
        }

        if (type == MessageType.LEGACY_GET_MAPS) {
            // Expects cityId in payload
            if (request.getPayload() instanceof Integer) {
                int cityId = (Integer) request.getPayload();
                ArrayList<common.Map> maps = MySQLController.getMapsForCity(cityId);
                return Response.success(request, maps);
            }
            return Response.error(request, Response.ERR_VALIDATION, "City ID required");
        }

        return Response.error(request, Response.ERR_INTERNAL,
                "No handler for message type: " + type);
    }

    /**
     * Handle legacy string-based protocol (backward compatibility).
     */
    private void handleLegacyMessage(String request, ConnectionToClient client) throws IOException {
        System.out.println("Legacy message: " + request);

        // CASE 0: Login authentication (Format: "login [username] [password]")
        if (request.startsWith("login ")) {
            try {
                String[] parts = request.split(" ");
                if (parts.length >= 3) {
                    String username = parts[1];
                    String password = parts[2];

                    String[] authResult = MySQLController.authenticateUser(username, password);

                    if (authResult != null) {
                        client.sendToClient("login_success " + authResult[0] + " " + authResult[1]);
                    } else {
                        client.sendToClient("login_failed");
                    }
                } else {
                    client.sendToClient("login_failed");
                }
            } catch (Exception e) {
                client.sendToClient("login_failed");
                e.printStackTrace();
            }
        }

        // CASE 1: Get all cities
        else if (request.equals("get_cities")) {
            ArrayList<City> cities = MySQLController.getAllCities();
            client.sendToClient(cities);
        }

        // CASE 2: Get maps for a specific city (Format: "get_maps [id]")
        else if (request.startsWith("get_maps ")) {
            try {
                String idPart = request.trim().split("\\s+")[1];
                int cityId = Integer.parseInt(idPart);
                ArrayList<common.Map> maps = MySQLController.getMapsForCity(cityId);
                client.sendToClient(maps != null ? maps : new ArrayList<common.Map>());
            } catch (NumberFormatException e) {
                System.out.println("Error parsing ID for get_maps: " + e.getMessage());
                client.sendToClient("Error: City ID must be a number.");
            } catch (Exception e) {
                System.out.println("Error in get_maps: " + e.getMessage());
                client.sendToClient("Error: Could not load maps for city - " + e.getMessage());
            }
        }

        // CASE 3: Update Price (Format: "update_price [id] [new_price]")
        else if (request.startsWith("update_price ")) {
            try {
                String[] parts = request.split(" ");
                int cityId = Integer.parseInt(parts[1]);
                double newPrice = Double.parseDouble(parts[2]);

                boolean success = MySQLController.updateCityPrice(cityId, newPrice);

                if (success) {
                    client.sendToClient("Success: Price updated!");
                    client.sendToClient(MySQLController.getAllCities());
                } else {
                    client.sendToClient("Error: Could not update price.");
                }
            } catch (Exception e) {
                client.sendToClient("Error: Invalid update format.");
            }
        }
    }

    @Override
    protected void serverStarted() {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║          GCM SERVER STARTED SUCCESSFULLY                 ║");
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.println("║  Port: " + getPort() + "                                             ║");
        System.out.println("║  Thread pool: " + THREAD_POOL_SIZE + " request handlers                      ║");
        System.out.println("║  Protocol: Request/Response + Legacy String              ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        // Start subscription expiry scheduler (Phase 7)
        SubscriptionScheduler.getInstance().start();
    }

    @Override
    protected void serverStopped() {
        System.out.println("Server stopping...");

        // Shutdown request executor (Phase 12)
        requestExecutor.shutdown();
        try {
            if (!requestExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                requestExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            requestExecutor.shutdownNow();
        }

        // Close connection pool (Phase 12)
        DBConnector.closePool();

        System.out.println("✓ Server stopped");
    }

    @Override
    protected void clientConnected(ConnectionToClient client) {
        String clientId = getClientId(client);
        System.out.println("→ Client connected: " + clientId);
    }

    @Override
    protected synchronized void clientDisconnected(ConnectionToClient client) {
        String clientId = getClientId(client);
        System.out.println("← Client disconnected: " + clientId);

        // Phase 13: Clean up session on disconnect
        SessionManager.getInstance().invalidateByConnectionId(clientId);
    }

    // MAIN METHOD TO START THE SERVER
    public static void main(String[] args) {
        int port = 5555;
        GCMServer server = new GCMServer(port);

        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutdown signal received...");
            try {
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));

        try {
            server.listen();
        } catch (IOException e) {
            System.out.println("Error starting server");
            e.printStackTrace();
        }
    }
}