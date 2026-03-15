# Phase 1 - Search Feature

## Overview
Phase 1 implements the search functionality for the GCM system, allowing guests (no login required) to search maps by city name, POI name, or both.

---

## Features Implemented

### Search Types
1. **Search by City Name** - Find all maps for cities matching the search term
2. **Search by POI Name** - Find maps containing Points of Interest matching the search term
3. **Search by City + POI** - Combined search for maps in specific cities with specific POIs

### Guest Access
- Search is available without login from the login screen via "Browse City Catalog" button
- No authentication required for any search operation

---

## Message Types

| MessageType | Payload | Response |
|-------------|---------|----------|
| `GET_CITIES_CATALOG` | None | `List<CitySearchResult>` |
| `SEARCH_BY_CITY_NAME` | `SearchRequest{cityName}` | `List<CitySearchResult>` |
| `SEARCH_BY_POI_NAME` | `SearchRequest{poiName}` | `List<CitySearchResult>` |
| `SEARCH_BY_CITY_AND_POI` | `SearchRequest{cityName, poiName}` | `List<CitySearchResult>` |

---

## Database Changes

### New Tables
- `pois` - Points of Interest with city, category, location, accessibility
- `map_pois` - Junction table linking POIs to maps (many-to-many)
- `tours` - Tour definitions
- `tour_stops` - Tour stops with POI references and duration

### Indexes Added
- `idx_cities_name` on `cities.name`
- `idx_pois_name` on `pois.name`
- `idx_pois_category` on `pois.category`
- `idx_map_pois_poi_id` on `map_pois.poi_id`

### Seed Data
- 7 cities (Haifa, Tel Aviv, Jerusalem, New York, London, Paris, Tokyo)
- 16 maps across cities
- 31 POIs with categories (Beach, Museum, Religious, Historic, etc.)
- Sample tours with stops

---

## Files Created/Modified

### New Files
| File | Description |
|------|-------------|
| `common/MessageType.java` | Enum of all message types |
| `common/Request.java` | Universal request wrapper |
| `common/Response.java` | Universal response wrapper |
| `common/Poi.java` | POI entity class |
| `common/dto/SearchRequest.java` | Search request DTO |
| `common/dto/MapSummary.java` | Map summary DTO |
| `common/dto/CitySearchResult.java` | City search result DTO |
| `server/dao/SearchDAO.java` | Data access for search operations |
| `server/handler/SearchHandler.java` | Handler for search message types |
| `client/control/SearchControl.java` | Client-side search controller |
| `client/boundary/CatalogSearchScreen.java` | Search UI controller |
| `resources/client/catalog_search.fxml` | Search screen layout |
| `test/java/server/dao/SearchDAOTest.java` | Search tests |

### Modified Files
| File | Changes |
|------|---------|
| `server/GCMServer.java` | Added Request/Response dispatcher |
| `client/LoginController.java` | Added Browse Catalog button handler |
| `resources/client/login.fxml` | Added Browse Catalog button |
| `dummy_db.sql` | Complete schema rewrite with POIs |

---

## How to Run

### 1. Setup Database
```bash
mysql -u root -p < dummy_db.sql
```

### 2. Start Server
```bash
mvn compile exec:java -Dexec.mainClass=server.GCMServer
```

### 3. Start Client
```bash
mvn javafx:run
```

### 4. Access Search
1. On login screen, click **"Browse City Catalog"**
2. Search screen opens
3. Select search mode (City / POI / Both)
4. Enter search term(s) and click **Search**
5. Or click **Show All** to view full catalog

---

## Running Tests
```bash
mvn test -Dtest=SearchDAOTest
```

---

## User Guide

### Search by City
1. Select "By City" radio button
2. Type city name (e.g., "Haifa", "Tel Aviv")
3. Click "Search"
4. Results show matching cities with their maps

### Search by POI
1. Select "By POI" radio button
2. Type POI name (e.g., "Beach", "Museum", "Temple")
3. Click "Search"
4. Results show all cities/maps containing matching POIs

### Combined Search
1. Select "City + POI" radio button
2. Enter both city and POI names
3. Click "Search"
4. Results show only maps in that city with that POI

### Viewing Details
1. Click on a city in results list
2. City details panel shows:
   - City name and price
   - Description
   - List of maps
3. Click on a map to see:
   - Map name and description
   - POI count
   - Tour count
