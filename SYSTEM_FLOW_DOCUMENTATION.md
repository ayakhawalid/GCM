# GCM (Global City Maps) — Complete System Flow Documentation

This document describes architecture, protocol, server dispatch, sessions, and **end-to-end flows** for major features (auth, search, map editing, approvals, purchases, support, etc.). It is written for developers maintaining the codebase: detailed enough to trace **classes, `MessageType`s, and data layers**, with repetitive steps **grouped** where possible. For exact SQL and edge cases, use the DAO classes and `dummy_db.sql`.

**Scope:** `target/` is gitignored — run `mvn package` after clone. **Column-level detail** lives in **`dummy_db.sql`** and DAOs; this doc focuses on **flows and relationships**.

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
11. [Purchase Actions](#11-purchase-actions) — table of `MessageType`s below
12. [Customer/Profile Actions](#12-customerprofile-actions) — table below
13. [Notification Actions](#13-notification-actions) — table below
14. [Pricing Actions](#14-pricing-actions) — table below
15. [Support Ticket Actions](#15-support-ticket-actions) — table below
16. [Report Actions](#16-report-actions)
    - 16.1 [Get Activity Report](#161-get-activity-report)
17. [Subscription Scheduler](#17-subscription-scheduler)
18. [Database Schema (Inferred)](#18-database-schema-inferred)
19. [Database Table Relationships & Connection](#19-database-table-relationships--how-the-database-connects-to-the-project)

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
- **Handlers** (`server.handler.*`): `SearchHandler`, `MapEditHandler`, `ApprovalHandler`, `AuthHandler`, `PurchaseHandler`, `UserManagementHandler`, `CustomerHandler`, `NotificationHandler`, `PricingHandler`, `SupportHandler`, `ReportHandler` (see [§4](#4-server-dispatch-mechanism) for dispatch order). Each handler declares which `MessageType` values it can handle, validates the request, calls DAOs, and returns a `Response`.
- **DAOs** (`server.dao.*`): Data access objects that execute SQL against MySQL via `DBConnector` (HikariCP pool).
- **Services** (`server.service.*`): `BotService` (FAQ matching), report generators (`CityReportGenerator`, `AllCitiesReportGenerator`), `OsrmClient` (driving distance).
- **Scheduler** (`server.scheduler.*`): `SubscriptionScheduler` runs every 2 minutes to check for expiring subscriptions and send reminders.

**Shared:**
- **common**: `Request`, `Response`, `MessageType`, entity classes (`City`, `Map`, `Poi`, `DailyStat`), and all DTOs (`common.dto.*`).

### 1.1 Build, JARs, and client–server addressing

| Topic | Detail |
|--------|--------|
| **Build** | `mvn clean package` produces `target/GCM-Server.jar` and `target/GCM-Client.jar` (shade / fat JARs). |
| **Server** | Default TCP **5555**; `GCMServer` listens on **all interfaces** (`0.0.0.0`), suitable for LAN clients. |
| **MySQL** | Server uses JDBC in `DBConnector` (typically `localhost` **on the server machine**). |
| **Client default host** | Not hardcoded only to `localhost`: `LoginApp` applies **`gcm-client.properties`** (`gcm.server.host`, `gcm.server.port`), then **`-Dgcm.server.*`**, then **CLI** `java -jar GCM-Client.jar <host> [port]` (highest precedence). |
| **Consistent endpoints** | `GCMClient.configureEndpoint` runs before login; `SearchControl` / `ContentManagementControl` / registration use **`GCMClient.getConfiguredHost()`** / **`getConfiguredPort()`** (registration opens a **separate `Socket`** for one-shot isolation, same host/port). |

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

`GCMClient` is a singleton obtained via `getInstance()`. On first call, it connects to whatever host/port was set by **`LoginApp`** via **`GCMClient.configureEndpoint`** (from `gcm-client.properties`, system properties, or CLI args — default `localhost:5555` if none).

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

`dispatchRequest` checks handlers in **this exact order** (first `canHandle` wins):

1. **SearchHandler** — catalog & search (`GET_CITIES_CATALOG`, `SEARCH_BY_*`). Typically no login.
2. **MapEditHandler** — city/map/POI/tour CRUD, drafts, submit, pending map-edit approvals (`GET_PENDING_MAP_EDITS`, `APPROVE_MAP_EDIT`, `REJECT_MAP_EDIT`), `GET_POIS_FOR_CITY`, etc.
3. **ApprovalHandler** — **map version** pipeline (`LIST_PENDING_MAP_VERSIONS`, `GET_MAP_VERSION_DETAILS`, `APPROVE_MAP_VERSION`, `REJECT_MAP_VERSION`).
4. **AuthHandler** — `REGISTER_CUSTOMER`, `LOGIN`, `LOGOUT`. On successful **`LOGIN`**, `GCMServer` links the new session token to the OCSF connection via **`SessionManager.setSessionConnection(token, clientId)`**.
5. **PurchaseHandler** — prices, purchases, entitlements, downloads, views, `GET_MY_PURCHASES`, discount check.
6. **UserManagementHandler** — company-manager staff ops: `ADMIN_LIST_STAFF`, `ADMIN_UPDATE_USER_ROLE`, `ADMIN_REVOKE_ROLE`, `ADMIN_CREATE_STAFF_USER`.
7. **CustomerHandler** — profile; admin customer list / purchases.
8. **NotificationHandler** — notifications and unread count.
9. **PricingHandler** — pricing requests and approvals.
10. **SupportHandler** — tickets, FAQ bot, customer/agent flows (includes **`CLOSE_TICKET`** where implemented).
11. **ReportHandler** — `GET_ACTIVITY_REPORT`.
12. **Legacy** — `LEGACY_GET_CITIES` / `LEGACY_GET_MAPS` via `MySQLController`, plus string-based protocol in `handleLegacyMessage`.

If no handler matches: error response (`No handler for message type: …`).

### userId Resolution

Many handlers need the userId. The server resolves it via a method called `resolveUserId(request)`:
1. If `request.getUserId() > 0`, use that directly.
2. Else if `request.getSessionToken() != null`, validate it via `SessionManager.validateSession(token)` and get `sessionInfo.getUserId()`.
3. If neither works, the userId remains 0 (guest).

---

## 5. Session Management

`SessionManager` is a singleton that manages all active sessions in memory (no database persistence for sessions).

### Data Structures

- `sessions`: `ConcurrentHashMap<String, SessionInfo>` — token → session details.
- `userSessions`: `ConcurrentHashMap<Integer, String>` — userId → token (one token per user).
- `connectionSessions`: `ConcurrentHashMap<String, String>` — connectionId → token (for cleanup on disconnect).

### SessionInfo

Each session stores:
- `userId`, `username`, `role` (String), `createdAt` (timestamp), `connectionId` (String, set after login).

### Lifecycle

1. **Creation** (`createSession(userId, username, role)`):
   - Refuses a second session if the user is already logged in (`isUserLoggedIn`) — returns `null` (see **AuthHandler** below for login flow).
   - Generates a token via `UUID.randomUUID().toString()`, stores in all maps, returns the token.

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

### Single session and re-login

**`AuthHandler.handleLogin`** explicitly **`invalidateUserSession(user.id)`** if a session already exists, then calls **`createSession`**. So a **new login replaces** the previous session (same user). The older client’s token is no longer valid on the next request.

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
   - Opens a new `Socket(GCMClient.getConfiguredHost(), GCMClient.getConfiguredPort())` (same endpoint as the main client; **not** hardcoded `localhost`).
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

### 8.5–8.11 City, map, and POI CRUD (grouped)

All of these follow the same **Boundary → `ContentManagementControl` → `Request` → `MapEditHandler` → DAO** path. Payloads are `CityDTO`, `MapContent` / map id, or `Poi` as appropriate.

| § | MessageType | Server / DAO (summary) |
|---|-------------|-------------------------|
| 8.5 | `CREATE_CITY` | `CityDAO.createCity` — `INSERT` into `cities` (often `approved = 0` draft); returns new id in `ValidationResult`. |
| 8.6 | `UPDATE_CITY` | `CityDAO.updateCity` — `UPDATE cities SET ...` |
| 8.7 | `CREATE_MAP` | `MapDAO.createMap`; may increment **daily stats** (`DailyStatsDAO`); returns new map id. |
| 8.8 | `DELETE_MAP` | `MapDAO.deleteMap` — remove `map_pois` then `maps`. |
| 8.9 | `ADD_POI` | `PoiDAO.createPoi` — `INSERT` into `pois` (lat/lng parsed from `location` where applicable). |
| 8.10 | `UPDATE_POI` | `PoiDAO.updatePoi` — `UPDATE pois`. |
| 8.11 | `DELETE_POI` | `PoiDAO.isPoiUsedInTour`; if used in **`tour_stops`**, reject; else `DELETE` POI. |

**Map click → POI:** `MapEditorScreen` (Leaflet / `WebView`) → JS `onMapClick` → **`MapClickBridge`** → Nominatim (or similar) to pre-fill the POI form.

---

### 8.12 Link/Unlink POI to Map

**Link**: `MessageType.LINK_POI_TO_MAP` with a `PoiMapLink(mapId, poiId, displayOrder)`. Server calls `PoiDAO.linkPoiToMap(mapId, poiId, displayOrder)`: `INSERT INTO map_pois (map_id, poi_id, display_order) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE display_order = ?`.

**Unlink**: `MessageType.UNLINK_POI_FROM_MAP` with a `PoiMapLink`. Server calls `PoiDAO.unlinkPoiFromMap(mapId, poiId)`: `DELETE FROM map_pois WHERE map_id = ? AND poi_id = ?`.

These are typically sent as part of batched `MapChanges` rather than individually.

---

### 8.13–8.18 Tour and tour-stop CRUD (grouped)

| § | MessageType | Server / DAO (summary) |
|---|-------------|-------------------------|
| 8.13 | `CREATE_TOUR` | `TourDAO.createTour` — `INSERT` into `tours`. |
| 8.14 | `UPDATE_TOUR` | `TourDAO.updateTour`. |
| 8.15 | `DELETE_TOUR` | `TourDAO.deleteTour` (cascades per schema). |
| 8.16 | `ADD_TOUR_STOP` | Validate POI exists; `TourDAO.addTourStop` — `INSERT`/`UPSERT` into `tour_stops`. |
| 8.17 | `UPDATE_TOUR_STOP` | `TourDAO.updateTourStop`. |
| 8.18 | `REMOVE_TOUR_STOP` | `TourDAO.removeTourStop` — `DELETE` stop row. |

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

All routes go through **PurchaseHandler** + **PurchaseDAO** (and **DailyStatsDAO** / **UserDAO** where noted). **Boundary:** mainly CatalogSearchScreen, MyPurchasesScreen, ProfileScreen; **Control:** PurchaseControl / direct GCMClient where applicable.

| § | MessageType | Behavior (summary) |
|---|-------------|-------------------|
| 11.1 | GET_CITY_PRICE | PurchaseDAO.getCityPrice → **CityPriceInfo** (one-time + 1/3/6 month subscription pricing from city base price). |
| 11.2 | PURCHASE_ONE_TIME | Session check; PurchaseDAO.purchaseOneTime → purchases row; optional save card; **DailyStatsDAO.increment(PURCHASE_ONE_TIME)**; **PurchaseResponse**. |
| 11.3 | PURCHASE_SUBSCRIPTION | Renewal discount via **hasActiveExpiringSubscription**; PurchaseDAO.purchaseSubscription → **subscriptions**; stats RENEWAL vs **SUBSCRIPTION**; returns expiry. |
| 11.4 | CHECK_DISCOUNT_ELIGIBILITY | Boolean for UI (guest → false). |
| 11.5 | GET_ENTITLEMENT | **NONE / ONE_TIME / SUBSCRIPTION** from active subscription, else one-time purchase + download count rules. |
| 11.6 | CAN_DOWNLOAD | Simplified boolean from entitlement logic. |
| 11.7 | DOWNLOAD_MAP_VERSION | Entitlement check; for one-time, **PurchaseDAO.recordDownload**; **DailyStatsDAO.increment(DOWNLOAD)**. |
| 11.8 | RECORD_VIEW_EVENT | Parse city/map from payload; **PurchaseDAO.recordView** + view metric. |
| 11.9 | GET_MY_PURCHASES | Lists subscriptions + purchases for **MyPurchasesScreen** / profile. |

**Theory (one line each):** One-time = limited download/view per product rules in DAO; subscription = time-bounded unlimited access per implementation; renewal **10%** discount when **hasActiveExpiringSubscription** is true.

## 12. Customer/Profile Actions

**Handler:** **CustomerHandler** + **UserDAO** / **PurchaseDAO** (admin purchases). **Screens:** ProfileScreen, AdminCustomersScreen.

| § | MessageType | Behavior (summary) |
|---|-------------|-------------------|
| 12.1 | GET_MY_PROFILE | Session → **UserDAO.getProfile** → **CustomerProfileDTO** (aggregates purchases/subscriptions; managers see masked card). |
| 12.2 | UPDATE_MY_PROFILE | Map of fields; email uniqueness via **UserDAO.findByEmail**; **UserDAO.updateProfile** (users + customers). |
| 12.3 | ADMIN_LIST_CUSTOMERS | Role **CONTENT_MANAGER** or **COMPANY_MANAGER**; optional payload **Boolean lastMonthOnly** → **UserDAO.listAllCustomers**. |
| 12.4 | ADMIN_GET_CUSTOMER_PURCHASES | **userId** (+ optional **lastMonthOnly**) → **PurchaseDAO.getPurchasesDetailed** (purchases + subscriptions). |

## 13. Notification Actions

**Handler:** **NotificationHandler** + **NotificationDAO**. **Screens:** Dashboard (badge/dialog).

| § | MessageType | Behavior (summary) |
|---|-------------|-------------------|
| 13.1 | GET_MY_NOTIFICATIONS | **NotificationDAO.getNotificationsForUser** → list ordered by date. |
| 13.2 | MARK_NOTIFICATION_READ | **NotificationDAO.markAsRead** by id. |
| 13.3 | GET_UNREAD_COUNT | Count where `is_read = FALSE` → badge. |

## 14. Pricing Actions

**Handler:** **PricingHandler** + **PricingDAO** + **AuditLogDAO**; approve/reject also **NotificationDAO**. **Theory:** Content manager proposes a city price change; company manager approves/rejects; approve updates **cities.price** in a transaction and notifies submitter.

| § | MessageType | Behavior (summary) |
|---|-------------|-------------------|
| 14.1 | GET_CURRENT_PRICES | **PricingDAO.ensureTableExists** + **getAllCurrentPrices** from **cities** → **CityPriceInfo** list. |
| 14.2 | SUBMIT_PRICING_REQUEST | Validate price/reason; one **PENDING** per city; **createPricingRequest** + audit. |
| 14.3 | LIST_PENDING_PRICING_REQUESTS | Join request + city + submitter for approval UI. |
| 14.4 | APPROVE_PRICING_REQUEST | Txn: mark approved, **UPDATE cities SET price**, audit, notify submitter. |
| 14.5 | REJECT_PRICING_REQUEST | Mark rejected + reason; city price unchanged; notify submitter. |

## 15. Support Ticket Actions

**Handler:** SupportHandler + **SupportDAO** + **BotService** (FAQ keyword match from **faq_entries**) + **AuditLogDAO**. Many screens use **sendRequestSync**. Flow: duplicate detection (similar open tickets) → create or merge → bot reply → optional **ESCALATED** status.

| § | MessageType | Behavior (summary) |
|---|-------------|-------------------|
| 15.1 | CREATE_TICKET | See duplicate merge; insert ticket + first message; bot answer; may escalate. |
| 15.2 | GET_MY_TICKETS | List tickets for user (no full thread). |
| 15.3 | GET_TICKET_DETAILS | Ticket + messages (+ sender names). |
| 15.4 | CUSTOMER_REPLY | Add message; may re-run bot if still OPEN/BOT_RESPONDED. |
| 15.5 | ESCALATE_TICKET | Status **ESCALATED**; agent queue. |
| 15.6 | CLOSE_TICKET | Customer close → **SupportDAO.closeTicket** (routed by **SupportHandler**). |
| 15.7 | AGENT_LIST_ASSIGNED | Tickets where **assigned_agent_id** = agent. |
| 15.8 | AGENT_LIST_PENDING | **ESCALATED** and unassigned, by priority. |
| 15.9 | AGENT_CLAIM_TICKET | Set assignee + system message. |
| 15.10 | AGENT_REPLY | Agent message line. |
| 15.11 | AGENT_CLOSE_TICKET | Optional final message + close. |

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

**Canonical column definitions:** dummy_db.sql and DAO/migration code. This table lists **purpose** only — open the SQL when you need types, indexes, and constraints.

| Table | Purpose |
|-------|---------|
| users | Login identity: username, email, password, role |
| customers | Customer add-on: phone, card, payment token (user_id → users) |
| cities | Catalog: name, description, price, approval columns |
| maps | Maps under a city; optional tour_id for tour-route map |
| pois | Points of interest; location + lat/lng, category |
| map_pois | Map ↔ POI links, display order, approval |
| tours / tour_stops | Tours and ordered POI stops |
| poi_distances | Cached driving distances (OSRM) between POIs |
| map_versions | Published map version workflow |
| map_edit_requests | Editor drafts/submissions (changes_json) |
| approvals | Generic approval records |
| purchases | One-time purchases per user/city |
| subscriptions | Time-bounded access (months, date range) |
| download_events / view_events | Usage analytics |
| pricing_requests | Proposed city price changes |
| notifications | User notifications |
| support_tickets / ticket_messages | Support + threaded messages |
| faq_entries | FAQ for support bot |
| daily_stats | Daily per-city metrics |
| audit_log | Action audit trail |
| subscription_reminders | Deduped renewal reminders |

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

**Pool lifecycle (summary):** First `getConnection()` may initialize the pool and run `ensureSchema()`; `serverStarted()` runs migrations such as `CityDAO.ensureCitiesApprovalColumns()`; scheduler starts; on shutdown, thread pool stops and `DBConnector.closePool()` runs.

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

**Migration SQL:** see project root (`dummy_db.sql`, `database_update.sql`, `migration_*.sql`) — consolidated commentary also appears in `dummy_db.sql` near the end.

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

### 19.3 Foreign keys (compact)

The **ER diagram in §19.2** is authoritative. In words: **`users`** anchor **customers**, **employees**, purchases, subscriptions, events, notifications, tickets, map version metadata, pricing requests, audit rows, and city `created_by`. **`cities`** own **maps**, **pois**, **tours**, commerce and analytics rows, and edit requests. **`maps`** link to **`map_pois`**, **`map_versions`**, view events, edits. **`pois`** join to **`map_pois`**, **`tour_stops`**, **`poi_distances`**. **`tours`** own **tour_stops** and may link a **route map** via `maps.tour_id`. **`support_tickets`** own **ticket_messages**. **`subscriptions`** tie to **subscription_reminders**. Some edit-request FKs are **logical** (not every column has a DB-declared constraint in all migrations). For exact `ON DELETE` rules, see `dummy_db.sql` / live schema.

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

## 20. Design Patterns (condensed reference)

The codebase uses the patterns below; see classes named in each row for full detail.

### Pattern summary

| # | Pattern | Role | Key classes |
|---|---------|------|-------------|
| 1 | Singleton | One shared instance | GCMClient, SessionManager, SubscriptionScheduler, DBConnector (pool), OsrmClient |
| 2 | Observer / callback | Server → UI updates | GCMClient.MessageHandler; ContentCallback, SearchResultCallback, purchase callbacks; JavaFX listeners |
| 3 | Factory helpers | Consistent object construction | Response.success / error, SearchRequest.byCity / yPoi, PaginatedResponse, etc. |
| 4 | Strategy | Swappable algorithms | ReportGenerator ← CityReportGenerator, AllCitiesReportGenerator |
| 5 | Template method | OCSF defines socket lifecycle | AbstractClient→GCMClient, AbstractServer→GCMServer |
| 6 | Chain of responsibility | First matching handler wins | GCMServer.dispatchRequest — Search → MapEdit → Approval → Auth → Purchase → **UserManagement** → Customer → Notification → Pricing → Support → Report → legacy |
| 7 | Command | Encapsulate operations | Request, Response, MessageType |
| 8 | Fluent builder | Batch edits | MapChanges |
| 9 | DAO | All SQL via one layer | server.dao.* |
| 10 | MVC / BCE | UI / logic / model | client.boundary.*, client.control.*, common.* |
| 11 | Facade | Simple API over subsystems | ContentManagementControl, SearchControl, PurchaseControl; MySQLController (legacy) |
| 12 | Bridge / adapter | JS map ↔ Java | MapClickBridge |
| 13 | Object pool | JDBC reuse | HikariCP (DBConnector) |
| 14 | Lazy init | Connect on first use | Pool, GCMClient instance, etc. |
| 15 | Thread pool | Concurrent request handling | GCMServer fixed pool (10); scheduler; report executor |
| 16 | DTO | Wire transfer | common.dto.* |
| 17 | Callback | Typed async results | Control-layer callbacks |
| 18 | Scheduled task | Renewals / reminders | SubscriptionScheduler |
| 19 | Synchronous proxy | Block on BlockingQueue | GCMClient.sendRequestSync |
| 20 | Service interface (partial) | Future refactor hooks | IAuthService, IMapService, IPurchaseService, ISupportService |

### Notes

- **Handlers** use **static** handle methods + DAOs (not full service layer yet).
- **Legacy** string protocol and MySQLController bypass the modern handler stack for a few paths.
- **Thread safety:** session maps use **ConcurrentHashMap**; GCMClient message handler swapping vs reader thread remains a design tradeoff (see interview §21).

---

## 21. Interview Questions — Varying Difficulty

*(Answers shortened; full flows are in sections 2–17 above.)*

### Easy

**Q1.** Framework / wire protocol? **A:** OCSF over TCP; Java serialization; default port 5555.

**Q2.** Purpose of `MessageType`? **A:** Selects which server handler runs for each `Request`.

**Q3.** Why `GCMClient` singleton? **A:** One socket; one place to demux responses.

**Q4.** DTOs? **A:** Serializable payloads for cross-JVM transfer in `Request`/`Response`.

**Q5.** Roles (typical)? **A:** CUSTOMER; CONTENT_EDITOR; CONTENT_MANAGER; COMPANY_MANAGER; SUPPORT_AGENT (+ others in `users.role` / seed).

**Q6.** One-time vs subscription? **A:** One-time: paid access for download/view rules as implemented; subscription: time-bounded access (see `PurchaseHandler` / `PurchaseDAO`).

**Q7.** `SessionManager`? **A:** In-memory token → user; links token to OCSF connection id; invalidates on disconnect; maps use **`ConcurrentHashMap`**.

**Q8.** `daily_stats`? **A:** Per-city daily metrics for reports (`DailyStatsDAO`, `ReportHandler`).

### Medium

**Q9.** Request lifecycle? **A:** Boundary → Control builds `Request` → `GCMClient.sendToServer` → server pool → `dispatchRequest` → handler → DAO → `Response` → reader thread → `displayMessage` → Control callback → `Platform.runLater` UI.

**Q10.** Second login same user? **A:** **`AuthHandler`** calls **`invalidateUserSession`** then **`createSession`** — old token invalid; other client gets errors on next call.

**Q11.** `SAVE_MAP_CHANGES` vs `SUBMIT_MAP_CHANGES`? **A:** Save = draft persistence; submit = role-dependent (direct apply vs pending approval) — see **§8.19–8.20** and `MapEditHandler`.

**Q12.** Support bot? **A:** `BotService` + FAQ keywords in `faq_entries`; may escalate; messages in `ticket_messages`.

**Q13.** Template Method in OCSF? **A:** `AbstractClient`/`AbstractServer` define loop/hooks; `GCMClient`/`GCMServer` override.

**Q14.** Registration separate socket? **A:** One-shot isolation; same **host/port** as `GCMClient` (`getConfiguredHost`/`Port`); avoids sharing singleton state.

**Q15.** Chain of Responsibility? **A:** First `canHandle` wins; order in **§4** matters; duplicates would be dead code.

**Q16.** HikariCP? **A:** Pool borrows/`try-with-resources` returns connections; avoids per-query TCP handshake cost.

### Hard

**Q17.** Concurrency issues? **A:** (1) `messageHandler` swapped on FX vs responses on reader thread. (2) Session maps are **`ConcurrentHashMap`** but logical races remain on multi-step operations. (3) Double draft save interleaving. (4) Static handler state if any.

**Q18.** `MySQLController` leaks? **A:** Legacy path may not return connections — exhausts pool; fix with try-with-resources per call.

**Q19.** `CLOSE_TICKET`? **A:** Implemented in **`SupportHandler`** (`canHandle` / `handle`); routed after Pricing, before Report. If missing, would fall through to “no handler” error (verify `MessageType` set in handler).

**Q20.** Security weaknesses? **A:** No TLS on OCSF by default; tokens in clear; DB password in source; password storage policy — see `UserDAO`; no rate limit; etc.

**Q21.** Add REST? **A:** Reuse DAOs/DTOs; add JSON + HTTP; JWT or shared session store; handlers static — refactor for DI optional.

**Q22.** Java serialization risks? **A:** Deserialization class of attacks; version skew; use JSON/Protobuf/REST for production.

**Q23.** Discount subscription sequence? **A:** `CatalogSearchScreen` → `SearchControl` / purchase flow → `PurchaseHandler` + `PurchaseDAO` + stats; see **§11**.

**Q24.** `APPROVE_MAP_EDIT` layers? **A:** `MapApprovalsScreen` → `ContentManagementControl` → `MapEditHandler` applies `MapChanges` via DAOs, updates edit request, notifies customers — tables listed in **§8–9** / **§18**.

**Q25.** Horizontal scaling? **A:** In-memory sessions + sticky TCP + scheduler duplication + pool × N servers — need shared sessions, leader election, pool tuning.

**Q26.** Observer pros/cons? **A:** Simple wiring; single active `MessageHandler` risks wrong screen; no type-safe bus — consider event bus + FX thread marshaling.

