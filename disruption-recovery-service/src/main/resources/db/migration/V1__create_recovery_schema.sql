-- Disruption Recovery owns this database. No other service reads or writes it.

CREATE TABLE disruption (
    id              UUID PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL,
    schedule_date   DATE         NOT NULL,
    type            VARCHAR(20)  NOT NULL CHECK (type IN ('LATE_ARRIVAL', 'DOCK_OUTAGE')),
    shipment_id     UUID,
    new_arrival     TIMESTAMPTZ,
    dock_code       VARCHAR(40),
    outage_start    TIMESTAMPTZ,
    outage_end      TIMESTAMPTZ,
    decision_time   TIMESTAMPTZ  NOT NULL,
    base_run_id     UUID         NOT NULL,
    status          VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'APPLIED', 'REJECTED', 'FAILED')),
    reported_by     VARCHAR(255) NOT NULL,
    reported_at     TIMESTAMPTZ  NOT NULL,
    decided_by      VARCHAR(255),
    decided_at      TIMESTAMPTZ,
    decision_note   VARCHAR(500),
    applied_strategy VARCHAR(40),
    applied_run_id  UUID,
    version         BIGINT       NOT NULL,
    CONSTRAINT disruption_details CHECK (
        (type = 'LATE_ARRIVAL' AND shipment_id IS NOT NULL AND new_arrival IS NOT NULL)
        OR (type = 'DOCK_OUTAGE' AND dock_code IS NOT NULL AND outage_start IS NOT NULL AND outage_end > outage_start))
);
CREATE INDEX idx_disruption_tenant_reported ON disruption (tenant_id, reported_at DESC);

CREATE TABLE recovery_plan (
    id                  UUID PRIMARY KEY,
    disruption_id       UUID             NOT NULL REFERENCES disruption (id),
    strategy            VARCHAR(40)      NOT NULL,
    plan_rank           INT              NOT NULL CHECK (plan_rank >= 1),
    moved               INT              NOT NULL CHECK (moved >= 0),
    scheduled           INT              NOT NULL CHECK (scheduled >= 0),
    unscheduled         INT              NOT NULL CHECK (unscheduled >= 0),
    urgent_unscheduled  INT              NOT NULL CHECK (urgent_unscheduled >= 0),
    avg_wait_minutes    DOUBLE PRECISION NOT NULL,
    apply_request       TEXT             NOT NULL,
    CONSTRAINT uq_plan_strategy UNIQUE (disruption_id, strategy)
);

-- Who did what, when. Append-only: the database itself refuses to change or remove history,
-- so neither a bug nor someone with application credentials can rewrite it.
CREATE TABLE audit_entry (
    id             UUID PRIMARY KEY,
    seq            BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    tenant_id      VARCHAR(64)   NOT NULL,
    disruption_id  UUID          NOT NULL REFERENCES disruption (id),
    action         VARCHAR(40)   NOT NULL,
    actor          VARCHAR(255)  NOT NULL,
    occurred_at    TIMESTAMPTZ   NOT NULL,
    detail         VARCHAR(1000) NOT NULL
);
CREATE INDEX idx_audit_disruption ON audit_entry (disruption_id, seq);

CREATE FUNCTION audit_entry_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_entry is append-only: % is not allowed', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_entry_no_update_or_delete
    BEFORE UPDATE OR DELETE ON audit_entry
    FOR EACH ROW EXECUTE FUNCTION audit_entry_is_append_only();

CREATE TRIGGER audit_entry_no_truncate
    BEFORE TRUNCATE ON audit_entry
    FOR EACH STATEMENT EXECUTE FUNCTION audit_entry_is_append_only();
