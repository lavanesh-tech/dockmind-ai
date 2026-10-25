package com.dockmind.ai.api;

import java.util.List;

/**
 * @param answer     the assistant's answer
 * @param toolsUsed  which read-only lookups it made, in order (transparency for the user)
 * @param notice     a reminder that the answer is AI-generated
 */
public record AskResponse(String answer, List<String> toolsUsed, String notice) {
}
