-- Shipments booked by a tenant. The CHECK and UNIQUE constraints repeat the entity's rules
-- so the database rejects bad data even if application code is bypassed.

CREATE TABLE shipment (
    id                UUID         PRIMARY KEY,
    tenant_id         VARCHAR(64)  NOT NULL,
    reference         VARCHAR(64)  NOT NULL,
    direction         VARCHAR(16)  NOT NULL,
    pallet_count      INTEGER      NOT NULL,
    priority          VARCHAR(16)  NOT NULL,
    window_start      TIMESTAMPTZ  NOT NULL,
    window_end        TIMESTAMPTZ  NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    actual_arrival_at TIMESTAMPTZ,
    version           BIGINT       NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_shipment_tenant_reference UNIQUE (tenant_id, reference),
    CONSTRAINT ck_shipment_direction CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT ck_shipment_priority  CHECK (priority IN ('STANDARD', 'EXPEDITED', 'CRITICAL')),
    CONSTRAINT ck_shipment_status    CHECK (status IN ('BOOKED', 'ARRIVED', 'CANCELLED')),
    CONSTRAINT ck_shipment_pallets   CHECK (pallet_count BETWEEN 1 AND 60),
    CONSTRAINT ck_shipment_window    CHECK (window_end > window_start)
);

-- Every query filters by tenant, and the scheduler reads shipments by window start.
CREATE INDEX ix_shipment_tenant_window_start ON shipment (tenant_id, window_start);

CREATE TABLE shipment_required_capability (
    shipment_id UUID        NOT NULL REFERENCES shipment (id) ON DELETE CASCADE,
    capability  VARCHAR(32) NOT NULL,
    PRIMARY KEY (shipment_id, capability),
    CONSTRAINT ck_capability CHECK (capability IN ('REFRIGERATED', 'HAZMAT', 'SIDE_LOAD', 'OVERSIZED'))
);
