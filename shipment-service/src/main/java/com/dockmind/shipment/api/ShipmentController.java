package com.dockmind.shipment.api;

import com.dockmind.shipment.application.ShipmentService;
import com.dockmind.shipment.domain.Shipment;
import com.dockmind.shipment.security.TenantId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * HTTP layer for shipments. It only translates between HTTP and the service: no business
 * rules live here. Role checks are in {@code SecurityConfiguration}; the tenant comes from the token.
 *
 * <p>State changes are commands ({@code POST .../arrival}, {@code POST .../cancel}) rather than
 * a generic {@code PATCH status}. That keeps the allowed transitions explicit and easy to secure.
 */
@RestController
@RequestMapping("/api/v1/shipments")
@Tag(name = "Shipments")
@SecurityRequirement(name = OpenApiConfiguration.BEARER)
public class ShipmentController {

    private final ShipmentService service;

    public ShipmentController(ShipmentService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Book a shipment", description = "Requires DISPATCHER or ADMIN.")
    public ResponseEntity<ShipmentResponse> book(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody CreateShipmentRequest request) {
        Shipment shipment = service.book(TenantId.of(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/shipments/" + shipment.getId()))
                .body(ShipmentResponse.from(shipment));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one shipment", description = "Returns 404 for unknown IDs and for other tenants' shipments.")
    public ShipmentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ShipmentResponse.from(service.get(TenantId.of(jwt), id));
    }

    @GetMapping
    @Operation(summary = "List shipments whose window starts in [from, to)", description = "At most 7 days.")
    public List<ShipmentResponse> list(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return service.listByWindowStart(TenantId.of(jwt), from, to).stream().map(ShipmentResponse::from).toList();
    }

    @PostMapping("/{id}/arrival")
    @Operation(summary = "Record that the truck arrived", description = "Requires DISPATCHER or ADMIN. 409 if not BOOKED.")
    public ShipmentResponse recordArrival(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                          @Valid @RequestBody RecordArrivalRequest request) {
        return ShipmentResponse.from(service.recordArrival(TenantId.of(jwt), id, request.arrivedAt()));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a booking", description = "Requires DISPATCHER or ADMIN. 409 if not BOOKED.")
    public ShipmentResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ShipmentResponse.from(service.cancel(TenantId.of(jwt), id));
    }
}
