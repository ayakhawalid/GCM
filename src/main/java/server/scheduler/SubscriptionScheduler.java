package server.scheduler;

import server.DBConnector;
import server.dao.NotificationDAO;
import server.dao.PurchaseDAO;
import server.dao.PurchaseDAO.ExpiringSubscription;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Background scheduler that sends subscription expiry reminders.
 * 
 * Features:
 * - Runs periodically (configurable interval)
 * - Finds subscriptions expiring within 3 days
 * - Creates in-app notifications
 * - Simulates email/SMS via console logging
 * - Prevents duplicate reminders using dedup table
 * 
 * For demo purposes, runs every 2 minutes.
 * In production, would run daily.
 */
public class SubscriptionScheduler {

    private static final int EXPIRY_WARNING_DAYS = 3;
    private static final int DEMO_INTERVAL_MINUTES = 2;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ScheduledExecutorService scheduler;
    private boolean isRunning = false;

    private static SubscriptionScheduler instance;

    private SubscriptionScheduler() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SubscriptionScheduler");
            t.setDaemon(true); // Don't prevent JVM shutdown
            return t;
        });
    }

    /**
     * Get singleton instance.
     */
    public static synchronized SubscriptionScheduler getInstance() {
        if (instance == null) {
            instance = new SubscriptionScheduler();
        }
        return instance;
    }

    /**
     * Start the scheduler.
     */
    public void start() {
        if (isRunning) {
            System.out.println("⚠ SubscriptionScheduler already running");
            return;
        }

        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║     SUBSCRIPTION EXPIRY SCHEDULER STARTED                ║");
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.println("║  Interval: Every " + DEMO_INTERVAL_MINUTES + " minutes (demo mode)                   ║");
        System.out.println("║  Warning: " + EXPIRY_WARNING_DAYS + " days before expiry                           ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");

        // Schedule: initial delay of 30 seconds, then repeat every N minutes
        scheduler.scheduleAtFixedRate(
                this::checkExpiringSubscriptions,
                30, // Initial delay
                DEMO_INTERVAL_MINUTES * 60, // Period in seconds
                TimeUnit.SECONDS);

        isRunning = true;
    }

    /**
     * Stop the scheduler.
     */
    public void stop() {
        if (!isRunning) {
            return;
        }

        System.out.println("Stopping SubscriptionScheduler...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        isRunning = false;
        System.out.println("✓ SubscriptionScheduler stopped");
    }

    /**
     * Main task: Check for expiring subscriptions and send reminders.
     */
    private void checkExpiringSubscriptions() {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        System.out.println("\n═══════════════════════════════════════════════════════════");
        System.out.println("🔔 SUBSCRIPTION SCHEDULER RUN @ " + timestamp);
        System.out.println("═══════════════════════════════════════════════════════════");

        try {
            List<ExpiringSubscription> expiring = PurchaseDAO.getExpiringSubscriptions(EXPIRY_WARNING_DAYS);

            if (expiring.isEmpty()) {
                System.out.println("   No subscriptions expiring within " + EXPIRY_WARNING_DAYS + " days.");
                return;
            }

            System.out.println("   Found " + expiring.size() + " subscription(s) expiring soon:");
            int notificationsSent = 0;

            for (ExpiringSubscription sub : expiring) {
                System.out.println("   → " + sub.username + " | " + sub.cityName +
                        " | Expires: " + sub.expiryDate + " (" + sub.daysUntilExpiry + " days)");

                // Determine reminder type based on days remaining
                String reminderType = getReminderType(sub.daysUntilExpiry);

                // Requirement: specifically notify 3 days before expiry (not at 2 days).
                if ("3_DAYS".equals(reminderType) && sub.daysUntilExpiry != 3) {
                    continue;
                }
                // Keep the 1-day reminder behavior as-is, if present.
                if ("1_DAY".equals(reminderType) && sub.daysUntilExpiry != 1) {
                    continue;
                }

                // Check if reminder already sent (dedup)
                if (PurchaseDAO.hasReminderBeenSent(sub.subscriptionId, reminderType)) {
                    System.out.println("     ⏭ Reminder already sent, skipping...");
                    continue;
                }

                // Create notifications and simulate external channels
                boolean success = sendReminder(sub, reminderType);
                if (success) {
                    notificationsSent++;
                }
            }

            System.out.println("───────────────────────────────────────────────────────────");
            System.out.println("   ✓ Sent " + notificationsSent + " new reminder(s)");
            System.out.println("═══════════════════════════════════════════════════════════\n");

        } catch (Exception e) {
            System.err.println("   ✗ Scheduler error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send reminder for a subscription.
     */
    private boolean sendReminder(ExpiringSubscription sub, String reminderType) {
        try (Connection conn = DBConnector.getConnection()) {
            // 1. Create in-app notification
            String title = "Subscription Expiring Soon";
            String body = String.format(
                    "Your subscription to %s will expire in %d day(s) on %s. " +
                            "Renew now for the same city and the same duration to receive a 10%% discount!",
                    sub.cityName,
                    sub.daysUntilExpiry,
                    sub.expiryDate);

            int notificationId = NotificationDAO.createNotification(conn, sub.userId, title, body);
            if (notificationId > 0) {
                System.out.println("     📬 IN-APP: Notification #" + notificationId + " created");
            }

            // 2. Simulate email notification
            simulateEmail(sub);

            // 3. Simulate SMS notification
            simulateSms(sub);

            // 4. Record reminder sent for dedup
            PurchaseDAO.recordReminderSent(sub.subscriptionId, reminderType);
            System.out.println("     ✓ Reminder recorded for dedup");

            return true;

        } catch (SQLException e) {
            System.err.println("     ✗ Failed to send reminder: " + e.getMessage());
            return false;
        }
    }

    /**
     * Simulate sending email (just logs to console).
     */
    private void simulateEmail(ExpiringSubscription sub) {
        System.out.println("     📧 EMAIL ──────────────────────────────────────");
        System.out.println("        To: " + sub.email);
        System.out.println("        Subject: Your GCM subscription is expiring soon!");
        System.out.println("        Body: Dear " + sub.username + ", your subscription to");
        System.out.println("              " + sub.cityName + " will expire on " + sub.expiryDate + ".");
        System.out.println(
                "              Renew now for the same city and the same duration at gcm.com to receive a 10% discount!");
        System.out.println("     ───────────────────────────────────────────────");
    }

    /**
     * Simulate sending SMS (just logs to console).
     */
    private void simulateSms(ExpiringSubscription sub) {
        if (sub.phone == null || sub.phone.isEmpty()) {
            System.out.println("     📱 SMS: Skipped (no phone number)");
            return;
        }

        System.out.println("     📱 SMS ────────────────────────────────────────");
        System.out.println("        To: " + sub.phone);
        System.out.println("        Message: GCM: Your " + sub.cityName + " subscription");
        System.out.println(
                "                 expires on " + sub.expiryDate
                        + ". Renew now for the same city and the same duration to get 10% off!");
        System.out.println("     ───────────────────────────────────────────────");
    }

    /**
     * Determine reminder type based on days remaining.
     */
    private String getReminderType(int daysRemaining) {
        if (daysRemaining <= 1) {
            return "1_DAY";
        } else if (daysRemaining <= 3) {
            return "3_DAYS";
        } else {
            return "GENERAL";
        }
    }

    /**
     * Force run a check immediately (for testing).
     */
    public void runNow() {
        System.out.println("🚀 Manual scheduler trigger...");
        checkExpiringSubscriptions();
    }
}
