package com.dockmind.dock.api;

import com.dockmind.dock.domain.Dock;
import com.dockmind.scheduling.model.Capability;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record DockResponse(UUID id, String code, List<Capability> capabilities, LocalTime opensAt, LocalTime closesAt) {

    static DockResponse from(Dock d) {
        return new DockResponse(d.getId(), d.getCode(), d.getCapabilities().stream().sorted().toList(),
                d.getOpensAt(), d.getClosesAt());
    }
}
