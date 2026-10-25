package com.dockmind.ai;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

/**
 * A stand-in for the real model: plays back scripted replies (tool calls or text) and records
 * what the real model would have been shown. No network, no API key, fully deterministic.
 */
public class ScriptedChatModel implements ChatModel {

    private final Deque<Function<Prompt, AssistantMessage>> script = new ArrayDeque<>();
    public final List<List<String>> offeredTools = new ArrayList<>();
    public final List<String> toolResults = new ArrayList<>();
    public volatile RuntimeException failWith;

    public void reset() {
        script.clear();
        offeredTools.clear();
        toolResults.clear();
        failWith = null;
    }

    public ScriptedChatModel thenCallTool(String name, String argumentsJson) {
        script.add(p -> AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-" + script.size(), "function", name, argumentsJson)))
                .build());
        return this;
    }

    public ScriptedChatModel thenAnswer(String text) {
        script.add(p -> new AssistantMessage(text));
        return this;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        if (failWith != null) {
            throw failWith;
        }
        if (prompt.getOptions() instanceof ToolCallingChatOptions options && options.getToolCallbacks() != null) {
            offeredTools.add(options.getToolCallbacks().stream().map(ToolCallback::getToolDefinition)
                    .map(d -> d.name()).sorted().toList());
        }
        for (Message m : prompt.getInstructions()) {
            if (m instanceof ToolResponseMessage r) {
                r.getResponses().forEach(x -> toolResults.add(x.name() + "=" + x.responseData()));
            }
        }
        Function<Prompt, AssistantMessage> next = script.poll();
        AssistantMessage reply = next == null ? new AssistantMessage("(no more scripted replies)") : next.apply(prompt);
        return new ChatResponse(List.of(new Generation(reply)));
    }

    @Override
    public ChatOptions getOptions() {
        return ToolCallingChatOptions.builder().build();
    }
}
