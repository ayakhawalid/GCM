# Phase 9 Implementation Report: Support Inquiry, Bot, & Escalation System

## 1. Overview
Phase 9 introduces a comprehensive customer support system to GCM, enabling users to create inquiries, receive automated responses from a bot, and escalate complex issues to human agents. It also includes a dedicated Agent Console for support staff to manage and resolve tickets.

## 2. Database Schema
New tables were created to facilitate ticket tracking and knowledge base management:
- **`support_tickets`**: Stores ticket metadata (subject, priority, status, assigned agent).
- **`ticket_messages`**: Stores conversation history (sender type: CUSTOMER, AGENT, BOT).
- **`faq_entries`**: Stores question/answer pairs used by the bot for automated responses.
- **Role Update**: Added `SUPPORT_AGENT` role to the user system.

## 3. Server-Side Architecture
The server implementation follows the project's layered architecture:
- **`SupportTicketDTO` & `TicketMessageDTO`**: Data Transfer Objects for client-server communication.
- **`SupportDAO`**: Manages direct database interactions for tickets, similarity checks, and FAQ lookups.
- **`BotService`**: Encapsulates the logic for keyword extraction, FAQ matching, and auto-escalation policies.
- **`SupportHandler`**: Centralized request handler processing operations like `CREATE_TICKET`, `AGENT_CLAIM_TICKET`, and `AGENT_REPLY`.
- **Integration**: Registered `SupportHandler` in `GCMServer` to route relevant `MessageType` requests.

## 4. Client-Side Implementation
Two main interfaces were developed:
### Customer Support Screen (`SupportScreen.java`)
- **Ticket Management**: Users can create new tickets and view their history.
- **Interaction**: Displays message bubbles color-coded by sender (Bot = Blue, Agent = Purple, Customer = Green).
- **Actions**: Allows users to escalate tickets to a human agent or close them if resolved.

### Agent Console (`AgentConsoleScreen.java`)
- **Dual Queue View**:
  - **"My Tickets"**: Tickets specifically assigned to the logged-in agent.
  - **"Pending Queue"**: Unassigned tickets waiting for varying agents to claim.
- **Resolution Tools**: Agents can claim tickets, type relies, and resolve/close tickets with concluding remarks.

### Dashboard Integration
- Updated `DashboardScreen` to dynamically display the "Support" card for all users and the "Agent Console" card exclusively for `SUPPORT_AGENT` role.
- Enhanced `LoginController` to correctly map and route the new agent role.

## 5. Automated Bot Logic
The `BotService` provides immediate assistance:
1.  **Keyword Analysis**: Scans ticket subject and body for key terms (e.g., "refund", "download", "payment").
2.  **FAQ Matching**: Queries `faq_entries` for relevant answers using similarity detection.
3.  **Auto-Escalation**:
    - If keywords indicate sensitive topics like "Billing" or "Refund", the ticket is automatically flagged for human review.
    - If no relevant FAQ is found, the bot advises the user to wait for an agent.
4.  **Context Awareness**: Incorporates user's purchase history (e.g., active subscriptions) to personalize responses.

## 6. Testing & Validation
- **`SupportDAOTest`**: Unit tests created to verify FAQ matching logic and DAO availability.
- **Manual Verification**: Verified end-to-end flows:
    - Customer creates ticket -> Bot answers.
    - Customer escalates -> Ticket appears in Agent Pending Queue.
    - Agent claims -> Replies -> Closes ticket.
