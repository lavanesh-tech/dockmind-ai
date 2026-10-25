package com.dockmind.recovery.domain;

/** PENDING_APPROVAL → APPLIED, REJECTED or FAILED. The last three are final. */
public enum DisruptionStatus {
    PENDING_APPROVAL, APPLIED, REJECTED, FAILED
}
