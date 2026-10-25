package com.dockmind.shipment.api;

import com.dockmind.shipment.TestcontainersConfiguration;
import com.dockmind.shipment.domain.ShipmentRepository;
import com.dockmind.shipment.security.TestSecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.net.http.HttpResponse;

import static com.dockmind.shipment.security.TestTokens.expiredToken;
import static com.dockmind.shipment.security.TestTokens.forgedToken;
import static com.dockmind.shipment.security.TestTokens.keycloakStyleToken;
import static com.dockmind.shipment.security.TestTokens.token;
import static com.dockmind.shipment.security.TestTokens.tokenFromWrongIssuer;
import static com.dockmind.shipment.security.TestTokens.tokenWithoutTenant;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end API tests: real HTTP, real signed JWTs, real PostgreSQL. Each nested class covers
 * one concern: authentication, roles, tenant isolation, validation, lifecycle and the contract.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
class ShipmentApiTest {

    private static final String A = "tenant-a";
    private static final String B = "tenant-b";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private ShipmentRepository repository;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(port);
        repository.deleteAll();
    }

    private static String body(String reference) {
        return body(reference, 12, "2026-11-02T08:00:00Z", "2026-11-02T10:00:00Z");
    }

    private static String body(String reference, int pallets, String start, String end) {
        return """
                {"reference":"%s","direction":"INBOUND","palletCount":%d,
                 "requiredCapabilities":["REFRIGERATED"],"priority":"EXPEDITED",
                 "windowStart":"%s","windowEnd":"%s"}""".formatted(reference, pallets, start, end);
    }

    private String book(String tenant, String reference) {
        HttpResponse<String> response = api.post("/api/v1/shipments", token(tenant, "DISPATCHER"), body(reference));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return ApiClient.idOf(response);
    }

    @Nested
    class Authentication {

        @Test
        void requestWithoutTokenIsRejectedWithProblemJson() {
            HttpResponse<String> response = api.get("/api/v1/shipments/00000000-0000-0000-0000-000000000000", null);

            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.headers().firstValue("WWW-Authenticate")).hasValue("Bearer");
            assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                    t -> assertThat(t).startsWith("application/problem+json"));
            assertThat(response.body()).contains("\"status\":401", "\"correlationId\":");
        }

        @Test
        void expiredTokenIsRejected() {
            assertThat(api.get("/api/v1/shipments/00000000-0000-0000-0000-000000000000",
                    expiredToken(A, "VIEWER")).statusCode()).isEqualTo(401);
        }

        @Test
        void tokenFromAnotherIssuerIsRejected() {
            assertThat(api.get("/api/v1/shipments/00000000-0000-0000-0000-000000000000",
                    tokenFromWrongIssuer(A, "ADMIN")).statusCode()).isEqualTo(401);
        }

        @Test
        void tokenSignedWithAnUntrustedKeyIsRejected() {
            assertThat(api.post("/api/v1/shipments", forgedToken(A, "ADMIN"), body("SO-1")).statusCode())
                    .isEqualTo(401);
            assertThat(repository.count()).isZero();
        }

        @Test
        void rejectionDoesNotExplainWhyTheTokenFailed() {
            String detail = api.get("/api/v1/shipments/00000000-0000-0000-0000-000000000000",
                    expiredToken(A, "VIEWER")).body();

            assertThat(detail).doesNotContainIgnoringCase("expired").doesNotContainIgnoringCase("signature");
        }
    }

    @Nested
    class Roles {

        @Test
        void viewerCanReadButNotBook() {
            String id = book(A, "SO-1");

            assertThat(api.get("/api/v1/shipments/" + id, token(A, "VIEWER")).statusCode()).isEqualTo(200);
            HttpResponse<String> denied = api.post("/api/v1/shipments", token(A, "VIEWER"), body("SO-2"));
            assertThat(denied.statusCode()).isEqualTo(403);
            assertThat(denied.body()).contains("\"status\":403");
        }

        @Test
        void viewerCannotCancel() {
            String id = book(A, "SO-1");

            assertThat(api.post("/api/v1/shipments/" + id + "/cancel", token(A, "VIEWER"), "").statusCode())
                    .isEqualTo(403);
        }

        @Test
        void adminCanBook() {
            assertThat(api.post("/api/v1/shipments", token(A, "ADMIN"), body("SO-1")).statusCode()).isEqualTo(201);
        }

        @Test
        void keycloakRealmRolesAreRecognised() {
            assertThat(api.post("/api/v1/shipments", keycloakStyleToken(A, "DISPATCHER"), body("SO-1")).statusCode())
                    .isEqualTo(201);
        }

        @Test
        void unknownRolesGrantNothing() {
            assertThat(api.get("/api/v1/shipments/00000000-0000-0000-0000-000000000000",
                    token(A, "SUPERUSER")).statusCode()).isEqualTo(403);
        }

        @Test
        void tokenWithoutTenantIsForbidden() {
            HttpResponse<String> response = api.post("/api/v1/shipments", tokenWithoutTenant("DISPATCHER"), body("SO-1"));

            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(repository.count()).isZero();
        }
    }

    @Nested
    class TenantIsolation {

        @Test
        void anotherTenantGets404NotTheShipment() {
            String id = book(A, "SO-1");

            HttpResponse<String> response = api.get("/api/v1/shipments/" + id, token(B, "ADMIN"));

            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.body()).doesNotContain("SO-1");
        }

        @Test
        void anotherTenantCannotCancelOrRecordArrival() {
            String id = book(A, "SO-1");

            assertThat(api.post("/api/v1/shipments/" + id + "/cancel", token(B, "ADMIN"), "").statusCode())
                    .isEqualTo(404);
            assertThat(api.post("/api/v1/shipments/" + id + "/arrival", token(B, "ADMIN"),
                    "{\"arrivedAt\":\"2026-11-02T08:10:00Z\"}").statusCode()).isEqualTo(404);
            assertThat(api.get("/api/v1/shipments/" + id, token(A, "VIEWER")).body()).contains("\"status\":\"BOOKED\"");
        }

        @Test
        void listShowsOnlyTheCallersShipments() {
            book(A, "A-1");
            book(B, "B-1");

            String list = api.get("/api/v1/shipments?from=2026-11-02T00:00:00Z&to=2026-11-03T00:00:00Z",
                    token(A, "VIEWER")).body();

            assertThat(list).contains("A-1").doesNotContain("B-1");
        }

        @Test
        void sameReferenceIsAllowedInDifferentTenants() {
            book(A, "SO-1");
            book(B, "SO-1");

            assertThat(repository.count()).isEqualTo(2);
        }

        @Test
        void tenantInTheBodyIsIgnored() {
            String sneaky = body("SO-1").replace("{", "{\"tenantId\":\"tenant-b\",");

            HttpResponse<String> response = api.post("/api/v1/shipments", token(A, "DISPATCHER"), sneaky);

            assertThat(response.statusCode()).isEqualTo(201);
            assertThat(repository.findAll()).allSatisfy(s -> assertThat(s.getTenantId()).isEqualTo(A));
        }
    }

    @Nested
    class Validation {

        @Test
        void invalidFieldsAreAllReported() {
            HttpResponse<String> response = api.post("/api/v1/shipments", token(A, "DISPATCHER"),
                    body("bad ref!", 0, "2026-11-02T10:00:00Z", "2026-11-02T08:00:00Z"));

            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).contains("\"reference\"", "\"palletCount\"", "\"windowEndAfterStart\"");
        }

        @Test
        void malformedJsonIsABadRequest() {
            assertThat(api.post("/api/v1/shipments", token(A, "DISPATCHER"), "{not json").statusCode()).isEqualTo(400);
        }

        @Test
        void unknownEnumValueIsABadRequest() {
            assertThat(api.post("/api/v1/shipments", token(A, "DISPATCHER"),
                    body("SO-1").replace("INBOUND", "SIDEWAYS")).statusCode()).isEqualTo(400);
        }

        @Test
        void invalidIdIsABadRequest() {
            assertThat(api.get("/api/v1/shipments/not-a-uuid", token(A, "VIEWER")).statusCode()).isEqualTo(400);
        }

        @Test
        void listNeedsABoundedRange() {
            assertThat(api.get("/api/v1/shipments?from=2026-11-02T00:00:00Z", token(A, "VIEWER")).statusCode())
                    .isEqualTo(400);
            assertThat(api.get("/api/v1/shipments?from=2026-11-01T00:00:00Z&to=2026-11-30T00:00:00Z",
                    token(A, "VIEWER")).statusCode()).isEqualTo(400);
        }

        @Test
        void duplicateReferenceIsAConflict() {
            book(A, "SO-1");

            HttpResponse<String> response = api.post("/api/v1/shipments", token(A, "DISPATCHER"), body("SO-1"));

            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(response.body()).contains("urn:dockmind:problem:conflict");
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void bookReturnsCreatedWithLocationAndBody() {
            HttpResponse<String> response = api.post("/api/v1/shipments", token(A, "DISPATCHER"), body("SO-1"));

            assertThat(response.statusCode()).isEqualTo(201);
            String id = ApiClient.idOf(response);
            assertThat(response.headers().firstValue("Location")).hasValue("/api/v1/shipments/" + id);
            assertThat(response.body()).contains("\"status\":\"BOOKED\"", "\"requiredCapabilities\":[\"REFRIGERATED\"]",
                    "\"windowStart\":\"2026-11-02T08:00:00Z\"", "\"version\":0");
        }

        @Test
        void lateArrivalIsFlagged() {
            String id = book(A, "SO-1");

            HttpResponse<String> response = api.post("/api/v1/shipments/" + id + "/arrival", token(A, "DISPATCHER"),
                    "{\"arrivedAt\":\"2026-11-02T10:30:00Z\"}");

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"ARRIVED\"", "\"late\":true", "\"version\":1");
        }

        @Test
        void arrivedShipmentCannotBeCancelled() {
            String id = book(A, "SO-1");
            api.post("/api/v1/shipments/" + id + "/arrival", token(A, "DISPATCHER"), "{\"arrivedAt\":\"2026-11-02T08:30:00Z\"}");

            HttpResponse<String> response = api.post("/api/v1/shipments/" + id + "/cancel", token(A, "DISPATCHER"), "");

            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(response.body()).contains("ARRIVED");
        }
    }

    @Nested
    class Contract {

        @Test
        void openApiDocumentDescribesTheApiAndItsSecurity() {
            HttpResponse<String> response = api.get("/v3/api-docs", null);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains(
                    "\"/api/v1/shipments\"", "\"/api/v1/shipments/{id}\"", "\"/api/v1/shipments/{id}/arrival\"",
                    "\"/api/v1/shipments/{id}/cancel\"", "\"bearerAuth\"", "\"CreateShipmentRequest\"");
            assertThat(response.body()).doesNotContain("tenantId");
        }

        @Test
        void errorsAlwaysUseProblemJson() {
            HttpResponse<String> response = api.get("/api/v1/shipments/00000000-0000-0000-0000-000000000000",
                    token(A, "VIEWER"));

            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                    t -> assertThat(t).startsWith("application/problem+json"));
            assertThat(response.body()).contains("\"type\":\"urn:dockmind:problem:not-found\"", "\"correlationId\":");
        }
    }
}
