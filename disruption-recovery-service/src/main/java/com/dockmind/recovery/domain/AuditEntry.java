package com.dockmind.recovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * One line of the audit trail. {@code @Immutable} stops Hibernate from ever issuing an UPDATE,
 * and a database trigger rejects UPDATE, DELETE and TRUNCATE from anyone.
 */
@Entity
@Immutable
@Table(name = "audit_entry")
public class AuditEntry implements Persistable<UUID> {

    @Id
    private UUID id;

    /** Insertion order, assigned by the database; orders entries written in the same instant. */
    @Column(insertable = false, updatable = false)
    private Long seq;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "disruption_id", nullable = false)
    private UUID disruptionId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private String detail;

    protected AuditEntry() {
    }

    public AuditEntry(String tenantId, UUID disruptionId, String action, String actor, Instant occurredAt, String detail) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.disruptionId = disruptionId;
        this.action = action;
        this.actor = actor;
        this.occurredAt = occurredAt;
        this.detail = detail.length() > 1000 ? detail.substring(0, 1000) : detail;
    }

    @Override
    public UUID getId() { return id; }

    /** Audit entries are only ever inserted, so tell Spring Data to INSERT without checking first. */
    @Override
    public boolean isNew() {
        return true;
    }

    public String getTenantId() { return tenantId; }
    public UUID getDisruptionId() { return disruptionId; }
    public String getAction() { return action; }
    public String getActor() { return actor; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getDetail() { return detail; }
}
