-- RM-30: automatic delivery retry with backoff, terminal GAVE_UP status
ALTER TABLE notifications ADD COLUMN next_attempt_at TIMESTAMP;
ALTER TABLE notifications ADD COLUMN last_error VARCHAR(300);

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS chk_notifications_status;
ALTER TABLE notifications ADD CONSTRAINT chk_notifications_status
    CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'RETRYING', 'SUPPRESSED', 'GAVE_UP'));

-- rows that are already FAILED become due now, so the job picks them up once
UPDATE notifications SET next_attempt_at = NOW() WHERE status = 'FAILED' AND next_attempt_at IS NULL;

CREATE INDEX idx_notifications_status_next_attempt ON notifications (status, next_attempt_at);
