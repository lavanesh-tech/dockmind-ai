package com.dockmind.shipment.application;

import com.dockmind.common.web.ApiException;
import com.dockmind.common.web.ResourceNotFoundException;
import com.dockmind.shipment.api.CreateShipmentRequest;
import com.dockmind.shipment.domain.Shipment;
import com.dockmind.shipment.domain.ShipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Shipment use cases. Every method takes the tenant explicitly, as its first parameter, so it
 * is impossible to call one without deciding whose data it touches.
 *
 * <p>{@code @Transactional} means each public method runs in one database transaction: either
 * everything it changes is saved, or, if it throws, nothing is.
 */
@Service
@Transactional
public class ShipmentService {

    /** Largest window a list request may cover, to keep responses bounded. */
    public static final Duration MAX_LIST_RANGE = Duration.ofDays(7);

    private final ShipmentRepository repository;

    public ShipmentService(ShipmentRepository repository) {
        this.repository = repository;
    }

    public Shipment book(String tenantId, CreateShipmentRequest request) {
        if (repository.existsByTenantIdAndReference(tenantId, request.reference())) {
            throw ApiException.conflict("A shipment with reference " + request.reference() + " already exists.");
        }
        Shipment shipment;
        try {
            shipment = new Shipment(tenantId, request.reference(), request.direction(), request.palletCount(),
                    request.requiredCapabilities(), request.priority(), request.windowStart(), request.windowEnd());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage()); // entity rules; messages are written to be safe
        }
        return repository.saveAndFlush(shipment);
    }

    @Transactional(readOnly = true)
    public Shipment get(String tenantId, UUID id) {
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", id));
    }

    @Transactional(readOnly = true)
    public List<Shipment> listByWindowStart(String tenantId, Instant from, Instant to) {
        if (!to.isAfter(from)) {
            throw ApiException.badRequest("'to' must be after 'from'.");
        }
        if (Duration.between(from, to).compareTo(MAX_LIST_RANGE) > 0) {
            throw ApiException.badRequest("The range may be at most " + MAX_LIST_RANGE.toDays() + " days.");
        }
        return repository.findByTenantIdAndWindowStartGreaterThanEqualAndWindowStartLessThanOrderByWindowStartAsc(
                tenantId, from, to);
    }

    public Shipment recordArrival(String tenantId, UUID id, Instant arrivedAt) {
        Shipment shipment = get(tenantId, id);
        try {
            shipment.markArrived(arrivedAt);
        } catch (IllegalStateException e) {
            throw ApiException.conflict(e.getMessage());
        }
        return repository.saveAndFlush(shipment); // flush now so the response shows the new version
    }

    public Shipment cancel(String tenantId, UUID id) {
        Shipment shipment = get(tenantId, id);
        try {
            shipment.cancel();
        } catch (IllegalStateException e) {
            throw ApiException.conflict(e.getMessage());
        }
        return repository.saveAndFlush(shipment);
    }
}
