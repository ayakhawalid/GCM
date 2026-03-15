package server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages user sessions. Single session per user: if a user is already logged in
 * somewhere, they must logout first before logging in from another place.
 */
public class SessionManager {

    // Singleton instance
    private static SessionManager instance;

    // Maps session token → SessionInfo
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    // Maps userId → single session token (one login per user at a time)
    private final Map<Integer, String> userSessions = new ConcurrentHashMap<>();

    // Maps connectionId → session token (for disconnect cleanup)
    private final Map<String, String> connectionSessions = new ConcurrentHashMap<>();

    private SessionManager() {
    }

    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    /**
     * Session information holder.
     * Phase 13: Added connectionId for disconnect cleanup.
     */
    public static class SessionInfo {
        public final int userId;
        public final String username;
        public final String role;
        public final long createdAt;
        public String connectionId; // Mutable - set when login completes

        public SessionInfo(int userId, String username, String role) {
            this.userId = userId;
            this.username = username;
            this.role = role;
            this.createdAt = System.currentTimeMillis();
            this.connectionId = null;
        }
    }

    /**
     * Check if a user is already logged in (has an active session).
     */
    public boolean isUserLoggedIn(int userId) {
        return userSessions.containsKey(userId);
    }

    /**
     * Create a new session for a user. Fails if user is already logged in
     * (single session per user).
     */
    public String createSession(int userId, String username, String role) {
        if (isUserLoggedIn(userId)) {
            System.out.println("⚠ User " + username + " already has active session - login denied");
            return null;
        }
        String token = UUID.randomUUID().toString();
        sessions.put(token, new SessionInfo(userId, username, role));
        userSessions.put(userId, token);
        System.out.println("✓ Session created for user: " + username + " (token: " + token.substring(0, 8) + "...)");
        return token;
    }

    /**
     * Associate a session with a connection ID for disconnect cleanup.
     * Called after successful login.
     * 
     * @param token        Session token
     * @param connectionId Connection identifier (e.g., client address)
     */
    public void setSessionConnection(String token, String connectionId) {
        SessionInfo info = sessions.get(token);
        if (info != null) {
            info.connectionId = connectionId;
            connectionSessions.put(connectionId, token);
            System.out.println("  → Session linked to connection: " + connectionId);
        }
    }

    /**
     * Validate a session token.
     * 
     * @param token Session token to validate
     * @return SessionInfo if valid, null otherwise
     */
    public SessionInfo validateSession(String token) {
        if (token == null)
            return null;
        return sessions.get(token);
    }

    /**
     * Invalidate a session (logout).
     * 
     * @param token Session token to invalidate
     * @return true if session was found and invalidated
     */
    public boolean invalidateSession(String token) {
        SessionInfo info = sessions.remove(token);
        if (info != null) {
            userSessions.remove(info.userId);
            if (info.connectionId != null) {
                connectionSessions.remove(info.connectionId);
            }
            System.out.println("✓ Session invalidated for user: " + info.username);
            return true;
        }
        return false;
    }

    /**
     * Invalidate session by connection ID (for disconnect cleanup).
     * Phase 13: Called when client disconnects unexpectedly.
     * 
     * @param connectionId Connection ID
     * @return true if session was found and invalidated
     */
    public boolean invalidateByConnectionId(String connectionId) {
        String token = connectionSessions.get(connectionId);
        if (token != null) {
            System.out.println("🔌 Connection lost - cleaning up session for: " + connectionId);
            return invalidateSession(token);
        }
        return false;
    }

    /**
     * Get session by connection ID.
     * 
     * @param connectionId Connection ID
     * @return SessionInfo or null
     */
    public SessionInfo getSessionByConnectionId(String connectionId) {
        String token = connectionSessions.get(connectionId);
        if (token != null) {
            return sessions.get(token);
        }
        return null;
    }

    /**
     * Get session token for a user (if logged in).
     */
    public String getSessionToken(int userId) {
        return userSessions.get(userId);
    }

    /**
     * Invalidate session by user ID (force logout).
     */
    public boolean invalidateUserSession(int userId) {
        String token = userSessions.get(userId);
        if (token != null) {
            return invalidateSession(token);
        }
        return false;
    }

    /**
     * Get count of active sessions.
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Get session statistics for monitoring.
     */
    public String getStats() {
        return String.format("Sessions[active=%d, connections=%d]",
                sessions.size(), connectionSessions.size());
    }
}
