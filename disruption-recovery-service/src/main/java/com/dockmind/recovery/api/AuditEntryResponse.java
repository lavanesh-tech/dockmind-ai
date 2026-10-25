package com.dockmind.recovery.api;

import com.dockmind.recovery.domain.AuditEntry;

import java.time.Instant;

public record AuditEntryResponse(Instant at, String action, String actor, String detail) {

    public static AuditEntryResponse of(AuditEntry e) {
        return new AuditEntryResponse(e.getOccurredAt(), e.getAction(), e.getActor(), e.getDetail());
    }
}
