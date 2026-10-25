package com.dockmind.shipment;

import com.dockmind.common.web.CorrelationId;
import com.dockmind.testsupport.TestSecurityConfiguration;
import com.dockmind.testsupport.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the real application on a random port and calls it over HTTP, the same way a
 * load balancer or another service would. This proves the wiring works end to end:
 * embedded Tomcat, actuator, and the shared correlation ID filter.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
class ShipmentServiceApplicationTest {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${local.server.port}")
    private int port;

    @Test
    void healthEndpointReportsUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void livenessAndReadinessProbesAreAvailable() throws Exception {
        assertThat(get("/actuator/health/liveness", null).statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health/readiness", null).statusCode()).isEqualTo(200);
    }

    @Test
    void sensitiveActuatorEndpointsAreNotExposed() throws Exception {
        // Not exposed over HTTP at all, and also behind authentication: either way, never readable.
        assertThat(get("/actuator/env", null).statusCode()).isIn(401, 404);
        assertThat(get("/actuator/beans", null).statusCode()).isIn(401, 404);
    }

    @Test
    void unknownUrlReturnsProblemDetail404() throws Exception {
        HttpResponse<String> response = get("/api/v1/does-not-exist", "probe-404",
                TestTokens.token("tenant-a", "VIEWER"));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.body()).contains("\"correlationId\":\"probe-404\"");
    }

    @Test
    void echoesIncomingCorrelationId() throws Exception {
        HttpResponse<String> response = get("/actuator/health", "trace-from-gateway-1");

        assertThat(response.headers().firstValue(CorrelationId.HEADER)).hasValue("trace-from-gateway-1");
    }

    @Test
    void generatesCorrelationIdWhenNoneIsSent() throws Exception {
        HttpResponse<String> response = get("/actuator/health", null);

        assertThat(response.headers().firstValue(CorrelationId.HEADER))
                .hasValueSatisfying(id -> assertThat(CorrelationId.isValid(id)).isTrue());
    }

    private HttpResponse<String> get(String path, String correlationId) throws IOException, InterruptedException {
        return get(path, correlationId, null);
    }

    private HttpResponse<String> get(String path, String correlationId, String bearerToken)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .GET();
        if (correlationId != null) {
            builder.header(CorrelationId.HEADER, correlationId);
        }
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
