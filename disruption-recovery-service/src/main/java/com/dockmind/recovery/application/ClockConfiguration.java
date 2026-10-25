package com.dockmind.recovery.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** One clock for the service, so tests can control time. */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }
}
