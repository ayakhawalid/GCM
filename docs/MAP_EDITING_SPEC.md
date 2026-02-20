# Map Editing for Employees – Specification

This document describes how map editing works for **Content Editors** and **Content Managers** (e.g. for a city like Haifa).

---

## Scope: City and Maps

- **City** (e.g. Haifa) has one or more **maps**.
- Employee can:
  - **Add a map** or **remove a map** for the city.
  - **Edit a map** (metadata: name, description, etc.).

---

## Map Content: POIs and Tours

### POI (Place of Interest)

- Employee can **add**, **edit**, and **delete** POIs.
- **A POI can appear in more than one map** of the same city (e.g. a museum in three different Haifa maps).
- POIs are stored per city; the `map_pois` junction table links them to maps (many-to-many).

### Tour

- Employee can **add**, **edit**, and **delete** tours.
- Employee can **add, edit, and delete POIs (stops)** in a tour.
- **A tour can include POIs from different maps** of the same city (e.g. museum from map 1 and park from map 2).
- Tours are city-scoped; tour stops reference POIs by ID (no requirement that all POIs come from one map).

### Map editing

- Besides POIs and tours, the employee can **edit the map itself** (name, description, etc.).

---

## Adding a City

- Employee can **add a city**.
- Adding a city is supported when creating a map for a **city that does not yet exist** on the website (e.g. “create map for unfound city”).

---

## Workflow After Editing

### Content Editor

1. Editor edits city/maps (add/remove/edit maps, POIs, tours, map details).
2. Editor **submits** the new version of the city’s map(s).
3. The new version is **sent to the Content Manager** for approval.
4. Content Manager **confirms** (approves) the version.
5. After approval, **customers can see and purchase** the new version.

### Content Manager

1. Manager edits city/maps (same operations as above).
2. Manager **releases** the new version.
3. **No approval step** – the version is live once released; customers can see and purchase it.

---

## Implementation Notes (GCM_S)

- **Role-based submit** (`MapEditHandler.handleSubmitMapChanges`):
  - **CONTENT_EDITOR**: Submitting map changes creates a **Map Edit Request** (pending). A Content Manager must use **Approve Map Edit** to apply changes and create an **APPROVED** map version; then customers are notified.
  - **CONTENT_MANAGER** (or **COMPANY_MANAGER**): Submitting map changes **applies them immediately** and creates an **APPROVED** map version (no pending request). Customers are notified.
- **POI–map relationship**: `map_pois` table allows a POI to be linked to multiple maps (same city).
- **Tours**: `tours` is city-scoped; `tour_stops` reference `poi_id`, so a tour can include POIs from different maps of that city.
- **New city**: `MapChanges` supports `isCreateNewCity()` and new city fields; when applied (by editor request approval or manager direct release), the city is created and then the map/POI/tour changes are applied.
