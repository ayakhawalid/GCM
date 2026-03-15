# GCM (Geo City Maps) — Complete System Flow Documentation

This document explains, in excruciating detail, the theory and exact sequence of events for every action in the GCM system. It covers the full lifecycle of each operation: what triggers it, what classes are involved, what messages are sent over the wire, how the server processes them, what database operations occur, and how the response travels back to the client.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [The OCSF Communication Layer](#2-the-ocsf-communication-layer)
3. [The Request/Response Protocol](#3-the-requestresponse-protocol)
4. [Server Dispatch Mechanism](#4-server-dispatch-mechanism)
5. [Session Management](#5-session-management)
6. [Authentication Actions](#6-authentication-actions)
   - 6.1 [Registration](#61-registration)
   - 6.2 [Login](#62-login)
   - 6.3 [Logout](#63-logout)
   - 6.4 [Guest Access](#64-guest-access)
7. [Search & Catalog Actions](#7-search--catalog-actions)
   - 7.1 [Get Cities Catalog](#71-get-cities-catalog)
   - 7.2 [Search by City Name](#72-search-by-city-name)
   - 7.3 [Search by POI Name](#73-search-by-poi-name)
   - 7.4 [Search by City and POI](#74-search-by-city-and-poi)
8. [Map Editing Actions](#8-map-editing-actions)
   - 8.1 [Load Cities](#81-load-cities)
   - 8.2 [Load Maps for a City](#82-load-maps-for-a-city)
   - 8.3 [Load Map Content](#83-load-map-content)
   - 8.4 [Load POIs for a City](#84-load-pois-for-a-city)
   - 8.5 [Create City](#85-create-city)
   - 8.6 [Update City](#86-update-city)
   - 8.7 [Create Map](#87-create-map)
   - 8.8 [Delete Map](#88-delete-map)
   - 8.9 [Add POI](#89-add-poi)
   - 8.10 [Update POI](#810-update-poi)
   - 8.11 [Delete POI](#811-delete-poi)
   - 8.12 [Link/Unlink POI to Map](#812-linkunlink-poi-to-map)
   - 8.13 [Create Tour](#813-create-tour)
   - 8.14 [Update Tour](#814-update-tour)
   - 8.15 [Delete Tour](#815-delete-tour)
   - 8.16 [Add Tour Stop](#816-add-tour-stop)
   - 8.17 [Update Tour Stop](#817-update-tour-stop)
   - 8.18 [Remove Tour Stop](#818-remove-tour-stop)
   - 8.19 [Save Map Changes (Draft)](#819-save-map-changes-draft)
   - 8.20 [Submit Map Changes](#820-submit-map-changes)
   - 8.21 [Load My Draft](#821-load-my-draft)
9. [Map Edit Approval Actions](#9-map-edit-approval-actions)
   - 9.1 [Get Pending Map Edits](#91-get-pending-map-edits)
   - 9.2 [Approve Map Edit](#92-approve-map-edit)
   - 9.3 [Reject Map Edit](#93-reject-map-edit)
10. [Map Version Approval Actions](#10-map-version-approval-actions)
    - 10.1 [List Pending Map Versions](#101-list-pending-map-versions)
    - 10.2 [Get Map Version Details](#102-get-map-version-details)
    - 10.3 [Approve Map Version](#103-approve-map-version)
    - 10.4 [Reject Map Version](#104-reject-map-version)
11. [Purchase Actions](#11-purchase-actions)
    - 11.1 [Get City Price](#111-get-city-price)
    - 11.2 [Purchase One-Time](#112-purchase-one-time)
    - 11.3 [Purchase Subscription](#113-purchase-subscription)
    - 11.4 [Check Discount Eligibility](#114-check-discount-eligibility)
    - 11.5 [Get Entitlement](#115-get-entitlement)
    - 11.6 [Can Download](#116-can-download)
    - 11.7 [Download Map Version](#117-download-map-version)
    - 11.8 [Record View Event](#118-record-view-event)
    - 11.9 [Get My Purchases](#119-get-my-purchases)
12. [Customer/Profile Actions](#12-customerprofile-actions)
    - 12.1 [Get My Profile](#121-get-my-profile)
    - 12.2 [Update My Profile](#122-update-my-profile)
    - 12.3 [Admin List Customers](#123-admin-list-customers)
    - 12.4 [Admin Get Customer Purchases](#124-admin-get-customer-purchases)
13. [Notification Actions](#13-notification-actions)
    - 13.1 [Get My Notifications](#131-get-my-notifications)
    - 13.2 [Mark Notification Read](#132-mark-notification-read)
    - 13.3 [Get Unread Count](#133-get-unread-count)
14. [Pricing Actions](#14-pricing-actions)
    - 14.1 [Get Current Prices](#141-get-current-prices)
    - 14.2 [Submit Pricing Request](#142-submit-pricing-request)
    - 14.3 [List Pending Pricing Requests](#143-list-pending-pricing-requests)
    - 14.4 [Approve Pricing Request](#144-approve-pricing-request)
    - 14.5 [Reject Pricing Request](#145-reject-pricing-request)
15. [Support Ticket Actions](#15-support-ticket-actions)
    - 15.1 [Create Ticket](#151-create-ticket)
    - 15.2 [Get My Tickets](#152-get-my-tickets)
    - 15.3 [Get Ticket Details](#153-get-ticket-details)
    - 15.4 [Customer Reply](#154-customer-reply)
    - 15.5 [Escalate Ticket](#155-escalate-ticket)
    - 15.6 [Close Ticket](#156-close-ticket)
    - 15.7 [Agent List Assigned](#157-agent-list-assigned)
    - 15.8 [Agent List Pending](#158-agent-list-pending)
    - 15.9 [Agent Claim Ticket](#159-agent-claim-ticket)
    - 15.10 [Agent Reply](#1510-agent-reply)
    - 15.11 [Agent Close Ticket](#1511-agent-close-ticket)
16. [Report Actions](#16-report-actions)
    - 16.1 [Get Activity Report](#161-get-activity-report)
17. [Subscription Scheduler](#17-subscription-scheduler)
18. [Database Schema (Inferred)](#18-database-schema-inferred)

---

## 1. Architecture Overview

GCM is a three-tier JavaFX client–server application built on the **OCSF (Object Client–Server Framework)**:

```
┌──────────────────────────────────────────────────────────┐
│                       CLIENT                              │
│                                                          │
│  ┌─────────────┐   ┌──────────────┐   ┌──────────────┐  │
│  │  Boundary    │──▶│   Control     │──▶│  GCMClient   │  │
│  │  (Screens)   │◀──│   Classes     │◀──│  (Singleton) │  │
│  └─────────────┘   └──────────────┘   └──────┬───────┘  │
│                                               │          │
└───────────────────────────────────────────────┼──────────┘
                                                │ TCP/5555
                                                │ Java Serialization
┌───────────────────────────────────────────────┼──────────┐
│                       SERVER                   │          │
│                                               ▼          │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐  │
│  │  GCMServer   │──▶│   Handlers    │──▶│    DAOs       │  │
│  │              │◀──│              │◀──│              │  │
│  └──────────────┘   └──────────────┘   └──────┬───────┘  │
│                                               │          │
│                                        ┌──────▼───────┐  │
│                                        │   MySQL DB    │  │
│                                        │  (HikariCP)   │  │
│                                        └──────────────┘  │
└──────────────────────────────────────────────────────────┘
```

**Client layers:**
- **Boundary** (`client.boundary.*`): JavaFX controllers/screens. Each screen implements either `GCMClient.MessageHandler` or a control-class callback interface.
- **Control** (`client.control.*`): `SearchControl`, `ContentManagementControl`, `PurchaseControl`. These build `Request` objects and send them through `GCMClient`. They implement `GCMClient.MessageHandler` and route responses to callback interfaces.
- **GCMClient** (singleton): Extends `AbstractClient`. Manages the TCP socket, sends/receives serialized Java objects, routes incoming `Response` objects to the active `MessageHandler`.

**Server layers:**
- **GCMServer**: Extends `AbstractServer`. Accepts connections, dispatches incoming `Request` objects to the appropriate handler using a fixed thread pool of 10 threads.
- **Handlers** (`server.handler.*`): `AuthHandler`, `SearchHandler`, `MapEditHandler`, `ApprovalHandler`, `PurchaseHandler`, `CustomerHandler`, `NotificationHandler`, `PricingHandler`, `SupportHandler`, `ReportHandler`. Each handler declares which `MessageType` values it can handle, validates the request, calls DAOs, and returns a `Response`.
- **DAOs** (`server.dao.*`): Data access objects that execute SQL against MySQL via `DBConnector` (HikariCP pool).
- **Services** (`server.service.*`): `BotService` (FAQ matching), report generators (`CityReportGenerator`, `AllCitiesReportGenerator`), `OsrmClient` (driving distance).
- **Scheduler** (`server.scheduler.*`): `SubscriptionScheduler` runs every 2 minutes to check for expiring subscriptions and send reminders.

**Shared:**
- **common**: `Request`, `Response`, `MessageType`, entity classes (`City`, `Map`, `Poi`, `DailyStat`), and all DTOs (`common.dto.*`).

---

## 2. The OCSF Communication Layer

### How OCSF Works

OCSF provides two abstract classes:

**`AbstractClient`** (extended by `GCMClient`):
1. `openConnection()` creates a `Socket` to `host:port`, wraps it in `ObjectOutputStream` and `ObjectInputStream`, and starts a reader thread.
2. The reader thread (`run()`) loops forever: `Object msg = input.readObject()` → `handleMessageFromServer(msg)`.
3. `sendToServer(Object msg)` writes the object to the output stream: `output.writeObject(msg)`.
4. `closeConnection()` stops the reader thread and closes the socket.

**`AbstractServer`** (extended by `GCMServer`):
1. `listen()` creates a `ServerSocket` on the port and starts an accept thread.
2. The accept thread loops forever: `Socket clientSocket = serverSocket.accept()` → creates a new `ConnectionToClient` thread.
3. Each `ConnectionToClient` has its own reader thread: loops `Object msg = input.readObject()` → `server.receiveMessageFromClient(msg, this)` → `handleMessageFromClient(msg, client)`.
4. `ConnectionToClient.sendToClient(Object msg)` writes to that client's output stream.

### GCMClient Specifics

`GCMClient` is a singleton obtained via `getInstance()`. On first call, it connects to `localhost:5555`.

**Message handler system:**
- `setMessageHandler(MessageHandler handler)` sets the current active screen/controller.
- `handleMessageFromServer(Object msg)` does two things:
  1. If the msg is a `Response` and its `requestId` matches `pendingSyncRequestId`, it puts the response into a `BlockingQueue` (for synchronous calls).
  2. It calls `messageHandler.displayMessage(msg)` so the current screen can process the response.

**Synchronous mode:**
- `sendRequestSync(Request request)` sets `pendingSyncRequestId = request.getRequestId()`, calls `sendToServer(request)`, then blocks on `responseQueue.poll(30, SECONDS)`.
- This is used by screens like `SupportScreen` and `AgentConsoleScreen` that need to wait for the response before updating the UI.

**Asynchronous mode:**
- Most screens call `client.sendToServer(request)` and handle the response later in their `displayMessage(msg)` callback.

---

## 3. The Request/Response Protocol

### Request

Every client-to-server message is wrapped in a `common.Request` object:

| Field | Type | Purpose |
|-------|------|---------|
| `requestId` | `UUID` | Unique ID for correlating request–response pairs. Generated automatically via `UUID.randomUUID()`. |
| `type` | `MessageType` | Enum value identifying which operation this is (e.g., `LOGIN`, `GET_CITIES_CATALOG`). |
| `payload` | `Object` | The DTO or primitive data for the operation. Can be `null`. Examples: `LoginRequest`, `SearchRequest`, `MapChanges`, `int` (cityId). |
| `sessionToken` | `String` | The session token obtained after login. `null` for guest/unauthenticated requests. |
| `userId` | `int` | The user's ID. Set by the client or resolved by the server from the session. |

**Construction patterns:**
- Guest: `new Request(MessageType.GET_CITIES_CATALOG, null)`
- Authenticated: `new Request(MessageType.GET_MY_PROFILE, null, sessionToken)`
- With userId: `new Request(MessageType.GET_MAP_CONTENT, mapId, sessionToken, userId)`

### Response

Every server-to-client reply is a `common.Response` object:

| Field | Type | Purpose |
|-------|------|---------|
| `requestId` | `UUID` | Matches the `requestId` of the original `Request`. |
| `ok` | `boolean` | `true` for success, `false` for error. |
| `payload` | `Object` | The result data. Type depends on the operation. |
| `errorCode` | `String` | Error code constant (e.g., `ERR_VALIDATION`, `ERR_UNAUTHORIZED`). |
| `errorMessage` | `String` | Human-readable error description. |
| `requestType` | `MessageType` | Echoes back the type from the request, so the client knows which operation this response belongs to. |

**Error code constants:**
- `ERR_NOT_FOUND` — Resource not found.
- `ERR_UNAUTHORIZED` — Not authenticated.
- `ERR_FORBIDDEN` — Insufficient permissions.
- `ERR_VALIDATION` — Input validation failed.
- `ERR_DATABASE` — Database error.
- `ERR_INTERNAL` — Unexpected server error.
- `ERR_SESSION_EXPIRED` — Session no longer valid.
- `ERR_ALREADY_LOGGED_IN` — User already has an active session.
- `ERR_AUTHENTICATION` — Wrong credentials.

**Construction patterns:**
- Success: `Response.success(request, payload)`
- Error: `Response.error(request, Response.ERR_VALIDATION, "Price must be positive")`

---

## 4. Server Dispatch Mechanism

When `GCMServer.handleMessageFromClient(Object msg, ConnectionToClient client)` is called:

1. A task is submitted to the thread pool (size 10) via `executorService.submit(...)`.
2. Inside the task, `processClientMessage(msg, client)` runs.
3. If `msg` is a `Request`:
   - The request is logged.
   - `dispatchRequest(request, clientId)` is called, which returns a `Response`.
   - The response is sent back via `client.sendToClient(response)`.
4. If `msg` is a `String`: legacy protocol handling (e.g., `"get_cities"`, `"login <user> <pass>"`).

### Handler Dispatch Order

`dispatchRequest` iterates through handlers in this exact order:

1. **SearchHandler** — handles: `GET_CITIES_CATALOG`, `SEARCH_BY_CITY_NAME`, `SEARCH_BY_POI_NAME`, `SEARCH_BY_CITY_AND_POI`. No authentication required.
2. **MapEditHandler** — handles: all map/city/POI/tour CRUD, `SAVE_MAP_CHANGES`, `SUBMIT_MAP_CHANGES`, `GET_MY_DRAFT`, `GET_PENDING_MAP_EDITS`, `APPROVE_MAP_EDIT`, `REJECT_MAP_EDIT`.
3. **ApprovalHandler** — handles: `LIST_PENDING_MAP_VERSIONS`, `GET_MAP_VERSION_DETAILS`, `APPROVE_MAP_VERSION`, `REJECT_MAP_VERSION`.
4. **AuthHandler** — handles: `REGISTER_CUSTOMER`, `LOGIN`, `LOGOUT`. Special: after successful LOGIN, the server calls `SessionManager.setSessionConnection(token, clientId)` to bind the session to the connection.
5. **PurchaseHandler** — handles: `GET_CITY_PRICE`, `PURCHASE_ONE_TIME`, `PURCHASE_SUBSCRIPTION`, `GET_ENTITLEMENT`, `CHECK_DISCOUNT_ELIGIBILITY`, `CAN_DOWNLOAD`, `DOWNLOAD_MAP_VERSION`, `RECORD_VIEW_EVENT`, `GET_MY_PURCHASES`.
6. **CustomerHandler** — handles: `GET_MY_PROFILE`, `UPDATE_MY_PROFILE`, `ADMIN_LIST_CUSTOMERS`, `ADMIN_GET_CUSTOMER_PURCHASES`.
7. **NotificationHandler** — handles: `GET_MY_NOTIFICATIONS`, `MARK_NOTIFICATION_READ`, `GET_UNREAD_COUNT`.
8. **PricingHandler** — handles: `GET_CURRENT_PRICES`, `SUBMIT_PRICING_REQUEST`, `LIST_PENDING_PRICING_REQUESTS`, `APPROVE_PRICING_REQUEST`, `REJECT_PRICING_REQUEST`.
9. **SupportHandler** — handles: `CREATE_TICKET`, `GET_MY_TICKETS`, `GET_TICKET_DETAILS`, `ESCALATE_TICKET`, `CUSTOMER_REPLY`, `AGENT_LIST_ASSIGNED`, `AGENT_LIST_PENDING`, `AGENT_CLAIM_TICKET`, `AGENT_REPLY`, `AGENT_CLOSE_TICKET`.
10. **ReportHandler** — handles: `GET_ACTIVITY_REPORT`.
11. **Legacy handlers** — `LEGACY_GET_CITIES`, `LEGACY_GET_MAPS` via `MySQLController`.

If no handler matches, the server returns `Response.error(request, ERR_INTERNAL, "No handler found")`.

### userId Resolution

Many handlers need the userId. The server resolves it via a method called `resolveUserId(request)`:
1. If `request.getUserId() > 0`, use that directly.
2. Else if `request.getSessionToken() != null`, validate it via `SessionManager.validateSession(token)` and get `sessionInfo.getUserId()`.
3. If neither works, the userId remains 0 (guest).

---

## 5. Session Management

`SessionManager` is a singleton that manages all active sessions in memory (no database persistence for sessions).

### Data Structures

- `sessions`: `Map<String, SessionInfo>` — token → session details.
- `userSessions`: `Map<Integer, String>` — userId → token (enforces single session per user).
- `connectionSessions`: `Map<Long, String>` — connectionId → token (for cleanup on disconnect).

### SessionInfo

Each session stores:
- `userId`, `username`, `role` (String), `createdAt` (timestamp), `connectionId` (Long).

### Lifecycle

1. **Creation** (`createSession(userId, username, role)`):
   - Checks if user already has an active session via `userSessions.get(userId)`.
   - If yes, invalidates the old session first.
   - Generates a token via `UUID.randomUUID().toString()`.
   - Stores in all three maps.
   - Returns the token.

2. **Validation** (`validateSession(token)`):
   - Looks up `sessions.get(token)`.
   - Returns `SessionInfo` or `null` if invalid/expired.

3. **Connection binding** (`setSessionConnection(token, connectionId)`):
   - After a successful login, `GCMServer` calls this to associate the session with the OCSF connection ID.
   - Stores the connectionId in both `SessionInfo` and `connectionSessions`.

4. **Invalidation** (`invalidateSession(token)`):
   - Removes from all three maps.

5. **Disconnect cleanup** (`invalidateByConnectionId(connectionId)`):
   - When a client disconnects (TCP connection drops), `GCMServer.clientDisconnected` calls this.
   - Looks up the token via `connectionSessions.get(connectionId)`.
   - Invalidates that session.
   - This means if a client crashes without logging out, their session is still cleaned up.

### Single Session Enforcement

Only one session is allowed per user at a time. If User A logs in on Machine 1, and then logs in on Machine 2, the Machine 1 session is invalidated. The Machine 1 client will get `ERR_SESSION_EXPIRED` on its next request.

---

## 6. Authentication Actions

### 6.1 Registration

**Theory:** A new user creates an account. Registration does not use `GCMClient` — instead, `RegistrationController` opens its own raw socket connection for this single request, to avoid interfering with any existing client session.

**Sequence of events:**

1. **User** fills in the registration form on `RegistrationController`:
   - Username (3–20 characters)
   - Email (valid format)
   - Password (≥4 characters, confirmed)
   - Phone
   - Card last 4 digits (exactly 4 digits)

2. **Client validation** (`RegistrationController`):
   - Checks all fields are non-empty.
   - Validates username length (3–20).
   - Validates email format via regex.
   - Checks password matches confirmation.
   - Validates card is exactly 4 digits.
   - If any check fails, shows error dialog and stops.

3. **Client sends request** (`RegistrationController`):
   - Opens a new `Socket("localhost", 5555)`.
   - Creates `ObjectOutputStream` and `ObjectInputStream` on that socket.
   - Builds `RegisterRequest(username, email, password, phone, paymentToken, cardLast4)`.
   - Wraps in `Request(MessageType.REGISTER_CUSTOMER, registerRequest)`.
   - Sends via `output.writeObject(request)`.

4. **Server receives** (`GCMServer.handleMessageFromClient`):
   - Submits to thread pool.
   - `processClientMessage` → `dispatchRequest`.
   - `AuthHandler.canHandle(REGISTER_CUSTOMER)` returns `true`.
   - `AuthHandler.handle(request)` is called.

5. **AuthHandler.handleRegister(request)**:
   - Extracts `RegisterRequest` from payload.
   - Validates: username ≥ 4 chars, email non-empty, password ≥ 4 chars.
   - Calls `UserDAO.usernameExists(username)` — if `true`, returns error "Username already taken".
   - Calls `UserDAO.emailExists(email)` — if `true`, returns error "Email already registered".
   - If `paymentToken` is `null`, defaults to `"simulated_token"`.
   - If `cardLast4` is `null`, defaults to `"0000"`.
   - Calls `UserDAO.createCustomer(username, email, password, phone, paymentToken, cardLast4)`.

6. **UserDAO.createCustomer**:
   - Gets a `Connection` from `DBConnector.getConnection()`.
   - `INSERT INTO users (username, email, password_hash, role, is_active, created_at) VALUES (?, ?, ?, 'CUSTOMER', TRUE, NOW())`.
   - Retrieves generated user ID.
   - `INSERT INTO customers (user_id, phone, payment_token, card_last4) VALUES (?, ?, ?, ?)`.
   - Returns the new user ID.

7. **Server responds**:
   - `Response.success(request, "Registration successful! You can now login.")`.
   - Sent via `client.sendToClient(response)`.

8. **Client receives** (`RegistrationController`):
   - Reads `Response` from `input.readObject()`.
   - If `response.isOk()`: shows success dialog, waits 1.5 seconds, navigates to login screen.
   - If error: shows error dialog with `response.getErrorMessage()`.
   - Closes the socket.

---

### 6.2 Login

**Theory:** An existing user authenticates. The server creates a session, assigns a token, and returns the user's role. The client stores the token for all subsequent authenticated requests.

**Sequence of events:**

1. **User** enters username and password on `LoginController` and clicks Login.

2. **Client validation** (`LoginController`):
   - Username must be 3–20 characters.
   - Password must be 4–30 characters.
   - If either fails, shows a validation warning and stops.

3. **Client sends request** (`LoginController`):
   - Builds `LoginRequest(username, password)`.
   - Wraps in `Request(MessageType.LOGIN, loginRequest)`.
   - Calls `GCMClient.getInstance().sendToServer(request)`.

4. **Server receives** → dispatch to `AuthHandler`.

5. **AuthHandler.handleLogin(request)**:
   - Extracts `LoginRequest` from payload.
   - Calls `UserDAO.authenticate(username, password)`.

6. **UserDAO.authenticate**:
   - `SELECT id, username, email, role, is_active FROM users WHERE username = ? AND password_hash = ?`.
   - If no row: returns `null`.
   - If `is_active == false`: returns `null`.
   - Returns a `UserInfo` (id, username, email, role).

7. **Back in AuthHandler**:
   - If `userInfo == null`: returns `Response.error(request, ERR_AUTHENTICATION, "Invalid username or password")`.
   - Calls `SessionManager.createSession(userId, username, role)`.
   - The session manager checks if this user already has an active session — if yes, that old session is invalidated first.
   - A new session token (UUID string) is created and stored.
   - Calls `UserDAO.updateLastLogin(userId)`: `UPDATE users SET last_login_at = NOW() WHERE id = ?`.
   - Determines `isSubscribed` (currently hardcoded to `false`).
   - Builds `LoginResponse(sessionToken, userId, username, role, isSubscribed)`.
   - Returns `Response.success(request, loginResponse)`.

8. **GCMServer post-processing** (after dispatch):
   - Detects that this was a `LOGIN` request and the response was successful.
   - Extracts `LoginResponse` from the response payload.
   - Calls `SessionManager.setSessionConnection(token, clientId)` to bind the session to this OCSF connection.
   - This is critical: it allows `invalidateByConnectionId` to work when the client disconnects.

9. **Client receives** (`LoginController.displayMessage`):
   - Called on the OCSF reader thread.
   - Checks `response.getRequestType() == LOGIN` and `response.isOk()`.
   - Extracts `LoginResponse` from payload.
   - Stores `currentSessionToken = loginResponse.getSessionToken()`.
   - Stores `currentUserId = loginResponse.getUserId()`.
   - Stores `currentUsername = loginResponse.getUsername()`.
   - Maps `loginResponse.getRole()` string to a `UserRole` enum (CUSTOMER, CONTENT_EDITOR, CONTENT_MANAGER, COMPANY_MANAGER, SUPPORT_AGENT).
   - Calls `GCMClient.getInstance().setCurrentUser(userId, username, role)`.
   - Navigates to `dashboard.fxml` on the JavaFX Application Thread via `Platform.runLater(...)`.

---

### 6.3 Logout

**Theory:** The user ends their session. The session is invalidated on the server, and the client navigates back to the login screen.

**Sequence of events:**

1. **User** clicks Logout on the `DashboardScreen`.

2. **Client sends request** (`DashboardScreen`):
   - Builds `Request(MessageType.LOGOUT, sessionToken, sessionToken)` — the payload is the token itself.
   - Sends synchronously via `GCMClient.getInstance().sendRequestSync(request)`.
   - Blocks until the response arrives (up to 30 seconds).

3. **Server receives** → dispatch to `AuthHandler`.

4. **AuthHandler.handleLogout(request)**:
   - Extracts the session token from either the payload or `request.getSessionToken()`.
   - Calls `SessionManager.invalidateSession(token)`.
   - The session manager removes the session from all three maps (sessions, userSessions, connectionSessions).
   - Returns `Response.success(request, "Logged out successfully")`.

5. **Client receives** (`DashboardScreen`):
   - The synchronous call returns the `Response`.
   - Clears `LoginController.currentSessionToken = null`.
   - Calls `GCMClient.getInstance().clearCurrentUser()`.
   - Navigates to `login.fxml`.

**Also on application close** (`LoginApp.stop()`):
- Sends `LOGOUT` request with the current session token.
- Closes the `GCMClient` connection.
- This ensures the session is cleaned up even when the user closes the window.

---

### 6.4 Guest Access

**Theory:** A user can browse the catalog without logging in. Guest users have no session token and can only perform search operations.

**Sequence of events:**

1. **User** clicks "Browse as Guest" on `LoginController`.

2. **Client** (`LoginController.handleBrowseGuest`):
   - Sets `currentUsername = "Guest"`.
   - Sets `currentUserRole = UserRole.ANONYMOUS`.
   - Does NOT send any server request.
   - Navigates directly to `catalog_search.fxml`.

3. **Guest searches**: uses `SearchControl` which sends requests without a session token. `SearchHandler` on the server does not require authentication, so these succeed.

4. **Guest tries to purchase**: The `CatalogSearchScreen` checks if the user is logged in before allowing purchase. If guest, shows "Login required" dialog.

---

## 7. Search & Catalog Actions

All search operations go through `SearchControl` on the client and `SearchHandler` on the server. No authentication is required.

### 7.1 Get Cities Catalog

**Theory:** Retrieves the full list of cities with their maps, used as the default view in the catalog screen.

**Sequence:**

1. **User** opens `CatalogSearchScreen` or clicks "Show All".

2. **CatalogSearchScreen** calls `searchControl.getCatalog()`.

3. **SearchControl.getCatalog()**:
   - Builds `Request(MessageType.GET_CITIES_CATALOG, null, sessionToken)` (sessionToken may be null for guests).
   - Calls `client.sendToServer(request)`.

4. **Server** → `SearchHandler.handle(request)`.

5. **SearchHandler**:
   - Calls `SearchDAO.getCitiesCatalog()`.

6. **SearchDAO.getCitiesCatalog()**:
   - Executes `SELECT id, name, description, price FROM cities` (with `WHERE approved = 1` if the `approved` column exists, otherwise no filter).
   - For each city, calls `getMapsForCity(cityId)`:
     - `SELECT m.id, m.name, m.short_description, (SELECT COUNT(*) FROM map_pois ...) as poi_count, (SELECT COUNT(*) FROM tours ...) as tour_count FROM maps m WHERE m.city_id = ?` (with `AND m.approved = 1` if available).
   - Builds a `List<CitySearchResult>`, each containing a list of `MapSummary` objects.
   - Returns the list.

7. **Server** returns `Response.success(request, List<CitySearchResult>)`.

8. **Client** → `SearchControl.displayMessage(msg)`:
   - Checks `response.getRequestType() == GET_CITIES_CATALOG`.
   - If success: calls `callback.onSearchResults((List<CitySearchResult>) response.getPayload())`.
   - If error: calls `callback.onError(response.getErrorMessage())`.

9. **CatalogSearchScreen.onSearchResults(results)**:
   - On JavaFX thread via `Platform.runLater(...)`.
   - Clears the results list and populates it with city items.
   - Each item shows city name, description, price, number of maps.

---

### 7.2 Search by City Name

**Sequence:**

1. **User** enters a city name and clicks "Search by City".

2. **CatalogSearchScreen** calls `searchControl.searchByCityName(cityName)`.

3. **SearchControl**:
   - Builds `SearchRequest.byCity(cityName)`.
   - Wraps in `Request(MessageType.SEARCH_BY_CITY_NAME, searchRequest, sessionToken)`.
   - Sends via `client.sendToServer(request)`.

4. **Server** → `SearchHandler` → `SearchDAO.searchByCityName(cityName)`:
   - `SELECT id, name, description, price FROM cities WHERE LOWER(name) LIKE LOWER('%<cityName>%')` (approved only if column exists).
   - For each matching city, loads its maps via `getMapsForCity(cityId)`.
   - Returns `List<CitySearchResult>`.

5. **Response** flows back through `SearchControl` → `callback.onSearchResults(results)` → UI update.

---

### 7.3 Search by POI Name

**Sequence:**

1. **User** enters a POI name and clicks "Search by Place".

2. **CatalogSearchScreen** calls `searchControl.searchByPoiName(poiName)`.

3. **SearchControl**:
   - Builds `SearchRequest.byPoi(poiName)`.
   - Wraps in `Request(MessageType.SEARCH_BY_POI_NAME, searchRequest, sessionToken)`.
   - Sends.

4. **Server** → `SearchHandler` → `SearchDAO.searchByPoiName(poiName)`:
   - Joins `cities`, `maps`, `map_pois`, `pois` tables.
   - `WHERE LOWER(p.name) LIKE LOWER('%<poiName>%')`.
   - Groups results by city.
   - Returns `List<CitySearchResult>`.

5. **Response** flows back to client, updates the search results UI.

---

### 7.4 Search by City and POI

**Sequence:**

1. **User** enters both a city name and a POI name, clicks search.

2. **CatalogSearchScreen** calls `searchControl.searchByCityAndPoi(cityName, poiName)`.

3. **SearchControl**:
   - Builds `SearchRequest.byCityAndPoi(cityName, poiName)`.
   - Wraps in `Request(MessageType.SEARCH_BY_CITY_AND_POI, searchRequest, sessionToken)`.

4. **Server** → `SearchDAO.searchByCityAndPoi(cityName, poiName)`:
   - Applies both city name and POI name filters.
   - Returns matching results.

5. **Response** returns to client and updates UI.

---

## 8. Map Editing Actions

Map editing is the most complex subsystem. It supports granular CRUD operations (individual city/map/POI/tour creates, updates, deletes) as well as batched changes via `MapChanges`. The client uses `ContentManagementControl` and the server uses `MapEditHandler`.

### Role-Based Access Control (RBAC) for Map Editing

- **CONTENT_MANAGER** / **COMPANY_MANAGER**: Can directly publish changes. When they submit with `draft=false`, changes are applied immediately.
- **CUSTOMER** / **CONTENT_EDITOR** / other roles: Cannot publish directly. When they submit with `draft=false`, a `MapEditRequest` with status `PENDING` is created, and a manager must approve it.
- All roles can save drafts (`draft=true`), which are stored as `MapEditRequest` with status `DRAFT`.

---

### 8.1 Load Cities

**Sequence:**

1. **MapEditorScreen** initializes → calls `contentControl.getCities()`.

2. **ContentManagementControl.getCities()**:
   - Builds `Request(MessageType.GET_CITIES, null, sessionToken, userId)`.
   - Sends via `client.sendToServer(request)`.

3. **Server** → `MapEditHandler`:
   - Resolves userId from session.
   - Calls `CityDAO.getAllCities(userId)`.

4. **CityDAO.getAllCities(userId)**:
   - `SELECT id, name, description, price FROM cities`.
   - If the `approved` column exists: `WHERE approved = 1 OR created_by = <userId>` — this shows approved cities plus any drafts created by the current user.
   - If no `approved` column: returns all cities.
   - Maps each row to `CityDTO`.
   - Returns `List<CityDTO>`.

5. **Response** → `ContentManagementControl.displayMessage`:
   - Detects `GET_CITIES` response with `List<CityDTO>` payload.
   - Calls `callback.onCitiesReceived(cities)`.

6. **MapEditorScreen.onCitiesReceived(cities)**:
   - Populates the city ComboBox with city names.
   - Also loads the user's draft via `contentControl.getMyDraft()`.

---

### 8.2 Load Maps for a City

**Sequence:**

1. **User** selects a city from the ComboBox.

2. **MapEditorScreen** calls `contentControl.getMapsForCity(cityId)`.

3. **ContentManagementControl**:
   - Builds `Request(MessageType.GET_MAPS_FOR_CITY, cityId, sessionToken)`.
   - Sends.

4. **Server** → `MapEditHandler` → `MapDAO.getMapsForCity(cityId)`:
   - First calls `ensureTourMapsForCity(cityId)`: for every tour in the city that has ≥2 stops, creates a "tour route" map if one doesn't exist yet.
   - `SELECT m.id, m.name, m.short_description, ... FROM maps m WHERE m.city_id = ?`.
   - For each map, counts POIs and tours.
   - Checks `map_edit_requests` to determine if a map has a pending approval request (`waitingForApproval`).
   - Returns `List<MapSummary>`.

5. **Response** → `callback.onMapsReceived(maps)`.

6. **MapEditorScreen.onMapsReceived(maps)**:
   - Separates maps into regular maps and tour maps (those with `tourId != null`).
   - Populates the maps list and tour maps list.

---

### 8.3 Load Map Content

**Sequence:**

1. **User** selects a map from the maps list.

2. **MapEditorScreen** calls `contentControl.getMapContent(mapId)`.

3. **Server** → `MapEditHandler` → `MapDAO.getMapContent(mapId)`:
   - Loads the map record, city info, POIs (via `map_pois` join), tours, and tour stops.
   - Includes draft POIs visible to the current user.
   - If there's a pending draft (`MapEditRequest` with status DRAFT for this map+user), attaches it as `pendingDraftChanges`.
   - Calculates tour distances via `PoiDistanceDAO`.
   - Returns `MapContent` (mapId, cityId, cityName, mapName, description, pois, tours, draft changes).

4. **Response** → `callback.onMapContentReceived(content)`.

5. **MapEditorScreen.onMapContentReceived(content)**:
   - Populates the POI list from `content.getPois()`.
   - Populates the tour list from `content.getTours()`.
   - Updates the map info tab.
   - Renders POI markers on the Gluon MapView.
   - If there are `pendingDraftChanges`, restores them (shows draft POIs, marks pending deletions, etc.).

---

### 8.4 Load POIs for a City

**Sequence:**

1. Triggered when the user wants to add an existing POI to a tour or link a POI from another map.

2. **MapEditorScreen** calls `contentControl.getPoisForCity(cityId)`.

3. **Server** → `MapEditHandler` → `PoiDAO.getPoisForCity(cityId)`:
   - `SELECT * FROM pois WHERE city_id = ?`.
   - Returns `List<Poi>`.

4. **Response** → `callback.onPoisForCityReceived(pois)`.

---

### 8.5 Create City

**Sequence:**

1. **User** clicks "Create City" in the map editor and enters city name, description, and price.

2. **MapEditorScreen** calls `contentControl.createCity(name, description, price)`.

3. **ContentManagementControl.createCity()**:
   - Builds a `CityDTO(0, name, description, price, 0, false)`.
   - Wraps in `Request(MessageType.CREATE_CITY, cityDTO, sessionToken, userId)`.
   - Sends.

4. **Server** → `MapEditHandler.handleCreateCity(request)`:
   - Validates the `CityDTO`: name must be non-empty, price ≥ 0.
   - Calls `CityDAO.createCity(name, description, price, userId)`.

5. **CityDAO.createCity()**:
   - `INSERT INTO cities (name, description, price, approved, created_by) VALUES (?, ?, ?, 0, ?)`.
   - `approved = 0` means the city starts as a draft and needs manager approval.
   - Returns the generated city ID.

6. **Server** returns `Response.success(request, validationResult)` where `validationResult.createdCityId` contains the new ID.

7. **Client** receives → shows success message, refreshes city list.

---

### 8.6 Update City

Similar to create, but sends `MessageType.UPDATE_CITY` with the modified `CityDTO`. Server calls `CityDAO.updateCity()` which executes `UPDATE cities SET name = ?, description = ?, price = ? WHERE id = ?`.

---

### 8.7 Create Map

**Sequence:**

1. **User** clicks "Create Map", enters map name and description.

2. **MapEditorScreen** calls `contentControl.createMap(name, description, cityId)`.

3. **ContentManagementControl**:
   - Builds `MapContent` with the name, description, and cityId.
   - Wraps in `Request(MessageType.CREATE_MAP, mapContent, sessionToken, userId)`.

4. **Server** → `MapEditHandler.handleCreateMap(request)`:
   - Validates: name non-empty.
   - Calls `MapDAO.createMap(name, description, cityId)`.
   - `INSERT INTO maps (name, short_description, city_id) VALUES (?, ?, ?)`.
   - Calls `DailyStatsDAO.increment(Metric.MAPS_COUNT, cityId)`.
   - Returns `ValidationResult` with `createdMapId`.

5. **Client** → refreshes maps list for the current city.

---

### 8.8 Delete Map

Sends `MessageType.DELETE_MAP` with the mapId. Server calls `MapDAO.deleteMap(mapId)` which deletes POI links (`DELETE FROM map_pois WHERE map_id = ?`) and then the map itself (`DELETE FROM maps WHERE id = ?`).

---

### 8.9 Add POI

**Sequence:**

1. **User** fills in the POI form (name, category, location/coordinates, description, accessibility) and clicks "Add POI".

2. **MapEditorScreen** builds a `Poi` object with the form data and calls `contentControl.addPoi(poi)`.

3. **ContentManagementControl**:
   - Wraps `Poi` in `Request(MessageType.ADD_POI, poi, sessionToken, userId)`.
   - Sends.

4. **Server** → `MapEditHandler.handleAddPoi(request)`:
   - Validates: name non-empty, cityId > 0.
   - Calls `PoiDAO.createPoi(poi)`.
   - `INSERT INTO pois (city_id, name, location, latitude, longitude, category, short_explanation, accessible) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`.
   - Returns `ValidationResult` with the created POI ID.

5. **Client** → adds the new POI to the UI list and map.

**Map click to add POI**: The `MapEditorScreen` uses a `WebView` with a Leaflet map. When the user clicks the map, JavaScript calls `window.javaApp.onMapClick(lat, lng)` which bridges to Java via `MapClickBridge`. The coordinates are used to reverse-geocode via OpenStreetMap Nominatim API to get a place name, and a new POI form is pre-filled.

---

### 8.10 Update POI

Sends `MessageType.UPDATE_POI` with the modified `Poi`. Server calls `PoiDAO.updatePoi(poi)`: `UPDATE pois SET name = ?, location = ?, ... WHERE id = ?`.

---

### 8.11 Delete POI

**Sequence:**

1. **User** selects a POI and clicks "Remove".

2. **MapEditorScreen** calls `contentControl.deletePoi(poiId)`.

3. **Server** → `MapEditHandler.handleDeletePoi(request)`:
   - Calls `PoiDAO.isPoiUsedInTour(poiId)` — checks if any `tour_stops` reference this POI.
   - If used in a tour: returns error "Cannot delete POI that is used in a tour. Remove it from all tours first."
   - Otherwise: calls `PoiDAO.deletePoi(poiId)`: `DELETE FROM pois WHERE id = ?`.
   - Returns success.

---

### 8.12 Link/Unlink POI to Map

**Link**: `MessageType.LINK_POI_TO_MAP` with a `PoiMapLink(mapId, poiId, displayOrder)`. Server calls `PoiDAO.linkPoiToMap(mapId, poiId, displayOrder)`: `INSERT INTO map_pois (map_id, poi_id, display_order) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE display_order = ?`.

**Unlink**: `MessageType.UNLINK_POI_FROM_MAP` with a `PoiMapLink`. Server calls `PoiDAO.unlinkPoiFromMap(mapId, poiId)`: `DELETE FROM map_pois WHERE map_id = ? AND poi_id = ?`.

These are typically sent as part of batched `MapChanges` rather than individually.

---

### 8.13 Create Tour

**Sequence:**

1. **User** fills in tour name and description, clicks "Create Tour".

2. **MapEditorScreen** calls `contentControl.createTour(name, description, cityId)`.

3. **ContentManagementControl**:
   - Builds `TourDTO(0, cityId, name, description, null, new ArrayList<>())`.
   - Wraps in `Request(MessageType.CREATE_TOUR, tourDTO, sessionToken, userId)`.

4. **Server** → `MapEditHandler.handleCreateTour(request)`:
   - Validates name non-empty.
   - Calls `TourDAO.createTour(tour)`: `INSERT INTO tours (city_id, name, description) VALUES (?, ?, ?)`.
   - Returns `ValidationResult` with created tour ID.

---

### 8.14 Update Tour

Sends `MessageType.UPDATE_TOUR` with modified `TourDTO`. Server calls `TourDAO.updateTour(tour)`: `UPDATE tours SET name = ?, description = ? WHERE id = ?`.

---

### 8.15 Delete Tour

Sends `MessageType.DELETE_TOUR` with tourId. Server calls `TourDAO.deleteTour(tourId)`: `DELETE FROM tours WHERE id = ?`.

---

### 8.16 Add Tour Stop

**Sequence:**

1. **User** selects a POI to add as a stop to the current tour.

2. **MapEditorScreen** calls `contentControl.addTourStop(tourId, poiId, stopOrder, notes)`.

3. **ContentManagementControl**:
   - Builds `TourStopDTO(0, tourId, poiId, null, null, stopOrder, null, notes)`.
   - Wraps in `Request(MessageType.ADD_TOUR_STOP, stopDTO, sessionToken, userId)`.

4. **Server** → `MapEditHandler.handleAddTourStop(request)`:
   - Validates: calls `TourDAO.poiExists(poiId)` — `SELECT COUNT(*) FROM pois WHERE id = ?`. If POI doesn't exist, returns error.
   - Calls `TourDAO.addTourStop(stop)`: `INSERT INTO tour_stops (tour_id, poi_id, stop_order, notes) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE stop_order = ?, notes = ?`.
   - Returns success.

---

### 8.17 Update Tour Stop

Sends `MessageType.UPDATE_TOUR_STOP` with the modified `TourStopDTO`. Server validates POI exists, then calls `TourDAO.updateTourStop(stop)`: `UPDATE tour_stops SET poi_id = ?, stop_order = ?, notes = ? WHERE id = ?`.

---

### 8.18 Remove Tour Stop

Sends `MessageType.REMOVE_TOUR_STOP` with the stopId. Server calls `TourDAO.removeTourStop(stopId)`: `DELETE FROM tour_stops WHERE id = ?`.

---

### 8.19 Save Map Changes (Draft)

**Theory:** This saves the user's work-in-progress without submitting for approval. Changes are stored as a JSON-serialized `MapChanges` object in the `map_edit_requests` table with status `DRAFT`.

**Sequence:**

1. **User** clicks "Save" in the map editor.

2. **MapEditorScreen** collects all pending changes into a `MapChanges` object:
   - Added POIs, updated POIs, deleted POI IDs.
   - POI map links and unlinks.
   - Added tours, updated tours, deleted tour IDs.
   - Added/updated/removed tour stops.
   - New cities, new maps, city+map combos.
   - Sets `draft = true`.

3. **MapEditorScreen** calls `contentControl.saveMapChanges(mapChanges)`.

4. **ContentManagementControl**:
   - Wraps in `Request(MessageType.SAVE_MAP_CHANGES, mapChanges, sessionToken, userId)`.
   - Sends.

5. **Server** → `MapEditHandler.handleSaveOrSubmit(request)`:
   - **Forced to draft mode**: regardless of what the client sent, `draft` is set to `true` for `SAVE_MAP_CHANGES`.
   - Resolves userId.
   - Calls `applyMapChangesAsDraftOrSubmit(mapChanges, userId, true)`.

6. **applyMapChangesAsDraftOrSubmit (draft=true)**:
   - Since this is a draft save:
     - If the changes reference a specific map: calls `MapEditRequestDAO.upsertDraftRequest(mapId, cityId, userId, mapChanges)`.
       - This **deletes** any existing DRAFT for this map+user and inserts a new one.
       - The `MapChanges` is serialized to JSON and stored in the `changes_json` column.
     - If the changes involve city-level operations (city deletions): calls `MapEditRequestDAO.upsertUserDraft(userId, mapChanges)`.
   - Returns `ValidationResult.success("Draft saved successfully")`.

7. **Client** → shows "Draft saved" confirmation.

---

### 8.20 Submit Map Changes

**Theory:** This is the most complex action. Depending on the user's role and the `draft` flag, it either: (a) saves as draft, (b) applies changes directly (for managers), or (c) creates a pending approval request (for non-managers).

**Sequence:**

1. **User** clicks "Send to Manager" (or "Publish" if they are a manager) in the map editor.

2. **MapEditorScreen** collects all changes into a `MapChanges` object and sets `draft = false`.

3. **MapEditorScreen** calls `contentControl.submitMapChanges(mapChanges, false)`.

4. **ContentManagementControl**:
   - Sets `mapChanges.setDraft(false)`.
   - Wraps in `Request(MessageType.SUBMIT_MAP_CHANGES, mapChanges, sessionToken, userId)`.
   - Sends.

5. **Server** → `MapEditHandler.handleSaveOrSubmit(request)`:
   - Resolves userId from session.
   - Gets user role from `SessionManager.validateSession(token).getRole()`.
   - `draft = mapChanges.isDraft()` → `false` in this case.
   - Calls `applyMapChangesAsDraftOrSubmit(mapChanges, userId, false)`.

6. **applyMapChangesAsDraftOrSubmit (draft=false)**:
   - **Branch A: User is CONTENT_MANAGER or COMPANY_MANAGER**:
     - Changes are applied directly.
     - Creates new cities (if any) via `CityDAO.createCity`.
     - Creates new maps (if any) via `MapDAO.createMap`.
     - Creates new POIs via `PoiDAO.createPoi`.
     - Updates existing POIs via `PoiDAO.updatePoi`.
     - Deletes POIs via `PoiDAO.deletePoi`.
     - Creates/deletes POI-map links via `PoiDAO.linkPoiToMap` / `unlinkPoiFromMap`.
     - Creates/updates/deletes tours and stops.
     - Deletes draft requests for this map+user: `MapEditRequestDAO.deleteDraftForMapUser`.
     - Updates daily stats: `DailyStatsDAO.increment(MAPS_COUNT)`.
     - Returns `ValidationResult.success("Changes published successfully")`.

   - **Branch B: User is NOT a manager**:
     - Changes are NOT applied directly.
     - First validates all changes via `validateAllChanges(mapChanges, userId)`:
       - Checks if previously rejected delete-tour or delete-POI operations are being resubmitted without re-performing the action.
     - Enriches `MapChanges` with draft POIs info for display in the approval screen.
     - Calls `MapEditRequestDAO.createRequest(mapId, cityId, userId, mapChanges, "PENDING")`.
       - This inserts a row in `map_edit_requests` with `status = 'PENDING'` and the `changes_json`.
     - Also deletes any existing DRAFT for this map+user.
     - Also deletes any previous PENDING requests by this user for the same map/city pair.
     - Returns `ValidationResult.success("Changes submitted for approval")`.

7. **Client** → shows success message. If manager, maps are refreshed. If non-manager, shows "Waiting for approval" status.

---

### 8.21 Load My Draft

**Sequence:**

1. **MapEditorScreen** calls `contentControl.getMyDraft()` during initialization.

2. **ContentManagementControl**:
   - Builds `Request(MessageType.GET_MY_DRAFT, null, sessionToken, userId)`.
   - Sends.

3. **Server** → `MapEditHandler`:
   - Resolves userId.
   - Calls `MapEditRequestDAO.getDraftRequestForUser(userId)`:
     - `SELECT * FROM map_edit_requests WHERE user_id = ? AND status = 'DRAFT' AND map_id IS NULL ORDER BY created_at DESC LIMIT 1`.
     - This retrieves user-level drafts (e.g., city deletions).
   - Returns the `MapEditRequestDTO` or null.

4. **Client** → `callback.onMyDraftReceived(draft)`:
   - If draft exists: applies saved changes (e.g., marks cities for deletion in the UI).

---

## 9. Map Edit Approval Actions

These actions are used by managers to review and approve/reject map edit requests submitted by non-managers.

### 9.1 Get Pending Map Edits

**Sequence:**

1. **MapApprovalsScreen** initializes → calls `contentControl.getPendingMapEdits()`.

2. **ContentManagementControl**:
   - Builds `Request(MessageType.GET_PENDING_MAP_EDITS, null, sessionToken)`.
   - Sends.

3. **Server** → `MapEditHandler` → `MapEditRequestDAO.getPendingRequests()`:
   - `SELECT r.*, u.username, m.name as map_name, c.name as city_name FROM map_edit_requests r LEFT JOIN users u ON r.user_id = u.id LEFT JOIN maps m ON r.map_id = m.id LEFT JOIN cities c ON r.city_id = c.id WHERE r.status = 'PENDING' ORDER BY r.created_at ASC`.
   - For each row, deserializes `changes_json` back into a `MapChanges` object.
   - Returns `List<MapEditRequestDTO>`.

4. **Client** → `callback.onPendingRequestsReceived(requests)`:
   - Populates the approval list with each request's details (map name, city, submitter, date, summary of changes).

---

### 9.2 Approve Map Edit

**Theory:** When a manager approves a map edit, all the changes contained in the `MapChanges` are applied to the database. This includes creating/updating/deleting POIs, tours, and tour stops. Customer notifications are sent.

**Sequence:**

1. **Manager** selects a pending request and clicks "Approve".

2. **MapApprovalsScreen** calls `contentControl.approveMapEdit(requestId)`.

3. **ContentManagementControl**:
   - Builds `Request(MessageType.APPROVE_MAP_EDIT, requestId, sessionToken)`.
   - Sends.

4. **Server** → `MapEditHandler.handleApproveMapEdit(request)`:
   - Calls `MapEditRequestDAO.getRequest(requestId)` to load the full request including `MapChanges`.
   - If the request's status is not `PENDING`, returns error.
   - Calls `applyMapChanges(changes, userId)`:
     - Creates new cities and sets them as approved: `CityDAO.createCity(...)`, `CityDAO.setCityApproved(cityId)`.
     - Creates new maps and sets approved: `MapDAO.createMap(...)`, `MapDAO.setMapApproved(mapId)`.
     - Creates new POIs: `PoiDAO.createPoi(poi)`.
     - Approves draft POI-map links: updates `map_pois` rows from `approved = 0` to `approved = 1`.
     - Updates existing POIs: `PoiDAO.updatePoi(poi)`.
     - Deletes POIs: `PoiDAO.deletePoi(poiId)`.
     - Processes POI unlinks: `PoiDAO.unlinkPoiFromMap(mapId, poiId)`.
     - Creates tours: `TourDAO.createTour(tour)`.
     - Updates tours: `TourDAO.updateTour(tour)`.
     - Deletes tours: `TourDAO.deleteTour(tourId)`.
     - Adds/updates/removes tour stops.
     - Deletes maps if requested: `MapDAO.deleteMap(mapId)`.
     - Deletes cities if requested: `CityDAO.deleteCity(cityId)`.
   - For each tour that has ≥2 stops, creates a tour route map via `MapDAO.createTourMap(tour, cityId)`.
   - Calls `MapEditRequestDAO.updateStatus(requestId, "APPROVED")`.
   - Notifies affected customers:
     - `PurchaseDAO.getCustomerIdsForCity(cityId)` — finds all users who purchased this city.
     - `NotificationDAO.createNotification(userId, "IN_APP", "Map Updated", "A map in <city> has been updated")`.
   - Returns `ValidationResult.success("Map edit approved and changes applied")`.

5. **Client** → shows success, refreshes the pending list.

---

### 9.3 Reject Map Edit

**Sequence:**

1. **Manager** selects a pending request and clicks "Reject".

2. **MapApprovalsScreen** calls `contentControl.rejectMapEdit(requestId)`.

3. **Server** → `MapEditHandler.handleRejectMapEdit(request)`:
   - Calls `MapEditRequestDAO.updateStatus(requestId, "REJECTED")`.
   - Returns `ValidationResult.success("Map edit request rejected")`.

4. **Client** → shows success, refreshes pending list.

**Note:** When a non-manager's request is rejected, and they later try to resubmit the same delete operations (delete tour or delete POI), the system checks `MapEditRequestDAO.getRejectedDeletedTourIdsForUserAndScope(userId, mapId, cityId)` and blocks the resubmission, requiring the user to re-perform the deletion action first.

---

## 10. Map Version Approval Actions

Map versions are a separate approval workflow from map edits. They are used for formal versioning of published maps.

### 10.1 List Pending Map Versions

**Sequence:**

1. **EditApprovalScreen** initializes → sends `Request(MessageType.LIST_PENDING_MAP_VERSIONS, null, sessionToken)`.

2. **Server** → `ApprovalHandler` → `MapVersionDAO.listPendingVersions()`:
   - `SELECT mv.*, m.name as map_name, c.name as city_name, u.username as created_by_username FROM map_versions mv JOIN maps m ON mv.map_id = m.id JOIN cities c ON m.city_id = c.id LEFT JOIN users u ON mv.created_by = u.id WHERE mv.status = 'PENDING' ORDER BY mv.created_at ASC`.
   - Returns `List<MapVersionDTO>`.

3. **Client** → populates the versions table.

---

### 10.2 Get Map Version Details

Sends `MessageType.GET_MAP_VERSION_DETAILS` with versionId. Server calls `MapVersionDAO.getVersionById(versionId)` and returns the full `MapVersionDTO`.

---

### 10.3 Approve Map Version

**Sequence:**

1. **Manager** clicks "Approve" on a pending version.

2. **EditApprovalScreen** sends `Request(MessageType.APPROVE_MAP_VERSION, ApprovalRequest.approve(versionId), sessionToken)`.

3. **Server** → `ApprovalHandler.handleApproveVersion(request)`:
   - Gets a DB connection and starts a transaction.
   - Loads the version via `MapVersionDAO.getVersionById(versionId)`.
   - Verifies status is `PENDING`.
   - Calls `MapVersionDAO.updateStatus(conn, versionId, "APPROVED", userId, null)`.
   - Calls `ApprovalDAO.updateApproval(conn, "MAP_VERSION", versionId, "APPROVED", userId, reason)`.
   - Calls `AuditLogDAO.log(conn, userId, "APPROVE_MAP_VERSION", ...)`.
   - Gets `cityId` via `MapVersionDAO.getCityIdForVersion(conn, versionId)`.
   - Calls `NotificationDAO.notifyCustomersAboutMapUpdate(conn, cityId, mapName)`:
     - This queries `NotificationDAO.getCustomersWhoPurchasedCity(cityId)` to find all users who have a purchase record for this city.
     - Creates an IN_APP notification for each such user: "Map Updated: <mapName> has a new approved version".
   - Commits the transaction.
   - Returns the updated `MapVersionDTO`.

4. **Client** → refreshes the versions list, shows success dialog.

---

### 10.4 Reject Map Version

**Sequence:**

1. **Manager** clicks "Reject", enters a reason (required).

2. **EditApprovalScreen** sends `Request(MessageType.REJECT_MAP_VERSION, ApprovalRequest.reject(versionId, reason), sessionToken)`.

3. **Server** → `ApprovalHandler.handleRejectVersion(request)`:
   - Validates the reason is non-empty.
   - Loads version, verifies `PENDING`.
   - `MapVersionDAO.updateStatus(conn, versionId, "REJECTED", userId, reason)`.
   - `ApprovalDAO.updateApproval(...)`.
   - `AuditLogDAO.log(...)`.
   - `NotificationDAO.createNotification(submitterId, "IN_APP", "Version Rejected", "Your version for <mapName> was rejected: <reason>")`.
   - Commits transaction.
   - Returns updated `MapVersionDTO`.

4. **Client** → refreshes, shows success.

---

## 11. Purchase Actions

### 11.1 Get City Price

**Sequence:**

1. **CatalogSearchScreen** or **PurchaseControl** requests pricing for a specific city.

2. Client sends `Request(MessageType.GET_CITY_PRICE, cityId, sessionToken)`.

3. **Server** → `PurchaseHandler` → `PurchaseDAO.getCityPrice(cityId)`:
   - `SELECT id, name, price FROM cities WHERE id = ?`.
   - Builds `CityPriceInfo(cityId, cityName, oneTimePrice)`.
   - Also populates subscription prices: `Map<Integer, Double>` for 1, 3, 6 month options, each calculated based on the city's base price.
   - Returns `CityPriceInfo`.

4. **Response** returns to client.

---

### 11.2 Purchase One-Time

**Theory:** A one-time purchase gives the user a single view and single download for a city's maps. No expiration date.

**Sequence:**

1. **User** selects a city in `CatalogSearchScreen`, chooses "One-Time Purchase", fills in payment details (card number, ID, expiry, CVV).

2. **CatalogSearchScreen** builds `PurchaseRequest.oneTime(cityId)` with card details.

3. Client sends `Request(MessageType.PURCHASE_ONE_TIME, purchaseRequest, sessionToken)`.

4. **Server** → `PurchaseHandler.handlePurchaseOneTime(request)`:
   - Validates session via `SessionManager.validateSession(token)`.
   - If session invalid: returns `ERR_SESSION_EXPIRED`.
   - Extracts `PurchaseRequest` from payload.
   - Calls `PurchaseDAO.purchaseOneTime(userId, cityId)`:
     - `INSERT INTO purchases (user_id, city_id, price_paid, purchased_at) VALUES (?, ?, (SELECT price FROM cities WHERE id = ?), NOW())`.
     - Returns the price paid.
   - If `purchaseRequest.isSaveCard()`: calls `UserDAO.updateProfile(userId, { "card_last4": ..., "card_expiry": ... })`.
   - Calls `DailyStatsDAO.increment(Metric.PURCHASE_ONE_TIME, cityId)` to record the metric.
   - Returns `Response.success(request, PurchaseResponse(true, "Purchase successful", EntitlementType.ONE_TIME, null))`.

5. **Client** → shows "Purchase successful" dialog.

---

### 11.3 Purchase Subscription

**Theory:** A subscription gives unlimited views and downloads for a city's maps for a period of 1, 3, or 6 months. A 10% discount applies if the user has an active subscription for the same city expiring within 3 days.

**Sequence:**

1. **User** selects a subscription duration (1, 3, or 6 months) and fills payment details.

2. **CatalogSearchScreen** builds `PurchaseRequest.subscription(cityId, months)` with card details.

3. Client sends `Request(MessageType.PURCHASE_SUBSCRIPTION, purchaseRequest, sessionToken)`.

4. **Server** → `PurchaseHandler.handlePurchaseSubscription(request)`:
   - Validates session.
   - Calls `PurchaseDAO.hasActiveExpiringSubscription(userId, cityId, months)`:
     - Checks if user has a subscription for this city+duration that expires within the next 3 days.
     - Returns `true` if eligible for renewal discount.
   - Calls `PurchaseDAO.purchaseSubscription(userId, cityId, months, isRenewal)`:
     - Calculates price: `base_price * months_factor` (e.g., 1 month = 1x, 3 months = 2.5x, 6 months = 4x).
     - If `isRenewal`: applies 10% discount.
     - `INSERT INTO subscriptions (user_id, city_id, months, price_paid, start_date, end_date) VALUES (?, ?, ?, ?, NOW(), DATE_ADD(NOW(), INTERVAL ? MONTH))`.
   - If save card: updates user profile.
   - Calls `DailyStatsDAO.increment(isRenewal ? Metric.RENEWAL : Metric.PURCHASE_SUBSCRIPTION, cityId)`.
   - Returns `PurchaseResponse(true, message, EntitlementType.SUBSCRIPTION, expiryDate)`.

5. **Client** → shows success with expiry date.

---

### 11.4 Check Discount Eligibility

**Sequence:**

1. **CatalogSearchScreen** wants to know if the user qualifies for a renewal discount.

2. Client (via `SearchControl.checkDiscountEligibility(cityId, months)`) sends `Request(MessageType.CHECK_DISCOUNT_ELIGIBILITY, DiscountCheckRequest(cityId, months), sessionToken)`.

3. **Server** → `PurchaseHandler`:
   - Validates session (guest gets `false`).
   - Calls `PurchaseDAO.hasActiveExpiringSubscription(userId, cityId, months)`.
   - Returns `Response.success(request, true/false)`.

4. **Client** → `callback.onDiscountEligibility(eligible)`:
   - If eligible: shows "10% renewal discount available!" in the UI.

---

### 11.5 Get Entitlement

**Theory:** Checks what access a user has for a specific city. Returns one of `NONE`, `ONE_TIME`, or `SUBSCRIPTION`.

**Sequence:**

1. Client sends `Request(MessageType.GET_ENTITLEMENT, cityId, sessionToken)`.

2. **Server** → `PurchaseHandler`:
   - If no valid session (guest): returns `EntitlementInfo` with type `NONE`.
   - Calls `PurchaseDAO.getEntitlement(userId, cityId)`:
     - First checks for active subscription: `SELECT * FROM subscriptions WHERE user_id = ? AND city_id = ? AND end_date > NOW() ORDER BY end_date DESC LIMIT 1`.
     - If found: returns `EntitlementInfo(SUBSCRIPTION, expiryDate, canView=true, canDownload=true)`.
     - If no subscription: checks for one-time purchase: `SELECT * FROM purchases WHERE user_id = ? AND city_id = ?`.
     - If found: checks download count via `getDownloadCount(userId, cityId)`.
     - If `downloadCount < 1`: returns `EntitlementInfo(ONE_TIME, null, canView=true, canDownload=true)`.
     - If already downloaded: returns `EntitlementInfo(ONE_TIME, null, canView=true, canDownload=false)`.
     - If no purchase at all: returns `EntitlementInfo(NONE)`.
   - Returns the entitlement info.

3. **Client** → uses the entitlement to enable/disable download buttons, show purchase options, etc.

---

### 11.6 Can Download

Similar to Get Entitlement but returns a simple `boolean`. Sends `MessageType.CAN_DOWNLOAD`. Server checks entitlement and returns `canDownload` status.

---

### 11.7 Download Map Version

**Sequence:**

1. **User** clicks "Download" on a map in `MyPurchasesScreen` or `ProfileScreen`.

2. Client sends `Request(MessageType.DOWNLOAD_MAP_VERSION, cityId, sessionToken)`.

3. **Server** → `PurchaseHandler`:
   - Validates session.
   - Gets entitlement via `PurchaseDAO.getEntitlement(userId, cityId)`.
   - If entitlement is `NONE` or `canDownload` is `false`: returns error.
   - If entitlement is `ONE_TIME`: calls `PurchaseDAO.recordDownload(userId, cityId)`:
     - `INSERT INTO download_events (user_id, city_id, downloaded_at) VALUES (?, ?, NOW())`.
   - Calls `DailyStatsDAO.increment(Metric.DOWNLOAD, cityId)`.
   - Returns success message.

4. **Client** → shows "Download successful" dialog. (In this simulation, no actual file is downloaded — it's a record-keeping operation.)

---

### 11.8 Record View Event

**Sequence:**

1. When a user views a map (opens map content), the client sends `Request(MessageType.RECORD_VIEW_EVENT, "cityId,mapId", sessionToken)`.

2. **Server** → `PurchaseHandler`:
   - Parses "cityId,mapId" from payload.
   - Calls `PurchaseDAO.recordView(userId, cityId, mapId)`:
     - `INSERT INTO view_events (user_id, city_id, map_id, viewed_at) VALUES (?, ?, ?, NOW())`.
   - Calls `DailyStatsDAO.increment(Metric.VIEW, cityId)`.
   - Returns success.

---

### 11.9 Get My Purchases

**Sequence:**

1. **ProfileScreen** or **MyPurchasesScreen** loads purchases.

2. Client sends `Request(MessageType.GET_MY_PURCHASES, null, sessionToken)`.

3. **Server** → `PurchaseHandler`:
   - Validates session.
   - Calls `PurchaseDAO.getUserPurchases(userId)`:
     - Queries subscriptions and purchases, builds `List<EntitlementInfo>` with details about each city's access.
   - Returns the list.

4. **Client** → populates purchase tables.

---

## 12. Customer/Profile Actions

### 12.1 Get My Profile

**Sequence:**

1. **ProfileScreen** initializes → sends `Request(MessageType.GET_MY_PROFILE, null, sessionToken)`.

2. **Server** → `CustomerHandler`:
   - Validates session, gets userId.
   - Calls `UserDAO.getProfile(userId)`:
     - `SELECT u.id, u.username, u.email, u.created_at, u.last_login_at, c.phone, c.card_last4, c.card_expiry, (SELECT COUNT(*) FROM purchases WHERE user_id = u.id) + (SELECT COUNT(*) FROM subscriptions WHERE user_id = u.id) as total_purchases, (SELECT COALESCE(SUM(price_paid),0) FROM purchases WHERE user_id = u.id) + (SELECT COALESCE(SUM(price_paid),0) FROM subscriptions WHERE user_id = u.id) as total_spent FROM users u LEFT JOIN customers c ON u.id = c.user_id WHERE u.id = ?`.
   - Returns `CustomerProfileDTO`.
   - For manager roles: card details are masked.

3. **Client** → displays profile info.

---

### 12.2 Update My Profile

**Sequence:**

1. **User** edits email, phone, or removes card on `ProfileScreen`, clicks "Save".

2. **ProfileScreen** builds a `Map<String, String>` with changed fields (e.g., `{"email": "new@email.com", "phone": "050-1234567"}`).

3. Client sends `Request(MessageType.UPDATE_MY_PROFILE, fieldsMap, sessionToken)`.

4. **Server** → `CustomerHandler`:
   - Validates session.
   - If email is being changed: checks `UserDAO.findByEmail(newEmail)` to ensure uniqueness.
   - Calls `UserDAO.updateProfile(userId, fields)`:
     - Updates `users` table (email).
     - Updates `customers` table (phone, card_last4, card_expiry).
   - Re-fetches and returns updated `CustomerProfileDTO`.

5. **Client** → shows "Profile updated" and refreshes display.

---

### 12.3 Admin List Customers

**Sequence:**

1. **AdminCustomersScreen** initializes → sends `Request(MessageType.ADMIN_LIST_CUSTOMERS, null, sessionToken)`.

2. **Server** → `CustomerHandler`:
   - Validates session.
   - Checks role: must be `CONTENT_MANAGER` or `COMPANY_MANAGER`. Otherwise returns `ERR_FORBIDDEN`.
   - Calls `UserDAO.listAllCustomers()`:
     - `SELECT u.id, u.username, u.email, u.is_active, u.created_at, c.phone, (purchase/subscription aggregate stats) FROM users u LEFT JOIN customers c ON u.id = c.user_id WHERE u.role = 'CUSTOMER' ORDER BY u.created_at DESC`.
   - Returns `List<CustomerListItemDTO>`.

3. **Client** → populates customers table with summary stats (total customers, revenue, average spend).

---

### 12.4 Admin Get Customer Purchases

**Sequence:**

1. **Admin** selects a customer row and clicks to view their purchases.

2. **AdminCustomersScreen** sends `Request(MessageType.ADMIN_GET_CUSTOMER_PURCHASES, Map{"userId": selectedUserId, "lastMonthOnly": false}, sessionToken)`.

3. **Server** → `CustomerHandler`:
   - Validates session and role (must be manager).
   - Calls `PurchaseDAO.getPurchasesDetailed(targetUserId, lastMonthOnly)`:
     - Queries both `purchases` and `subscriptions` tables.
     - For subscriptions: includes months, start_date, end_date, is_active.
     - Returns `List<CustomerPurchaseDTO>`.

4. **Client** → populates the purchases panel for the selected customer.

---

## 13. Notification Actions

### 13.1 Get My Notifications

**Sequence:**

1. **DashboardScreen** opens notification dialog → sends `Request(MessageType.GET_MY_NOTIFICATIONS, null, sessionToken)`.

2. **Server** → `NotificationHandler`:
   - Validates session.
   - Calls `NotificationDAO.getNotificationsForUser(userId)`:
     - `SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC`.
   - Returns `List<NotificationDTO>`.

3. **Client** → displays notifications in a dialog, with "Mark Read" buttons for unread ones.

---

### 13.2 Mark Notification Read

**Sequence:**

1. **User** clicks "Mark Read" on a notification.

2. Client sends `Request(MessageType.MARK_NOTIFICATION_READ, notificationId, sessionToken)`.

3. **Server** → `NotificationHandler`:
   - Validates session.
   - Calls `NotificationDAO.markAsRead(notificationId)`:
     - `UPDATE notifications SET is_read = TRUE WHERE id = ?`.
   - Returns success.

4. **Client** → updates notification as read, refreshes unread count.

---

### 13.3 Get Unread Count

**Sequence:**

1. **DashboardScreen** initializes → sends `Request(MessageType.GET_UNREAD_COUNT, null, sessionToken)`.

2. **Server** → `NotificationHandler`:
   - Validates session.
   - Calls `NotificationDAO.getUnreadCount(userId)`:
     - `SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = FALSE`.
   - Returns the count as `int`.

3. **Client** → updates the notification badge on the dashboard. If count > 0, badge is visible.

---

## 14. Pricing Actions

### 14.1 Get Current Prices

**Sequence:**

1. **PricingScreen** initializes → sends `Request(MessageType.GET_CURRENT_PRICES, null, sessionToken)`.

2. **Server** → `PricingHandler`:
   - Calls `PricingDAO.ensureTableExists()` — creates `pricing_requests` table if it doesn't exist.
   - Calls `PricingDAO.getAllCurrentPrices()`:
     - `SELECT id, name, price FROM cities ORDER BY name`.
     - Wraps each row in a `CityPriceInfo`.
   - Returns `List<CityPriceInfo>`.

3. **Client** → populates the pricing table.

---

### 14.2 Submit Pricing Request

**Theory:** A content manager proposes a new price for a city. The request goes to a company manager for approval.

**Sequence:**

1. **User** (content manager) selects a city, enters a new price and justification (≥10 characters), clicks "Submit".

2. **PricingScreen** sends `Request(MessageType.SUBMIT_PRICING_REQUEST, SubmitPricingRequest(cityId, proposedPrice, reason), sessionToken)`.

3. **Server** → `PricingHandler.handleSubmitPricingRequest(request)`:
   - Validates session.
   - Validates: price between 0 and 10,000; reason ≥ 10 characters.
   - Calls `PricingDAO.hasPendingRequest(cityId)`:
     - `SELECT COUNT(*) FROM pricing_requests WHERE city_id = ? AND status = 'PENDING'`.
     - If a pending request exists for this city, returns error "A pricing request is already pending for this city".
   - Calls `PricingDAO.createPricingRequest(cityId, proposedPrice, reason, userId)`:
     - `INSERT INTO pricing_requests (city_id, proposed_price, reason, created_by, status, created_at) VALUES (?, ?, ?, ?, 'PENDING', NOW())`.
   - Calls `AuditLogDAO.logSimple(userId, "SUBMIT_PRICING_REQUEST", ...)`.
   - Returns the created `PricingRequestDTO`.

4. **Client** → shows "Pricing request submitted successfully", clears form.

---

### 14.3 List Pending Pricing Requests

**Sequence:**

1. **PricingApprovalScreen** initializes → sends `Request(MessageType.LIST_PENDING_PRICING_REQUESTS, null, sessionToken)`.

2. **Server** → `PricingHandler` → `PricingDAO.listPendingRequests()`:
   - `SELECT pr.*, c.name as city_name, c.price as current_price, u.username as created_by_name FROM pricing_requests pr JOIN cities c ON pr.city_id = c.id LEFT JOIN users u ON pr.created_by = u.id WHERE pr.status = 'PENDING' ORDER BY pr.created_at ASC`.
   - Returns `List<PricingRequestDTO>`.

3. **Client** → populates the approval table showing each request with current price, proposed price, change percentage, submitter, and date.

---

### 14.4 Approve Pricing Request

**Sequence:**

1. **Company manager** selects a request and clicks "Approve".

2. **PricingApprovalScreen** sends `Request(MessageType.APPROVE_PRICING_REQUEST, ApprovePricingRequest(requestId, null), sessionToken)`.

3. **Server** → `PricingHandler.handleApprovePricingRequest(request)`:
   - Validates session.
   - Gets a DB connection for transaction.
   - Loads request via `PricingDAO.getRequestById(requestId)`.
   - If status is not `PENDING`: returns error.
   - Calls `PricingDAO.approveRequest(conn, requestId, userId)`:
     - `UPDATE pricing_requests SET status = 'APPROVED', approved_by = ?, processed_at = NOW() WHERE id = ?`.
     - `UPDATE cities SET price = ? WHERE id = ?` — **applies the new price immediately**.
   - Calls `AuditLogDAO.log(conn, userId, "APPROVE_PRICING_REQUEST", ...)`.
   - Calls `NotificationDAO.createNotification(conn, submitterId, "IN_APP", "Pricing Approved", "Your pricing request for <city> has been approved. New price: $<price>")`.
   - Commits transaction.
   - Returns updated `PricingRequestDTO`.

4. **Client** → refreshes list, shows success dialog.

---

### 14.5 Reject Pricing Request

**Sequence:**

1. **Company manager** clicks "Reject", enters a reason.

2. **PricingApprovalScreen** sends `Request(MessageType.REJECT_PRICING_REQUEST, ApprovePricingRequest(requestId, reason), sessionToken)`.

3. **Server** → `PricingHandler.handleRejectPricingRequest(request)`:
   - Validates reason is non-empty.
   - Calls `PricingDAO.rejectRequest(conn, requestId, userId, reason)`:
     - `UPDATE pricing_requests SET status = 'REJECTED', approved_by = ?, rejection_reason = ?, processed_at = NOW() WHERE id = ?`.
     - Does NOT change the city's price.
   - Logs audit entry.
   - Notifies submitter: "Your pricing request for <city> was rejected: <reason>".
   - Commits.
   - Returns updated `PricingRequestDTO`.

4. **Client** → refreshes, shows success.

---

## 15. Support Ticket Actions

### 15.1 Create Ticket

**Theory:** A customer creates a support ticket. The system first checks for similar recent tickets (to avoid duplicates), then has a bot service attempt to answer using FAQ matching. If the bot can't help, the ticket is auto-escalated for human agent intervention.

**Sequence:**

1. **User** fills in subject, message, and priority on `SupportScreen`, clicks "Submit".

2. **SupportScreen** sends synchronously: `sendRequestSync(Request(MessageType.CREATE_TICKET, CreateTicketRequest(subject, message, priority), sessionToken))`.

3. **Server** → `SupportHandler.handleCreateTicket(request)`:
   - Validates session.
   - Extracts `CreateTicketRequest`.
   
   **Step A: Similar ticket check:**
   - Calls `SupportDAO.findSimilarRecentTicket(userId, subject, message)`:
     - Loads all `OPEN` or `BOT_RESPONDED` tickets for this user.
     - For each, computes keyword overlap between the new ticket's subject+message and the existing ticket's subject.
     - If overlap ≥ 2 keywords: returns the existing ticket (potential duplicate).
   - If a similar ticket is found:
     - Adds the new message to the existing ticket: `SupportDAO.addMessage(existingTicketId, CUSTOMER, userId, message)`.
     - Returns a response with `{ "ticket": existingTicket, "wasDuplicate": true, "message": "Added to existing similar ticket" }`.
   
   **Step B: Create new ticket:**
   - Calls `SupportDAO.createTicket(userId, subject, message, priority)`:
     - `INSERT INTO support_tickets (user_id, subject, status, priority, created_at) VALUES (?, ?, 'OPEN', ?, NOW())`.
     - `INSERT INTO ticket_messages (ticket_id, sender_type, sender_id, message, created_at) VALUES (?, 'CUSTOMER', ?, ?, NOW())`.
     - Returns the new ticket ID.
   
   **Step C: Bot response:**
   - Calls `BotService.generateResponse(subject, message)`:
     - Tokenizes the subject and message into keywords (lowercased, stopwords removed).
     - Calls `SupportDAO.findMatchingFaq(keywords)`:
       - For each FAQ entry, counts keyword matches.
       - Returns matching FAQs sorted by match count.
     - **Strong match (≥3 keyword matches)**: Returns FAQ answer as the bot response.
     - **Partial match (1–2 matches)**: Returns FAQ answer + "If this doesn't help, consider escalating".
     - **Special keywords** (billing, charge, payment, refund): Suggests escalation.
     - **No match**: Returns generic "Thanks for contacting us. A support agent will review your ticket." and flags for auto-escalation.
   - Calls `BotService.addBotResponseToTicket(ticketId, botResponse)`:
     - `SupportDAO.addMessage(ticketId, BOT, null, botResponse)`.
     - `SupportDAO.updateTicketStatus(ticketId, "BOT_RESPONDED")`.
     - If auto-escalate flag is set: `SupportDAO.updateTicketStatus(ticketId, "ESCALATED")`.
   
   **Step D: Audit log:**
   - `AuditLogDAO.logSimple(userId, "CREATE_TICKET", ...)`.
   
   - Returns `{ "ticket": ticketDTO, "wasDuplicate": false, "message": "Ticket created" }`.

4. **Client** → shows the ticket with the bot's response. If it was a duplicate, informs the user.

---

### 15.2 Get My Tickets

**Sequence:**

1. **SupportScreen** initializes or refreshes → `sendRequestSync(Request(MessageType.GET_MY_TICKETS, null, sessionToken))`.

2. **Server** → `SupportHandler` → `SupportDAO.getTicketsForUser(userId)`:
   - `SELECT * FROM support_tickets WHERE user_id = ? ORDER BY created_at DESC`.
   - Does NOT load messages (only ticket summary).
   - Returns `List<SupportTicketDTO>`.

3. **Client** → populates ticket list.

---

### 15.3 Get Ticket Details

**Sequence:**

1. **User** clicks a ticket in the list.

2. **SupportScreen** sends `sendRequestSync(Request(MessageType.GET_TICKET_DETAILS, ticketId, sessionToken))`.

3. **Server** → `SupportHandler` → `SupportDAO.getTicketById(ticketId)`:
   - Loads ticket record.
   - Loads all messages: `SELECT tm.*, u.username as sender_name FROM ticket_messages tm LEFT JOIN users u ON tm.sender_id = u.id WHERE tm.ticket_id = ? ORDER BY tm.created_at ASC`.
   - Returns `SupportTicketDTO` with full message list.

4. **Client** → displays the ticket detail with the full message thread (customer messages, bot responses, agent replies).

---

### 15.4 Customer Reply

**Sequence:**

1. **User** types a reply and clicks "Send".

2. **SupportScreen** sends `sendRequestSync(Request(MessageType.CUSTOMER_REPLY, Map{"ticketId": id, "message": text}, sessionToken))`.

3. **Server** → `SupportHandler.handleCustomerReply(request)`:
   - Validates session and ticket ownership (ticket's userId matches session userId).
   - If ticket is CLOSED: returns error.
   - Calls `SupportDAO.addMessage(ticketId, CUSTOMER, userId, message)`.
   - If ticket status is `OPEN` or `BOT_RESPONDED`: invokes `BotService.generateResponse(subject, message)` again and adds a bot reply. This gives the bot another chance to help before human escalation.
   - Returns success.

4. **Client** → refreshes ticket details to show the new messages.

---

### 15.5 Escalate Ticket

**Sequence:**

1. **User** clicks "Escalate" (only visible for non-closed tickets).

2. **SupportScreen** sends `sendRequestSync(Request(MessageType.ESCALATE_TICKET, ticketId, sessionToken))`.

3. **Server** → `SupportHandler`:
   - Validates ownership.
   - If ticket is CLOSED: returns error.
   - Calls `SupportDAO.updateTicketStatus(ticketId, "ESCALATED")`.
   - Adds a system message: `SupportDAO.addMessage(ticketId, BOT, null, "Ticket has been escalated to a support agent. Please wait for a response.")`.
   - Returns success.

4. **Client** → ticket status changes to ESCALATED. It now appears in the agent's pending queue.

---

### 15.6 Close Ticket

**Sequence:**

1. **User** clicks "Close Ticket".

2. **SupportScreen** sends `sendRequestSync(Request(MessageType.CLOSE_TICKET, ticketId, sessionToken))`.

3. **Server** → `SupportHandler`:
   - Validates ownership.
   - Calls `SupportDAO.closeTicket(ticketId)`:
     - `UPDATE support_tickets SET status = 'CLOSED', closed_at = NOW() WHERE id = ?`.
   - Logs audit entry.
   - Returns success.

4. **Client** → ticket status changes to CLOSED.

**Note:** There is a known issue where `CLOSE_TICKET` may not be included in `SupportHandler.canHandle()`, which means it might not be routed correctly. The handler does have the logic, but the routing check may be incomplete.

---

### 15.7 Agent List Assigned

**Sequence:**

1. **AgentConsoleScreen** "My Tickets" tab → `sendRequestSync(Request(MessageType.AGENT_LIST_ASSIGNED, null, sessionToken))`.

2. **Server** → `SupportHandler` → `SupportDAO.getTicketsForAgent(agentId)`:
   - `SELECT * FROM support_tickets WHERE assigned_agent_id = ? ORDER BY created_at DESC`.
   - Returns `List<SupportTicketDTO>`.

3. **Client** → populates "My Tickets" list.

---

### 15.8 Agent List Pending

**Sequence:**

1. **AgentConsoleScreen** "Pending Queue" tab → `sendRequestSync(Request(MessageType.AGENT_LIST_PENDING, null, sessionToken))`.

2. **Server** → `SupportHandler` → `SupportDAO.getPendingEscalations()`:
   - `SELECT * FROM support_tickets WHERE status = 'ESCALATED' AND assigned_agent_id IS NULL ORDER BY FIELD(priority, 'HIGH', 'MEDIUM', 'LOW'), created_at ASC`.
   - Returns escalated, unassigned tickets sorted by priority.

3. **Client** → populates "Pending Queue" list.

---

### 15.9 Agent Claim Ticket

**Sequence:**

1. **Agent** selects a pending ticket and clicks "Claim".

2. **AgentConsoleScreen** sends `sendRequestSync(Request(MessageType.AGENT_CLAIM_TICKET, ticketId, sessionToken))`.

3. **Server** → `SupportHandler`:
   - Loads ticket, verifies it's unassigned.
   - Calls `SupportDAO.assignAgent(ticketId, agentId)`:
     - `UPDATE support_tickets SET assigned_agent_id = ? WHERE id = ?`.
   - Adds system message: `SupportDAO.addMessage(ticketId, AGENT, agentId, "Support agent <name> has been assigned to your ticket.")`.
   - Returns success.

4. **Client** → ticket moves from Pending Queue to My Tickets.

---

### 15.10 Agent Reply

**Sequence:**

1. **Agent** types a reply and clicks "Send".

2. **AgentConsoleScreen** sends `sendRequestSync(Request(MessageType.AGENT_REPLY, Map{"ticketId": id, "message": text}, sessionToken))`.

3. **Server** → `SupportHandler`:
   - Validates agent is assigned to this ticket.
   - Calls `SupportDAO.addMessage(ticketId, AGENT, agentId, message)`.
   - Returns success.

4. **Client** → refreshes ticket detail.

---

### 15.11 Agent Close Ticket

**Sequence:**

1. **Agent** clicks "Resolve" (optionally with a closing message).

2. **AgentConsoleScreen** sends `sendRequestSync(Request(MessageType.AGENT_CLOSE_TICKET, Map{"ticketId": id, "message": closingMessage}, sessionToken))`.

3. **Server** → `SupportHandler`:
   - Validates agent is assigned.
   - If message is provided: `SupportDAO.addMessage(ticketId, AGENT, agentId, message)`.
   - Calls `SupportDAO.closeTicket(ticketId)`.
   - Returns success.

4. **Client** → ticket status changes to CLOSED.

---

## 16. Report Actions

### 16.1 Get Activity Report

**Theory:** Managers can generate reports showing daily statistics (purchases, subscriptions, renewals, views, downloads) for a date range, either for all cities or a specific city.

**Sequence:**

1. **ReportsController** loads → sends `Request(MessageType.GET_CITIES, null, sessionToken)` to populate the city selector.

2. **User** selects a date range and optionally a city, clicks "Generate Report".

3. **ReportsController** sends `Request(MessageType.GET_ACTIVITY_REPORT, ReportRequest(fromDate, toDate, cityId), sessionToken)`.

4. **Server** → `ReportHandler`:
   - Extracts `ReportRequest`.
   - Selects the appropriate report generator:
     - If `cityId > 0`: uses `CityReportGenerator`.
     - Otherwise: uses `AllCitiesReportGenerator`.
   - Calls `generator.generate(fromDate, toDate, cityId)`.

5. **CityReportGenerator.generate()** (for specific city):
   - Calls `DailyStatsDAO.getStats(fromDate, toDate, cityId)`:
     - `SELECT * FROM daily_stats WHERE stat_date BETWEEN ? AND ? AND city_id = ? ORDER BY stat_date`.
   - Gets map count via `MapDAO.countMapsForCity(cityId)`.
   - Fills in the maps count for each day.
   - Returns `List<DailyStat>`.

6. **AllCitiesReportGenerator.generate()** (for all cities):
   - Gets all city IDs with approved maps: `MapDAO.getCityIdsWithApprovedMaps()`.
   - Gets per-city totals: `DailyStatsDAO.getPerCityTotals(fromDate, toDate)`.
   - Gets global daily stats: `DailyStatsDAO.getGlobalStatsPerDay(fromDate, toDate)`.
   - Combines into aggregated `List<DailyStat>`.

7. **Response** returns `List<DailyStat>`.

8. **Client** → `ReportsController.displayMessage`:
   - Detects `GET_ACTIVITY_REPORT` response.
   - Calls `updateChart(stats)` on JavaFX thread.
   - Builds a `BarChart` with 6 series: Maps, One-time Purchases, Subscriptions, Renewals, Views, Downloads.
   - Each bar represents a date on the X-axis.
   - Also shows a text summary of totals.

---

## 17. Subscription Scheduler

**Theory:** A background job on the server that periodically checks for subscriptions about to expire and sends reminder notifications to users.

**How it works:**

1. `SubscriptionScheduler` is a singleton started when `GCMServer` starts listening.

2. It uses `ScheduledExecutorService` to run every **2 minutes** (demo interval; would be daily in production).

3. Each run:
   - Calls `PurchaseDAO.createReminderTable()` — creates `subscription_reminders` table if it doesn't exist.
   - Calls `PurchaseDAO.getExpiringSubscriptions(3)`:
     - `SELECT s.id, s.user_id, u.username, u.email, c2.phone, c.name as city_name, s.end_date, DATEDIFF(s.end_date, NOW()) as days_until FROM subscriptions s JOIN users u ON s.user_id = u.id JOIN cities c ON s.city_id = c.id LEFT JOIN customers c2 ON s.user_id = c2.user_id WHERE s.end_date BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 3 DAY) AND s.end_date > NOW()`.
     - Returns `List<ExpiringSubscriptionDTO>`.

4. For each expiring subscription:
   - Determines reminder type: `1_DAY` if ≤1 day left, `3_DAYS` if ≤3 days, or `GENERAL`.
   - Checks `PurchaseDAO.hasReminderBeenSent(subscriptionId, reminderType)`.
   - If not yet sent:
     - Creates IN_APP notification via `NotificationDAO.createNotification(userId, "IN_APP", "Subscription Expiring Soon", "Your subscription for <city> expires in <days> days...")`.
     - Simulates email notification (prints to console).
     - Simulates SMS notification (prints to console).
     - Records the reminder: `PurchaseDAO.recordReminderSent(subscriptionId, reminderType)`.

---

## 18. Database Schema (Inferred)

Based on all the DAO SQL queries, the database has the following tables:

### `users`
| Column | Type | Notes |
|--------|------|-------|
| id | INT (PK, auto-increment) | |
| username | VARCHAR | Unique |
| email | VARCHAR | Unique |
| password_hash | VARCHAR | Plain text in this implementation |
| role | VARCHAR | CUSTOMER, CONTENT_EDITOR, CONTENT_MANAGER, COMPANY_MANAGER, SUPPORT_AGENT |
| is_active | BOOLEAN | |
| created_at | TIMESTAMP | |
| last_login_at | TIMESTAMP | |

### `customers`
| Column | Type | Notes |
|--------|------|-------|
| user_id | INT (FK → users.id) | |
| phone | VARCHAR | |
| payment_token | VARCHAR | Simulated |
| card_last4 | VARCHAR(4) | |
| card_expiry | VARCHAR | e.g., "12/25" |

### `cities`
| Column | Type | Notes |
|--------|------|-------|
| id | INT (PK, auto-increment) | |
| name | VARCHAR | |
| description | TEXT | |
| price | DOUBLE | |
| approved | TINYINT | 0 = draft, 1 = approved (added dynamically) |
| created_by | INT | FK → users.id (added dynamically) |

### `maps`
| Column | Type | Notes |
|--------|------|-------|
| id | INT (PK, auto-increment) | |
| name | VARCHAR | |
| short_description | TEXT | |
| city_id | INT (FK → cities.id) | |
| approved | TINYINT | 0 = draft, 1 = approved |
| tour_id | INT | If this map is a tour route map |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

### `pois`
| Column | Type | Notes |
|--------|------|-------|
| id | INT (PK, auto-increment) | |
| city_id | INT (FK → cities.id) | |
| name | VARCHAR | |
| location | VARCHAR | "lat,lng" string |
| latitude | DOUBLE | |
| longitude | DOUBLE | |
| category | VARCHAR | Beach, Museum, etc. |
| short_explanation | TEXT | |
| accessible | BOOLEAN | |

### `map_pois`
| Column | Type | Notes |
|--------|------|-------|
| map_id | INT (FK → maps.id) | |
| poi_id | INT (FK → pois.id) | |
| display_order | INT | |
| approved | TINYINT | 0 = draft link, 1 = approved |
| PRIMARY KEY (map_id, poi_id) | | |

### `tours`
| Column | Type | Notes |
|--------|------|-------|
| id | INT (PK, auto-increment) | |
| city_id | INT (FK → cities.id) | |
| name | VARCHAR | |
| description | TEXT | |

### `tour_stops`
| Column | Type | Notes |
|--------|------|-------|
| id | INT (PK, auto-increment) | |
| tour_id | INT (FK → tours.id) | |
| poi_id | INT (FK → pois.id) | |
| stop_order | INT | |
| notes | TEXT | |

### `poi_distances`
| Column | Type | Notes |
|--------|------|-------|
| from_poi_id | INT | |
| to_poi_id | INT | |
| distance_meters | DOUBLE | Driving distance from OSRM |
| PRIMARY KEY (from_poi_id, to_poi_id) | | |

### `map_versions`
| Column | Type | Notes |
|--------|------|-------|
| id | INT (PK, auto-increment) | |
| map_id | INT (FK → maps.id) | |
| version_number | INT | |
| status | VARCHAR | DRAFT, PENDING, APPROVED, REJECTED |
| description_text | TEXT | |
| created_by | INT (FK → users.id) | |
| created_at | TIMESTAMP | |
| approved_by | INT (FK → users.id) | |
| approved_at | TIMESTAMP | |
| rejection_reason | TEXT | |

### `map_edit_requests`
| Column | Type | Notes |
|--------|------|-------|
| id | INT (PK, auto-increment) | |
| map_id | INT | Nullable (null for user-level drafts) |
| city_id | INT | |
| user_id | INT (FK → users.id) | |
| changes_json | TEXT | JSON-serialized MapChanges |
| status | VARCHAR | DRAFT, PENDING, APPROVED, REJECTED |
| created_at | TIMESTAMP | |

### `approvals`
| Column | Type | Notes |
|--------|------|-------|
| id | INT (PK, auto-increment) | |
| entity_type | VARCHAR | MAP_VERSION, PRICING_REQUEST |
| entity_id | INT | |
| status | VARCHAR | PENDING, APPROVED, REJECTED |
| reason | TEXT | |
| approved_by | INT | |
| created_at | TIMESTAMP | |
| updated_at | TIMESTAMP | |

### `purchases`
| Column | Type | Notes |
|--------|------|-------|
| id | INT (PK, auto-increment) | |
| user_id | INT (FK → users.id) | |
| city_id | INT (FK → cities.id) | |
| price_paid | DOUBLE | |
| purchased_at | TIMESTAMP | |

### `subscriptions`
| Column | Type | Notes |
|--------|------|-------|
| id | INT (PK, auto-increment) | |
| user_id | INT (FK → users.id) | |
| city_id | INT (FK → cities.id) | |
| months | INT | 1, 3, or 6 |
| price_paid | DOUBLE | |
| start_date | DATE | |
| end_date | DATE | |

### `download_events`
| Column | Type | Notes |
|--------|------|-------|
| id | INT (PK, auto-increment) | |
| user_id | INT | |
| city_id | INT | |
| downloaded_at | TIMESTAMP | |

### `view_events`
| Column | Type | Notes |
|--------|------|-------|
| id | INT (PK, auto-increment) | |
| user_id | INT | |
| city_id | INT | |
| map_id | INT | |
| viewed_at | TIMESTAMP | |

### `pricing_requests`
| Column | Type | Notes |
|--------|------|-------|
| id | INT (PK, auto-increment) | |
| city_id | INT (FK → cities.id) | |
| proposed_price | DOUBLE | |
| reason | TEXT | |
| created_by | INT (FK → users.id) | |
| status | VARCHAR | PENDING, APPROVED, REJECTED |
| created_at | TIMESTAMP | |
| approved_by | INT | |
| rejection_reason | TEXT | |
| processed_at | TIMESTAMP | |

### `notifications`
| Column | Type | Notes |
|--------|------|-------|
| id | INT (PK, auto-increment) | |
| user_id | INT (FK → users.id) | |
| channel | VARCHAR | IN_APP, EMAIL, SMS |
| title | VARCHAR | |
| body | TEXT | |
| created_at | TIMESTAMP | |
| sent_at | TIMESTAMP | |
| is_read | BOOLEAN | Default FALSE |

### `support_tickets`
| Column | Type | Notes |
|--------|------|-------|
| id | INT (PK, auto-increment) | |
| user_id | INT (FK → users.id) | |
| subject | VARCHAR | |
| status | VARCHAR | OPEN, BOT_RESPONDED, ESCALATED, CLOSED |
| priority | VARCHAR | LOW, MEDIUM, HIGH |
| assigned_agent_id | INT | FK → users.id (null until claimed) |
| created_at | TIMESTAMP | |
| closed_at | TIMESTAMP | |

### `ticket_messages`
| Column | Type | Notes |
|--------|------|-------|
| id | INT (PK, auto-increment) | |
| ticket_id | INT (FK → support_tickets.id) | |
| sender_type | VARCHAR | CUSTOMER, BOT, AGENT |
| sender_id | INT | FK → users.id (null for BOT) |
| message | TEXT | |
| created_at | TIMESTAMP | |

### `faq_entries`
| Column | Type | Notes |
|--------|------|-------|
| id | INT (PK, auto-increment) | |
| question | TEXT | |
| answer | TEXT | |
| usage_count | INT | Incremented on match |

### `daily_stats`
| Column | Type | Notes |
|--------|------|-------|
| stat_date | DATE | |
| city_id | INT | |
| metric | VARCHAR | MAPS_COUNT, PURCHASE_ONE_TIME, PURCHASE_SUBSCRIPTION, RENEWAL, VIEW, DOWNLOAD |
| value | INT | |
| PRIMARY KEY (stat_date, city_id, metric) | | Uses INSERT ON DUPLICATE KEY UPDATE |

### `audit_log`
| Column | Type | Notes |
|--------|------|-------|
| id | INT (PK, auto-increment) | |
| user_id | INT | |
| action | VARCHAR | e.g., APPROVE_MAP_VERSION, SUBMIT_PRICING_REQUEST |
| details | TEXT | JSON or key-value pairs |
| created_at | TIMESTAMP | |

### `subscription_reminders`
| Column | Type | Notes |
|--------|------|-------|
| subscription_id | INT | |
| reminder_type | VARCHAR | 1_DAY, 3_DAYS, GENERAL |
| sent_at | TIMESTAMP | |
| PRIMARY KEY (subscription_id, reminder_type) | | Prevents duplicate reminders |

---

## 19. Database Table Relationships & How the Database Connects to the Project

### 19.1 How the Database Is Connected to the Project

#### Technology Stack

- **Database**: MySQL 8.x, schema name `gcm_db`.
- **JDBC Driver**: `com.mysql:mysql-connector-j:8.2.0` (declared in `pom.xml`).
- **Connection Pool**: `com.zaxxer:HikariCP:5.1.0` — a high-performance JDBC connection pool.
- **Access Layer**: Static DAO methods → `DBConnector.getConnection()` → HikariCP pool → MySQL.

#### DBConnector — The Central Database Gateway

`server.DBConnector` is the single point of contact between the Java application and MySQL. Every DAO in the system obtains its JDBC `Connection` through `DBConnector.getConnection()`.

**HikariCP pool configuration:**

| Setting | Value | Meaning |
|---------|-------|---------|
| JDBC URL | `jdbc:mysql://localhost:3306/gcm_db?serverTimezone=Asia/Jerusalem` | Local MySQL, `gcm_db` database |
| Username / Password | `root` / hardcoded in source | Database credentials |
| Max Pool Size | 10 | At most 10 concurrent connections |
| Min Idle | 2 | At least 2 idle connections kept warm |
| Connection Timeout | 30,000 ms | Max wait to get a connection from the pool |
| Idle Timeout | 600,000 ms (10 min) | Idle connection is evicted after 10 minutes |
| Max Lifetime | 1,800,000 ms (30 min) | Connection recycled after 30 minutes |
| Connection Test Query | `SELECT 1` | Health check for borrowed connections |
| Prepared Statement Cache | enabled, 250 statements, up to 2048 chars | Improves repeated query performance |

**Pool lifecycle:**

```
Server starts
    │
    ▼
GCMServer.listen() → serverStarted()
    │
    ├─ CityDAO.ensureCitiesApprovalColumns()  ← first getConnection() call
    │       │
    │       ▼
    │   DBConnector.getConnection()
    │       │
    │       ▼ (pool not yet initialized)
    │   DBConnector.initializePool()
    │       │
    │       ├─ Creates HikariDataSource with config above
    │       └─ Calls ensureSchema() → ALTER TABLE customers ADD card_expiry
    │
    ├─ SubscriptionScheduler.start()
    │
    ▼
Server running — handlers and DAOs borrow/return connections via try-with-resources
    │
    ▼
Server shutdown (Ctrl+C or close())
    │
    ▼
GCMServer.serverStopped()
    │
    ├─ requestExecutor.shutdown()
    └─ DBConnector.closePool() → dataSource.close()
```

**How DAOs use connections:**

All DAOs follow the same pattern:
```java
try (Connection conn = DBConnector.getConnection();
     PreparedStatement ps = conn.prepareStatement(SQL)) {
    ps.setXxx(1, value);
    ResultSet rs = ps.executeQuery();
    // process results
}
// Connection automatically returned to pool when try block exits
```

Some operations that need transactions receive a `Connection` parameter from the handler, which manages `conn.setAutoCommit(false)`, `conn.commit()`, and `conn.rollback()` explicitly.

**Legacy exception — MySQLController:**

`MySQLController` (used only for legacy string-protocol messages like `"get_cities"`, `"login ..."`) obtains connections but **never closes them**, causing connection leaks. This is a known design flaw in the legacy code path.

#### Dynamic Schema Management

The application does not rely on a single upfront schema creation. Several tables and columns are created or altered dynamically at runtime:

| What | When | Method |
|------|------|--------|
| `customers.card_expiry` column | First `getConnection()` call | `DBConnector.ensureSchema()` |
| `cities.approved` and `cities.created_by` columns | Server startup | `CityDAO.ensureCitiesApprovalColumns()` |
| `daily_stats` table | First use of `DailyStatsDAO` (static initializer) | `DailyStatsDAO.createTable()` |
| `map_edit_requests` table | First use of `MapEditRequestDAO` (static initializer) | `MapEditRequestDAO.createTable()` |
| `pricing_requests` table | First `GET_CURRENT_PRICES` request | `PricingDAO.ensureTableExists()` |
| `subscription_reminders` table | First reminder check | `PurchaseDAO.createReminderTable()` |

Additionally, there are SQL migration scripts in the project root:
- `dummy_db.sql` — Main schema with CREATE/DROP, seed data
- `database_update.sql` — Adds `subscription_reminders`
- `migration_city_map_approval.sql` — Approval columns for cities/maps
- `migration_map_pois_approved.sql` — `map_pois.approved` column
- `migration_map_pois_linked_by_user.sql` — `map_pois.linked_by_user_id`
- `migration_poi_coords_and_distances.sql` — POI lat/lng, `poi_distances` table
- `migration_tour_distance.sql` — `tours.total_distance_meters`
- `migration_tour_remove_duration.sql` — Removes duration column
- `migration_tour_maps.sql` — `maps.tour_id`

---

### 19.2 Table Relationships — Entity-Relationship Map

Below is a complete map of every foreign key relationship between tables. The arrows indicate "child references parent" (the child table has a column that points to the parent's primary key).

```
                         ┌──────────┐
                         │  users   │
                         │  (PK: id)│
                         └────┬─────┘
           ┌──────────────────┼──────────────────────────────────────────┐
           │                  │                                          │
           ▼                  ▼                                          ▼
    ┌─────────────┐   ┌──────────────┐                          ┌──────────────────┐
    │  customers  │   │  employees   │                          │ support_tickets   │
    │ FK: user_id │   │ FK: user_id  │                          │ FK: user_id       │
    └─────────────┘   └──────────────┘                          │ FK: assigned_     │
                                                                │     agent_id      │
                                                                └───────┬──────────┘
                                                                        │
                                                                        ▼
                                                                ┌──────────────────┐
                                                                │ ticket_messages   │
                                                                │ FK: ticket_id     │
                                                                │ FK: sender_id     │
                                                                │     → users.id    │
                                                                └──────────────────┘

    ┌──────────┐
    │  cities   │
    │  (PK: id) │
    │ FK: created_by → users.id │
    └────┬─────┘
         │
    ┌────┴─────────────────────────────────────────────────┐
    │              │              │              │          │
    ▼              ▼              ▼              ▼          ▼
┌────────┐   ┌────────┐   ┌────────────┐  ┌──────────┐  ┌─────────────────┐
│  maps  │   │  pois  │   │   tours    │  │purchases │  │  subscriptions  │
│FK:     │   │FK:     │   │FK: city_id │  │FK:user_id│  │FK: user_id      │
│city_id │   │city_id │   └─────┬──────┘  │FK:city_id│  │FK: city_id      │
│tour_id │   └───┬────┘         │         └────┬─────┘  └───────┬─────────┘
│→tours  │       │              │              │                │
└───┬────┘       │              ▼              ▼                ▼
    │            │       ┌─────────────┐  ┌───────────────┐  ┌─────────────────────┐
    │            │       │ tour_stops   │  │download_events│  │subscription_reminders│
    │            │       │FK: tour_id   │  │FK: user_id    │  │FK: subscription_id   │
    │            │       │FK: poi_id    │  │FK: city_id    │  └─────────────────────┘
    │            │       │   → pois.id  │  └───────────────┘
    │            │       └─────────────┘
    │            │
    ▼            ▼
┌──────────────────┐     ┌───────────────┐
│    map_pois      │     │ poi_distances  │
│ FK: map_id       │     │FK: from_poi_id │
│ FK: poi_id       │     │FK: to_poi_id   │
│ PK: (map_id,     │     │   → pois.id    │
│      poi_id)     │     └───────────────┘
└──────────────────┘

    ┌──────────────────┐        ┌──────────────────┐
    │  map_versions    │        │  view_events     │
    │ FK: map_id       │        │ FK: user_id      │
    │ FK: created_by   │        │ FK: city_id      │
    │ FK: approved_by  │        │ FK: map_id       │
    │   → users.id     │        └──────────────────┘
    └──────────────────┘

    ┌──────────────────┐        ┌──────────────────┐
    │  approvals       │        │  pricing_requests│
    │ FK: approved_by  │        │ FK: city_id      │
    │   → users.id     │        │ FK: created_by   │
    └──────────────────┘        │ FK: approved_by  │
                                │   → users.id     │
    ┌──────────────────┐        └──────────────────┘
    │  notifications   │
    │ FK: user_id      │        ┌──────────────────┐
    └──────────────────┘        │  audit_log       │
                                │ FK: actor         │
    ┌──────────────────┐        │   → users.id     │
    │map_edit_requests │        └──────────────────┘
    │ logical FK:      │
    │  user_id→users   │        ┌──────────────────┐
    │  map_id→maps     │        │  daily_stats     │
    │  city_id→cities  │        │ PK: (stat_date,  │
    │ (no declared FK) │        │      city_id)    │
    └──────────────────┘        │ (no declared FK) │
                                └──────────────────┘
```

### 19.3 Detailed Foreign Key Relationships

The following table lists every foreign key relationship, organized by parent table:

#### Parent: `users`

| Child Table | Child Column | Relationship | ON DELETE |
|-------------|-------------|--------------|-----------|
| `customers` | `user_id` | Each customer IS A user (1:1) | CASCADE |
| `employees` | `user_id` | Each employee IS A user (1:1) | CASCADE |
| `purchases` | `user_id` | A user makes many purchases (1:N) | CASCADE |
| `subscriptions` | `user_id` | A user has many subscriptions (1:N) | CASCADE |
| `download_events` | `user_id` | A user generates many download events (1:N) | CASCADE |
| `view_events` | `user_id` | A user generates many view events (1:N) | CASCADE |
| `notifications` | `user_id` | A user receives many notifications (1:N) | CASCADE |
| `support_tickets` | `user_id` | A user creates many tickets (1:N) | CASCADE |
| `support_tickets` | `assigned_agent_id` | A user (agent) is assigned many tickets (1:N) | SET NULL |
| `ticket_messages` | `sender_id` | A user sends many messages (1:N) | SET NULL |
| `map_versions` | `created_by` | A user creates many map versions (1:N) | — |
| `map_versions` | `approved_by` | A user approves many map versions (1:N) | — |
| `approvals` | `approved_by` | A user approves many approvals (1:N) | SET NULL |
| `pricing_requests` | `created_by` | A user submits many pricing requests (1:N) | — |
| `pricing_requests` | `approved_by` | A user processes many pricing requests (1:N) | — |
| `audit_log` | `actor` | A user generates many audit entries (1:N) | SET NULL |
| `cities` | `created_by` | A user creates many cities (1:N) | — |
| `map_edit_requests` | `user_id` | A user submits many edit requests (1:N) | (logical, no declared FK) |

#### Parent: `cities`

| Child Table | Child Column | Relationship | ON DELETE |
|-------------|-------------|--------------|-----------|
| `maps` | `city_id` | A city contains many maps (1:N) | CASCADE |
| `pois` | `city_id` | A city contains many POIs (1:N) | CASCADE |
| `tours` | `city_id` | A city contains many tours (1:N) | CASCADE |
| `purchases` | `city_id` | A city has many purchases (1:N) | CASCADE |
| `subscriptions` | `city_id` | A city has many subscriptions (1:N) | CASCADE |
| `download_events` | `city_id` | A city has many download events (1:N) | CASCADE |
| `view_events` | `city_id` | A city has many view events (1:N) | CASCADE |
| `pricing_requests` | `city_id` | A city has many pricing requests (1:N) | CASCADE |
| `map_edit_requests` | `city_id` | A city has many edit requests (1:N) | (logical) |

#### Parent: `maps`

| Child Table | Child Column | Relationship | ON DELETE |
|-------------|-------------|--------------|-----------|
| `map_pois` | `map_id` | A map has many POI links (1:N) | CASCADE |
| `map_versions` | `map_id` | A map has many versions (1:N) | CASCADE |
| `view_events` | `map_id` | A map has many view events (1:N) | CASCADE |
| `map_edit_requests` | `map_id` | A map has many edit requests (1:N) | (logical) |

#### Parent: `pois`

| Child Table | Child Column | Relationship | ON DELETE |
|-------------|-------------|--------------|-----------|
| `map_pois` | `poi_id` | A POI appears on many maps (1:N) | CASCADE |
| `tour_stops` | `poi_id` | A POI is a stop on many tours (1:N) | CASCADE |
| `poi_distances` | `from_poi_id` | Distance from this POI (1:N) | CASCADE |
| `poi_distances` | `to_poi_id` | Distance to this POI (1:N) | CASCADE |

#### Parent: `tours`

| Child Table | Child Column | Relationship | ON DELETE |
|-------------|-------------|--------------|-----------|
| `tour_stops` | `tour_id` | A tour has many stops (1:N) | CASCADE |
| `maps` | `tour_id` | A tour may have one auto-generated route map (1:1) | CASCADE |

#### Parent: `support_tickets`

| Child Table | Child Column | Relationship | ON DELETE |
|-------------|-------------|--------------|-----------|
| `ticket_messages` | `ticket_id` | A ticket has many messages (1:N) | CASCADE |

#### Parent: `subscriptions`

| Child Table | Child Column | Relationship | ON DELETE |
|-------------|-------------|--------------|-----------|
| `subscription_reminders` | `subscription_id` | A subscription has many reminders (1:N) | (logical) |

### 19.4 Many-to-Many Relationships

| Table A | Junction Table | Table B | Meaning |
|---------|---------------|---------|---------|
| `maps` | `map_pois` | `pois` | A map contains many POIs; a POI can appear on many maps |

`map_pois` is the junction/bridge table with composite PK `(map_id, poi_id)` and an additional `display_order` column and an `approved` flag.

### 19.5 How Each Table Group Serves the Application

| Table Group | Tables | Purpose |
|-------------|--------|---------|
| **Identity & Auth** | `users`, `customers`, `employees` | User accounts, roles, profile data, payment info |
| **Geography & Content** | `cities`, `maps`, `pois`, `map_pois`, `tours`, `tour_stops`, `poi_distances` | The core content: cities containing maps, POIs, and tours |
| **Versioning & Approval** | `map_versions`, `map_edit_requests`, `approvals`, `pricing_requests` | Workflow for content changes, version control, pricing changes |
| **Commerce** | `purchases`, `subscriptions`, `download_events`, `view_events`, `subscription_reminders` | Purchasing, entitlements, usage tracking, renewal reminders |
| **Support** | `support_tickets`, `ticket_messages`, `faq_entries` | Customer support with bot and agent workflows |
| **Analytics & Audit** | `daily_stats`, `audit_log`, `notifications` | Metrics, audit trail, user notifications |

---

## 20. Design Patterns — Every Pattern Used in the Project

### 20.1 Singleton

**What it is:** Ensures a class has exactly one instance and provides a global access point to it.

**Where it is used:**

| Class | Evidence |
|-------|----------|
| `GCMClient` | `private static GCMClient instance;` + `private GCMClient(String host, int port)` + `public static GCMClient getInstance()` |
| `SessionManager` | `private static SessionManager instance;` + `private SessionManager()` + `public static synchronized SessionManager getInstance()` |
| `OsrmClient` | `private static final OsrmClient INSTANCE = new OsrmClient();` + `public static OsrmClient getInstance()` |
| `SubscriptionScheduler` | `private static SubscriptionScheduler instance;` + `private SubscriptionScheduler()` + `public static synchronized SubscriptionScheduler getInstance()` |
| `DBConnector` (pool) | Shared `HikariDataSource` with `poolInitialized` flag — ensures only one pool is ever created |

**How it works in context:**
- `GCMClient.getInstance()` is called by every control class and screen to get the single network connection to the server. If the instance doesn't exist yet, it creates one and connects to `localhost:5555`.
- `SessionManager.getInstance()` is called by every handler that needs to validate a session token. The single instance holds all active sessions in memory.
- Thread safety: `SessionManager` and `SubscriptionScheduler` use `synchronized` on `getInstance()`. `OsrmClient` uses eager initialization (instance created at class load). `GCMClient` is not thread-safe (but is only used from one client JVM).

---

### 20.2 Observer / Listener

**What it is:** An object (the subject) maintains a list of dependents (observers) and notifies them of state changes.

**Where it is used:**

**A) GCMClient.MessageHandler — Server response dispatching:**

- `GCMClient` defines the interface: `public interface MessageHandler { void displayMessage(Object msg); }`
- The client has a field `private MessageHandler messageHandler` and a setter `setMessageHandler(handler)`.
- When a response arrives from the server, `handleMessageFromServer(msg)` calls `messageHandler.displayMessage(msg)`.
- Every screen that expects server responses implements `MessageHandler` and registers itself with `client.setMessageHandler(this)` on initialization.

Implementing classes:
`LoginController`, `DashboardScreen`, `ProfileScreen`, `MyPurchasesScreen`, `EditApprovalScreen`, `AdminCustomersScreen`, `PricingScreen`, `PricingApprovalScreen`, `ReportsController`, `SearchControl`, `ContentManagementControl`.

**B) Control-class callback interfaces — Layered observer:**

- `ContentManagementControl.ContentCallback`: methods `onCitiesReceived()`, `onMapsReceived()`, `onMapContentReceived()`, `onPoisForCityReceived()`, `onValidationResult()`, `onPendingRequestsReceived()`, `onMyDraftReceived()`, `onError()`.
  - Implemented by: `MapEditorScreen`, `MapApprovalsScreen`.
  - The control class receives server responses via `MessageHandler`, then dispatches typed results to its callback.

- `SearchControl.SearchResultCallback`: methods `onSearchResults()`, `onDiscountEligibility()`, `onError()`.
  - Implemented by: `CatalogSearchScreen`.

- `PurchaseControl` callbacks: `PurchaseCallback`, `PriceCallback`, `EntitlementCallback`, `GenericCallback`.

**C) JavaFX property listeners:**

- `selectedItemProperty().addListener((obs, old, newVal) -> ...)` — used extensively in `MapEditorScreen` (city ComboBox, map list, POI list, tour list), `CatalogSearchScreen` (results list), `MapApprovalsScreen`, `EditApprovalScreen`, `PricingApprovalScreen`, `AdminCustomersScreen`.
- `textProperty().addListener(...)` — live validation in `LoginController`, `RegistrationController`, `PricingScreen`.

**How it works in context:**
The observer pattern creates a clean separation between the network layer (GCMClient), the business logic (Control classes), and the UI (Boundary screens). When a server response arrives, it cascades through: `GCMClient` → `MessageHandler` (Control or Screen) → `Callback` (Screen) → UI update. At no point does the network layer need to know about specific screens.

---

### 20.3 Factory Method / Static Factory

**What it is:** A static method that creates and returns instances, encapsulating construction logic.

**Where it is used:**

| Class | Factory Methods | Purpose |
|-------|----------------|---------|
| `Response` | `success(request, payload)`, `success(request)`, `error(request, errorCode, errorMessage)` | Creates success/error response objects with proper field initialization |
| `ApprovalRequest` | `approve(versionId)`, `reject(versionId, reason)` | Creates typed approval/rejection objects |
| `ValidationResult` | `success(message)`, `error(field, message)` | Creates validation results |
| `PaginatedResponse` | `empty()`, `fromList(allItems, page, pageSize)` | Creates paginated wrappers with calculated page metadata |
| `SearchRequest` | `byCity(name)`, `byPoi(name)`, `byCityAndPoi(city, poi)` | Creates typed search requests |
| `CustomerPurchaseDTO` | `oneTime(...)`, `subscription(...)` | Creates purchase DTOs for different purchase types |

**How it works in context:**
Every handler in the server returns its response using `Response.success(request, data)` or `Response.error(request, code, message)`. This ensures the `requestId` and `requestType` are always correctly propagated, and eliminates the possibility of forgetting to set error fields on failure.

---

### 20.4 Strategy

**What it is:** Defines a family of interchangeable algorithms, letting the client choose which one to use at runtime.

**Where it is used:**

- **Interface**: `server.service.ReportGenerator` — declares `List<DailyStat> generate(LocalDate from, LocalDate to, Integer cityId)`.
- **Concrete strategies**:
  - `CityReportGenerator` — generates report for a single city using `DailyStatsDAO.getStats(from, to, cityId)` and `MapDAO.countMapsForCity(cityId)`.
  - `AllCitiesReportGenerator` — generates aggregated report across all cities using `DailyStatsDAO.getPerCityTotals()` and `DailyStatsDAO.getGlobalStatsPerDay()`.
- **Context (client)**: `ReportHandler` selects the strategy:
  ```
  if (reportRequest.getCityId() != null && reportRequest.getCityId() > 0)
      generator = new CityReportGenerator();
  else
      generator = new AllCitiesReportGenerator();
  List<DailyStat> stats = generator.generate(from, to, cityId);
  ```

**How it works in context:**
The handler doesn't need to know how reports are generated. It just picks the right strategy and delegates. Adding a new report type (e.g., `RegionReportGenerator`) would require no changes to the handler — just a new class implementing `ReportGenerator`.

---

### 20.5 Template Method

**What it is:** Defines the skeleton of an algorithm in a base class, with specific steps deferred to subclasses via abstract or hook methods.

**Where it is used:**

**A) `AbstractClient` (OCSF) → `GCMClient`:**

The base class `AbstractClient` defines the fixed algorithm in `run()`:
1. Open socket → create streams → start reader thread.
2. Reader loop: `while (!readyToStop) { Object msg = input.readObject(); handleMessageFromServer(msg); }`
3. On exit: call `connectionClosed()`.

Hook/abstract methods overridden by `GCMClient`:
- `handleMessageFromServer(Object msg)` — **abstract**, the core extension point. GCMClient routes messages to the active `MessageHandler` and to the sync response queue.
- `connectionEstablished()` — hook (empty in base), overridden for logging.
- `connectionClosed()` — hook, overridden for cleanup.
- `connectionException(Exception)` — hook, overridden for error handling.

**B) `AbstractServer` (OCSF) → `GCMServer`:**

The base class defines:
1. `listen()` → create `ServerSocket` → accept loop in `run()`.
2. Accept loop: `Socket client = serverSocket.accept()` → create `ConnectionToClient` thread.
3. Each `ConnectionToClient.run()`: `while (!readyToStop) { Object msg = input.readObject(); server.receiveMessageFromClient(msg, this); }`.

Hook/abstract methods overridden by `GCMServer`:
- `handleMessageFromClient(Object msg, ConnectionToClient client)` — **abstract**. GCMServer submits request processing to a thread pool.
- `serverStarted()` — hook. GCMServer runs schema migration and starts the scheduler.
- `serverStopped()` — hook. GCMServer shuts down the thread pool and closes the DB pool.
- `clientConnected(ConnectionToClient)` — hook. GCMServer logs the connection.
- `clientDisconnected(ConnectionToClient)` — hook. GCMServer invalidates the session.
- `clientException(ConnectionToClient, Throwable)` — hook. GCMServer logs the error.

**How it works in context:**
The OCSF framework handles all the low-level socket management, threading, and lifecycle. GCM only needs to implement "what to do when a message arrives" and "what to do when connections change state." This is the textbook Template Method pattern.

---

### 20.6 Chain of Responsibility

**What it is:** A request is passed along a chain of potential handlers until one handles it.

**Where it is used:**

In `GCMServer.dispatchRequest(Request request, long clientId)`:

```
if (SearchHandler.canHandle(type))    return SearchHandler.handle(request);
if (MapEditHandler.canHandle(type))   return MapEditHandler.handle(request);
if (ApprovalHandler.canHandle(type))  return ApprovalHandler.handle(request);
if (AuthHandler.canHandle(type))      return AuthHandler.handle(request);
if (PurchaseHandler.canHandle(type))  return PurchaseHandler.handle(request);
if (CustomerHandler.canHandle(type))  return CustomerHandler.handle(request);
if (NotificationHandler.canHandle(type)) return NotificationHandler.handle(request);
if (PricingHandler.canHandle(type))   return PricingHandler.handle(request);
if (SupportHandler.canHandle(type))   return SupportHandler.handle(request);
if (ReportHandler.canHandle(type))    return ReportHandler.handle(request);
// legacy handlers...
return Response.error(request, ERR_INTERNAL, "No handler found");
```

Each handler has a `canHandle(MessageType)` method that checks if the given message type is in its set of supported types. The first handler that returns `true` processes the request; all subsequent handlers are skipped.

**How it works in context:**
This cleanly separates request routing from request processing. Each handler is self-contained and only needs to know about its own message types. Adding a new handler just means adding one more `if` block to the chain.

---

### 20.7 Command

**What it is:** Encapsulates a request as an object, containing all information needed to perform the action.

**Where it is used:**

- `Request` is the command object. It contains:
  - `type` (MessageType) — identifies WHICH operation to perform (like a command name).
  - `payload` (Object) — the parameters for the operation.
  - `sessionToken` — the authentication context.
  - `requestId` (UUID) — unique identifier for tracking.

- `Response` is the command result, carrying back `ok`, `payload`, `errorCode`, and `errorMessage`.

- `MessageType` is effectively the command registry — an enum of 65+ operation types.

**How it works in context:**
Every user action is encoded as a `Request` object that travels over the network. The server decodes it, routes it, executes it, and returns a `Response`. This allows operations to be queued (thread pool), logged (audit log), correlated (requestId matching), and retried — all hallmarks of the Command pattern.

---

### 20.8 Builder (Fluent)

**What it is:** Constructs a complex object step-by-step through method chaining.

**Where it is used:**

`MapChanges` uses fluent builder-style methods:
- `MapChanges.forMap(mapId)` — sets the target map, returns `this`.
- `.forCity(cityId)` — sets the target city, returns `this`.
- `.withNewCity(name, desc, price)` — configures a new city, returns `this`.
- `.addPoi(poi)` — adds a POI to the added list, returns `this`.
- `.updatePoi(poi)` — adds a POI to the updated list, returns `this`.
- `.deletePoi(poiId)` — adds a POI ID to the deleted list, returns `this`.
- `.addTour(tour)`, `.updateTour(tour)`, `.deleteTour(tourId)` — same pattern.

This allows building complex edit batches like:
```java
MapChanges changes = new MapChanges()
    .forMap(42)
    .forCity(7)
    .addPoi(newPoi)
    .updatePoi(modifiedPoi)
    .deleteTour(oldTourId);
```

---

### 20.9 Data Access Object (DAO)

**What it is:** Separates data persistence logic from business logic by providing an abstract interface to the database.

**Where it is used:**

All 16 DAO classes in `server.dao`:

| DAO | Tables Accessed | Key Operations |
|-----|----------------|----------------|
| `UserDAO` | `users`, `customers` | authenticate, createCustomer, getProfile, updateProfile, listAllCustomers |
| `CityDAO` | `cities` | getAllCities, createCity, updateCity, deleteCity, ensureApprovalColumns |
| `MapDAO` | `maps`, `map_pois`, `cities`, `tours`, `tour_stops` | getMapsForCity, getMapContent, createMap, deleteMap, createTourMap |
| `PoiDAO` | `pois`, `map_pois`, `tour_stops` | createPoi, updatePoi, deletePoi, linkPoiToMap, unlinkPoiFromMap |
| `TourDAO` | `tours`, `tour_stops`, `pois` | createTour, updateTour, deleteTour, addTourStop, removeTourStop |
| `MapVersionDAO` | `map_versions`, `maps`, `cities`, `users` | createVersion, listPendingVersions, updateStatus |
| `MapEditRequestDAO` | `map_edit_requests`, `users`, `maps`, `cities` | createRequest, getPendingRequests, upsertDraftRequest, updateStatus |
| `ApprovalDAO` | `approvals`, `users` | createApproval, updateApproval, getApproval |
| `PurchaseDAO` | `purchases`, `subscriptions`, `download_events`, `view_events`, `subscription_reminders`, `cities` | purchaseOneTime, purchaseSubscription, getEntitlement, recordDownload |
| `PricingDAO` | `pricing_requests`, `cities`, `users` | createPricingRequest, approveRequest, rejectRequest, listPendingRequests |
| `SearchDAO` | `cities`, `maps`, `map_pois`, `pois`, `tours`, `tour_stops` | getCitiesCatalog, searchByCityName, searchByPoiName |
| `NotificationDAO` | `notifications`, `purchases`, `customers` | createNotification, getNotificationsForUser, markAsRead |
| `SupportDAO` | `support_tickets`, `ticket_messages`, `users`, `faq_entries` | createTicket, getTicketById, addMessage, closeTicket, findMatchingFaq |
| `DailyStatsDAO` | `daily_stats` | increment, getStats, getGlobalStatsPerDay, getPerCityTotals |
| `AuditLogDAO` | `audit_log` | log, logSimple |
| `PoiDistanceDAO` | `poi_distances`, `pois` | getDistance, setDistance, recomputeAndStoreDistances |

**How it works in context:**
Handlers never write SQL directly. They call DAO methods like `UserDAO.authenticate(username, password)` which internally executes `SELECT ... FROM users WHERE username = ? AND password_hash = ?`. This means if the database schema changes, only the DAO needs updating — not every handler that uses user data.

---

### 20.10 MVC / Boundary–Control–Entity (BCE)

**What it is:** Separates an application into three concerns: Model (data), View (presentation), and Controller (logic). In the BCE variant used here, Boundary = View, Control = Controller, Entity = Model.

**Where it is used:**

**Boundary (View) — `client.boundary.*`:**
All screen classes. They handle UI layout, user input, and displaying results. They do NOT perform business logic or send raw messages.

**Control (Controller) — `client.control.*`:**
`SearchControl`, `ContentManagementControl`, `PurchaseControl`. They receive method calls from boundaries, build `Request` objects, send them to the server, receive `Response` objects, and dispatch results back to boundaries via callbacks.

**Entity (Model) — `common.*` and `common.dto.*`:**
`City`, `Map`, `Poi`, `DailyStat`, and all 37 DTOs. These are pure data classes with no behavior, shared between client and server.

**Data flow:**
```
User clicks button
    → Boundary calls Control method
        → Control builds Request, sends to GCMClient
            → GCMClient sends to server
            → Server processes, returns Response
        → GCMClient delivers to Control's displayMessage()
    → Control calls Boundary's callback
→ Boundary updates UI
```

---

### 20.11 Facade

**What it is:** Provides a simplified interface to a complex subsystem.

**Where it is used:**

- **`ContentManagementControl`** hides the complexity of building `Request` objects, managing `GCMClient`, handling `MessageType` enums, and routing `Response` objects. The `MapEditorScreen` just calls `contentControl.getCities()` instead of dealing with `new Request(MessageType.GET_CITIES, null, sessionToken, userId)` + `client.sendToServer(...)` + response routing.

- **`SearchControl`** same — `CatalogSearchScreen` calls `searchControl.searchByCityName("Haifa")` instead of building and sending the full request.

- **`PurchaseControl`** same for purchase operations.

- **`MySQLController`** is a legacy facade over direct JDBC calls, providing `authenticateUser()`, `getAllCities()`, etc.

---

### 20.12 Bridge / Adapter (JavaScript ↔ Java)

**What it is:** Converts the interface of one system into an interface expected by another.

**Where it is used:**

`MapClickBridge` (`client.util.MapClickBridge`):
- The Leaflet map running in a `WebView` emits JavaScript events (map clicks, map ready).
- JavaScript calls `window.javaApp.onMapClick(lat, lng)` which is bound to the `MapClickBridge` Java object.
- `MapClickBridge` converts this into a Java `BiConsumer<Double, Double>` call on the JavaFX Application Thread via `Platform.runLater(...)`.
- This bridges two completely different environments (browser JS and Java Swing/FX threading) through a clean adapter interface.

---

### 20.13 Object Pool (Connection Pool)

**What it is:** Maintains a pool of reusable objects to avoid the overhead of creating and destroying them repeatedly.

**Where it is used:**

`DBConnector` with HikariCP:
- Instead of opening a new MySQL connection for every database query (expensive: TCP handshake, authentication, SSL), connections are borrowed from a pool and returned after use.
- The pool maintains 2–10 connections (min idle to max size).
- `getConnection()` borrows a connection; `close()` on the connection returns it to the pool (HikariCP intercepts the close call).
- `getPoolStats()` returns metrics: active, idle, and total connections.

---

### 20.14 Lazy Initialization

**What it is:** Delays the creation of an object until it is first needed.

**Where it is used:**

| Class | What is lazily initialized | Trigger |
|-------|---------------------------|---------|
| `DBConnector` | The HikariCP connection pool | First call to `getConnection()` |
| `GCMClient` | The singleton instance and TCP connection | First call to `getInstance()` |
| `SessionManager` | The singleton instance | First call to `getInstance()` |
| `SubscriptionScheduler` | The singleton instance | First call to `getInstance()` |
| `ReportsController` | The `reportExecutor` thread pool | First call to `getReportExecutor()` |

**How it works in context:**
The DB pool is not created when the server starts. It's created when the first database operation runs (typically during `CityDAO.ensureCitiesApprovalColumns()` in `serverStarted()`). This means if the database is down, the error occurs on first use rather than at startup — though in practice the migration runs immediately so the effect is minimal.

---

### 20.15 Thread Pool

**What it is:** A pool of worker threads that execute submitted tasks, avoiding the overhead of creating a new thread per task.

**Where it is used:**

| Class | Pool Type | Size | Purpose |
|-------|-----------|------|---------|
| `GCMServer` | `FixedThreadPool` | 10 threads | Processes incoming client requests concurrently. Each request is submitted as a task. |
| `SubscriptionScheduler` | `SingleThreadScheduledExecutor` | 1 thread | Runs the subscription check every 2 minutes. |
| `ReportsController` | `SingleThreadExecutor` | 1 thread | Runs report requests off the JavaFX UI thread. |

**How it works in context:**
When `GCMServer.handleMessageFromClient()` is called (on the OCSF connection thread), it does NOT process the request on that thread. Instead it submits a task to the 10-thread pool: `requestExecutor.submit(() -> processClientMessage(msg, client))`. This prevents one slow request from blocking other clients. The OCSF connection thread is immediately free to read the next message.

---

### 20.16 Data Transfer Object (DTO)

**What it is:** A plain object used to transfer data between layers or across the network, with no business logic.

**Where it is used:**

37 DTO classes in `common.dto.*`, all implementing `Serializable`. They carry data in `Request.payload` and `Response.payload` across the TCP connection. Examples:
- `LoginRequest` (username, password) → sent from client to server.
- `LoginResponse` (sessionToken, userId, username, role) → sent from server to client.
- `MapContent` (mapId, cityId, pois, tours) → sent from server to client.
- `MapChanges` (addedPois, deletedPoiIds, addedTours, ...) → sent from client to server.

DTOs are distinct from entities: `City` is a simple entity with `(id, name, description, price)`, while `CityDTO` adds `mapCount` and `draft` which are presentation concerns. `CitySearchResult` further adds `maps` (a list of `MapSummary`) for search result display.

---

### 20.17 Callback

**What it is:** A reference to executable code passed as an argument, to be called at a later time when a result is available.

**Where it is used:**

- `ContentManagementControl.ContentCallback` — 8 callback methods for different response types. `MapEditorScreen` implements this interface and receives typed results.
- `SearchControl.SearchResultCallback` — 3 callback methods. `CatalogSearchScreen` implements it.
- `PurchaseControl.PurchaseCallback`, `PriceCallback`, `EntitlementCallback`, `GenericCallback` — various purchase result callbacks.
- `MapClickBridge` — uses `BiConsumer<Double, Double>` and `Runnable` as callbacks for JavaScript events.

**How it works in context:**
When `ContentManagementControl` receives a server response for `GET_CITIES`, it casts the payload to `List<CityDTO>` and calls `callback.onCitiesReceived(cities)`. The `MapEditorScreen` (which registered itself as the callback) then populates the city dropdown. This avoids the control class needing to know anything about JavaFX or the specific screen.

---

### 20.18 Scheduled Task

**What it is:** A task that runs automatically at fixed intervals.

**Where it is used:**

`SubscriptionScheduler`:
- Schedules `checkExpiringSubscriptions()` with `scheduleAtFixedRate(task, 30 seconds initial delay, 120 seconds interval)`.
- Each run: finds subscriptions expiring within 3 days → creates notifications → prevents duplicate reminders via `subscription_reminders` table.

---

### 20.19 Synchronous Proxy (Blocking RPC)

**What it is:** Provides a synchronous interface over an asynchronous communication channel.

**Where it is used:**

`GCMClient.sendRequestSync(Request request)`:
- The underlying OCSF communication is asynchronous (send → receive later on reader thread).
- `sendRequestSync` creates a synchronous wrapper:
  1. Sets `pendingSyncRequestId = request.getRequestId()`.
  2. Sends the request via `sendToServer()`.
  3. Blocks on `responseQueue.poll(30, TimeUnit.SECONDS)`.
  4. When `handleMessageFromServer()` receives a response matching the pending ID, it enqueues it.
  5. The blocked call returns with the response.
- Used by: `SupportScreen`, `AgentConsoleScreen`, `DashboardScreen` (for logout, notifications).

---

### 20.20 Service Layer (Interface Segregation)

**What it is:** Defines abstract service interfaces for business operations, separating the contract from the implementation.

**Where it is used:**

The project defines (but does not fully use) service interfaces:
- `IAuthService` — `login()`, `logout()`, `registerCustomer()`, `validateSession()`
- `IMapService` — `getAllCities()`, `getMapContent()`, `createCity()`, etc.
- `IPurchaseService` — purchase operations
- `ISupportService` — ticket operations

Currently, handlers call DAOs directly rather than going through these services. The interfaces appear to be prepared for a future REST API or service-oriented refactoring.

---

### 20.21 Design Patterns Summary Table

| # | Pattern | Where Used | Key Classes |
|---|---------|-----------|-------------|
| 1 | Singleton | Client connection, session store, scheduler, DB pool, OSRM client | `GCMClient`, `SessionManager`, `SubscriptionScheduler`, `DBConnector`, `OsrmClient` |
| 2 | Observer / Listener | Server response handling, JavaFX UI updates | `MessageHandler`, all screens, `ContentCallback`, `SearchResultCallback`, JavaFX listeners |
| 3 | Factory Method | Object creation with clear semantics | `Response`, `ApprovalRequest`, `ValidationResult`, `PaginatedResponse`, `SearchRequest`, `CustomerPurchaseDTO` |
| 4 | Strategy | Interchangeable report generation | `ReportGenerator`, `CityReportGenerator`, `AllCitiesReportGenerator` |
| 5 | Template Method | OCSF framework lifecycle and extension | `AbstractClient` → `GCMClient`, `AbstractServer` → `GCMServer` |
| 6 | Chain of Responsibility | Server request routing | `GCMServer.dispatchRequest()` + 10 handlers with `canHandle()` |
| 7 | Command | Encapsulating operations as objects | `Request`, `Response`, `MessageType` |
| 8 | Builder (Fluent) | Complex edit batch construction | `MapChanges` |
| 9 | DAO | Database access abstraction | 16 DAO classes in `server.dao.*` |
| 10 | MVC / BCE | Architectural separation of concerns | `client.boundary.*` (View), `client.control.*` (Controller), `common.*` (Model) |
| 11 | Facade | Simplified subsystem access | `ContentManagementControl`, `SearchControl`, `PurchaseControl`, `MySQLController` |
| 12 | Bridge / Adapter | JavaScript–Java interop | `MapClickBridge` |
| 13 | Object Pool | Connection reuse | `DBConnector` + HikariCP |
| 14 | Lazy Initialization | Deferred resource creation | `DBConnector`, `GCMClient`, `SessionManager`, `SubscriptionScheduler`, `ReportsController` |
| 15 | Thread Pool | Concurrent request processing | `GCMServer` (10 threads), `SubscriptionScheduler` (1 thread), `ReportsController` (1 thread) |
| 16 | DTO | Network data transfer | 37 DTO classes in `common.dto.*` |
| 17 | Callback | Async result delivery | `ContentCallback`, `SearchResultCallback`, `PurchaseCallback`, `MapClickBridge` consumers |
| 18 | Scheduled Task | Periodic background work | `SubscriptionScheduler` |
| 19 | Synchronous Proxy | Blocking RPC over async channel | `GCMClient.sendRequestSync()` with `BlockingQueue` |
| 20 | Service Interface | Abstraction for future refactoring | `IAuthService`, `IMapService`, `IPurchaseService`, `ISupportService` |

---

## 21. Interview Questions — Varying Difficulty

### Easy (Conceptual / Recall)

**Q1.** What communication framework does this project use for client–server communication, and what protocol does it run over?

> **A:** OCSF (Object Client–Server Framework), running over TCP with Java object serialization on port 5555.

**Q2.** What is the purpose of the `MessageType` enum?

> **A:** It defines every possible operation type (65+ values) that a client can request from the server. Each `Request` carries a `MessageType` so the server knows which handler should process it.

**Q3.** Why is `GCMClient` implemented as a Singleton?

> **A:** Because the entire client application should have exactly one network connection to the server. Multiple instances would create multiple sockets and cause confusion about which connection receives which response.

**Q4.** What is a DTO and why are DTOs used in this project?

> **A:** A Data Transfer Object is a plain serializable class that carries data between the client and server. DTOs are used because the client and server run in separate JVMs — data must be serialized, sent over TCP, and deserialized. DTOs provide a clean, type-safe way to structure this data without exposing internal implementation details.

**Q5.** What are the five user roles in the system, and what is each one allowed to do?

> **A:** CUSTOMER (browse, purchase, view maps, support tickets), CONTENT_EDITOR (edit maps, submit for approval), CONTENT_MANAGER (edit and publish maps directly, submit pricing requests, manage content), COMPANY_MANAGER (approve map edits, approve pricing requests, view reports, manage customers), SUPPORT_AGENT (claim and respond to support tickets).

**Q6.** What is the difference between a one-time purchase and a subscription?

> **A:** A one-time purchase gives the user a single download and limited views of a city's maps, with no expiration. A subscription gives unlimited views and downloads for 1, 3, or 6 months, after which it expires.

**Q7.** What does the `SessionManager` do?

> **A:** It manages active user sessions in memory. It maps session tokens to user info, enforces one session per user (new login invalidates old session), binds sessions to OCSF connections, and cleans up sessions when clients disconnect.

**Q8.** What is the purpose of the `daily_stats` table?

> **A:** It stores daily aggregated metrics per city — number of maps, one-time purchases, subscriptions, renewals, views, and downloads. This data powers the activity reports generated by `ReportsController`.

---

### Medium (Understanding / Application)

**Q9.** Explain the full lifecycle of a `Request` object from the moment a user clicks a button to the moment they see the result.

> **A:**
> 1. User clicks button in a Boundary screen.
> 2. Screen calls a Control class method (e.g., `searchControl.searchByCityName("Haifa")`).
> 3. Control builds a `Request(MessageType.SEARCH_BY_CITY_NAME, SearchRequest.byCity("Haifa"), sessionToken)` with a generated UUID.
> 4. Control calls `GCMClient.getInstance().sendToServer(request)`.
> 5. `GCMClient.sendToServer()` serializes the Request via `ObjectOutputStream.writeObject()` over TCP.
> 6. Server's `ConnectionToClient` reader thread deserializes it.
> 7. `GCMServer.handleMessageFromClient()` submits it to the thread pool.
> 8. Thread pool worker calls `dispatchRequest()`, which iterates handlers; `SearchHandler.canHandle(SEARCH_BY_CITY_NAME)` returns true.
> 9. `SearchHandler.handle()` calls `SearchDAO.searchByCityName("Haifa")`, which runs SQL.
> 10. Handler returns `Response.success(request, List<CitySearchResult>)`.
> 11. `GCMServer` sends response via `client.sendToClient(response)`.
> 12. Client's `AbstractClient` reader thread deserializes the Response.
> 13. `GCMClient.handleMessageFromServer()` calls `messageHandler.displayMessage(response)`.
> 14. `SearchControl.displayMessage()` checks `response.getRequestType() == SEARCH_BY_CITY_NAME`, casts payload, calls `callback.onSearchResults(results)`.
> 15. `CatalogSearchScreen.onSearchResults()` runs on JavaFX thread via `Platform.runLater()`, populates the UI.

**Q10.** What happens if a user is logged in on Machine A and then logs in on Machine B with the same credentials?

> **A:** `SessionManager.createSession()` checks `userSessions` and finds an existing session for that userId. It calls `invalidateSession()` on the old token, removing it from all maps. Then it creates a new session with a new token for Machine B. Machine A's next request will fail with `ERR_SESSION_EXPIRED` because its token is no longer valid.

**Q11.** Explain the difference between `SAVE_MAP_CHANGES` and `SUBMIT_MAP_CHANGES`. What happens for a Content Manager vs a Customer?

> **A:**
> - `SAVE_MAP_CHANGES` always saves as a draft (status=DRAFT) in `map_edit_requests`, regardless of who calls it. Changes are NOT applied to the database. This is a work-in-progress save.
> - `SUBMIT_MAP_CHANGES` with `draft=false`:
>   - **Content Manager / Company Manager**: Changes are applied directly to the database (POIs created/updated/deleted, tours modified, etc.). No approval needed.
>   - **Customer / Content Editor**: Changes are NOT applied. Instead, a `map_edit_requests` row with status=PENDING is created. A manager must approve it via `APPROVE_MAP_EDIT` before changes take effect.

**Q12.** How does the bot service work when a support ticket is created?

> **A:**
> 1. `BotService.generateResponse(subject, message)` tokenizes the text into keywords (lowercased, stopwords removed).
> 2. It calls `SupportDAO.findMatchingFaq(keywords)` which searches `faq_entries` for keyword matches.
> 3. If ≥3 keywords match an FAQ: returns the FAQ answer (strong match).
> 4. If 1–2 keywords match: returns the FAQ answer plus a suggestion to escalate if it doesn't help (partial match).
> 5. If billing-related keywords are detected: suggests escalation.
> 6. If no match: returns a generic response and flags for auto-escalation.
> 7. The bot response is added as a `BOT` message to the ticket, and the status changes to `BOT_RESPONDED` or `ESCALATED`.

**Q13.** What is the Template Method pattern and how does OCSF use it?

> **A:** Template Method defines an algorithm skeleton in a base class, deferring certain steps to subclasses. In OCSF, `AbstractClient.run()` defines the fixed algorithm: open connection → read messages in a loop → handle each message → clean up on close. The `handleMessageFromServer()` step is abstract — `GCMClient` must implement it. Hook methods like `connectionEstablished()`, `connectionClosed()`, `connectionException()` have empty default implementations that `GCMClient` can optionally override. The base class controls the flow; the subclass customizes the behavior.

**Q14.** Why does `RegistrationController` open its own socket instead of using `GCMClient`?

> **A:** `GCMClient` is a Singleton that may already be connected with a different user's session (or not connected at all). Registration is a one-shot operation that should not interfere with any existing session or connection state. By opening a separate socket, the registration is completely isolated — it sends one request, receives one response, and closes. This avoids race conditions and state pollution in the shared client.

**Q15.** How does the Chain of Responsibility pattern work in request dispatching? What would happen if two handlers both claimed to handle the same `MessageType`?

> **A:** `GCMServer.dispatchRequest()` iterates through handlers in a fixed order, calling `canHandle(type)` on each. The first handler that returns `true` processes the request and returns a `Response`. If two handlers both claimed the same type, only the first one in the chain would ever process it — the second would be silently ignored. This makes handler ordering important.

**Q16.** Explain how the connection pool works and why it is important.

> **A:** `DBConnector` uses HikariCP to maintain a pool of 2–10 pre-established MySQL connections. When a DAO needs a connection, it calls `getConnection()` which borrows one from the pool (or waits up to 30 seconds if all are busy). When the DAO closes the connection (via try-with-resources), HikariCP intercepts the `close()` and returns the connection to the pool instead of actually closing it. This is important because creating a new TCP connection to MySQL is expensive (handshake, authentication, SSL negotiation). With 10 concurrent request-handler threads, the pool ensures they can all access the database without the overhead of creating and destroying connections.

---

### Hard (Analysis / Design / Critical Thinking)

**Q17.** Identify at least three potential concurrency issues in this codebase and explain how they could manifest.

> **A:**
> 1. **GCMClient.messageHandler race condition**: `setMessageHandler()` is called on the JavaFX thread when navigating between screens, but `handleMessageFromServer()` is called on the OCSF reader thread. If a response arrives while the handler is being swapped, it could be delivered to the wrong screen or to a null handler.
> 2. **SessionManager without concurrent data structures**: `SessionManager` uses regular `HashMap` for `sessions`, `userSessions`, and `connectionSessions`. Multiple handler threads can call `validateSession()`, `createSession()`, and `invalidateSession()` concurrently. Without `ConcurrentHashMap` or synchronization on these maps, race conditions could corrupt the session state (e.g., a session being validated while simultaneously being invalidated).
> 3. **Draft save race condition**: If a user rapidly clicks "Save" twice, two `SAVE_MAP_CHANGES` requests are sent. Both handlers call `upsertDraftRequest()` which does DELETE + INSERT. If they interleave, the first INSERT might succeed, then the second DELETE removes it, and the second INSERT creates a new one — losing the first save silently. Or both DELETEs could run before either INSERT.

**Q18.** The `MySQLController` never closes database connections. What is the impact of this, and how would you fix it?

> **A:** Each call to `MySQLController.authenticateUser()`, `getAllCities()`, `getMapsForCity()`, or `updateCityPrice()` borrows a connection from the HikariCP pool but never returns it. After 10 such calls, the pool is exhausted. The 11th call blocks for 30 seconds and then throws a `ConnectionTimeoutException`. All other handlers that need database access also fail. The fix is to wrap each method body in `try (Connection conn = DBConnector.getConnection()) { ... }` so the connection is automatically returned to the pool.

**Q19.** The `CLOSE_TICKET` MessageType is reportedly not included in `SupportHandler.canHandle()`. Trace through the code to explain what happens when a customer tries to close a ticket.

> **A:** When the client sends `Request(MessageType.CLOSE_TICKET, ticketId, sessionToken)`, the server's `dispatchRequest()` iterates through all handlers. `SupportHandler.canHandle(CLOSE_TICKET)` returns `false` (the MessageType is missing from the set). No other handler claims it either. The method falls through to the end and returns `Response.error(request, ERR_INTERNAL, "No handler found for message type: CLOSE_TICKET")`. The client receives an error. The ticket is never closed. This is a bug — `CLOSE_TICKET` needs to be added to `SupportHandler`'s set of handled types.

**Q20.** Evaluate the security of the authentication system. What are the vulnerabilities?

> **A:**
> 1. **Plaintext passwords**: `UserDAO.authenticate()` compares `password_hash` with the raw password — suggesting passwords are stored in plaintext (or with a misleading column name). No hashing (bcrypt, argon2) is used.
> 2. **Session tokens over unencrypted TCP**: Session tokens are UUID strings sent in plaintext over TCP. Anyone sniffing the network can steal a session.
> 3. **No rate limiting**: There is no mechanism to prevent brute-force login attempts.
> 4. **Credentials in source code**: `DBConnector` hardcodes the MySQL password in the source file.
> 5. **No input sanitization beyond PreparedStatement**: While `PreparedStatement` prevents SQL injection, there is no protection against XSS or other injection types in stored text fields.
> 6. **Registration bypasses GCMClient**: `RegistrationController` opens a raw socket, which means it bypasses any connection-level security or logging that `GCMClient` might provide.

**Q21.** If you were asked to add a REST API layer to this server so that web clients could use it alongside the existing JavaFX desktop clients, how would you architect the change? Which design patterns in the existing code help or hinder this?

> **A:**
> **Helpful patterns:**
> - **DAO pattern**: All database access is already abstracted into DAOs. A REST controller can call the same DAOs without any changes.
> - **Service interfaces** (`IAuthService`, `IMapService`, etc.): These are already defined (though not fully used). A REST controller can implement business logic through these interfaces.
> - **DTO pattern**: All data is already in serializable DTOs. These can be serialized to JSON instead of Java serialization with minimal changes (add Jackson annotations).
> - **Command pattern** (`Request`/`Response`): The same structure can map to HTTP request/response.
>
> **Hindering patterns:**
> - **Singleton `GCMClient`**: Would not be used by web clients, but the tight coupling between Control classes and `GCMClient` means the Control layer cannot be reused for REST.
> - **Java Serialization**: DTOs use Java serialization (`Serializable`). REST needs JSON. You'd need to add JSON serialization support.
> - **Static handler methods**: All handlers have static `handle()` methods, making them hard to test and inject dependencies into. Spring or JAX-RS prefers instance methods with dependency injection.
> - **Session management in memory**: `SessionManager` stores sessions in a `HashMap`. For REST, you'd need JWT tokens or a shared session store (Redis) for horizontal scaling.
>
> **Architecture:** Add a Spring Boot or JAX-RS layer alongside `GCMServer`. REST controllers call the same DAOs/services. Map HTTP methods to `MessageType` equivalents. Use JWT for authentication. Both the OCSF server and REST server can coexist, sharing the same database and DAOs.

**Q22.** The system currently uses Java Object Serialization for client–server communication. What are the risks and alternatives?

> **A:**
> **Risks:**
> 1. **Deserialization attacks**: Java deserialization is inherently unsafe. A malicious client can send a crafted byte stream that executes arbitrary code on the server during `readObject()`. This is a well-known vulnerability class.
> 2. **Version coupling**: If a DTO class changes (field added/removed), old clients can't talk to new servers unless `serialVersionUID` is carefully managed.
> 3. **Language lock-in**: Only Java clients can communicate with this server. No web, mobile, or Python clients.
> 4. **Debugging difficulty**: Binary serialized objects are opaque — you can't inspect them with tools like curl or Postman.
>
> **Alternatives:**
> - **JSON over TCP**: Use Jackson/Gson to serialize DTOs to JSON strings. Human-readable, cross-language, debuggable.
> - **Protocol Buffers / gRPC**: Binary but schema-defined, cross-language, with built-in versioning.
> - **REST over HTTP**: Standard web protocol with JSON. Most widely supported.
> - **WebSockets with JSON**: For real-time bidirectional communication (like the current OCSF model) but with web compatibility.

**Q23.** Draw a sequence diagram (in text) for the "Purchase Subscription with Renewal Discount" flow, covering all classes involved.

> **A:**
> ```
> CatalogSearchScreen    SearchControl    GCMClient    GCMServer    PurchaseHandler    PurchaseDAO    DailyStatsDAO
>       │                     │               │            │              │                │              │
>       │──checkDiscount()───▶│               │            │              │                │              │
>       │                     │──sendToServer()▶           │              │                │              │
>       │                     │               │──TCP──────▶│              │                │              │
>       │                     │               │            │──dispatch()─▶│                │              │
>       │                     │               │            │              │──hasActiveExp()▶│              │
>       │                     │               │            │              │◀──true─────────│              │
>       │                     │               │            │◀─Response────│                │              │
>       │                     │               │◀──TCP──────│              │                │              │
>       │                     │◀──display()───│            │              │                │              │
>       │◀──onDiscount(true)──│               │            │              │                │              │
>       │                                                                                                │
>       │ [user sees "10% discount!" and clicks Subscribe]                                               │
>       │                                                                                                │
>       │──purchaseSub()─────▶│               │            │              │                │              │
>       │                     │──sendToServer()▶           │              │                │              │
>       │                     │               │──TCP──────▶│              │                │              │
>       │                     │               │            │──dispatch()─▶│                │              │
>       │                     │               │            │              │──hasActiveExp()▶│              │
>       │                     │               │            │              │◀──true─────────│              │
>       │                     │               │            │              │──purchaseSub()─▶│              │
>       │                     │               │            │              │  (10% discount) │              │
>       │                     │               │            │              │◀──price/expiry──│              │
>       │                     │               │            │              │──increment()───────────────────▶│
>       │                     │               │            │              │◀──ok────────────────────────────│
>       │                     │               │            │◀─Response────│                │              │
>       │                     │               │◀──TCP──────│              │                │              │
>       │                     │◀──display()───│            │              │                │              │
>       │◀──onPurchase(ok)────│               │            │              │                │              │
>       │                                                                                                │
>       │ [shows "Purchase successful! Expires: <date>"]                                                 │
> ```

**Q24.** Explain every layer of the `MapEditorScreen` → `APPROVE_MAP_EDIT` flow. A non-manager submitted changes for a map. A manager is now approving them. Trace through every class, method, and database table touched.

> **A:**
> 1. **`MapApprovalsScreen`**: Manager clicks "Approve" → calls `contentControl.approveMapEdit(requestId)`.
> 2. **`ContentManagementControl.approveMapEdit(requestId)`**: Builds `Request(MessageType.APPROVE_MAP_EDIT, requestId, sessionToken)` → `client.sendToServer(request)`.
> 3. **`GCMClient`**: Serializes and sends over TCP.
> 4. **`GCMServer.dispatchRequest()`**: `MapEditHandler.canHandle(APPROVE_MAP_EDIT)` → `true`.
> 5. **`MapEditHandler.handleApproveMapEdit(request)`**:
>    - Calls `MapEditRequestDAO.getRequest(requestId)` → reads `map_edit_requests` table, deserializes `changes_json` to `MapChanges`.
>    - Checks status == PENDING.
>    - Calls `applyMapChanges(mapChanges, userId)`:
>      - For each new city: `CityDAO.createCity()` → INSERT into `cities`. Then `CityDAO.setCityApproved(cityId)` → UPDATE `cities` SET `approved = 1`.
>      - For each new map: `MapDAO.createMap()` → INSERT into `maps`. Then `MapDAO.setMapApproved()`.
>      - For each added POI: `PoiDAO.createPoi()` → INSERT into `pois`.
>      - For each POI-map link: `PoiDAO.linkPoiToMap()` → INSERT into `map_pois` with `approved = 1`.
>      - For each updated POI: `PoiDAO.updatePoi()` → UPDATE `pois`.
>      - For each deleted POI: `PoiDAO.deletePoi()` → DELETE from `pois`.
>      - For each POI unlink: `PoiDAO.unlinkPoiFromMap()` → DELETE from `map_pois`.
>      - For each tour CRUD: `TourDAO.createTour()` / `updateTour()` / `deleteTour()` → `tours` table.
>      - For each stop CRUD: `TourDAO.addTourStop()` / `removeTourStop()` → `tour_stops` table.
>      - For each deleted map: `MapDAO.deleteMap()` → DELETE `map_pois` then DELETE `maps`.
>      - For each deleted city: `CityDAO.deleteCity()` → DELETE `cities`.
>    - For each tour with ≥2 stops: `MapDAO.createTourMap(tour, cityId)` → INSERT into `maps` with `tour_id` set.
>    - `MapEditRequestDAO.updateStatus(requestId, "APPROVED")` → UPDATE `map_edit_requests`.
>    - `PurchaseDAO.getCustomerIdsForCity(cityId)` → SELECT from `purchases` and `subscriptions`.
>    - For each customer: `NotificationDAO.createNotification(userId, ...)` → INSERT into `notifications`.
>    - Returns `ValidationResult.success(...)`.
> 6. **`GCMServer`**: Sends `Response.success(request, validationResult)` to client.
> 7. **`GCMClient`**: Deserializes → `messageHandler.displayMessage(response)`.
> 8. **`ContentManagementControl.displayMessage()`**: Detects `APPROVE_MAP_EDIT`, extracts `ValidationResult`, calls `callback.onValidationResult(result)`.
> 9. **`MapApprovalsScreen.onValidationResult(result)`**: Shows "Approved" success dialog, refreshes the pending list.
>
> **Tables touched**: `map_edit_requests` (read + update), `cities` (insert/update/delete), `maps` (insert/delete), `pois` (insert/update/delete), `map_pois` (insert/delete), `tours` (insert/update/delete), `tour_stops` (insert/delete), `purchases` (read), `subscriptions` (read), `notifications` (insert).

**Q25.** If the server were to scale horizontally (multiple server instances behind a load balancer), which components would break and why? How would you fix each one?

> **A:**
> 1. **`SessionManager` (in-memory HashMap)**: Sessions are stored in one JVM's memory. A user logging in on Server A would not be recognized by Server B. **Fix**: Use a shared session store (Redis, database-backed sessions, or JWT tokens that don't need server-side storage).
> 2. **`SubscriptionScheduler`**: Each server instance would run its own scheduler. Expiring subscriptions would receive duplicate reminders. **Fix**: Use a distributed scheduler (Quartz with JDBC store) or a leader-election mechanism so only one instance runs the scheduler.
> 3. **`GCMClient` Singleton**: Each server instance would be a separate OCSF server. The client connects to one. If that server goes down, the client loses its connection. **Fix**: Use a connection broker or switch to HTTP/REST with a load balancer.
> 4. **OCSF connection state**: OCSF binds a client to a specific `ConnectionToClient` thread on a specific server. There's no way to migrate this state. **Fix**: Move to a stateless protocol (REST) where any server can handle any request.
> 5. **`DBConnector` pool**: Each server would create its own pool. 3 servers × 10 connections = 30 connections to MySQL, which could exceed MySQL's `max_connections`. **Fix**: Tune pool sizes per instance, or use a connection proxy like ProxySQL.

**Q26.** Evaluate the Observer pattern implementation in this project. What are its strengths and weaknesses compared to alternatives?

> **A:**
> **Strengths:**
> - Clean separation: GCMClient doesn't know about specific screens. It just calls `messageHandler.displayMessage()`.
> - Flexible: Any class implementing `MessageHandler` can receive messages.
> - Layered: Control classes add a second observer layer (ContentCallback, SearchResultCallback), providing typed results instead of raw `Object` payloads.
>
> **Weaknesses:**
> - **Single handler**: Only ONE `MessageHandler` is active at a time (`setMessageHandler(this)`). If a background operation's response arrives while a different screen is active, the response goes to the wrong handler and is silently dropped or mishandled.
> - **No event bus**: There's no way for multiple screens to listen simultaneously. A proper event bus or publish-subscribe system would allow multiple interested parties to receive messages.
> - **Thread safety**: `setMessageHandler()` is called on the FX thread; `displayMessage()` is called on the OCSF reader thread. No synchronization protects the `messageHandler` field.
> - **No type safety in displayMessage**: The `Object msg` parameter requires runtime type checking and casting in every handler.
>
> **Alternative**: Use an event bus (Guava EventBus, or custom) where handlers register for specific `MessageType` values. Multiple handlers can coexist. Events are dispatched by type, eliminating the casting. Thread marshaling to the FX thread can be handled by the bus.
