package server.handler;

import common.MessageType;
import common.Request;
import common.Response;
import common.dto.CreateTicketRequest;
import common.dto.SupportTicketDTO;
import common.dto.TicketMessageDTO;
import server.SessionManager;
import server.dao.SupportDAO;
import server.dao.AuditLogDAO;
import server.service.BotService;
import server.service.BotService.BotResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for all support ticket operations.
 * Manages ticket creation, escalation, and agent interactions.
 */
@SuppressWarnings("unused") // Suppress false positive dead code warnings on null checks
public class SupportHandler {

    // Action names for audit logging
    public static final String ACTION_TICKET_CREATED = "TICKET_CREATED";
    public static final String ACTION_TICKET_ESCALATED = "TICKET_ESCALATED";
    public static final String ACTION_TICKET_CLOSED = "TICKET_CLOSED";
    public static final String ACTION_AGENT_ASSIGNED = "AGENT_ASSIGNED";
    public static final String ACTION_AGENT_REPLIED = "AGENT_REPLIED";

    /**
     * Check if this handler can process the given message type.
     */
    public static boolean canHandle(MessageType type) {
        switch (type) {
            case CREATE_TICKET:
            case GET_MY_TICKETS:
            case GET_TICKET_DETAILS:
            case CLOSE_TICKET:
            case ESCALATE_TICKET:
            case CUSTOMER_REPLY:
            case AGENT_LIST_ASSIGNED:
            case AGENT_LIST_PENDING:
            case AGENT_CLAIM_TICKET:
            case AGENT_REPLY:
            case AGENT_CLOSE_TICKET:
                return true;
            default:
                return false;
        }
    }

    /**
     * Handle a support-related request.
     */
    public static Response handle(Request request) {
        MessageType type = request.getType();
        System.out.println("[SupportHandler] Handling: " + type);

        try {
            switch (type) {
                case CREATE_TICKET:
                    return handleCreateTicket(request);
                case GET_MY_TICKETS:
                    return handleGetMyTickets(request);
                case GET_TICKET_DETAILS:
                    return handleGetTicketDetails(request);
                case CLOSE_TICKET:
                    return handleCloseTicket(request);
                case ESCALATE_TICKET:
                    return handleEscalateTicket(request);
                case CUSTOMER_REPLY:
                    return handleCustomerReply(request);
                case AGENT_LIST_ASSIGNED:
                    return handleAgentListAssigned(request);
                case AGENT_LIST_PENDING:
                    return handleAgentListPending(request);
                case AGENT_CLAIM_TICKET:
                    return handleAgentClaimTicket(request);
                case AGENT_REPLY:
                    return handleAgentReply(request);
                case AGENT_CLOSE_TICKET:
                    return handleAgentCloseTicket(request);
                default:
                    return Response.error(request, Response.ERR_INTERNAL, "Unknown support operation");
            }
        } catch (Exception e) {
            System.err.println("[SupportHandler] Error: " + e.getMessage());
            e.printStackTrace();
            return Response.error(request, Response.ERR_INTERNAL, "Support operation failed: " + e.getMessage());
        }
    }

    /**
     * Create a new support ticket.
     * Checks for similar tickets, creates ticket, and generates bot response.
     */
    private static Response handleCreateTicket(Request request) {
        Integer userId = request.getUserId();
        if (userId == null) {
            return Response.error(request, Response.ERR_AUTHENTICATION, "Authentication required");
        }

        Object payload = request.getPayload();
        if (!(payload instanceof CreateTicketRequest)) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid request format");
        }

        CreateTicketRequest ticketReq = (CreateTicketRequest) payload;

        // Validate input
        if (ticketReq.getSubject() == null || ticketReq.getSubject().trim().isEmpty()) {
            return Response.error(request, Response.ERR_VALIDATION, "Subject is required");
        }
        if (ticketReq.getMessage() == null || ticketReq.getMessage().trim().isEmpty()) {
            return Response.error(request, Response.ERR_VALIDATION, "Message is required");
        }

        // Check for similar recent ticket
        int similarTicketId = SupportDAO.findSimilarRecentTicket(userId, ticketReq.getSubject());
        if (similarTicketId > 0) {
            // Link to existing ticket instead of creating duplicate
            SupportDAO.addMessage(similarTicketId, TicketMessageDTO.SenderType.CUSTOMER,
                    userId, ticketReq.getMessage());

            SupportTicketDTO existingTicket = SupportDAO.getTicketById(similarTicketId);

            Map<String, Object> result = new HashMap<>();
            result.put("ticket", existingTicket);
            result.put("wasDuplicate", true);
            result.put("message", "Your message was added to an existing similar ticket (#" + similarTicketId + ")");

            return Response.success(request, result);
        }

        // Create new ticket
        int ticketId = SupportDAO.createTicket(userId, ticketReq.getSubject().trim(),
                ticketReq.getMessage().trim(), ticketReq.getPriority());
        if (ticketId <= 0) {
            return Response.error(request, Response.ERR_INTERNAL, "Failed to create ticket");
        }

        // Generate bot response
        BotResult botResult = BotService.generateResponse(ticketReq.getSubject(), ticketReq.getMessage(), userId);
        BotService.addBotResponseToTicket(ticketId, botResult);

        // Log the action
        auditLog(ACTION_TICKET_CREATED, userId, "SUPPORT_TICKET", ticketId,
                "subject", ticketReq.getSubject(), "autoEscalated", String.valueOf(botResult.shouldAutoEscalate));

        // Return created ticket with messages
        SupportTicketDTO ticket = SupportDAO.getTicketById(ticketId);

        Map<String, Object> result = new HashMap<>();
        result.put("ticket", ticket);
        result.put("wasDuplicate", false);
        result.put("message", "Ticket created successfully");

        return Response.success(request, result);
    }

    /**
     * Get current user's tickets.
     */
    private static Response handleGetMyTickets(Request request) {
        Integer userId = request.getUserId();
        if (userId == null) {
            return Response.error(request, Response.ERR_AUTHENTICATION, "Authentication required");
        }

        List<SupportTicketDTO> tickets = SupportDAO.getTicketsForUser(userId);
        return Response.success(request, tickets);
    }

    /**
     * Get ticket details with all messages.
     */
    private static Response handleGetTicketDetails(Request request) {
        Integer userId = resolveAgentId(request);
        if (userId == null) {
            return Response.error(request, Response.ERR_AUTHENTICATION, "Authentication required");
        }

        Object payload = request.getPayload();
        if (!(payload instanceof Integer)) {
            return Response.error(request, Response.ERR_VALIDATION, "Ticket ID required");
        }

        int ticketId = (Integer) payload;
        SupportTicketDTO ticket = SupportDAO.getTicketById(ticketId);

        if (ticket == null) {
            return Response.error(request, Response.ERR_NOT_FOUND, "Ticket not found");
        }

        // Verify ownership (agents can view all tickets - simplified role check)
        if (ticket.getUserId() != userId) {
            // Allow agents to view any ticket - for now we trust the client
            // A full implementation would check the role from session
        }

        return Response.success(request, ticket);
    }

    /**
     * Close a ticket (customer satisfied).
     */
    private static Response handleCloseTicket(Request request) {
        Integer userId = request.getUserId();
        if (userId == null) {
            return Response.error(request, Response.ERR_AUTHENTICATION, "Authentication required");
        }

        Object payload = request.getPayload();
        if (!(payload instanceof Integer)) {
            return Response.error(request, Response.ERR_VALIDATION, "Ticket ID required");
        }

        int ticketId = (Integer) payload;
        SupportTicketDTO ticket = SupportDAO.getTicketById(ticketId);

        if (ticket == null) {
            return Response.error(request, Response.ERR_NOT_FOUND, "Ticket not found");
        }

        if (ticket.getUserId() != userId) {
            return Response.error(request, Response.ERR_FORBIDDEN, "Only ticket owner can close");
        }

        if (ticket.getStatus() == SupportTicketDTO.Status.CLOSED) {
            return Response.error(request, Response.ERR_VALIDATION, "Ticket already closed");
        }

        boolean success = SupportDAO.closeTicket(ticketId);
        if (!success) {
            return Response.error(request, Response.ERR_INTERNAL, "Failed to close ticket");
        }

        // Log the action
        auditLog(ACTION_TICKET_CLOSED, userId, "SUPPORT_TICKET", ticketId, "closedBy", "CUSTOMER");

        return Response.success(request, "Ticket closed successfully");
    }

    /**
     * Escalate ticket to human agent.
     */
    private static Response handleEscalateTicket(Request request) {
        Integer userId = request.getUserId();
        if (userId == null) {
            return Response.error(request, Response.ERR_AUTHENTICATION, "Authentication required");
        }

        Object payload = request.getPayload();
        if (!(payload instanceof Integer)) {
            return Response.error(request, Response.ERR_VALIDATION, "Ticket ID required");
        }

        int ticketId = (Integer) payload;
        SupportTicketDTO ticket = SupportDAO.getTicketById(ticketId);

        if (ticket == null) {
            return Response.error(request, Response.ERR_NOT_FOUND, "Ticket not found");
        }

        if (ticket.getUserId() != userId) {
            return Response.error(request, Response.ERR_FORBIDDEN, "Only ticket owner can escalate");
        }

        if (ticket.getStatus() == SupportTicketDTO.Status.CLOSED) {
            return Response.error(request, Response.ERR_VALIDATION, "Cannot escalate closed ticket");
        }

        if (ticket.getStatus() == SupportTicketDTO.Status.ESCALATED) {
            return Response.success(request, "Ticket is already escalated");
        }

        boolean success = SupportDAO.updateTicketStatus(ticketId, SupportTicketDTO.Status.ESCALATED);
        if (!success) {
            return Response.error(request, Response.ERR_INTERNAL, "Failed to escalate ticket");
        }

        // Add system message
        SupportDAO.addMessage(ticketId, TicketMessageDTO.SenderType.BOT, null,
                "This ticket has been escalated to our support team. An agent will be assigned shortly.");

        // Log the action
        auditLog(ACTION_TICKET_ESCALATED, userId, "SUPPORT_TICKET", ticketId);

        return Response.success(request, "Ticket escalated to support team");
    }

    /**
     * Agent: Get assigned tickets.
     */
    private static Response handleAgentListAssigned(Request request) {
        Integer agentId = resolveAgentId(request);
        if (agentId == null) {
            return Response.error(request, Response.ERR_AUTHENTICATION, "Authentication required");
        }

        List<SupportTicketDTO> tickets = SupportDAO.getTicketsForAgent(agentId);
        return Response.success(request, tickets);
    }

    /**
     * Resolve agent/user ID from request (userId or session token).
     */
    private static Integer resolveAgentId(Request request) {
        int uid = request.getUserId();
        if (uid > 0) return uid;
        String token = request.getSessionToken();
        if (token != null && !token.isEmpty()) {
            SessionManager.SessionInfo info = SessionManager.getInstance().validateSession(token);
            if (info != null) return info.userId;
        }
        return null;
    }

    /**
     * Agent: Get pending escalations (unassigned).
     */
    private static Response handleAgentListPending(Request request) {
        Integer agentId = resolveAgentId(request);
        if (agentId == null) {
            return Response.error(request, Response.ERR_AUTHENTICATION, "Authentication required");
        }

        List<SupportTicketDTO> tickets = SupportDAO.getPendingEscalations();
        return Response.success(request, tickets);
    }

    /**
     * Agent: Claim an unassigned ticket.
     */
    private static Response handleAgentClaimTicket(Request request) {
        Integer agentId = resolveAgentId(request);
        if (agentId == null) {
            return Response.error(request, Response.ERR_AUTHENTICATION, "Authentication required");
        }

        Object payload = request.getPayload();
        if (!(payload instanceof Integer)) {
            return Response.error(request, Response.ERR_VALIDATION, "Ticket ID required");
        }

        int ticketId = (Integer) payload;
        SupportTicketDTO ticket = SupportDAO.getTicketById(ticketId);

        if (ticket == null) {
            return Response.error(request, Response.ERR_NOT_FOUND, "Ticket not found");
        }

        if (ticket.getAssignedAgentId() != null) {
            return Response.error(request, Response.ERR_VALIDATION, "Ticket already assigned to another agent");
        }

        boolean success = SupportDAO.assignAgent(ticketId, agentId);
        if (!success) {
            return Response.error(request, Response.ERR_INTERNAL, "Failed to claim ticket");
        }

        // Add notification message
        SupportDAO.addMessage(ticketId, TicketMessageDTO.SenderType.BOT, null,
                "A support agent has been assigned to your ticket.");

        // Log the action
        auditLog(ACTION_AGENT_ASSIGNED, agentId, "SUPPORT_TICKET", ticketId, "agentId", String.valueOf(agentId));

        return Response.success(request, "Ticket claimed successfully");
    }

    /**
     * Agent: Send reply to customer.
     */
    @SuppressWarnings("unchecked")
    private static Response handleAgentReply(Request request) {
        Integer agentId = resolveAgentId(request);
        if (agentId == null) {
            return Response.error(request, Response.ERR_AUTHENTICATION, "Authentication required");
        }

        Object payload = request.getPayload();
        if (!(payload instanceof Map)) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid request format");
        }

        Map<String, Object> data = (Map<String, Object>) payload;
        Integer ticketId = (Integer) data.get("ticketId");
        String message = (String) data.get("message");

        if (ticketId == null || message == null || message.trim().isEmpty()) {
            return Response.error(request, Response.ERR_VALIDATION, "Ticket ID and message required");
        }

        SupportTicketDTO ticket = SupportDAO.getTicketById(ticketId);
        if (ticket == null) {
            return Response.error(request, Response.ERR_NOT_FOUND, "Ticket not found");
        }

        // Verify agent is assigned
        if (ticket.getAssignedAgentId() == null || !ticket.getAssignedAgentId().equals(agentId)) {
            return Response.error(request, Response.ERR_FORBIDDEN, "You are not assigned to this ticket");
        }

        boolean success = SupportDAO.addMessage(ticketId, TicketMessageDTO.SenderType.AGENT,
                agentId, message.trim());
        if (!success) {
            return Response.error(request, Response.ERR_INTERNAL, "Failed to send reply");
        }

        // Log the action
        auditLog(ACTION_AGENT_REPLIED, agentId, "SUPPORT_TICKET", ticketId);

        return Response.success(request, "Reply sent successfully");
    }

    /**
     * Customer: Send reply to an open or escalated ticket.
     */
    @SuppressWarnings("unchecked")
    private static Response handleCustomerReply(Request request) {
        Integer userId = request.getUserId();
        if (userId == null) {
            return Response.error(request, Response.ERR_AUTHENTICATION, "Authentication required");
        }

        Object payload = request.getPayload();
        if (!(payload instanceof Map)) {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid request format");
        }

        Map<String, Object> data = (Map<String, Object>) payload;
        Integer ticketId = (Integer) data.get("ticketId");
        String message = (String) data.get("message");

        if (ticketId == null || message == null || message.trim().isEmpty()) {
            return Response.error(request, Response.ERR_VALIDATION, "Ticket ID and message required");
        }

        SupportTicketDTO ticket = SupportDAO.getTicketById(ticketId);
        if (ticket == null) {
            return Response.error(request, Response.ERR_NOT_FOUND, "Ticket not found");
        }

        if (ticket.getUserId() != userId) {
            return Response.error(request, Response.ERR_FORBIDDEN, "Only ticket owner can reply");
        }

        if (ticket.getStatus() == SupportTicketDTO.Status.CLOSED) {
            return Response.error(request, Response.ERR_VALIDATION, "Cannot reply to a closed ticket");
        }

        boolean success = SupportDAO.addMessage(ticketId, TicketMessageDTO.SenderType.CUSTOMER,
                userId, message.trim());
        if (!success) {
            return Response.error(request, Response.ERR_INTERNAL, "Failed to send reply");
        }

        // Generate bot response if it's not escalated or agent assigned
        if (ticket.getStatus() == SupportTicketDTO.Status.OPEN
                || ticket.getStatus() == SupportTicketDTO.Status.BOT_RESPONDED) {
            BotResult botResult = BotService.generateResponse(ticket.getSubject(), message.trim(), userId);
            BotService.addBotResponseToTicket(ticketId, botResult);
        }

        // Log the action
        auditLog("CUSTOMER_REPLIED", userId, "SUPPORT_TICKET", ticketId);

        return Response.success(request, "Reply sent successfully");
    }

    /**
     * Agent: Close and resolve ticket.
     */
    @SuppressWarnings("unchecked")
    private static Response handleAgentCloseTicket(Request request) {
        Integer agentId = resolveAgentId(request);
        if (agentId == null) {
            return Response.error(request, Response.ERR_AUTHENTICATION, "Authentication required");
        }

        Object payload = request.getPayload();
        int ticketId;
        String closingMessage = null;

        if (payload instanceof Integer) {
            ticketId = (Integer) payload;
        } else if (payload instanceof Map) {
            Map<String, Object> data = (Map<String, Object>) payload;
            ticketId = (Integer) data.get("ticketId");
            closingMessage = (String) data.get("message");
        } else {
            return Response.error(request, Response.ERR_VALIDATION, "Invalid request format");
        }

        SupportTicketDTO ticket = SupportDAO.getTicketById(ticketId);
        if (ticket == null) {
            return Response.error(request, Response.ERR_NOT_FOUND, "Ticket not found");
        }

        if (ticket.getAssignedAgentId() == null || !ticket.getAssignedAgentId().equals(agentId)) {
            return Response.error(request, Response.ERR_FORBIDDEN, "You are not assigned to this ticket");
        }

        // Add closing message if provided
        if (closingMessage != null && !closingMessage.trim().isEmpty()) {
            SupportDAO.addMessage(ticketId, TicketMessageDTO.SenderType.AGENT, agentId, closingMessage.trim());
        }

        // Add system message
        SupportDAO.addMessage(ticketId, TicketMessageDTO.SenderType.BOT, null,
                "This ticket has been resolved and closed by our support team. Thank you for contacting us!");

        boolean success = SupportDAO.closeTicket(ticketId);
        if (!success) {
            return Response.error(request, Response.ERR_INTERNAL, "Failed to close ticket");
        }

        // Log the action
        auditLog(ACTION_TICKET_CLOSED, agentId, "SUPPORT_TICKET", ticketId, "closedBy", "AGENT");

        return Response.success(request, "Ticket resolved and closed");
    }

    /**
     * Helper to log audit entries with optional key-value pairs.
     */
    private static void auditLog(String action, int actorId, String entityType, int entityId, String... keyValues) {
        try {
            StringBuilder json = new StringBuilder("{");
            for (int i = 0; i < keyValues.length - 1; i += 2) {
                if (i > 0)
                    json.append(", ");
                json.append("\"").append(keyValues[i]).append("\": \"")
                        .append(keyValues[i + 1]).append("\"");
            }
            json.append("}");

            AuditLogDAO.logSimple(action, actorId, entityType, entityId,
                    keyValues.length > 0 ? json.toString() : null);
        } catch (Exception e) {
            System.err.println("[SupportHandler] Audit log failed: " + e.getMessage());
        }
    }
}
