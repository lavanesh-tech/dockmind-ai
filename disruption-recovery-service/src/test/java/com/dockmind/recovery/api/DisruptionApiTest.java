package com.dockmind.recovery.api;

import com.dockmind.recovery.FakeDockScheduling;
import com.dockmind.recovery.TestcontainersConfiguration;
import com.dockmind.recovery.domain.DisruptionStatus;
import com.dockmind.testsupport.TestSecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.dockmind.testsupport.TestTokens.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The whole recovery workflow against a real database and a fake Dock Scheduling on a real
 * port: report, compare, approve or reject, audit; plus roles, tenants, stale plans and outages.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
class DisruptionApiTest {

    static final FakeDockScheduling DOCK = new FakeDockScheduling();

    @DynamicPropertySource
    static void dockUrl(DynamicPropertyRegistry registry) {
        registry.add("dockmind.recovery.dock-scheduling-url", DOCK::url);
        registry.add("dockmind.recovery.read-timeout", () -> "2s");
    }

    /** Two trucks, two docks; each truck booked at 08:00. */
    static final String TWO_TRUCKS = """
            {"date":"2026-11-02","baseRunId":"b0000000-0000-0000-0000-000000000001","crewCapacity":2,
             "docks":[{"code":"D1","capabilities":[],"opensAt":"2026-11-02T06:00:00Z","closesAt":"2026-11-02T22:00:00Z"},
                      {"code":"D2","capabilities":[],"opensAt":"2026-11-02T06:00:00Z","closesAt":"2026-11-02T22:00:00Z"}],
             "jobs":[{"shipmentId":"a0000000-0000-0000-0000-00000000000a","reference":"A","requiredCapabilities":[],
                      "priority":"STANDARD","availableFrom":"2026-11-02T08:00:00Z","loadingSeconds":1800,"latestStart":"2026-11-02T12:00:00Z"},
                     {"shipmentId":"a0000000-0000-0000-0000-00000000000b","reference":"B","requiredCapabilities":[],
                      "priority":"STANDARD","availableFrom":"2026-11-02T08:00:00Z","loadingSeconds":1800,"latestStart":"2026-11-02T12:00:00Z"}],
             "appointments":[{"shipmentId":"a0000000-0000-0000-0000-00000000000a","reference":"A","dockCode":"D1",
                              "start":"2026-11-02T08:00:00Z","end":"2026-11-02T08:30:00Z"},
                             {"shipmentId":"a0000000-0000-0000-0000-00000000000b","reference":"B","dockCode":"D2",
                              "start":"2026-11-02T08:00:00Z","end":"2026-11-02T08:30:00Z"}]}
            """;

    /** Four trucks all booked on D1 in the afternoon; D2 is free. An outage on D1 moves all four. */
    static final String FOUR_ON_D1 = """
            {"date":"2026-11-02","baseRunId":"b0000000-0000-0000-0000-000000000002","crewCapacity":2,
             "docks":[{"code":"D1","capabilities":[],"opensAt":"2026-11-02T06:00:00Z","closesAt":"2026-11-02T22:00:00Z"},
                      {"code":"D2","capabilities":[],"opensAt":"2026-11-02T06:00:00Z","closesAt":"2026-11-02T22:00:00Z"}],
             "jobs":[%s],
             "appointments":[%s]}
            """.formatted(
            String.join(",", List.of(job(1, "12:00"), job(2, "12:30"), job(3, "13:00"), job(4, "13:30"))),
            String.join(",", List.of(appt(1, "12:00", "12:30"), appt(2, "12:30", "13:00"), appt(3, "13:00", "13:30"),
                    appt(4, "13:30", "14:00"))));

    private static String job(int n, String at) {
        return """
                {"shipmentId":"c0000000-0000-0000-0000-00000000000%d","reference":"J%d","requiredCapabilities":[],
                 "priority":"STANDARD","availableFrom":"2026-11-02T%s:00Z","loadingSeconds":1800,"latestStart":null}
                """.formatted(n, n, at);
    }

    private static String appt(int n, String start, String end) {
        return """
                {"shipmentId":"c0000000-0000-0000-0000-00000000000%d","reference":"J%d","dockCode":"D1",
                 "start":"2026-11-02T%s:00Z","end":"2026-11-02T%s:00Z"}
                """.formatted(n, n, start, end);
    }

    static final String LATE_A = """
            {"type":"LATE_ARRIVAL","scheduleDate":"2026-11-02","shipmentId":"a0000000-0000-0000-0000-00000000000a",
             "newArrival":"2026-11-02T09:00:00Z","decisionTime":"2026-11-02T07:00:00Z"}
            """;

    static final String D1_OUTAGE = """
            {"type":"DOCK_OUTAGE","scheduleDate":"2026-11-02","dockCode":"D1",
             "outageStart":"2026-11-02T11:00:00Z","outageEnd":"2026-11-02T16:00:00Z","decisionTime":"2026-11-02T10:00:00Z"}
            """;

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void reset() {
        DOCK.snapshot.set(TWO_TRUCKS);
        DOCK.snapshotStatus.set(200);
        DOCK.applyStatus.set(200);
        DOCK.lastApplyBody.set(null);
    }

    private HttpResponse<String> call(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json");
        b = body == null ? b.method(method, HttpRequest.BodyPublishers.noBody())
                : b.method(method, HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private DisruptionResponse report(String tenant, String role, String body) throws Exception {
        HttpResponse<String> r = call("POST", "/api/v1/disruptions", token(tenant, role), body);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        return json.readValue(r.body(), DisruptionResponse.class);
    }

    private List<String> auditActions(String tenant, UUID id) throws Exception {
        HttpResponse<String> r = call("GET", "/api/v1/disruptions/" + id + "/audit", token(tenant, "VIEWER"), null);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        return Arrays.stream(json.readValue(r.body(), AuditEntryResponse[].class)).map(AuditEntryResponse::action).toList();
    }

    private static String tenant() {
        return "t-" + UUID.randomUUID();
    }

    @Test
    void lateArrivalProducesRankedValidPlansWithoutTouchingTheSchedule() throws Exception {
        String tenant = tenant();
        String dispatcherToken = token(tenant, "DISPATCHER");
        HttpResponse<String> r = call("POST", "/api/v1/disruptions", dispatcherToken, LATE_A);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        DisruptionResponse d = json.readValue(r.body(), DisruptionResponse.class);

        assertThat(d.status()).isEqualTo(DisruptionStatus.PENDING_APPROVAL);
        assertThat(d.plans()).isNotEmpty();
        assertThat(d.plans().getFirst().recommended()).isTrue();
        assertThat(d.plans().getFirst().rank()).isEqualTo(1);
        assertThat(d.plans().getFirst().unscheduled()).isZero();
        assertThat(d.plans()).extracting(DisruptionResponse.PlanSummary::strategy)
                .containsExactlyInAnyOrder("MINIMAL_CHANGE", "FULL_RESCHEDULE");
        // The user's own token was relayed, and nothing was applied yet.
        assertThat(DOCK.lastAuthorization.get()).isEqualTo("Bearer " + dispatcherToken);
        assertThat(DOCK.lastApplyBody.get()).isNull();
        assertThat(auditActions(tenant, d.id())).containsExactly("REPORTED", "PLANS_GENERATED");
    }

    @Test
    void approvingAppliesThePlanThroughDockSchedulingAndIsAudited() throws Exception {
        String tenant = tenant();
        DisruptionResponse d = report(tenant, "DISPATCHER", LATE_A);
        String strategy = d.plans().getFirst().strategy();

        HttpResponse<String> r = call("POST", "/api/v1/disruptions/" + d.id() + "/approve", token(tenant, "DISPATCHER"),
                "{\"strategy\":\"" + strategy + "\",\"note\":\"driver called ahead\"}");
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        DisruptionResponse approved = json.readValue(r.body(), DisruptionResponse.class);

        assertThat(approved.status()).isEqualTo(DisruptionStatus.APPLIED);
        assertThat(approved.appliedStrategy()).isEqualTo(strategy);
        assertThat(approved.appliedRunId()).isEqualTo(UUID.fromString(DOCK.appliedRunId.get()));
        // Dock Scheduling received the full plan, tied to the schedule version it was made from,
        // plus the late arrival it relies on.
        assertThat(DOCK.lastApplyBody.get())
                .contains("b0000000-0000-0000-0000-000000000001")
                .contains("\"arrivalOverrides\"")
                .contains("2026-11-02T09:00:00Z");
        assertThat(auditActions(tenant, d.id())).containsExactly("REPORTED", "PLANS_GENERATED", "APPROVED", "APPLIED");

        HttpResponse<String> again = call("POST", "/api/v1/disruptions/" + d.id() + "/approve", token(tenant, "ADMIN"),
                "{\"strategy\":\"" + strategy + "\"}");
        assertThat(again.statusCode()).isEqualTo(409);
    }

    @Test
    void rejectingKeepsTheScheduleAndNeedsANote() throws Exception {
        String tenant = tenant();
        DisruptionResponse d = report(tenant, "DISPATCHER", LATE_A);

        assertThat(call("POST", "/api/v1/disruptions/" + d.id() + "/reject", token(tenant, "DISPATCHER"), "{}")
                .statusCode()).isEqualTo(400);
        HttpResponse<String> r = call("POST", "/api/v1/disruptions/" + d.id() + "/reject", token(tenant, "DISPATCHER"),
                "{\"note\":\"truck cancelled instead\"}");
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        assertThat(json.readValue(r.body(), DisruptionResponse.class).status()).isEqualTo(DisruptionStatus.REJECTED);
        assertThat(DOCK.lastApplyBody.get()).isNull();
        assertThat(auditActions(tenant, d.id())).containsExactly("REPORTED", "PLANS_GENERATED", "REJECTED");
    }

    @Test
    void largePlansNeedAnAdmin() throws Exception {
        DOCK.snapshot.set(FOUR_ON_D1);
        String tenant = tenant();
        DisruptionResponse d = report(tenant, "DISPATCHER", D1_OUTAGE);
        DisruptionResponse.PlanSummary best = d.plans().getFirst();
        assertThat(best.moved()).isEqualTo(4);
        assertThat(best.requiresAdmin()).isTrue();
        String body = "{\"strategy\":\"" + best.strategy() + "\"}";

        assertThat(call("POST", "/api/v1/disruptions/" + d.id() + "/approve", token(tenant, "DISPATCHER"), body)
                .statusCode()).isEqualTo(403);
        assertThat(DOCK.lastApplyBody.get()).isNull();

        HttpResponse<String> r = call("POST", "/api/v1/disruptions/" + d.id() + "/approve", token(tenant, "ADMIN"), body);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        assertThat(DOCK.lastApplyBody.get()).contains("\"extraBlocked\"").contains("2026-11-02T11:00:00Z");
        assertThat(auditActions(tenant, d.id()))
                .containsExactly("REPORTED", "PLANS_GENERATED", "APPROVAL_DENIED", "APPROVED", "APPLIED");
    }

    @Test
    void aPlanForAScheduleThatChangedFailsSafely() throws Exception {
        String tenant = tenant();
        DisruptionResponse d = report(tenant, "DISPATCHER", LATE_A);
        DOCK.applyStatus.set(409);

        HttpResponse<String> r = call("POST", "/api/v1/disruptions/" + d.id() + "/approve", token(tenant, "DISPATCHER"),
                "{\"strategy\":\"" + d.plans().getFirst().strategy() + "\"}");
        assertThat(r.statusCode()).isEqualTo(409);
        assertThat(r.body()).doesNotContain("Conflict\"}");

        HttpResponse<String> now = call("GET", "/api/v1/disruptions/" + d.id(), token(tenant, "VIEWER"), null);
        assertThat(json.readValue(now.body(), DisruptionResponse.class).status()).isEqualTo(DisruptionStatus.FAILED);
        assertThat(auditActions(tenant, d.id())).containsExactly("REPORTED", "PLANS_GENERATED", "APPLY_FAILED");
    }

    @Test
    void aFailingDockServiceGivesA503AndNothingIsSaved() throws Exception {
        DOCK.snapshotStatus.set(500);
        String tenant = tenant();
        HttpResponse<String> r = call("POST", "/api/v1/disruptions", token(tenant, "DISPATCHER"), LATE_A);
        assertThat(r.statusCode()).isEqualTo(503);
        assertThat(r.headers().firstValue("Content-Type")).hasValueSatisfying(v -> assertThat(v).contains("problem+json"));

        HttpResponse<String> list = call("GET", "/api/v1/disruptions", token(tenant, "VIEWER"), null);
        assertThat(list.body()).isEqualTo("[]");
    }

    @Test
    void invalidReportsAreRejected() throws Exception {
        String tenant = tenant();
        String dispatcher = token(tenant, "DISPATCHER");
        assertThat(call("POST", "/api/v1/disruptions", dispatcher,
                "{\"type\":\"LATE_ARRIVAL\",\"scheduleDate\":\"2026-11-02\"}").statusCode()).isEqualTo(400);
        assertThat(call("POST", "/api/v1/disruptions", dispatcher, LATE_A.replace("a0000000-0000-0000-0000-00000000000a",
                UUID.randomUUID().toString())).statusCode()).isEqualTo(400);
        assertThat(call("POST", "/api/v1/disruptions", dispatcher, D1_OUTAGE.replace("\"D1\"", "\"D9\"")).statusCode())
                .isEqualTo(400);
    }

    @Test
    void rolesAndTenantsAreEnforced() throws Exception {
        String tenant = tenant();
        assertThat(call("POST", "/api/v1/disruptions", token(tenant, "VIEWER"), LATE_A).statusCode()).isEqualTo(403);

        DisruptionResponse d = report(tenant, "DISPATCHER", LATE_A);
        String other = tenant();
        assertThat(call("GET", "/api/v1/disruptions/" + d.id(), token(other, "ADMIN"), null).statusCode()).isEqualTo(404);
        assertThat(call("POST", "/api/v1/disruptions/" + d.id() + "/approve", token(other, "ADMIN"),
                "{\"strategy\":\"MINIMAL_CHANGE\"}").statusCode()).isEqualTo(404);
        assertThat(call("GET", "/api/v1/disruptions", token(other, "ADMIN"), null).body()).isEqualTo("[]");
        assertThat(call("GET", "/api/v1/disruptions/" + d.id(), "not-a-token", null).statusCode()).isEqualTo(401);
    }

    @Test
    void theAuditTrailCannotBeChangedEvenWithDirectDatabaseAccess() throws Exception {
        String tenant = tenant();
        DisruptionResponse d = report(tenant, "DISPATCHER", LATE_A);

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_entry SET detail = 'rewritten' WHERE disruption_id = ?", d.id()))
                .hasStackTraceContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_entry WHERE disruption_id = ?", d.id()))
                .hasStackTraceContaining("append-only");
        assertThat(auditActions(tenant, d.id())).containsExactly("REPORTED", "PLANS_GENERATED");
    }
}
