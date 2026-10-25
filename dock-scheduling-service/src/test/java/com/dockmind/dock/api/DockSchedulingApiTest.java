package com.dockmind.dock.api;

import com.dockmind.dock.Events;
import com.dockmind.dock.domain.ShipmentViewRepository;
import com.dockmind.dock.events.ShipmentEventListener;
import com.dockmind.testsupport.DockMindContainers;
import com.dockmind.testsupport.TestSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static com.dockmind.testsupport.TestTokens.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The full flow inside Dock Scheduling: register docks, receive shipments as events, compute a
 * schedule, read it back. Plus roles and tenant isolation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({DockMindContainers.class, TestSecurityConfiguration.class})
class DockSchedulingApiTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private ShipmentViewRepository views;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> call(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
        b = body == null ? b.method(method, HttpRequest.BodyPublishers.noBody())
                : b.method(method, HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void registerDock(String tenant, String code, String capabilities) throws Exception {
        HttpResponse<String> r = call("POST", "/api/v1/docks", token(tenant, "ADMIN"),
                "{\"code\":\"%s\",\"capabilities\":%s,\"opensAt\":\"06:00\",\"closesAt\":\"22:00\"}".formatted(code, capabilities));
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
    }

    private UUID publishBooked(String tenant, String reference, String caps, String priority, String start, String end)
            throws Exception {
        UUID id = UUID.randomUUID();
        kafka.send(ShipmentEventListener.TOPIC, id.toString(), Events.booked(tenant, id, reference, caps, priority, start, end)).get();
        return id;
    }

    @Test
    void computesAValidatedScheduleFromEventsAndDocks() throws Exception {
        String tenant = Events.newTenant();
        registerDock(tenant, "D01-REEFER", "[\"REFRIGERATED\"]");
        registerDock(tenant, "D02", "[]");
        UUID cold = publishBooked(tenant, "COLD-1", "[\"REFRIGERATED\"]", "CRITICAL", "2026-11-02T08:00:00Z", "2026-11-02T09:00:00Z");
        UUID dry1 = publishBooked(tenant, "DRY-1", "[]", "STANDARD", "2026-11-02T08:00:00Z", "2026-11-02T12:00:00Z");
        UUID dry2 = publishBooked(tenant, "DRY-2", "[]", "STANDARD", "2026-11-02T08:05:00Z", "2026-11-02T12:00:00Z");
        await().atMost(Duration.ofSeconds(30)).until(() ->
                views.findById(cold).isPresent() && views.findById(dry1).isPresent() && views.findById(dry2).isPresent());

        HttpResponse<String> computed = call("POST", "/api/v1/schedules/2026-11-02/compute", token(tenant, "DISPATCHER"),
                "{\"crewCapacity\":2}");

        assertThat(computed.statusCode()).as(computed.body()).isEqualTo(200);
        assertThat(computed.body()).contains("\"algorithm\":\"DOCKMIND_DISPATCH\"", "\"scheduled\":3", "\"unscheduled\":0");
        assertThat(computed.body()).contains("\"reference\":\"COLD-1\",\"dockCode\":\"D01-REEFER\",\"start\":\"2026-11-02T08:00:00Z\"");

        HttpResponse<String> latest = call("GET", "/api/v1/schedules/2026-11-02", token(tenant, "VIEWER"), null);
        assertThat(latest.statusCode()).isEqualTo(200);
        assertThat(latest.body()).contains("COLD-1", "DRY-1", "DRY-2");
    }

    @Test
    void anotherTenantSeesNeitherDocksNorSchedules() throws Exception {
        String owner = Events.newTenant();
        String other = Events.newTenant();
        registerDock(owner, "D01", "[]");
        assertThat(call("POST", "/api/v1/schedules/2026-11-05/compute", token(owner, "DISPATCHER"), "{\"crewCapacity\":3}")
                .statusCode()).isEqualTo(200);

        assertThat(call("GET", "/api/v1/docks", token(other, "VIEWER"), null).body()).isEqualTo("[]");
        assertThat(call("GET", "/api/v1/schedules/2026-11-05", token(other, "VIEWER"), null).statusCode()).isEqualTo(404);
    }

    @Test
    void onlyAdminsRegisterDocksAndViewersCannotCompute() throws Exception {
        String tenant = Events.newTenant();
        String dock = "{\"code\":\"D09\",\"capabilities\":[],\"opensAt\":\"06:00\",\"closesAt\":\"22:00\"}";

        assertThat(call("POST", "/api/v1/docks", token(tenant, "DISPATCHER"), dock).statusCode()).isEqualTo(403);
        assertThat(call("POST", "/api/v1/schedules/2026-11-02/compute", token(tenant, "VIEWER"), "{\"crewCapacity\":2}")
                .statusCode()).isEqualTo(403);
    }

    @Test
    void validationAndConflicts() throws Exception {
        String tenant = Events.newTenant();
        registerDock(tenant, "D01", "[]");

        assertThat(call("POST", "/api/v1/docks", token(tenant, "ADMIN"),
                "{\"code\":\"D01\",\"capabilities\":[],\"opensAt\":\"06:00\",\"closesAt\":\"22:00\"}").statusCode()).isEqualTo(409);
        assertThat(call("POST", "/api/v1/docks", token(tenant, "ADMIN"),
                "{\"code\":\"D02\",\"capabilities\":[],\"opensAt\":\"22:00\",\"closesAt\":\"06:00\"}").statusCode()).isEqualTo(400);
        assertThat(call("POST", "/api/v1/schedules/2026-11-02/compute", token(tenant, "DISPATCHER"), "{\"crewCapacity\":0}")
                .statusCode()).isEqualTo(400);
        assertThat(call("POST", "/api/v1/schedules/not-a-date/compute", token(tenant, "DISPATCHER"), "{\"crewCapacity\":2}")
                .statusCode()).isEqualTo(400);
    }

    @Test
    void computingWithoutDocksIsABadRequest() throws Exception {
        assertThat(call("POST", "/api/v1/schedules/2026-11-02/compute", token(Events.newTenant(), "DISPATCHER"),
                "{\"crewCapacity\":2}").statusCode()).isEqualTo(400);
    }
}
