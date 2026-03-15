# GCM System - Complete Implementation Plan

> **Document Purpose**: Comprehensive plan for building the GCM (Global City Manager) system as a LAN Client-Server application with DB + GUI.
> 
> **Status**: Planning - NOT YET EXECUTED

---

## Table of Contents

1. [Global Constraints](#global-constraints)
2. [Architecture Overview](#architecture-overview)
3. [Data Model](#data-model)
4. [Phase Implementation Plan (1-17)](#phase-implementation-plan)
5. [Documentation Standard](#documentation-standard)
6. [Final Deliverables](#final-deliverables)

---

## Global Constraints

### A) Architecture (3 Tiers)

| Tier | Components | Responsibility |
|------|------------|----------------|
| **Client Tier (GUI)** | Boundaries (Screens) + Client Controllers | User interface and interaction |
| **Server Tier** | OCSF server + Request Dispatcher + Services/Controllers | Business logic, request processing |
| **Data Tier** | Entities + DAO/Repositories (JDBC) | Data persistence |

> **CRITICAL**: Client NEVER connects to DB directly.

### B) Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java |
| Networking | OCSF (AbstractClient/AbstractServer) |
| GUI | JavaFX (preferred) or Swing |
| Database | SQLite (simple) or MySQL |
| DB Access | JDBC |
| Packaging | Runnable JAR (client + server) |
| Logging | SLF4J + Logback (or java.util.logging) |

### C) Naming Alignment with Sequence Diagrams

| Flow | Class Hierarchy |
|------|-----------------|
| **Support** | Customer → SupportScreen (Boundary) → SupportControl → GCMClient → GCMServer → DatabaseController/DAO |
| **Pricing** | ContentManager → ContentEditorBoundary → ContentManagementControl → GCMClient → GCMServer → DatabaseController/DAO |
| **Map Editing** | ContentEditor → MapEditorScreen → ContentManagementControl → GCMClient → GCMServer → DatabaseController/DAO |

### D) Protocol Design

```java
// Message Type Enumeration
enum MessageType {
    // Search
    SEARCH_BY_CITY_NAME, SEARCH_BY_POI_NAME, SEARCH_BY_CITY_AND_POI,
    // Map Editing
    GET_CITIES, GET_MAPS_FOR_CITY, GET_MAP_CONTENT, SUBMIT_MAP_CHANGES,
    // Publishing
    LIST_PENDING_MAP_VERSIONS, APPROVE_MAP_VERSION, REJECT_MAP_VERSION,
    // Authentication
    REGISTER_CUSTOMER, LOGIN, LOGOUT,
    // Purchases
    GET_CITY_PRICE, PURCHASE_ONE_TIME, PURCHASE_SUBSCRIPTION, DOWNLOAD_MAP_VERSION,
    // ... and more
}

// Request Structure
class Request {
    UUID requestId;
    MessageType type;
    Object payload;
    String sessionToken;
}

// Response Structure
class Response {
    UUID requestId;
    boolean ok;
    Object payload;
    String errorCode;
    String errorMessage;
}
```

### E) Security + Sessions

- All actions except search require login
- Same user cannot be logged in twice concurrently
- Role-based access control (RBAC):
  - Customer
  - ContentEditor
  - ContentManager
  - CompanyManager
  - SupportAgent
- Password hashing: BCrypt

### F) Data Model (Core Entities)

#### User Management
| Table | Purpose |
|-------|---------|
| `users` | id, username, passwordHash, role, email, phone, createdAt |
| `customers` | userId FK, paymentToken/last4 |
| `employees` | userId FK, department/permissions |

#### Content Management
| Table | Purpose |
|-------|---------|
| `cities` | id, name, price |
| `maps` | id, cityId FK, name, shortDescription |
| `map_versions` | id, mapId FK, versionNumber, status[DRAFT/PENDING/APPROVED/REJECTED], createdBy, createdAt |
| `map_content` | versionId FK, descriptionText |
| `pois` | id, cityId FK, name, location, category, shortExplanation, accessibleFlag |
| `map_pois` | mapId FK, poiId FK (POI can be in multiple maps) |
| `tours` | id, cityId FK, name, generalDescription |
| `tour_stops` | tourId FK, poiId FK, stopOrder, recommendedDurationMinutes |

#### Approval Workflow
| Table | Purpose |
|-------|---------|
| `approvals` | id, entityType, entityId, status[PENDING/APPROVED/REJECTED], reason, approvedBy, timestamp |
| `pricing_requests` | id, requestedBy, payloadJson, status, reason, createdAt |
| `audit_log` | id, action, actor, entityType, entityId, timestamp, detailsJson |

#### Purchases & Subscriptions
| Table | Purpose |
|-------|---------|
| `purchases` | id, customerId, cityId, type[ONE_TIME/SUBSCRIPTION], pricePaid, purchasedAt |
| `subscriptions` | purchaseId FK, startDate, endDate, status[ACTIVE/EXPIRED], renewalCount |
| `renewal_events` | id, subscriptionId, renewedAt, discountApplied |
| `view_events` | id, customerId, cityId, mapId, timestamp |
| `download_events` | id, customerId, cityId, mapVersionId, timestamp |

#### Support System
| Table | Purpose |
|-------|---------|
| `support_tickets` | id, customerId, status[OPEN/CLOSED/ASSIGNED], createdAt, assignedAgentId, similarityGroupId |
| `ticket_history` | id, ticketId, eventType, timestamp, note |
| `ticket_messages` | id, ticketId, senderType[CUSTOMER/BOT/AGENT], messageText, timestamp |
| `assignments` | id, ticketId, agentId, assignedAt, status |

#### Notifications & Reporting
| Table | Purpose |
|-------|---------|
| `notifications` | id, userId, channel[IN_APP/EMAIL/SMS], title, body, createdAt, sentAt, status |
| `daily_stats` | date, cityId, oneTimeCount, subscriptionCount, renewalsCount, viewsCount, downloadsCount, newSubscriptionsCount |

---

## Phase Implementation Plan

### PHASE 1 — SEARCH (חיפוש)

**Requirements:**
1. Search map by city name
2. Search map by POI name
3. Search map by (city name + POI name)

**Note:** Anyone (guest) can search - no login required.

**Implementation Tasks:**
- [ ] GUI: "Catalog/Search" screen accessible without login
- [ ] Search input mode toggles (city/POI/both)
- [ ] Results list with city name, map count, short descriptions
- [ ] Server MessageTypes: `GET_CITIES_CATALOG`, `SEARCH_BY_CITY_NAME`, `SEARCH_BY_POI_NAME`, `SEARCH_BY_CITY_AND_POI`
- [ ] DB: indexes on `cities.name`, `pois.name`, join relations

**Tests:**
- [ ] City exists - returns results
- [ ] City not exists - returns empty
- [ ] POI exists in multiple maps - returns all maps
- [ ] City+POI mismatch - returns empty
- [ ] Case-insensitive match works

---

### PHASE 2 — MAP EDITING (עריכת מפה)

**Requirements:**
1. Create new city while creating first map for it
2. Add new map to a city
3. Add POI
4. Update/Delete POI
5. Create Tour
6. Add POIs to tour (ordered + duration)

**Sequence Diagram Alignment:**
```
openMapEditor → getCities → selectCity → getMapsForCity → selectMap(mapId) 
→ getMapContent(mapId) → edit → submitMapChanges → validate → (invalid/valid)
```

**Implementation Tasks:**
- [ ] GUI: MapEditorScreen with city selector, "Create City", maps list, "Create Map"
- [ ] Tabs: POIs / Tours / Map Description
- [ ] Client control: `ContentManagementControl` with methods
- [ ] Server handlers: `GET_CITIES`, `GET_MAPS_FOR_CITY`, `GET_MAP_CONTENT`, `SUBMIT_MAP_CHANGES`
- [ ] Validation rules (required fields, POI references, tour order, duration > 0)
- [ ] Transaction support for complex writes

**Tests:**
- [ ] Create city + first map
- [ ] Add POI then link to tour stop
- [ ] Delete POI that is in a tour (block or cascade)
- [ ] Invalid submission returns validationError

---

### PHASE 3 — PUBLISH NEW VERSION (פרסום גרסה חדשה)

**Requirements:**
- Send approval request to Content Department Manager
- Notify customers who purchased the city after approval

**Implementation Tasks:**
- [ ] After valid SUBMIT_MAP_CHANGES: create MapVersion (PENDING), Approval record, AuditLog
- [ ] Manager UI: list pending versions, review, approve/reject with reason
- [ ] Server messages: `LIST_PENDING_MAP_VERSIONS`, `APPROVE_MAP_VERSION`, `REJECT_MAP_VERSION`
- [ ] On approve: set APPROVED, create notifications to purchasers
- [ ] On reject: set Rejected + reason, notify ContentEditor

**State Machine:**
```
DRAFT → PENDING → APPROVED/REJECTED
```

**Tests:**
- [ ] Approve publishes version
- [ ] Reject keeps customer-visible version unchanged
- [ ] Notifications created for eligible customers only

---

### PHASE 4 — CUSTOMER REGISTRATION (רישום כלקוחה)

**Requirements:**
- Registration + payment details
- Search remains available without login

**Implementation Tasks:**
- [ ] GUI: Register + Login screens
- [ ] Store payment as token/last4 (mock)
- [ ] Server messages: `REGISTER_CUSTOMER`, `LOGIN`, `LOGOUT`
- [ ] Session registry: deny second login
- [ ] BCrypt password hashing

**Tests:**
- [ ] Duplicate username/email refused
- [ ] Wrong password rejected
- [ ] Second login refused if already active

---

### PHASE 5 — MAP PURCHASE (רכישת מפות)

**Requirements:**
1. One-time purchase + download
2. Subscription purchase enabling viewing and downloads

**Implementation Tasks:**
- [ ] GUI: Purchase panel on city/maps view
- [ ] Price displayed from active tariffs
- [ ] Server messages: `GET_CITY_PRICE`, `PURCHASE_ONE_TIME`, `PURCHASE_SUBSCRIPTION`, `DOWNLOAD_MAP_VERSION`, `RECORD_VIEW_EVENT`
- [ ] Rules: one-time = download at purchase; subscription = unlimited during period
- [ ] Record download_events/view_events

**Tests:**
- [ ] Cannot purchase without login
- [ ] Cannot download without entitlement
- [ ] Download allowed for active subscription, blocked after expiry

---

### PHASE 6 — CUSTOMER INFO (מידע על לקוחה)

**Requirements:**
1. Customer can view/update personal info and see purchase history
2. Management can view customer purchases

**Implementation Tasks:**
- [ ] GUI: Customer Profile + Purchase History
- [ ] Manager view: customer list + drill-down purchases
- [ ] Server messages: `GET_MY_PROFILE`, `UPDATE_MY_PROFILE`, `GET_MY_PURCHASES`, `ADMIN_LIST_CUSTOMERS`, `ADMIN_GET_CUSTOMER_PURCHASES`
- [ ] RBAC rules enforcement

**Tests:**
- [ ] Customer cannot access other customer data
- [ ] Manager can access all customers

---

### PHASE 7 — SUBSCRIPTION EXPIRY NOTIFICATIONS (הודעות על פקיעת מנוי)

**Requirements:**
- System sends reminders before expiry (3 days)

**Implementation Tasks:**
- [ ] Server scheduler thread (daily or every N minutes in demo)
- [ ] Find subscriptions expiring in 3 days
- [ ] Create Notification records; log + in-app inbox
- [ ] Messages: `GET_MY_NOTIFICATIONS`, `MARK_NOTIFICATION_READ`
- [ ] Prevent duplicate reminders

**Tests:**
- [ ] Expiring in 3 days triggers exactly one reminder
- [ ] Expired subscriptions do not get reminders

---

### PHASE 8 — PRICES (מחירים)

**Requirements:**
- Prices can be changed via approval workflow

**Sequence Alignment:**
```
openPricingScreen → getCurrentPrices → showPrices → enterNewPrices 
→ submitNewPrices → validatePrices → WRITE PricingRequest(Pending)
```

**Implementation Tasks:**
- [ ] GUI: Pricing screen for ContentManager
- [ ] CompanyManager approval screen
- [ ] Server messages: `GET_CURRENT_PRICES`, `SUBMIT_PRICING_REQUEST`, `LIST_PENDING_PRICING_REQUESTS`, `APPROVE_PRICING_REQUEST`, `REJECT_PRICING_REQUEST`
- [ ] Audit log for all pricing changes

**Tests:**
- [ ] Invalid prices rejected client-side + server-side
- [ ] Pending request visible to approver
- [ ] Approval applies new prices immediately

---

### PHASE 9 — SUPPORT INQUIRY + BOT + ESCALATION (פניה)

**Requirements:**
1. Submit inquiry/complaint
2. Automatic bot answer
3. Escalation to human agent

**Sequence Alignment:**
```
openSupportScreen → enterComplaint → submitComplaint → newTicketRequest 
→ similarity check → showBotResponse → alt(closeTicket / requestEscalation)
```

**Implementation Tasks:**
- [ ] GUI: SupportScreen (customer) + AgentConsole (support agent)
- [ ] Similarity check on recent tickets (TF-IDF or keyword overlap)
- [ ] BotService: rule-based answers using FAQ + knowledge
- [ ] Messages: `CREATE_TICKET`, `CLOSE_TICKET`, `ESCALATE_TICKET`, `AGENT_LIST_ASSIGNED_TICKETS`, `AGENT_REPLY_AND_CLOSE`
- [ ] Store ticket_history and ticket_messages

**Tests:**
- [ ] Similarity prevents duplicates or links existing
- [ ] Close ticket writes status + history
- [ ] Escalation assigns agent and writes history

---

### PHASE 10 — ACTIVITY REPORTS (דוח פעילות)

**Requirements:**
1. Choose date range
2. Report for specific city
3. Report for all cities
4. Show: #maps, #one-time, #subscriptions, #renewals, #views, #downloads, #new subscriptions
5. Graph required (histogram)

**Implementation Tasks:**
- [ ] DailyStats generation (incremental or nightly job)
- [ ] GUI: Report screen with date pickers + city selector + charts
- [ ] Server messages: `GET_REPORT_CITY`, `GET_REPORT_ALL`
- [ ] Strategy pattern: ReportType enum + ReportGenerator interface

**Tests:**
- [ ] Seeded events produce correct counts
- [ ] Graph renders and updates with filters

---

### PHASE 11 — DEPLOYMENT + GUI + JAR (Non-Functional)

**Requirements:**
- Client-Server on separate machines
- GUI
- Runnable JAR
- Connect on startup

**Deliverables:**
- [ ] `gcm-server.jar`
- [ ] `gcm-client.jar`
- [ ] Config: host/port in client; db path in server
- [ ] README with exact steps to run on two LAN machines

---

### PHASE 12 — MULTI-USER CONCURRENCY (Non-Functional)

**Requirements:**
- Multiple users can connect simultaneously

**Implementation Tasks:**
- [ ] Server: thread-per-client or thread pool
- [ ] DAO: safe connection handling (pool or per-request)
- [ ] GUI: background threads (no freeze)

**Tests:**
- [ ] Simulate N clients with concurrent requests

---

### PHASE 13 — SINGLE SESSION PER USER (Non-Functional)

**Requirements:**
- Same user cannot be logged in more than once

**Implementation Tasks:**
- [ ] SessionRegistry: username → activeSessionToken + connectionId
- [ ] On LOGIN: if active → reject
- [ ] On disconnect: cleanup

**Tests:**
- [ ] Second login refused
- [ ] Logout releases session

---

### PHASE 14 — EFFICIENCY (Non-Functional)

**Requirements:**
- No artificial delays
- Efficient operations

**Implementation Tasks:**
- [ ] Add indexes for search and FK columns
- [ ] Pagination for lists (maps/POIs/tickets)
- [ ] Avoid full scans in hot paths
- [ ] No Thread.sleep in production logic

---

### PHASE 15 — MAINTAINABLE + FUTURE WEB (Non-Functional)

**Requirements:**
- Design supports changes and future web migration

**Implementation Tasks:**
- [ ] Strict separation: UI ↔ DTO ↔ Services ↔ DAO
- [ ] All server logic via message handlers (mappable to REST)
- [ ] Use interfaces for services
- [ ] Avoid UI logic in server

---

### PHASE 16 — THREADING (Non-Functional)

**Requirements:**
- Use threads properly

**Implementation Tasks:**
- [ ] Server: request handling threads + scheduler threads
- [ ] Client: background tasks for network; UI updates on UI thread

---

### PHASE 17 — USABILITY (Non-Functional)

**Requirements:**
- Good UX: instructions, consistency, error/success feedback

**Implementation Tasks:**
- [ ] Unified dialog system for errors/success
- [ ] Field validation with inline hints
- [ ] Help/tooltips on complex screens
- [ ] Consistent navigation
- [ ] User manual with step-by-step walkthroughs

---

## Documentation Standard (Per Phase)

Each phase MUST produce:

1. **Phase README**: goal + user story + screens + message types + DB changes
2. **Sequence Alignment Note**: how this phase matches sequence/activity diagrams
3. **API/Protocol Doc**: list of Request/Response payload DTOs
4. **DB Doc**: schema snippet + indexes + seed data
5. **Test Plan**: at least 5 acceptance tests + how to run
6. **User Guide**: steps in GUI to demonstrate the phase

---

## Final Deliverables

### Working Demo Script

1. **Guest search** - search without login
2. **Content editor edits map** → submit → pending approval
3. **Manager approves** → customers notified
4. **Customer registers** → purchases one-time/subscription → downloads/views
5. **Pricing request** → company manager approves → new price applied
6. **Support ticket** → bot response → escalation → agent closes
7. **Report generation** with charts

### Artifacts

- [ ] `gcm-server.jar` - Server executable
- [ ] `gcm-client.jar` - Client executable
- [ ] Seeded database with test data
- [ ] Consolidated documentation index
- [ ] Per-phase documentation (17 sets)
- [ ] User manual
- [ ] Deployment guide

---

## Architecture Diagrams

### High-Level Architecture
```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│   CLIENT TIER   │   TCP   │   SERVER TIER   │  JDBC   │    DATA TIER    │
│   (JavaFX UI)   │ ◄─────► │  (OCSF Server)  │ ◄─────► │   (SQLite/MySQL)│
│                 │  :5555  │                 │         │                 │
│ • Boundaries    │         │ • Dispatcher    │         │ • Entities      │
│ • Controllers   │         │ • Services      │         │ • DAOs          │
│ • DTOs          │         │ • Handlers      │         │ • Repositories  │
└─────────────────┘         └─────────────────┘         └─────────────────┘
```

### Package Structure
```
com.gcm
├── common
│   ├── dto/           # Data Transfer Objects
│   ├── protocol/      # Request, Response, MessageType
│   └── model/         # Shared entity interfaces
├── client
│   ├── boundary/      # JavaFX screens (FXML controllers)
│   ├── control/       # Client-side controllers
│   └── app/           # Application entry point
└── server
    ├── ocsf/          # OCSF framework
    ├── handler/       # Message handlers (per MessageType)
    ├── service/       # Business logic services
    ├── dao/           # Data access objects
    ├── entity/        # Database entities
    ├── scheduler/     # Background tasks
    └── app/           # Server entry point
```

---

## Role Permissions Matrix

| Action | Guest | Customer | ContentEditor | ContentManager | CompanyManager | SupportAgent |
|--------|:-----:|:--------:|:-------------:|:--------------:|:--------------:|:------------:|
| Search | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Register | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Login | ❌ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Purchase | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| View Purchases | ❌ | ✅ (own) | ❌ | ❌ | ✅ (all) | ❌ |
| Download Map | ❌ | ✅ (entitled) | ✅ | ✅ | ✅ | ❌ |
| Edit Map | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ |
| Approve Map | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| Submit Pricing | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| Approve Pricing | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| Create Ticket | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ |
| Handle Ticket | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| View Reports | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ |

---

## State Machines

### MapVersion States
```
     ┌─────────┐
     │  DRAFT  │
     └────┬────┘
          │ submit
          ▼
     ┌─────────┐
     │ PENDING │
     └────┬────┘
          │
    ┌─────┴─────┐
    │           │
    ▼           ▼
┌────────┐  ┌──────────┐
│APPROVED│  │ REJECTED │
└────────┘  └──────────┘
```

### Subscription States
```
     ┌────────┐
     │ ACTIVE │
     └────┬───┘
          │ end_date passed
          ▼
     ┌─────────┐
     │ EXPIRED │
     └─────────┘
```

### Support Ticket States
```
     ┌──────┐
     │ OPEN │
     └──┬───┘
        │ escalate
        ▼
   ┌──────────┐
   │ ASSIGNED │
   └────┬─────┘
        │ resolve
        ▼
   ┌────────┐
   │ CLOSED │
   └────────┘
```

---

## Execution Notes

> **IMPORTANT**: This document is a PLAN. Implementation should proceed phase by phase (1-17), completing all requirements, tests, and documentation for each phase before moving to the next.

---

*Document Version: 1.0*
*Created: January 6, 2026*
*Project: GCM System Complete Implementation*
