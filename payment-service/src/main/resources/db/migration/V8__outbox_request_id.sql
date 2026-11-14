-- Correlation id of the request that wrote the event, relayed as the X-Request-Id Kafka header (OBSERVABILITY.md).
ALTER TABLE outbox_event ADD COLUMN request_id VARCHAR(64);
