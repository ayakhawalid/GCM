package server.service;

import common.dto.*;
import java.util.List;

/**
 * Support ticket service interface.
 * Phase 15: Maintainability - Strict separation with interfaces.
 * 
 * Designed to be mappable to REST endpoints for future web migration.
 */
public interface ISupportService {

    /**
     * Create a new support ticket.
     * REST mapping: POST /api/tickets
     * 
     * @return Ticket ID
     */
    int createTicket(int customerId, String subject, String description);

    /**
     * Get bot response for a query.
     * REST mapping: POST /api/support/bot
     */
    String getBotResponse(String query);

    /**
     * Escalate ticket to human agent.
     * REST mapping: POST /api/tickets/{ticketId}/escalate
     */
    boolean escalateTicket(int ticketId);

    /**
     * Close a ticket.
     * REST mapping: POST /api/tickets/{ticketId}/close
     */
    boolean closeTicket(int ticketId, String resolution);

    /**
     * Get customer's tickets.
     * REST mapping: GET /api/customers/{customerId}/tickets
     */
    List<SupportTicketDTO> getCustomerTickets(int customerId);

    /**
     * Get tickets assigned to an agent.
     * REST mapping: GET /api/agents/{agentId}/tickets
     */
    List<SupportTicketDTO> getAgentTickets(int agentId);

    /**
     * Add message to ticket.
     * REST mapping: POST /api/tickets/{ticketId}/messages
     */
    boolean addTicketMessage(int ticketId, String senderType, String message);

    /**
     * Get ticket messages.
     * REST mapping: GET /api/tickets/{ticketId}/messages
     */
    List<TicketMessageDTO> getTicketMessages(int ticketId);

    /**
     * Find similar tickets (for deduplication).
     */
    List<SupportTicketDTO> findSimilarTickets(String query);

    /**
     * Search FAQ for matching answers.
     */
    List<FaqDTO> searchFaq(String query);
}
