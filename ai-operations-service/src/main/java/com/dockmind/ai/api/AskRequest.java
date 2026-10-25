package com.dockmind.ai.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A question for the assistant. Kept short to bound cost and prompt size. */
public record AskRequest(@NotBlank @Size(max = 1000) String question) {
}
