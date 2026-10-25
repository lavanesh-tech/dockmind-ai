-- Transactional outbox: events are written here in the SAME transaction as the business change,
-- then a relay publishes them to Kafka. An event is never lost (the row commits with the change)
-- and never sent for a change that rolled back (the row rolls back too).

CREATE TABLE outbox_event (
    id             UUID          PRIMARY KEY,   -- also the event ID consumers deduplicate on
    tenant_id      VARCHAR(64)   NOT NULL,
    aggregate_id   UUID          NOT NULL,      -- the shipment; used as the Kafka key for per-shipment ordering
    event_type     VARCHAR(64)   NOT NULL,
    event_version  INTEGER       NOT NULL,
    envelope       TEXT          NOT NULL,      -- the exact JSON that will be published
    correlation_id VARCHAR(64),
    occurred_at    TIMESTAMPTZ   NOT NULL,
    published_at   TIMESTAMPTZ,
    attempts       INTEGER       NOT NULL DEFAULT 0,
    last_error     VARCHAR(500)
);

-- The relay only ever looks at unpublished rows, oldest first; a partial index keeps that cheap.
CREATE INDEX ix_outbox_unpublished ON outbox_event (occurred_at, id) WHERE published_at IS NULL;
