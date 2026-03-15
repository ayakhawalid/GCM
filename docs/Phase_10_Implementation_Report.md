# Phase 10 Implementation Report: Activity Reports

## 1. Overview
Phase 10 introduces the **Activity Reports** module, designed to provide Managers and Administrators with statistical insights into system usage. This feature tracks key performance metrics such as map creation, purchases, subscriptions, views, and downloads, aggregating them by city and date.

## 2. Features Implemented

### 2.1 Daily Statistics Tracking
The server now automatically records events as they occur in real-time. The tracked metrics include:
*   **Maps Created**: Counts of new maps added to a city.
*   **One-Time Purchases**: Counts of users purchasing single-use/permanent entitlement.
*   **Subscriptions**: Counts of new or renewed subscriptions.
*   **Renewals**: Specifically triggers on subscription extensions (currently aggregated with subscriptions).
*   **Views**: Counts of map view events by authorized users.
*   **Downloads**: Counts of map downloads (if enabled).

### 2.2 Reporting UI (Client-Side)
A new **Activity Reports Screen** has been integrated into the Dashboard.
*   **Access Control**: Restricted to `MANAGER` role (Content Manager, Company Manager).
*   **Filters**:
    *   **Date Range**: Start and End date pickers to define the report period.
    *   **City Selector**: Dropdown to filter stats for a specific city or view aggregate data for all cities.
*   **Visualization**: A **Bar Chart** dynamically displays the total counts for each metric within the selected timeframe.

### 2.3 Server-Side Architecture
*   **Database**: Added `daily_stats` table with atomic increment logic (`ON DUPLICATE KEY UPDATE`) to ensure data consistency without complex locking.
*   **Strategy Pattern**: Implemented `ReportGenerator` interface with `CityReportGenerator` and `AllCitiesReportGenerator` strategies to handle different report scopes efficiently.
*   **API**: Introduced `GET_ACTIVITY_REPORT` request type for fetching statistical data.

## 3. Technical Implementation Details

### 3.1 Database Schema
**Table**: `daily_stats`
| Column | Type | Description |
| :--- | :--- | :--- |
| `stat_date` | DATE | The date of the record (PK). |
| `city_id` | INT | The city ID (PK). |
| `maps_count` | INT | Number of maps created. |
| `one_time_purchases` | INT | Number of one-time purchases. |
| `subscriptions` | INT | Number of subscriptions. |
| `renewals` | INT | Number of renewals. |
| `views` | INT | Number of views. |
| `downloads` | INT | Number of downloads. |

### 3.2 Key Classes
*   **`common.DailyStat`**: DTO transferring stat data between server and client.
*   **`server.dao.DailyStatsDAO`**: Handles atomic SQL increments and range queries.
*   **`server.handler.ReportHandler`**: Processes report requests and delegates to the appropriate strategy.
*   **`client.boundary.ReportsController`**: Manages the JavaFX UI and chart rendering.

## 4. How to Use
1.  **Login** as a Manager (e.g., `contentman`, `companyman`).
2.  On the **Dashboard**, locate the **"Activity Reports"** section (only visible to managers).
3.  Click **"Open Reports"**.
4.  Select a **Start Date** and **End Date**.
5.  (Optional) Select a specific **City** from the dropdown.
6.  Click **"Generate Report"**.
7.  View the bar chart visualizing the activity for that period.

## 5. Status
*   **Server**: ✅ Complete (DAO, Handler, Event Hooks implemented).
*   **Client**: ✅ Complete (UI, Controller, Dashboard Integration implemented).
*   **Integration**: ✅ Verified.
