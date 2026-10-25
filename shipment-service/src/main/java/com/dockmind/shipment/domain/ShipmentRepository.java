package com.dockmind.shipment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for shipments. Spring Data generates the implementation from the method names.
 *
 * <p><b>Tenant rule:</b> application code must use the tenant-scoped methods below. The
 * inherited {@code findById} ignores the tenant and is only for internal infrastructure;
 * from Milestone 3 the tenant comes from the verified JWT, never from the request body.
 */
public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    Optional<Shipment> findByIdAndTenantId(UUID id, String tenantId);

    boolean existsByTenantIdAndReference(String tenantId, String reference);

    /** Shipments whose window starts in [from, to), earliest first: the input to the scheduler. */
    List<Shipment> findByTenantIdAndWindowStartGreaterThanEqualAndWindowStartLessThanOrderByWindowStartAsc(
            String tenantId, Instant from, Instant to);
}
