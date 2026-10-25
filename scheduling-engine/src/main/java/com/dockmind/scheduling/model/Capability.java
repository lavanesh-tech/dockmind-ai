package com.dockmind.scheduling.model;

/** Equipment a dock has, or a job needs. A dock can serve a job only if it has every required capability. */
public enum Capability {
    REFRIGERATED,
    HAZMAT,
    SIDE_LOAD,
    OVERSIZED
}
