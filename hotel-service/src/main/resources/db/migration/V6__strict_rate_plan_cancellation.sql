-- V6: Strict rate plan cancellation constraint
-- V3's CHECK allows NULL (FREE_CANCELLATION with NULL days passes the CHECK since NULL comparisons are UNKNOWN).
-- This new constraint explicitly enforces the cancellation rule at the database level.
ALTER TABLE rate_plans
    ADD CONSTRAINT chk_rate_plan_cancellation
        CHECK ((cancellation_policy = 'FREE_CANCELLATION' AND free_cancellation_days_before IS NOT NULL AND free_cancellation_days_before BETWEEN 0 AND 365)
               OR (cancellation_policy = 'NON_REFUNDABLE' AND free_cancellation_days_before IS NULL));
