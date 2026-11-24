-- Correlation id of the request that wrote the event, relayed as the X-Request-Id Kafka header (OBSERVABILITY.md).
-- Same column as package-booking V10 / payment V8; OutboxWriter and OutboxRelay read and write it.
ALTER TABLE outbox_event ADD COLUMN request_id VARCHAR(64);
