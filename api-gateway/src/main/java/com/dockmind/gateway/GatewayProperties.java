package com.dockmind.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Typed gateway settings from {@code dockmind.gateway.*} in application.yml.
 *
 * @param timeout     time limit for ordinary calls
 * @param longTimeout time limit for routes that legitimately take longer: recovery planning and AI answers
 */
@ConfigurationProperties(prefix = "dockmind.gateway")
public record GatewayProperties(URI shipmentUri, URI dockUri, URI recoveryUri, URI aiUri, Duration timeout,
                                Duration longTimeout, RateLimit rateLimit) {

    public record RateLimit(int replenishRate, int burstCapacity) {
    }
}
