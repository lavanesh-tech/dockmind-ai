package com.dockmind.recovery.domain;

import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.UUID;

/** Insert and read only: there is deliberately no update or delete method. */
public interface AuditEntryRepository extends Repository<AuditEntry, UUID> {

    AuditEntry save(AuditEntry entry);

    List<AuditEntry> findByTenantIdAndDisruptionIdOrderBySeqAsc(String tenantId, UUID disruptionId);
}
