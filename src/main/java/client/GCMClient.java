package client;

import common.Request;
import common.Response;
import ocsf.client.AbstractClient;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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

    private static GCMClient instance;
    private MessageHandler messageHandler;

    // User session info
    private Integer currentUserId;
    private String currentUsername;
    private String currentRole;

    // For synchronous requests
    private BlockingQueue<Response> responseQueue = new LinkedBlockingQueue<>();

    /**
     * Private constructor to enforce Singleton pattern.
     */
    private GCMClient(String host, int port) throws IOException {
        super(host, port);
        openConnection();
        System.out.println("GCMClient: Connected to server");
    }

    /**
     * Get the singleton instance. Establishes connection if needed.
     */
    /**
     * Get the singleton instance. Establishes connection if needed.
     */
    public static GCMClient getInstance() throws IOException {
        if (instance == null) {
            instance = new GCMClient("localhost", 5555);
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

    /**
     * Send a request synchronously and wait for response.
     * 
     * @param request The request to send
     * @return Response from server, or null on timeout/error
     */
    public Response sendRequestSync(Request request) {
        try {
            // Clear any stale responses
            responseQueue.clear();

            // Send request
            sendToServer(request);

            // Wait for response (30 second timeout)
            Response response = responseQueue.poll(30, TimeUnit.SECONDS);

            if (response == null) {
                System.err.println("GCMClient: Request timed out");
            }

            return response;

        } catch (IOException e) {
            System.err.println("GCMClient: Error sending request: " + e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    protected void handleMessageFromServer(Object msg) {
        System.out.println("GCMClient: handleMessageFromServer called with: " + msg.getClass().getName());

        // If it's a Response, add to queue for sync requests
        if (msg instanceof Response) {
            System.out.println("GCMClient: Adding Response to queue");
            responseQueue.offer((Response) msg);
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
