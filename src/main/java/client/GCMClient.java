package client;

import common.Request;
import common.Response;
import ocsf.client.AbstractClient;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * GCM Client - handles server communication.
 * Uses a callback interface to notify any controller of messages.
 */
public class GCMClient extends AbstractClient {

    /**
     * Interface for controllers that can receive messages.
     */
    public interface MessageHandler {
        void displayMessage(Object msg);
    }

    public static final String GCMClient = null;

    /** Default server address (overridden by {@link #configureEndpoint} before first {@link #getInstance()}). */
    private static volatile String configuredHost = "localhost";
    private static volatile int configuredPort = 5555;

    private static GCMClient instance;
    private MessageHandler messageHandler;

    // User session info
    private Integer currentUserId;
    private String currentUsername;
    private String currentRole;

    // For synchronous requests: only the response matching this ID is put in the queue
    private volatile UUID pendingSyncRequestId = null;
    private BlockingQueue<Response> responseQueue = new LinkedBlockingQueue<>();

    /** Callbacks for async requests (requestId -> callback). Response is delivered on handler thread. */
    private final Map<UUID, Consumer<Response>> asyncCallbacks = new ConcurrentHashMap<>();

    /**
     * Private constructor to enforce Singleton pattern.
     */
    private GCMClient(String host, int port) throws IOException {
        super(host, port);
        openConnection();
        System.out.println("GCMClient: Connected to " + host + ":" + port);
    }

    /**
     * Set the server host and port before the first {@link #getInstance()} call (e.g. from {@code LoginApp}
     * using command-line args or system properties). Ignored once a connection has been created.
     */
    public static void configureEndpoint(String host, int port) {
        if (instance != null) {
            System.err.println("GCMClient: configureEndpoint ignored — client already created.");
            return;
        }
        if (host != null && !host.isBlank()) {
            configuredHost = host.trim();
        }
        if (port > 0 && port <= 65535) {
            configuredPort = port;
        }
    }

    public static String getConfiguredHost() {
        return configuredHost;
    }

    public static int getConfiguredPort() {
        return configuredPort;
    }

    /**
     * Get the singleton instance. Establishes connection if needed.
     */
    public static GCMClient getInstance() throws IOException {
        if (instance == null) {
            instance = new GCMClient(configuredHost, configuredPort);
        } else if (!instance.isConnected()) {
            instance.openConnection();
        }
        return instance;
    }

    /**
     * Ensure the client is connected; if not, try to reconnect.
     * Call this before sending to avoid "failed to send request" when connection was closed (e.g. after logout).
     *
     * @return true if connected (or reconnected), false if connection could not be established
     */
    public boolean ensureConnected() {
        if (isConnected()) return true;
        try {
            openConnection();
            return isConnected();
        } catch (IOException e) {
            System.err.println("GCMClient: Reconnect failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Set the current message handler (the active screen).
     */
    public void setMessageHandler(MessageHandler handler) {
        this.messageHandler = handler;
    }

    /**
     * Set current user session info after login.
     */
    public void setCurrentUser(Integer userId, String username, String role) {
        this.currentUserId = userId;
        this.currentUsername = username;
        this.currentRole = role;
    }

    /**
     * Clear user session on logout.
     */
    public void clearCurrentUser() {
        this.currentUserId = null;
        this.currentUsername = null;
        this.currentRole = null;
    }

    public Integer getCurrentUserId() {
        return currentUserId;
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public String getCurrentRole() {
        return currentRole;
    }

    /** Lock so only one thread sends at a time (avoids "stream active" / concurrent write errors). */
    private final Object sendLock = new Object();

    /**
     * Send a request synchronously and wait for response.
     * 
     * @param request The request to send
     * @return Response from server, or null on timeout/error
     */
    public Response sendRequestSync(Request request) {
        synchronized (sendLock) {
            try {
                responseQueue.clear();
                pendingSyncRequestId = request.getRequestId();
                sendToServer(request);

                Response response = responseQueue.poll(30, TimeUnit.SECONDS);
                if (response == null) {
                    System.err.println("GCMClient: Request timed out (id=" + request.getRequestId() + ")");
                }
                return response;
            } catch (IOException e) {
                System.err.println("GCMClient: Error sending request: " + e.getMessage());
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } finally {
                pendingSyncRequestId = null;
            }
        }
    }

    /**
     * Send a request asynchronously. The lock is held only while sending; when the response
     * arrives it is passed to the callback (invoked on the same thread as handleMessageFromServer).
     * Use this for operations that can take a long time (e.g. bot reply) so the user can still
     * perform other actions (e.g. escalate) without blocking.
     */
    public void sendRequestAsync(Request request, Consumer<Response> callback) {
        if (callback == null) return;
        asyncCallbacks.put(request.getRequestId(), callback);
        synchronized (sendLock) {
            try {
                sendToServer(request);
            } catch (IOException e) {
                System.err.println("GCMClient: Error sending async request: " + e.getMessage());
                asyncCallbacks.remove(request.getRequestId());
                callback.accept(null);
            }
        }
    }

    @Override
    protected void handleMessageFromServer(Object msg) {
        System.out.println("GCMClient: handleMessageFromServer called with: " + msg.getClass().getName());

        // If it's a Response, deliver to sync waiter or async callback
        if (msg instanceof Response) {
            Response resp = (Response) msg;
            if (pendingSyncRequestId != null && pendingSyncRequestId.equals(resp.getRequestId())) {
                responseQueue.offer(resp);
            } else {
                Consumer<Response> async = asyncCallbacks.remove(resp.getRequestId());
                if (async != null) {
                    async.accept(resp);
                }
            }
        }

        // Also notify handler if set
        if (messageHandler != null) {
            System.out.println("GCMClient: Calling messageHandler.displayMessage");
            messageHandler.displayMessage(msg);
        } else if (!(msg instanceof Response)) {
            System.out.println("GCMClient: Received message but no handler set: " + msg);
        }
    }

    @Override
    protected void connectionClosed() {
        System.out.println("GCMClient: Connection closed");
    }

    @Override
    protected void connectionException(Exception exception) {
        System.out.println("GCMClient: Connection exception: " + exception.getMessage());
    }
}
