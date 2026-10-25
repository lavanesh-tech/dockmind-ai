package com.dockmind.dock.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ShipmentViewRepository extends JpaRepository<ShipmentView, UUID> {

    /** Shipments of one tenant whose window starts in [from, to) and that still need a dock. */
    List<ShipmentView> findByTenantIdAndStatusInAndWindowStartGreaterThanEqualAndWindowStartLessThan(
            String tenantId, Collection<String> statuses, Instant from, Instant to);
}
