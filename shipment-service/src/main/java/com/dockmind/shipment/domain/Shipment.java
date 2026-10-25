package com.dockmind.shipment.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A booked shipment: goods a truck will deliver or collect inside an arrival window.
 *
 * <p>Design notes:
 * <ul>
 *   <li><b>Invariants live in the entity.</b> You cannot construct a shipment with zero
 *       pallets or a window that ends before it starts, and illegal status changes throw.
 *       The database repeats the important rules as CHECK constraints, so bad data is
 *       rejected even if it bypasses this class.</li>
 *   <li><b>Every row has a tenant.</b> Queries must always filter by {@code tenantId}; see
 *       {@link ShipmentRepository}.</li>
 *   <li><b>Optimistic locking.</b> {@code @Version} makes concurrent updates safe: if two
 *       dispatchers edit the same shipment, the second save fails instead of silently
 *       overwriting the first.</li>
 *   <li>The ID is generated in Java, so a shipment has its identity before it is saved,
 *       which later helps idempotent event publishing.</li>
 * </ul>
 */
@Entity
@Table(name = "shipment")
public class Shipment {

    public static final int MAX_PALLETS = 60;
    public static final int MAX_REFERENCE_LENGTH = 64;

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, updatable = false, length = MAX_REFERENCE_LENGTH)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Direction direction;

    @Column(name = "pallet_count", nullable = false)
    private int palletCount;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "shipment_required_capability", joinColumns = @JoinColumn(name = "shipment_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "capability", nullable = false, length = 32)
    private Set<DockCapability> requiredCapabilities = EnumSet.noneOf(DockCapability.class);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Priority priority;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ShipmentStatus status;

    @Column(name = "actual_arrival_at")
    private Instant actualArrivalAt;

    /** Wrapper type: {@code null} tells Spring Data the entity is new, so it inserts without a select. */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA; not for application code. */
    protected Shipment() {
    }

    public Shipment(String tenantId, String reference, Direction direction, int palletCount,
                    Set<DockCapability> requiredCapabilities, Priority priority,
                    Instant windowStart, Instant windowEnd) {
        this.tenantId = requireText(tenantId, "tenantId", 64);
        this.reference = requireText(reference, "reference", MAX_REFERENCE_LENGTH);
        this.direction = Objects.requireNonNull(direction, "direction");
        if (palletCount < 1 || palletCount > MAX_PALLETS) {
            throw new IllegalArgumentException("palletCount must be between 1 and " + MAX_PALLETS);
        }
        this.palletCount = palletCount;
        this.requiredCapabilities = requiredCapabilities == null || requiredCapabilities.isEmpty()
                ? EnumSet.noneOf(DockCapability.class)
                : EnumSet.copyOf(requiredCapabilities);
        this.priority = Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(windowStart, "windowStart");
        Objects.requireNonNull(windowEnd, "windowEnd");
        if (!windowEnd.isAfter(windowStart)) {
            throw new IllegalArgumentException("windowEnd must be after windowStart");
        }
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.id = UUID.randomUUID();
        this.status = ShipmentStatus.BOOKED;
    }

    /** Records the truck's actual arrival. Only a booked shipment can arrive. */
    public void markArrived(Instant arrivedAt) {
        Objects.requireNonNull(arrivedAt, "arrivedAt");
        requireStatus(ShipmentStatus.BOOKED, "mark as arrived");
        this.actualArrivalAt = arrivedAt;
        this.status = ShipmentStatus.ARRIVED;
    }

    /** Cancels the booking. An arrived truck can no longer be cancelled. */
    public void cancel() {
        requireStatus(ShipmentStatus.BOOKED, "cancel");
        this.status = ShipmentStatus.CANCELLED;
    }

    /** A truck is late if it arrives after its window closes. */
    public boolean isLate(Instant arrivalTime) {
        return arrivalTime.isAfter(windowEnd);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    private void requireStatus(ShipmentStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException("Cannot " + action + " a shipment that is " + status);
        }
    }

    private static String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must be at most " + maxLength + " characters");
        }
        return value;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getReference() { return reference; }
    public Direction getDirection() { return direction; }
    public int getPalletCount() { return palletCount; }
    public Set<DockCapability> getRequiredCapabilities() { return Set.copyOf(requiredCapabilities); }
    public Priority getPriority() { return priority; }
    public Instant getWindowStart() { return windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
    public ShipmentStatus getStatus() { return status; }
    public Instant getActualArrivalAt() { return actualArrivalAt; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Entities are equal when they are the same row, identified by ID. */
    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof Shipment s && id != null && id.equals(s.id));
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
