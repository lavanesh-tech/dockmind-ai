package com.dockmind.dock.api;

import com.dockmind.common.security.TenantId;
import com.dockmind.dock.application.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/schedules")
@Tag(name = "Schedules")
@SecurityRequirement(name = OpenApiConfiguration.BEARER)
public class ScheduleController {

    private final ScheduleService service;

    public ScheduleController(ScheduleService service) {
        this.service = service;
    }

    @PostMapping("/{date}/compute")
    @Operation(summary = "Compute and save the schedule for a day (UTC)",
            description = "Requires DISPATCHER or ADMIN. Every computation is kept as history.")
    public ScheduleResponse compute(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                    @Valid @RequestBody ComputeScheduleRequest request) {
        return service.compute(TenantId.of(jwt), date, request.crewCapacity(), jwt.getSubject());
    }

    @GetMapping("/{date}")
    @Operation(summary = "Get the latest computed schedule for a day")
    public ScheduleResponse latest(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.latest(TenantId.of(jwt), date);
    }
}
