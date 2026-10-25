package com.dockmind.recovery.api;

import com.dockmind.common.security.TenantId;
import com.dockmind.common.web.ApiException;
import com.dockmind.recovery.application.RecoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/disruptions")
@Tag(name = "Disruptions", description = "Report disruptions, compare recovery plans, approve or reject them")
public class DisruptionController {

    private final RecoveryService service;

    public DisruptionController(RecoveryService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Report a late truck or a dock outage and get validated recovery plans",
            description = "Nothing changes in the schedule until a plan is approved. Requires DISPATCHER or ADMIN.")
    public DisruptionResponse report(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReportDisruptionRequest request) {
        return service.report(TenantId.of(jwt), jwt.getSubject(), jwt.getTokenValue(), request);
    }

    @GetMapping
    @Operation(summary = "The 50 most recent disruptions")
    public List<DisruptionResponse> recent(@AuthenticationPrincipal Jwt jwt) {
        return service.recent(TenantId.of(jwt));
    }

    @GetMapping("/{id}")
    public DisruptionResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.get(TenantId.of(jwt), id);
    }

    @GetMapping("/{id}/audit")
    @Operation(summary = "The append-only audit trail for a disruption")
    public List<AuditEntryResponse> audit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.auditTrail(TenantId.of(jwt), id).stream().map(AuditEntryResponse::of).toList();
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Apply one plan to the schedule",
            description = "Plans moving more than the threshold need ADMIN. 409 if the schedule changed since the plans were made.")
    public DisruptionResponse approve(@AuthenticationPrincipal Jwt jwt, Authentication authentication, @PathVariable UUID id,
                                      @Valid @RequestBody DecisionRequest request) {
        if (request.strategy() == null) {
            throw ApiException.badRequest("strategy is required.");
        }
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return service.approve(TenantId.of(jwt), id, request.strategy(), request.note(), jwt.getSubject(), isAdmin,
                jwt.getTokenValue());
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject all plans; the schedule stays as it is")
    public DisruptionResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                     @Valid @RequestBody DecisionRequest request) {
        if (request.note() == null || request.note().isBlank()) {
            throw ApiException.badRequest("A note explaining the rejection is required.");
        }
        return service.reject(TenantId.of(jwt), id, request.note(), jwt.getSubject());
    }
}
