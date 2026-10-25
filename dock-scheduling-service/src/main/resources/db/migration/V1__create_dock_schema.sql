-- Dock Scheduling owns these tables. No other service reads or writes them.

CREATE TABLE dock (
    id         UUID         PRIMARY KEY,
    tenant_id  VARCHAR(64)  NOT NULL,
    code       VARCHAR(32)  NOT NULL,
    opens_at   TIME         NOT NULL,
    closes_at  TIME         NOT NULL,
    version    BIGINT       NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_dock_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_dock_hours CHECK (closes_at > opens_at)
);

CREATE TABLE dock_capability (
    dock_id    UUID        NOT NULL REFERENCES dock (id) ON DELETE CASCADE,
    capability VARCHAR(32) NOT NULL,
    PRIMARY KEY (dock_id, capability),
    CONSTRAINT ck_dock_capability CHECK (capability IN ('REFRIGERATED', 'HAZMAT', 'SIDE_LOAD', 'OVERSIZED'))
);

-- A read model: this service's own copy of the shipment facts it needs, built from events.
CREATE TABLE shipment_view (
    shipment_id           UUID         PRIMARY KEY,
    tenant_id             VARCHAR(64)  NOT NULL,
    reference             VARCHAR(64)  NOT NULL,
    pallet_count          INTEGER      NOT NULL,
    required_capabilities VARCHAR(200) NOT NULL,
    priority              VARCHAR(16)  NOT NULL,
    window_start          TIMESTAMPTZ  NOT NULL,
    window_end            TIMESTAMPTZ  NOT NULL,
    status                VARCHAR(16)  NOT NULL,
    actual_arrival_at     TIMESTAMPTZ,
    last_event_id         UUID         NOT NULL,
    last_event_at         TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_shipment_view_tenant_window ON shipment_view (tenant_id, window_start);

-- Idempotent consumer: one row per event ever applied. A redelivered event finds its row and is skipped.
CREATE TABLE processed_event (
    event_id     UUID        PRIMARY KEY,
    event_type   VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);

-- Every computed schedule is kept: the history is the audit trail of scheduling decisions.
CREATE TABLE schedule_run (
    id                UUID             PRIMARY KEY,
    tenant_id         VARCHAR(64)      NOT NULL,
    schedule_date     DATE             NOT NULL,
    algorithm         VARCHAR(32)      NOT NULL,
    crew_capacity     INTEGER          NOT NULL,
    computed_at       TIMESTAMPTZ      NOT NULL,
    computed_by       VARCHAR(128)     NOT NULL,
    scheduled_count   INTEGER          NOT NULL,
    unscheduled_count INTEGER          NOT NULL,
    avg_wait_minutes  DOUBLE PRECISION NOT NULL
);
CREATE INDEX ix_schedule_run_lookup ON schedule_run (tenant_id, schedule_date, computed_at DESC);

CREATE TABLE schedule_appointment (
    run_id      UUID        NOT NULL REFERENCES schedule_run (id) ON DELETE CASCADE,
    shipment_id UUID        NOT NULL,
    reference   VARCHAR(64) NOT NULL,
    dock_code   VARCHAR(32) NOT NULL,
    starts_at   TIMESTAMPTZ NOT NULL,
    ends_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_schedule_appointment_run ON schedule_appointment (run_id);

CREATE TABLE schedule_unscheduled (
    run_id      UUID        NOT NULL REFERENCES schedule_run (id) ON DELETE CASCADE,
    shipment_id UUID        NOT NULL,
    reference   VARCHAR(64) NOT NULL,
    reason      VARCHAR(32) NOT NULL
);
CREATE INDEX ix_schedule_unscheduled_run ON schedule_unscheduled (run_id);
