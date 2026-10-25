package com.dockmind.gateway;

import com.dockmind.testsupport.TestTokens;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway with real HTTP, real Redis and real signed tokens. The backends are small JDK
 * HTTP servers: one healthy (and with a slow endpoint), and one address where nothing listens.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GatewayTestConfiguration.class)
class GatewayTest {

    private static final FakeBackend SHIPMENTS;

    static {
        try {
            SHIPMENTS = new FakeBackend();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) {
        registry.add("dockmind.gateway.shipment-uri", SHIPMENTS::uri);
        registry.add("dockmind.gateway.dock-uri", () -> "http://127.0.0.1:1"); // nothing listens: always down
        registry.add("dockmind.gateway.recovery-uri", SHIPMENTS::uri);
        registry.add("dockmind.gateway.ai-uri", SHIPMENTS::uri);
        registry.add("dockmind.gateway.timeout", () -> "1s");
        registry.add("dockmind.gateway.long-timeout", () -> "5s");
        registry.add("dockmind.gateway.rate-limit.replenish-rate", () -> "1");
        registry.add("dockmind.gateway.rate-limit.burst-capacity", () -> "3");
    }

    @AfterAll
    static void stopBackend() {
        SHIPMENTS.close();
    }

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private HttpResponse<String> get(String path, String token, String correlationId) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(15)).GET();
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        if (correlationId != null) {
            b.header("X-Correlation-Id", correlationId);
        }
        try {
            return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String newTenant() {
        return "t-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void routesToTheBackendForwardingTokenAndCorrelationId() {
        HttpResponse<String> response = get("/api/v1/shipments/abc", TestTokens.token(newTenant(), "VIEWER"), "gw-corr-1");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"path\":\"/api/v1/shipments/abc\"", "\"authForwarded\":true",
                "\"correlationId\":\"gw-corr-1\"");
        assertThat(response.headers().firstValue("X-Correlation-Id")).hasValue("gw-corr-1");
    }

    @Test
    void routesDisruptionAndAssistantPathsToTheirServices() {
        String token = TestTokens.token(newTenant(), "VIEWER");

        assertThat(get("/api/v1/disruptions", token, null).body()).contains("\"path\":\"/api/v1/disruptions\"");
        assertThat(get("/api/v1/assistant/ask", token, null).body()).contains("\"path\":\"/api/v1/assistant/ask\"");
    }

    @Test
    void createsACorrelationIdWhenTheCallerSendsNoneOrAnInvalidOne() {
        HttpResponse<String> response = get("/api/v1/shipments", TestTokens.token(newTenant(), "VIEWER"), "bad id with spaces");

        String id = response.headers().firstValue("X-Correlation-Id").orElseThrow();
        assertThat(id).isNotEqualTo("bad id with spaces").matches("^[A-Za-z0-9._-]{1,64}$");
        assertThat(response.body()).contains("\"correlationId\":\"" + id + "\"");
    }

    @Test
    void rejectsMissingExpiredAndForgedTokensAtTheEdge() {
        String tenant = newTenant();
        HttpResponse<String> missing = get("/api/v1/shipments", null, null);

        assertThat(missing.statusCode()).isEqualTo(401);
        assertThat(missing.headers().firstValue("Content-Type")).hasValueSatisfying(
                t -> assertThat(t).startsWith("application/problem+json"));
        assertThat(get("/api/v1/shipments", TestTokens.expiredToken(tenant, "VIEWER"), null).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/shipments", TestTokens.forgedToken(tenant, "ADMIN"), null).statusCode()).isEqualTo(401);
    }

    @Test
    void unknownPathsAreDenied() {
        assertThat(get("/internal/admin", TestTokens.token(newTenant(), "ADMIN"), null).statusCode()).isIn(401, 403, 404);
    }

    @Test
    void rateLimitIsPerTenant() {
        String busy = newTenant();
        String token = TestTokens.token(busy, "VIEWER");
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            statuses.add(get("/api/v1/shipments", token, null).statusCode());
        }

        assertThat(statuses).contains(200).contains(429);
        // A different tenant still gets through while the busy one is throttled.
        assertThat(get("/api/v1/shipments", TestTokens.token(newTenant(), "VIEWER"), null).statusCode()).isEqualTo(200);
    }

    @Test
    void slowBackendGetsAFast503InsteadOfHanging() {
        long start = System.nanoTime();
        HttpResponse<String> response = get("/api/v1/shipments/slow", TestTokens.token(newTenant(), "VIEWER"), "gw-slow");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(elapsedMs).isLessThan(4_000); // timeout is 1 s; the backend would take 5 s
        assertThat(response.body()).contains("urn:dockmind:problem:service-unavailable", "\"correlationId\":\"gw-slow\"");
    }

    @Test
    void unreachableBackendGetsA503ProblemResponse() {
        HttpResponse<String> response = get("/api/v1/docks", TestTokens.token(newTenant(), "VIEWER"), null);

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                t -> assertThat(t).startsWith("application/problem+json"));
        assertThat(response.body()).contains("dock-scheduling-service");
    }

    @Test
    void healthIsPublic() {
        assertThat(get("/actuator/health", null, null).statusCode()).isEqualTo(200);
    }
}
