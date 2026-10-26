package com.dockmind.ai.assistant;

import com.dockmind.ai.api.AskResponse;
import com.dockmind.ai.tools.OperationsTools;
import com.dockmind.ai.tools.ReadOnlyBackend;
import com.dockmind.common.web.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Answers one question. The model sees only the tools in {@link OperationsTools}; all of them
 * read. If the model asks for any other tool (for example after a prompt-injection attempt), the
 * request is refused and nothing is executed.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final ChatClient chat;
    private final ReadOnlyBackend backend;
    private final MeterRegistry meters;

    public AssistantService(ChatClient assistantChatClient, ReadOnlyBackend backend, MeterRegistry meters) {
        this.chat = assistantChatClient;
        this.backend = backend;
        this.meters = meters;
    }

    public AskResponse ask(String tenantId, String question, String token) {
        OperationsTools tools = new OperationsTools(backend, token);
        String answer;
        Timer.Sample sample = Timer.start(meters);
        String outcome = "answered";
        try {
            answer = chat.prompt()
                    .user(question)
                    .tools(tools)
                    .call()
                    .content();
        } catch (RuntimeException e) {
            outcome = requestedUnknownTool(e) ? "refused" : "unavailable";
            if (requestedUnknownTool(e)) {
                log.warn("Tenant {}: model requested a tool it does not have; refused. Tools used before: {}",
                        tenantId, tools.used());
                throw new ApiException(HttpStatus.valueOf(422), "ai-action-refused",
                        "The assistant tried to take an action it is not allowed to take. It can only read data; "
                                + "changes must be made by a dispatcher or admin.");
            }
            log.warn("Tenant {}: AI model call failed: {}", tenantId, e.getClass().getSimpleName());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ai-unavailable",
                    "The AI assistant is unavailable right now. Scheduling and recovery still work without it.");
        } finally {
            sample.stop(Timer.builder("dockmind.assistant.ask")
                    .description("Time to answer one assistant question, including tool calls")
                    .tag("outcome", outcome).register(meters));
        }
        tools.used().forEach(t -> meters.counter("dockmind.assistant.tool.calls", "tool", t).increment());
        log.info("Tenant {}: answered a question using tools {}", tenantId, tools.used());
        return new AskResponse(answer == null ? "" : answer, tools.used(),
                "AI-generated from DockMind data. Check the schedule before acting on it.");
    }

    private static boolean requestedUnknownTool(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("No ToolCallback found")) {
                return true;
            }
        }
        return false;
    }
}
