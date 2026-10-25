package com.dockmind.recovery.api;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param strategy which plan to apply (approve only)
 * @param note     why; required when rejecting
 */
public record DecisionRequest(@Pattern(regexp = "^[A-Z][A-Z0-9_]{0,39}$") String strategy, @Size(max = 500) String note) {
}
