# GCM System - User Manual

> **Version**: 1.0  
> **Phase 17**: Usability - Step-by-step walkthroughs

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Customer Workflows](#customer-workflows)
3. [Content Editor Workflows](#content-editor-workflows)
4. [Manager Workflows](#manager-workflows)
5. [Support Agent Workflows](#support-agent-workflows)
6. [Troubleshooting](#troubleshooting)

---

## Getting Started

### Launching the Application

1. **Start the Server** (Run first)
   ```
   java -jar GCM-Server.jar
   ```
   You should see: "GCM SERVER STARTED SUCCESSFULLY"

2. **Start the Client**
   ```
   java -jar GCM-Client.jar
   ```

### Test Accounts

| Username | Password | Role |
|----------|----------|------|
| customer | 1234 | Customer |
| employee | 1234 | Content Editor |
| manager | 1234 | Content Manager |

---

## Customer Workflows

### 1. Browsing Cities (No Login Required)

1. On the login screen, click **"Browse Maps"** or skip login
2. Use the **Search** feature to find cities by name
3. View city details and available maps

### 2. Registering a New Account

1. Click **"Register"** on the login screen
2. Fill in required fields:
   - Username (3-20 characters)
   - Email (valid format)
   - Password (minimum 4 characters)
3. Optionally add phone and payment details
4. Click **"Register"**
5. You'll be redirected to login automatically

### 3. Purchasing a City Map

1. Log in with your customer account
2. Navigate to **Dashboard → My Purchases**
3. Click **"Browse Cities"**
4. Select a city and choose:
   - **One-Time Purchase**: Download map once
   - **Subscription**: Unlimited access for 1-12 months
5. Confirm payment

### 4. Viewing Notifications

1. Click the **🔔 Notifications** icon on the dashboard
2. View unread notifications about:
   - Map updates for purchased cities
   - Subscription expiry reminders
3. Click notifications to mark as read

### 5. Submitting a Support Ticket

1. Navigate to **Dashboard → Support**
2. Enter your question or complaint
3. Click **"Submit"**
4. The bot will provide an automatic response
5. If unsatisfied, click **"Escalate to Agent"**

---

## Content Editor Workflows

### 1. Creating a New City

1. Log in as Content Editor
2. Navigate to **Dashboard → Map Editor**
3. Click **"Create City"**
4. Enter city name, description, and initial price
5. Click **"Save All Changes"**
6. Changes are submitted for manager approval

### 2. Editing a Map

1. Open **Map Editor**
2. Select a city from the dropdown
3. Select a map from the list
4. Use tabs to edit:
   - **POIs**: Points of Interest
   - **Tours**: Guided tour routes
   - **Map Info**: Name and description
5. Click **"Save All Changes"** to submit for approval

### 3. Adding a Point of Interest (POI)

1. In Map Editor, select a map
2. Go to the **POIs** tab
3. Click **"Add POI"**
4. Fill in:
   - Name (required)
   - Category (Museum, Beach, Historic, etc.)
   - Location (coordinates or description)
   - Description
   - ✓ Accessible (if wheelchair accessible)
5. Click **"Save"**

### 4. Creating a Tour

1. In Map Editor, select a city
2. Go to the **Tours** tab
3. Click **"Add Tour"**
4. Enter tour name, duration, description
5. Add tour stops by selecting POIs
6. Set recommended duration for each stop
7. Click **"Save"**

---

## Manager Workflows

### 1. Reviewing Map Edit Requests

1. Log in as Content Manager
2. Navigate to **Dashboard → Edit Approvals**
3. View pending map changes
4. For each request:
   - Review the proposed changes
   - Click **"Approve"** or **"Reject"**
   - Provide a reason for rejection (required)
5. Approved changes are published immediately
6. Customers who purchased the city are notified

### 2. Reviewing Pricing Requests

1. Navigate to **Dashboard → Pricing Approvals**
2. View pending price change requests
3. Review old vs. new prices
4. Approve or reject with reason

### 3. Viewing Activity Reports

1. Navigate to **Dashboard → Reports**
2. Select a date range
3. Choose:
   - Specific city report
   - All cities summary
4. View statistics:
   - Maps created
   - One-time purchases
   - Subscriptions
   - Views and downloads
5. Charts show trends over time

---

## Support Agent Workflows

### 1. Handling Escalated Tickets

1. Log in as Support Agent
2. Navigate to **Dashboard → Agent Console**
3. View tickets assigned to you
4. For each ticket:
   - Read customer's issue and bot response
   - Add a response message
   - Click **"Close and Respond"**

---

## Troubleshooting

### Connection Error

**Problem**: "Unable to connect to server"

**Solutions**:
1. Ensure the server is running
2. Check server is on port 5555
3. Verify network connection
4. Check firewall settings

### Login Failed

**Problem**: "Invalid username or password"

**Solutions**:
1. Check caps lock
2. Verify account exists
3. Reset password if forgotten
4. Check if account is active

### Already Logged In

**Problem**: "User already has active session"

**Solutions**:
1. Same user cannot log in twice simultaneously
2. Log out from the other session
3. Wait for session timeout (automatic cleanup)

### Map Editor Not Loading

**Problem**: Map content doesn't appear

**Solutions**:
1. Ensure you selected both city AND map
2. Check server connection
3. Refresh by selecting a different map

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Enter | Submit form / Confirm |
| Escape | Cancel / Close dialog |
| Tab | Move to next field |

---

## Need Help?

- Submit a support ticket through the application
- Contact: support@gcm.com
- Documentation: docs.gcm.com

---

*GCM System v1.0 - User Manual*
