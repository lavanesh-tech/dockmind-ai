package com.dockmind.gateway;

import com.dockmind.testsupport.TestTokens;
import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.testcontainers.utility.DockerImageName;

/** Real Redis for the rate limiter, and a reactive decoder that trusts the test signing key. */
@TestConfiguration(proxyBeanMethods = false)
class GatewayTestConfiguration {

    @Bean
    @ServiceConnection
    RedisContainer redis() {
        return new RedisContainer(DockerImageName.parse("redis:7.4-alpine"));
    }

    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder() {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(TestTokens.publicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(TestTokens.ISSUER));
        return decoder;
    }
}
