package server.dao;

import common.dto.SupportTicketDTO;
import common.dto.TicketMessageDTO;
import server.DBConnector;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Data Access Object for support tickets and messages.
 * Handles ticket CRUD, similarity detection, and agent assignment.
 */
public class SupportDAO {

    // Similarity threshold for duplicate detection (50%)
    private static final double SIMILARITY_THRESHOLD = 0.5;
    // Only check tickets from the last 7 days for similarity
    private static final int SIMILARITY_DAYS_LOOKBACK = 7;

    /**
     * Create a new support ticket with initial message.
     *
     * @param userId   User creating the ticket
     * @param subject  Ticket subject
     * @param message  Initial message text
     * @param priority Ticket priority
     * @return Created ticket ID, or -1 on failure
     */
    public static int createTicket(int userId, String subject, String message,
            SupportTicketDTO.Priority priority) {
        String ticketSql = "INSERT INTO support_tickets (user_id, subject, status, priority) VALUES (?, ?, 'OPEN', ?)";
        String messageSql = "INSERT INTO ticket_messages (ticket_id, sender_type, sender_id, message) VALUES (?, 'CUSTOMER', ?, ?)";

        try (Connection conn = DBConnector.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Insert ticket
                int ticketId;
                try (PreparedStatement stmt = conn.prepareStatement(ticketSql, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setInt(1, userId);
                    stmt.setString(2, subject);
                    stmt.setString(3, priority.name());
                    stmt.executeUpdate();

                    ResultSet keys = stmt.getGeneratedKeys();
                    if (keys.next()) {
                        ticketId = keys.getInt(1);
                    } else {
                        throw new SQLException("Failed to get ticket ID");
                    }
                }

                // Insert initial message
                try (PreparedStatement stmt = conn.prepareStatement(messageSql)) {
                    stmt.setInt(1, ticketId);
                    stmt.setInt(2, userId);
                    stmt.setString(3, message);
                    stmt.executeUpdate();
                }

                conn.commit();
                System.out.println("[SupportDAO] Created ticket #" + ticketId + " for user " + userId);
                return ticketId;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("[SupportDAO] Failed to create ticket: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Find similar recent ticket for duplicate detection.
     * Uses keyword overlap algorithm.
     *
     * @param userId  User to check
     * @param subject New ticket subject
     * @return Existing ticket ID if similar found, or -1 if no match
     */
    public static int findSimilarRecentTicket(int userId, String subject) {
        String sql = "SELECT id, subject FROM support_tickets " +
                "WHERE user_id = ? AND status != 'CLOSED' " +
                "AND created_at > DATE_SUB(NOW(), INTERVAL ? DAY)";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            stmt.setInt(2, SIMILARITY_DAYS_LOOKBACK);

            ResultSet rs = stmt.executeQuery();
            Set<String> newKeywords = extractKeywords(subject);

            while (rs.next()) {
                int ticketId = rs.getInt("id");
                String existingSubject = rs.getString("subject");
                Set<String> existingKeywords = extractKeywords(existingSubject);

                double similarity = calculateSimilarity(newKeywords, existingKeywords);
                if (similarity >= SIMILARITY_THRESHOLD) {
                    System.out.println("[SupportDAO] Found similar ticket #" + ticketId +
                            " (similarity: " + String.format("%.0f%%", similarity * 100) + ")");
                    return ticketId;
                }
            }

        } catch (SQLException e) {
            System.err.println("[SupportDAO] Error finding similar ticket: " + e.getMessage());
        }

        return -1;
    }

    /**
     * Extract keywords from text for similarity comparison.
     */
    private static Set<String> extractKeywords(String text) {
        if (text == null)
            return new HashSet<>();

        // Convert to lowercase and split by non-word characters
        String[] words = text.toLowerCase().replaceAll("[^a-z0-9\\s]", "").split("\\s+");
        Set<String> keywords = new HashSet<>();

        // Filter out common words
        Set<String> stopWords = new HashSet<>(Arrays.asList(
                "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
                "have", "has", "had", "do", "does", "did", "will", "would", "could",
                "should", "may", "might", "must", "can", "to", "of", "in", "for",
                "on", "with", "at", "by", "from", "as", "into", "about", "like",
                "through", "after", "over", "between", "out", "against", "during",
                "without", "before", "under", "around", "among", "i", "my", "me",
                "you", "your", "we", "our", "they", "their", "it", "its", "this",
                "that", "these", "those", "what", "which", "who", "whom", "how", "why"));

        for (String word : words) {
            if (word.length() > 2 && !stopWords.contains(word)) {
                keywords.add(word);
            }
        }

        return keywords;
    }

    /**
     * Calculate Jaccard similarity between two keyword sets.
     */
    private static double calculateSimilarity(Set<String> set1, Set<String> set2) {
        if (set1.isEmpty() || set2.isEmpty())
            return 0.0;

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        return (double) intersection.size() / union.size();
    }

    /**
     * Get ticket by ID with all messages.
     */
    public static SupportTicketDTO getTicketById(int ticketId) {
        String ticketSql = "SELECT t.*, u.username, a.username as agent_name " +
                "FROM support_tickets t " +
                "JOIN users u ON t.user_id = u.id " +
                "LEFT JOIN users a ON t.assigned_agent_id = a.id " +
                "WHERE t.id = ?";
        String messagesSql = "SELECT m.*, u.username as sender_name " +
                "FROM ticket_messages m " +
                "LEFT JOIN users u ON m.sender_id = u.id " +
                "WHERE m.ticket_id = ? ORDER BY m.created_at ASC";

        try (Connection conn = DBConnector.getConnection()) {
            SupportTicketDTO ticket = null;

            // Get ticket
            try (PreparedStatement stmt = conn.prepareStatement(ticketSql)) {
                stmt.setInt(1, ticketId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    ticket = mapTicketFromResultSet(rs);
                }
            }

            if (ticket == null)
                return null;

            // Get messages
            try (PreparedStatement stmt = conn.prepareStatement(messagesSql)) {
                stmt.setInt(1, ticketId);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    ticket.addMessage(mapMessageFromResultSet(rs));
                }
            }

            return ticket;

        } catch (SQLException e) {
            System.err.println("[SupportDAO] Error getting ticket: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get all tickets for a user (customer view).
     */
    public static List<SupportTicketDTO> getTicketsForUser(int userId) {
        String sql = "SELECT t.*, u.username, a.username as agent_name " +
                "FROM support_tickets t " +
                "JOIN users u ON t.user_id = u.id " +
                "LEFT JOIN users a ON t.assigned_agent_id = a.id " +
                "WHERE t.user_id = ? ORDER BY t.created_at DESC";

        List<SupportTicketDTO> tickets = new ArrayList<>();

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                tickets.add(mapTicketFromResultSet(rs));
            }

        } catch (SQLException e) {
            System.err.println("[SupportDAO] Error getting user tickets: " + e.getMessage());
        }

        return tickets;
    }

    /**
     * Get tickets assigned to an agent.
     */
    public static List<SupportTicketDTO> getTicketsForAgent(int agentId) {
        String sql = "SELECT t.*, u.username, a.username as agent_name " +
                "FROM support_tickets t " +
                "JOIN users u ON t.user_id = u.id " +
                "LEFT JOIN users a ON t.assigned_agent_id = a.id " +
                "WHERE t.assigned_agent_id = ? AND t.status != 'CLOSED' " +
                "ORDER BY t.priority DESC, t.created_at ASC";

        List<SupportTicketDTO> tickets = new ArrayList<>();

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, agentId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                tickets.add(mapTicketFromResultSet(rs));
            }

        } catch (SQLException e) {
            System.err.println("[SupportDAO] Error getting agent tickets: " + e.getMessage());
        }

        return tickets;
    }

    /**
     * Get pending escalated tickets (unassigned).
     */
    public static List<SupportTicketDTO> getPendingEscalations() {
        String sql = "SELECT t.*, u.username, NULL as agent_name " +
                "FROM support_tickets t " +
                "JOIN users u ON t.user_id = u.id " +
                "WHERE t.status = 'ESCALATED' AND t.assigned_agent_id IS NULL " +
                "ORDER BY t.priority DESC, t.created_at ASC";

        List<SupportTicketDTO> tickets = new ArrayList<>();

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                tickets.add(mapTicketFromResultSet(rs));
            }

        } catch (SQLException e) {
            System.err.println("[SupportDAO] Error getting pending escalations: " + e.getMessage());
        }

        return tickets;
    }

    /**
     * Add a message to a ticket.
     */
    public static boolean addMessage(int ticketId, TicketMessageDTO.SenderType senderType,
            Integer senderId, String message) {
        String sql = "INSERT INTO ticket_messages (ticket_id, sender_type, sender_id, message) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, ticketId);
            stmt.setString(2, senderType.name());
            if (senderId != null) {
                stmt.setInt(3, senderId);
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            stmt.setString(4, message);

            int rows = stmt.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("[SupportDAO] Error adding message: " + e.getMessage());
            return false;
        }
    }

    /**
     * Update ticket status.
     */
    public static boolean updateTicketStatus(int ticketId, SupportTicketDTO.Status status) {
        String sql = "UPDATE support_tickets SET status = ? WHERE id = ?";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status.name());
            stmt.setInt(2, ticketId);

            int rows = stmt.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("[SupportDAO] Error updating status: " + e.getMessage());
            return false;
        }
    }

    /**
     * Assign agent to ticket.
     */
    public static boolean assignAgent(int ticketId, int agentId) {
        String sql = "UPDATE support_tickets SET assigned_agent_id = ? WHERE id = ?";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, agentId);
            stmt.setInt(2, ticketId);

            int rows = stmt.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("[SupportDAO] Error assigning agent: " + e.getMessage());
            return false;
        }
    }

    /**
     * Close a ticket.
     */
    public static boolean closeTicket(int ticketId) {
        String sql = "UPDATE support_tickets SET status = 'CLOSED', closed_at = NOW() WHERE id = ?";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, ticketId);

            int rows = stmt.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("[SupportDAO] Error closing ticket: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get FAQ entries matching keywords.
     */
    public static List<FaqEntry> findMatchingFaq(String text) {
        Set<String> keywords = extractKeywords(text);
        if (keywords.isEmpty())
            return new ArrayList<>();

        String sql = "SELECT * FROM faq_entries";
        List<FaqEntry> matches = new ArrayList<>();

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String faqKeywords = rs.getString("keywords");
                Set<String> faqKeywordSet = new HashSet<>(Arrays.asList(faqKeywords.toLowerCase().split(",")));

                // Count matching keywords
                int matchCount = 0;
                for (String keyword : keywords) {
                    for (String faqKw : faqKeywordSet) {
                        if (faqKw.contains(keyword) || keyword.contains(faqKw)) {
                            matchCount++;
                            break;
                        }
                    }
                }

                if (matchCount > 0) {
                    FaqEntry entry = new FaqEntry();
                    entry.id = rs.getInt("id");
                    entry.keywords = faqKeywords;
                    entry.question = rs.getString("question");
                    entry.answer = rs.getString("answer");
                    entry.category = rs.getString("category");
                    entry.matchScore = matchCount;
                    matches.add(entry);
                }
            }

        } catch (SQLException e) {
            System.err.println("[SupportDAO] Error finding FAQ: " + e.getMessage());
        }

        // Sort by match score descending
        matches.sort((a, b) -> b.matchScore - a.matchScore);
        return matches;
    }

    /**
     * Increment FAQ usage count.
     */
    public static void incrementFaqUsage(int faqId) {
        String sql = "UPDATE faq_entries SET usage_count = usage_count + 1 WHERE id = ?";

        try (Connection conn = DBConnector.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, faqId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("[SupportDAO] Error incrementing FAQ usage: " + e.getMessage());
        }
    }

    /**
     * Map ResultSet to SupportTicketDTO.
     */
    private static SupportTicketDTO mapTicketFromResultSet(ResultSet rs) throws SQLException {
        SupportTicketDTO ticket = new SupportTicketDTO();
        ticket.setId(rs.getInt("id"));
        ticket.setUserId(rs.getInt("user_id"));
        ticket.setUsername(rs.getString("username"));
        ticket.setSubject(rs.getString("subject"));
        ticket.setStatus(SupportTicketDTO.Status.valueOf(rs.getString("status")));
        ticket.setPriority(SupportTicketDTO.Priority.valueOf(rs.getString("priority")));

        int agentId = rs.getInt("assigned_agent_id");
        if (!rs.wasNull()) {
            ticket.setAssignedAgentId(agentId);
            ticket.setAssignedAgentName(rs.getString("agent_name"));
        }

        ticket.setCreatedAt(rs.getTimestamp("created_at"));
        ticket.setClosedAt(rs.getTimestamp("closed_at"));

        return ticket;
    }

    /**
     * Map ResultSet to TicketMessageDTO.
     */
    private static TicketMessageDTO mapMessageFromResultSet(ResultSet rs) throws SQLException {
        TicketMessageDTO msg = new TicketMessageDTO();
        msg.setId(rs.getInt("id"));
        msg.setTicketId(rs.getInt("ticket_id"));
        msg.setSenderType(TicketMessageDTO.SenderType.valueOf(rs.getString("sender_type")));

        int senderId = rs.getInt("sender_id");
        if (!rs.wasNull()) {
            msg.setSenderId(senderId);
            msg.setSenderName(rs.getString("sender_name"));
        }

        msg.setMessage(rs.getString("message"));
        msg.setCreatedAt(rs.getTimestamp("created_at"));

        return msg;
    }

    /**
     * FAQ entry helper class.
     */
    public static class FaqEntry {
        public int id;
        public String keywords;
        public String question;
        public String answer;
        public String category;
        public int matchScore;
    }
}
