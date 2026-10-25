package com.dockmind.dock.domain;

import com.dockmind.scheduling.model.Capability;
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
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A loading dock belonging to one tenant. Opening hours are daily, in UTC (time zones per
 * warehouse are a documented later improvement).
 */
@Entity
@Table(name = "dock")
public class Dock {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @Column(nullable = false, updatable = false, length = 32)
    private String code;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "dock_capability", joinColumns = @JoinColumn(name = "dock_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "capability", nullable = false, length = 32)
    private Set<Capability> capabilities = EnumSet.noneOf(Capability.class);

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Dock() {
    }

    public Dock(String tenantId, String code, Set<Capability> capabilities, LocalTime opensAt, LocalTime closesAt) {
        this.id = UUID.randomUUID();
        this.tenantId = Objects.requireNonNull(tenantId);
        this.code = Objects.requireNonNull(code);
        this.capabilities = capabilities == null || capabilities.isEmpty()
                ? EnumSet.noneOf(Capability.class) : EnumSet.copyOf(capabilities);
        this.opensAt = Objects.requireNonNull(opensAt);
        this.closesAt = Objects.requireNonNull(closesAt);
        if (!closesAt.isAfter(opensAt)) {
            throw new IllegalArgumentException("closesAt must be after opensAt");
        }
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public Set<Capability> getCapabilities() { return Set.copyOf(capabilities); }
    public LocalTime getOpensAt() { return opensAt; }
    public LocalTime getClosesAt() { return closesAt; }
    public Long getVersion() { return version; }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Dock d && id != null && id.equals(d.id));
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
