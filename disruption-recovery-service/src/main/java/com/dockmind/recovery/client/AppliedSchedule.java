package com.dockmind.recovery.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** The part of Dock Scheduling's schedule response this service needs. Other fields are ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppliedSchedule(UUID runId) {
}
