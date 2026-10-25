package com.dockmind.dock.api;

import com.dockmind.common.security.TenantId;
import com.dockmind.dock.application.DockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/docks")
@Tag(name = "Docks")
@SecurityRequirement(name = OpenApiConfiguration.BEARER)
public class DockController {

    private final DockService service;

    public DockController(DockService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Register a dock", description = "Requires ADMIN.")
    public ResponseEntity<DockResponse> register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterDockRequest request) {
        var dock = service.register(TenantId.of(jwt), request.code(), request.capabilities(), request.opensAt(), request.closesAt());
        return ResponseEntity.created(URI.create("/api/v1/docks/" + dock.getId())).body(DockResponse.from(dock));
    }

    @GetMapping
    @Operation(summary = "List your docks")
    public List<DockResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(TenantId.of(jwt)).stream().map(DockResponse::from).toList();
    }
}
