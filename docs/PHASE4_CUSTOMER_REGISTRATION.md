# Phase 4: Customer Registration Documentation

## Overview

Phase 4 implements customer registration, login/logout, and session management with concurrent login prevention.

## Test Users (Default Credentials)

| Username | Password | Role |
|----------|----------|------|
| `customer` | `1234` | CUSTOMER |
| `employee` | `1234` | CONTENT_EDITOR |
| `manager` | `1234` | CONTENT_MANAGER |

---

## API Messages

### REGISTER_CUSTOMER

**Request Payload**: `RegisterRequest`
```java
{
  username: String (required, 3-20 chars)
  email: String (required, valid email)
  password: String (required, min 4 chars)
  phone: String (optional)
  paymentToken: String (mock)
  cardLast4: String (4 digits)
}
```

**Success Response**: `String` - "Registration successful! Please login."

**Error Responses**:
- `VALIDATION_ERROR` - Invalid fields, duplicate username/email
- `DATABASE_ERROR` - Failed to create account

---

### LOGIN

**Request Payload**: `LoginRequest`
```java
{
  username: String
  password: String
}
```

**Success Response**: `LoginResponse`
```java
{
  sessionToken: String (UUID)
  userId: int
  username: String
  role: String (CUSTOMER, CONTENT_EDITOR, CONTENT_MANAGER)
  isSubscribed: boolean
}
```

**Error Responses**:
- `UNAUTHORIZED` - Invalid credentials
- `UNAUTHORIZED` - User already logged in (concurrent login blocked)

---

### LOGOUT

**Request Payload**: `String` - session token

**Success Response**: `String` - "Logged out successfully"

**Error Responses**:
- `NOT_FOUND` - Session not found

---

## Session Management

### Concurrent Login Prevention

The `SessionManager` class prevents a user from logging in from multiple devices:

1. When user logs in, server checks if user already has active session
2. If yes → login is rejected with "User already logged in" error
3. If no → new session is created with unique token
4. On logout → session is invalidated

### Session Storage

Sessions are stored in-memory:
- `Map<String, SessionInfo>` - token → session info
- `Map<Integer, String>` - userId → token

---

## Security Notes

### Password Storage

For this university project, passwords are stored as **plain text** for easy testing and demonstration. In production:

```java
// Production would use BCrypt:
String hash = BCrypt.hashpw(password, BCrypt.gensalt());
boolean valid = BCrypt.checkpw(password, hash);
```

### Session Tokens

Session tokens are UUID-based and stored in memory. The client should include the token in subsequent requests for authenticated operations.

---

## Database Tables

### users
- `id` INT PRIMARY KEY
- `username` VARCHAR(50) UNIQUE
- `email` VARCHAR(100) UNIQUE
- `password_hash` VARCHAR(255) - plain text for demo
- `role` ENUM (CUSTOMER, CONTENT_EDITOR, CONTENT_MANAGER, ...)
- `phone` VARCHAR(20)
- `is_active` BOOLEAN
- `last_login_at` TIMESTAMP

### customers
- `id` INT PRIMARY KEY
- `user_id` INT FOREIGN KEY → users
- `payment_token` VARCHAR(100) - mock token
- `card_last4` VARCHAR(4)

---

## Testing Guide

### Run Tests
```bash
mvn test -Dtest=AuthDAOTest
mvn test -Dtest=SessionManagerTest
```

### Manual Testing

1. Start server: `mvn exec:java -Dexec.mainClass="server.GCMServer"`
2. Start client: `mvn javafx:run`
3. Click "Register here" → Fill form → Submit
4. Login with new credentials
5. Open second client → Try same login → Should fail

---

## File Summary

| File | Description |
|------|-------------|
| `AuthHandler.java` | Handles REGISTER/LOGIN/LOGOUT messages |
| `SessionManager.java` | In-memory session registry |
| `UserDAO.java` | Database operations for users |
| `RegisterRequest.java` | Registration DTO |
| `LoginRequest.java` | Login DTO |
| `LoginResponse.java` | Login response with session |
| `register.fxml` | Registration UI |
| `RegistrationController.java` | Registration logic |
