package server.service;

import common.dto.CustomerPurchaseDTO;
import common.dto.SupportTicketDTO;
import common.dto.TicketMessageDTO;
import server.dao.PurchaseDAO;
import server.dao.SupportDAO;
import server.dao.SupportDAO.FaqEntry;

import java.util.List;

/**
 * Bot service for generating automated support responses.
 * 
 * <h2>Decision Logic:</h2>
 * <ol>
 * <li>Extract keywords from ticket subject and message</li>
 * <li>Search FAQ entries for keyword matches</li>
 * <li>If strong match (≥3 keywords): return FAQ answer</li>
 * <li>If partial match (1-2 keywords): return FAQ answer + suggest
 * escalation</li>
 * <li>If no match: return generic response + auto-escalate flag</li>
 * </ol>
 * 
 * <h2>Escalation Policy:</h2>
 * <ul>
 * <li>Manual: Customer clicks "Escalate to Agent"</li>
 * <li>Automatic: Bot confidence below threshold (no FAQ match)</li>
 * <li>Billing/Refund: Always suggest escalation</li>
 * </ul>
 */
public class BotService {

    // Match thresholds
    private static final int STRONG_MATCH_THRESHOLD = 3;
    private static final int PARTIAL_MATCH_THRESHOLD = 1;

    /**
     * Result of bot response generation.
     */
    public static class BotResult {
        public String response;
        public boolean shouldAutoEscalate;
        public int faqId; // ID of FAQ used, or -1 if none

        public BotResult(String response, boolean shouldAutoEscalate, int faqId) {
            this.response = response;
            this.shouldAutoEscalate = shouldAutoEscalate;
            this.faqId = faqId;
        }
    }

    /**
     * Generate bot response for a support ticket.
     *
     * @param subject Ticket subject
     * @param message Initial message from customer
     * @param userId  Customer's user ID for context
     * @return BotResult containing response text and escalation flag
     */
    public static BotResult generateResponse(String subject, String message, int userId) {
        System.out.println("[BotService] Generating response for: " + subject);

        // Combine subject and message for keyword extraction
        String fullText = subject + " " + message;

        // Find matching FAQ entries
        List<FaqEntry> matches = SupportDAO.findMatchingFaq(fullText);

        // Check for billing/refund keywords - always suggest escalation
        boolean isBillingRelated = fullText.toLowerCase()
                .matches(".*(refund|chargeback|charge|billing|payment|cancel).*");

        if (matches.isEmpty()) {
            // No FAQ match - generic response and auto-escalate
            String response = buildGenericResponse(userId);
            return new BotResult(response, true, -1);
        }

        FaqEntry bestMatch = matches.get(0);
        SupportDAO.incrementFaqUsage(bestMatch.id);

        if (bestMatch.matchScore >= STRONG_MATCH_THRESHOLD && !isBillingRelated) {
            // Strong match - provide answer directly
            String response = buildStrongMatchResponse(bestMatch, userId);
            return new BotResult(response, false, bestMatch.id);
        } else if (bestMatch.matchScore >= PARTIAL_MATCH_THRESHOLD) {
            // Partial match - provide answer but suggest escalation
            String response = buildPartialMatchResponse(bestMatch, userId, isBillingRelated);
            return new BotResult(response, false, bestMatch.id);
        }

        // Fallback
        String response = buildGenericResponse(userId);
        return new BotResult(response, true, -1);
    }

    /**
     * Build response for strong FAQ match.
     */
    private static String buildStrongMatchResponse(FaqEntry faq, int userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hello! I found an answer that might help:\n\n");
        sb.append("**").append(faq.question).append("**\n\n");
        sb.append(faq.answer).append("\n\n");

        // Add personalized context if available
        String context = getPersonalizedContext(userId, faq.category);
        if (context != null) {
            sb.append(context).append("\n\n");
        }

        sb.append("Did this answer your question? If yes, please close this ticket. ");
        sb.append("If you need more help, click 'Escalate to Agent' to speak with a human.");

        return sb.toString();
    }

    /**
     * Build response for partial FAQ match.
     */
    private static String buildPartialMatchResponse(FaqEntry faq, int userId, boolean isBillingRelated) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hello! Based on your inquiry, this might be helpful:\n\n");
        sb.append("**").append(faq.question).append("**\n\n");
        sb.append(faq.answer).append("\n\n");

        if (isBillingRelated) {
            sb.append("I notice this may be related to billing. ");
            sb.append("For billing matters, I recommend speaking with a human agent who can assist you directly.\n\n");
        }

        // Add personalized context if available
        String context = getPersonalizedContext(userId, faq.category);
        if (context != null) {
            sb.append(context).append("\n\n");
        }

        sb.append(
                "If this doesn't answer your question, please click 'Escalate to Agent' for personalized assistance.");

        return sb.toString();
    }

    /**
     * Build generic response when no FAQ match found.
     */
    private static String buildGenericResponse(int userId) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hello! Thank you for contacting GCM Support.\n\n");
        sb.append("I couldn't find a specific answer in our knowledge base for your inquiry. ");
        sb.append("Your request has been flagged for review by our support team.\n\n");
        sb.append("An agent will be assigned to your ticket shortly. ");
        sb.append("You can also click 'Escalate to Agent' to prioritize your request.\n\n");
        sb.append("Thank you for your patience!");

        return sb.toString();
    }

    /**
     * Get personalized context based on user's purchase history.
     */
    private static String getPersonalizedContext(int userId, String category) {
        try {
            List<CustomerPurchaseDTO> purchases = PurchaseDAO.getPurchasesDetailed(userId, false);

            if (purchases == null || purchases.isEmpty()) {
                return null;
            }

            // Provide context based on FAQ category
            if ("Subscription".equalsIgnoreCase(category)) {
                for (CustomerPurchaseDTO p : purchases) {
                    if (p.isSubscription() && p.isActive()) {
                        return "You have an active subscription to " + p.getCityName() +
                                " that expires on " + p.getExpiryDate() + ".";
                    }
                }
            } else if ("Purchasing".equalsIgnoreCase(category) || "Downloads".equalsIgnoreCase(category)) {
                return "You have " + purchases.size() + " purchase(s) in your account.";
            }

        } catch (Exception e) {
            System.err.println("[BotService] Error getting personalized context: " + e.getMessage());
        }

        return null;
    }

    /**
     * Add bot response to a ticket.
     */
    public static void addBotResponseToTicket(int ticketId, BotResult result) {
        SupportDAO.addMessage(ticketId, TicketMessageDTO.SenderType.BOT, null, result.response);
        SupportDAO.updateTicketStatus(ticketId, SupportTicketDTO.Status.BOT_RESPONDED);

        // Do NOT set status to ESCALATED here: the bot message tells the user to click
        // "Escalate to Agent", so the button must stay visible (it is hidden when status is ESCALATED).
        // Ticket remains in agents' pending queue (BOT_RESPONDED with no agent assigned).
        if (result.shouldAutoEscalate) {
            System.out.println("[BotService] Ticket #" + ticketId + " flagged for review (user can escalate)");
        }
    }
}
