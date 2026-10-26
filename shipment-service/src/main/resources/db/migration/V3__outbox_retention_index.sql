-- Cleanup deletes published rows by age; a partial index over just those rows keeps it cheap.
CREATE INDEX ix_outbox_published_at ON outbox_event (published_at) WHERE published_at IS NOT NULL;
