# Phase 3 - Publish New Version

## Overview
Phase 3 implements the map version approval workflow, allowing Content Editors to submit map changes for approval and Content Managers to approve or reject them.

---

## State Machine

```
      ┌─────────┐
      │  DRAFT  │
      └────┬────┘
           │ submit (SUBMIT_MAP_CHANGES)
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

---

## Customer Visibility

> **IMPORTANT**: Customers only see APPROVED versions.

- PENDING versions are not visible to customers
- REJECTED versions are not visible to customers
- `MapVersionDAO.getLatestApprovedVersion(mapId)` returns what customers see

---

## Message Types

| MessageType | Payload | Response |
|-------------|---------|----------|
| `LIST_PENDING_MAP_VERSIONS` | - | `List<MapVersionDTO>` |
| `GET_MAP_VERSION_DETAILS` | `Integer versionId` | `MapVersionDTO` |
| `APPROVE_MAP_VERSION` | `ApprovalRequest` | `MapVersionDTO` |
| `REJECT_MAP_VERSION` | `ApprovalRequest` | `MapVersionDTO` |

---

## Files Created

### DTOs
| File | Description |
|------|-------------|
| `dto/MapVersionDTO.java` | Version with status and metadata |
| `dto/ApprovalDTO.java` | Approval record |
| `dto/ApprovalRequest.java` | Approve/reject payload |
| `dto/NotificationDTO.java` | User notification |

### DAOs
| File | Description |
|------|-------------|
| `dao/MapVersionDAO.java` | Version CRUD |
| `dao/ApprovalDAO.java` | Approval records |
| `dao/AuditLogDAO.java` | Audit logging |
| `dao/NotificationDAO.java` | Notifications |

### Handlers
| File | Description |
|------|-------------|
| `handler/ApprovalHandler.java` | All approval operations |

### Database Tables
| Table | Purpose |
|-------|---------|
| `approvals` | Tracks approval status |
| `audit_log` | Records all actions |
| `notifications` | User notifications |

---

## Workflow

### 1. Editor Submits Changes
```
Editor → SUBMIT_MAP_CHANGES → Creates PENDING MapVersion + Approval + AuditLog
```

### 2. Manager Approves
```
Manager → APPROVE_MAP_VERSION → Sets APPROVED + Notifies customers
```

### 3. Manager Rejects
```
Manager → REJECT_MAP_VERSION → Sets REJECTED + Notifies editor
```

---

## Running Tests

```bash
mvn test -Dtest=MapVersionDAOTest
```

### Test Cases
1. ✅ Create version → status is PENDING
2. ✅ Approve version → status is APPROVED
3. ✅ Reject version → customer-visible version unchanged
4. ✅ Notifications created for eligible customers
5. ✅ Audit log entries written

---

## Test Accounts

| Username | Password | Role |
|----------|----------|------|
| `employee` | `1234` | CONTENT_EDITOR (submit changes) |
| `manager` | `1234` | CONTENT_MANAGER (approve/reject) |
| `customer` | `1234` | CUSTOMER (sees approved only) |
