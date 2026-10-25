package com.dockmind.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("dockmind.ai")
public record AiProperties(URI dockSchedulingUrl, URI disruptionRecoveryUrl, Duration connectTimeout,
                           Duration readTimeout, int maxToolResultChars) {
}
