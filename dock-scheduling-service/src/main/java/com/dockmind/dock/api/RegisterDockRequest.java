package com.dockmind.dock.api;

import com.dockmind.scheduling.model.Capability;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalTime;
import java.util.Set;

/** Body of {@code POST /api/v1/docks}. Opening hours are daily, in UTC. */
public record RegisterDockRequest(
        @Schema(example = "D01-REEFER")
        @NotNull @Pattern(regexp = "^[A-Z0-9][A-Z0-9-]{0,31}$", message = "use 1-32 capital letters, digits or -")
        String code,

        Set<Capability> capabilities,

        @Schema(type = "string", example = "06:00")
        @NotNull LocalTime opensAt,

        @Schema(type = "string", example = "22:00")
        @NotNull LocalTime closesAt) {

    @AssertTrue(message = "closesAt must be after opensAt")
    @Schema(hidden = true)
    public boolean isClosesAfterOpens() {
        return opensAt == null || closesAt == null || closesAt.isAfter(opensAt);
    }
}
