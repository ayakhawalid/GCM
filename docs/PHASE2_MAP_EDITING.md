# Phase 2 - Map Editing Feature

## Overview
Phase 2 implements full map editing capabilities for the GCM system, allowing Content Editors to create and manage cities, maps, POIs, and tours.

---

## Features Implemented

### City Management
- **Create City** - Add new cities to the system
- **Update City** - Modify city name, description, price

### Map Management
- **Create Map** - Add maps to cities
- **Update Map** - Modify map name and description
- **Delete Map** - Remove maps (cascades to unlink POIs)

### POI (Points of Interest) Management
- **Add POI** - Create new POIs with category, location, accessibility
- **Update POI** - Modify existing POIs
- **Delete POI** - Remove POIs (blocked if used in tours)
- **Link/Unlink** - Connect POIs to maps

### Tour Management
- **Create Tour** - Define guided tours with duration
- **Update Tour** - Modify tour details
- **Delete Tour** - Remove tours (cascades to delete stops)
- **Add Stop** - Add POI stops to tours with order and duration
- **Update Stop** - Modify stop order, duration, notes
- **Remove Stop** - Remove stops from tours

---

## Validation Rules

| Rule | Description |
|------|-------------|
| **Required Fields** | City name, map name, POI name, tour name required |
| **POI References** | Tour stops must reference valid POI IDs |
| **Stop Order** | Must be sequential integers (1, 2, 3...) |
| **Duration** | Tour and stop durations must be > 0 |
| **Delete Protection** | POIs in tours cannot be deleted |

> **⚠️ Important**: To delete a POI that's used in a tour, you must first remove it from all tours.

---

## Message Types

| MessageType | Payload | Response |
|-------------|---------|----------|
| `GET_CITIES` | - | `List<CityDTO>` |
| `GET_MAPS_FOR_CITY` | `cityId` | `List<MapSummary>` |
| `GET_MAP_CONTENT` | `mapId` | `MapContent` |
| `CREATE_CITY` | `CityDTO` | `ValidationResult` |
| `CREATE_MAP` | `MapContent` | `ValidationResult` |
| `ADD_POI` | `Poi` | `ValidationResult` |
| `UPDATE_POI` | `Poi` | `ValidationResult` |
| `DELETE_POI` | `poiId` | `ValidationResult` |
| `CREATE_TOUR` | `TourDTO` | `ValidationResult` |
| `DELETE_TOUR` | `tourId` | `ValidationResult` |
| `ADD_TOUR_STOP` | `TourStopDTO` | `ValidationResult` |
| `SUBMIT_MAP_CHANGES` | `MapChanges` | `ValidationResult` |

---

## Files Created

### DTOs
| File | Description |
|------|-------------|
| `dto/CityDTO.java` | City data with map count |
| `dto/TourDTO.java` | Tour with stops list |
| `dto/TourStopDTO.java` | Tour stop with POI ref |
| `dto/MapContent.java` | Complete map for editing |
| `dto/MapChanges.java` | Batch changes submission |
| `dto/ValidationResult.java` | Validation errors |

### DAOs
| File | Description |
|------|-------------|
| `dao/CityDAO.java` | City CRUD operations |
| `dao/MapDAO.java` | Map CRUD + content loading |
| `dao/PoiDAO.java` | POI CRUD + map linking |
| `dao/TourDAO.java` | Tour + stops CRUD |

### Handlers
| File | Description |
|------|-------------|
| `handler/MapEditHandler.java` | All editing operations |

### Client
| File | Description |
|------|-------------|
| `control/ContentManagementControl.java` | Client controller |
| `boundary/MapEditorScreen.java` | UI controller |
| `map_editor.fxml` | Editor UI layout |

---

## User Interface

The Map Editor provides a comprehensive interface:

### Left Panel
- City dropdown selector
- "Create New City" button
- Maps list for selected city
- "Create New Map" button

### Center Panel (Tabs)
- **📍 POIs Tab**: List of POIs, add/edit/delete forms
- **🚶 Tours Tab**: Tours list, stops management
- **📋 Map Info Tab**: Edit map name and description

### Right Panel
- Save All Changes button
- Discard Changes button
- Help section explaining features

---

## Access Control

The Map Editor has role-based access control:

| Role | Access Level | Description |
|------|--------------|-------------|
| **Guest (ANONYMOUS)** | ❌ Blocked | Must login to access |
| **Customer** | ✅ Request Mode | Changes submitted as requests for approval |
| **Employee** | ✅ Full Access | Direct editing capabilities |
| **Manager** | ✅ Full Access | Direct editing + approval of customer requests |

> **Note**: Customers can browse and request edits, but their changes require employee/manager approval before being applied.

---

## How to Access

### From Login Screen
1. Login with username and password
2. On main screen, click **"✏️ Map Editor"**

### From Main Screen
1. Click **"✏️ Map Editor"** button in Advanced Features

### Test Accounts (from seed data)
| Username | Password | Role |
|----------|----------|------|
| `admin` | `1234` | Manager |
| `johnd` | `1234` | Employee |
| `janes` | `1234` | Customer |

---

## Running Tests

```bash
mvn test -Dtest=MapEditDAOTest
```

### Test Cases
1. ✅ Create city + first map
2. ✅ Add POI then link to tour
3. ✅ Delete POI in tour (blocked)
4. ✅ Invalid submission returns errors

---

## Transaction Support

Complex operations use database transactions:
- `SUBMIT_MAP_CHANGES` - All changes in single transaction
- Rollback on any failure
- Created entity IDs returned on success

```java
conn.setAutoCommit(false);
try {
    // Multiple operations...
    conn.commit();
} catch (SQLException e) {
    conn.rollback();
}
```
