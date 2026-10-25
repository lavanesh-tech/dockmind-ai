package com.dockmind.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/** Typed gateway settings from {@code dockmind.gateway.*} in application.yml. */
@ConfigurationProperties(prefix = "dockmind.gateway")
public record GatewayProperties(URI shipmentUri, URI dockUri, Duration timeout, RateLimit rateLimit) {

    public record RateLimit(int replenishRate, int burstCapacity) {
    }
}
