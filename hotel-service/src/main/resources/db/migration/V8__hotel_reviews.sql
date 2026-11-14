-- Verified hotel reviews (RM-29). order_item_id comes from package-booking-service (no cross-db FK).
CREATE TABLE hotel_reviews (
    id                 UUID PRIMARY KEY,
    hotel_id           UUID NOT NULL REFERENCES hotels(id) ON DELETE CASCADE,
    order_item_id      UUID NOT NULL,
    customer_id        UUID NOT NULL,
    score              INT NOT NULL CHECK (score BETWEEN 1 AND 5),
    review_text        VARCHAR(2000),
    status             VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('PUBLISHED', 'HIDDEN')),
    partner_response   VARCHAR(2000),
    responded_at       TIMESTAMP,
    responded_by       UUID,
    moderation_reason  VARCHAR(500),
    moderated_by       UUID,
    moderated_at       TIMESTAMP,
    created_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hotel_reviews_order_item UNIQUE (order_item_id)
);
CREATE INDEX idx_hotel_reviews_hotel_status ON hotel_reviews (hotel_id, status, created_at DESC);
CREATE INDEX idx_hotel_reviews_customer ON hotel_reviews (customer_id);

-- Append-only moderation history: who, when, why, and the status change.
CREATE TABLE hotel_review_moderation_events (
    id           UUID PRIMARY KEY,
    review_id    UUID NOT NULL REFERENCES hotel_reviews(id) ON DELETE CASCADE,
    from_status  VARCHAR(20) NOT NULL,
    to_status    VARCHAR(20) NOT NULL,
    reason       VARCHAR(500) NOT NULL,
    actor_id     UUID NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_hotel_review_mod_events_review ON hotel_review_moderation_events (review_id);
