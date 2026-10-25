package com.dockmind.ai.api;

import com.dockmind.ai.assistant.AssistantService;
import com.dockmind.common.security.TenantId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "Assistant", description = "Ask questions about schedules and disruptions (read-only)")
public class AssistantController {

    private final AssistantService service;

    public AssistantController(AssistantService service) {
        this.service = service;
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask the operations assistant a question",
            description = "Any role. The assistant can only read data the caller is allowed to read; it cannot change anything.")
    public AskResponse ask(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AskRequest request) {
        return service.ask(TenantId.of(jwt), request.question(), jwt.getTokenValue());
    }
}
