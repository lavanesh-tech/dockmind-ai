package com.dockmind.recovery;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * @param dockSchedulingUrl            where Dock Scheduling runs
 * @param connectTimeout               time allowed to open a connection to it
 * @param readTimeout                  time allowed for it to answer
 * @param adminApprovalMovedThreshold  plans moving more appointments than this need an ADMIN
 */
@ConfigurationProperties("dockmind.recovery")
public record RecoveryProperties(URI dockSchedulingUrl, Duration connectTimeout, Duration readTimeout,
                                 int adminApprovalMovedThreshold) {
}
