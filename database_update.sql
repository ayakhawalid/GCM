USE gcm_db;
CREATE TABLE IF NOT EXISTS subscription_reminders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    subscription_id INT NOT NULL,
    reminder_type VARCHAR(20) NOT NULL,
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_reminder (subscription_id, reminder_type)
);

