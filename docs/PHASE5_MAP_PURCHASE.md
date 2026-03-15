# Phase 5: Map Purchase & Subscriptions

## Overview
Phase 5 adds monetization to the GCM system, allowing users to purchase map rights via two models:
1. **One-Time Purchase**: Permanent access to download the current map version.
2. **Subscription**: Period-based access (1-6 months) allowing unlimited views and downloads.

## Permission Matrix

| Role / Entitlement | Browse Catalog | View City Price | Purchase | View Map Content | Download Map |
|-------------------|----------------|-----------------|----------|------------------|--------------|
| **Guest**         | ✓              | ✓               | ✗        | ✗                | ✗            |
| **Customer**      | ✓              | ✓               | ✓        | ✗                | ✗            |
| **One-Time Owner**| ✓              | ✓               | ✓        | ✗ (See Note)     | ✓            |
| **Subscriber**    | ✓              | ✓               | ✓        | ✓ (Active)       | ✓ (Active)   |

*> **Note**: One-time purchase grants download rights. Viewing in-app is a premium feature for subscribers only.*

## Database Schema

### Purchases Table
Stores one-time purchases.
- `id`: PK
- `user_id`: FK to users
- `city_id`: FK to cities
- `price_paid`: Amount paid
- `purchased_at`: Timestamp

### Subscriptions Table
Stores active and expired subscriptions.
- `id`: PK
- `user_id`: FK to users
- `city_id`: FK to cities
- `months`: Duration (1-6)
- `start_date`: Start of subscription
- `end_date`: Expiry date
- `is_active`: Status flag

### Event Tables
- `download_events`: Log of map downloads
- `view_events`: Log of map views

## API Messages

### Purchase Operations
| Message | Payload | Description |
|---------|---------|-------------|
| `GET_CITY_PRICE` | `cityId` (int) | Get prices |
| `PURCHASE_ONE_TIME` | `PurchaseRequest` | Buy one-time access |
| `PURCHASE_SUBSCRIPTION` | `PurchaseRequest` | Buy subscription |
| `GET_ENTITLEMENT` | `cityId` (int) | Check access rights |
| `DOWNLOAD_MAP_VERSION` | `cityId` (int) | Download map (records event) |
| `GET_MY_PURCHASES` | null | Get purchase history |

## Testing

### Automated Tests
Run `mvn test -Dtest=PurchaseDAOTest` to verify:
- Purchase recording
- Entitlement calculation
- Event logging

### Manual Testing
1. Login as customer
2. Open Catalog
3. Select City -> Click "Buy One-Time" or "Subscribe"
4. Verify success message
5. Go to "My Purchases" (from Dashboard)
6. Verify item appears
7. Click "Download" -> Verify success
