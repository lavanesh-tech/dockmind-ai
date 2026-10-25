package com.dockmind.common.security;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the real Keycloak with the committed realm file ({@code keycloak/realm-dockmind.json})
 * and checks that the tokens it issues work with DockMind's security code: the tenant claim,
 * the roles, and the issuer. Also checks the browser client can't be used for password logins
 * and that users can't change their own tenant.
 */
@Testcontainers
class KeycloakRealmTest {

    private static final String PASSWORD = "Pw-" + UUID.randomUUID();
    private static final String SMOKE_SECRET = UUID.randomUUID().toString();

    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.6.1")
            .withCommand("start-dev", "--import-realm")
            .withEnv("DEMO_USER_PASSWORD", PASSWORD)
            .withEnv("DOCKMIND_SMOKE_CLIENT_SECRET", SMOKE_SECRET)
            // 0644: Keycloak runs as a non-root user and must be able to read the file.
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("..", "keycloak", "realm-dockmind.json"), 0644),
                    "/opt/keycloak/data/import/realm-dockmind.json")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/dockmind").forPort(8080).withStartupTimeout(Duration.ofMinutes(3)));

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static String issuer;
    private static JwtDecoder decoder;

    @BeforeAll
    static void decoder() {
        issuer = "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080) + "/realms/dockmind";
        // Keys from the realm's JWKS endpoint; the issuer is checked on every token, as in the services.
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(issuer + "/protocol/openid-connect/certs").build();
        nimbus.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        decoder = nimbus;
    }

    private static HttpResponse<String> post(String path, Map<String, String> form, String bearer) throws Exception {
        String body = form.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(issuer + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (bearer != null) {
            b.header("Authorization", "Bearer " + bearer);
        }
        return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String accessToken(String username) throws Exception {
        HttpResponse<String> r = post("/protocol/openid-connect/token", Map.of("grant_type", "password",
                "client_id", "dockmind-smoke", "client_secret", SMOKE_SECRET, "username", username, "password", PASSWORD), null);
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        String marker = "\"access_token\":\"";
        int start = r.body().indexOf(marker) + marker.length();
        return r.body().substring(start, r.body().indexOf('"', start));
    }

    @Test
    void tokensCarryTenantAndRolesThatDockMindUnderstands() throws Exception {
        Jwt jwt = decoder.decode(accessToken("dispatcher.acme"));

        assertThat(jwt.getIssuer().toString()).isEqualTo(issuer);
        assertThat(TenantId.of(jwt)).isEqualTo("acme");
        assertThat(new DockMindJwtConverter().convert(jwt).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_DISPATCHER")
                .doesNotContain("ROLE_ADMIN");
    }

    @Test
    void eachDemoUserHasTheirOwnTenantAndRole() throws Exception {
        assertThat(TenantId.of(decoder.decode(accessToken("dispatcher.globex")))).isEqualTo("globex");
        assertThat(new DockMindJwtConverter().convert(decoder.decode(accessToken("admin.acme"))).getAuthorities())
                .extracting(GrantedAuthority::getAuthority).contains("ROLE_ADMIN");
        assertThat(new DockMindJwtConverter().convert(decoder.decode(accessToken("viewer.acme"))).getAuthorities())
                .extracting(GrantedAuthority::getAuthority).contains("ROLE_VIEWER").doesNotContain("ROLE_DISPATCHER");
    }

    @Test
    void theBrowserClientCannotBeUsedForPasswordLoginsAndWrongPasswordsFail() throws Exception {
        HttpResponse<String> web = post("/protocol/openid-connect/token", Map.of("grant_type", "password",
                "client_id", "dockmind-web", "username", "viewer.acme", "password", PASSWORD), null);
        assertThat(web.statusCode()).isIn(400, 401);
        assertThat(web.body()).contains("unauthorized_client");

        HttpResponse<String> wrong = post("/protocol/openid-connect/token", Map.of("grant_type", "password",
                "client_id", "dockmind-smoke", "client_secret", SMOKE_SECRET, "username", "viewer.acme",
                "password", "wrong"), null);
        assertThat(wrong.statusCode()).isIn(400, 401);
        assertThat(wrong.body()).contains("invalid_grant").doesNotContain("access_token");
    }

    @Test
    void usersCannotChangeTheirOwnTenant() throws Exception {
        String token = accessToken("viewer.acme");
        HttpResponse<String> attempt = HTTP.send(HttpRequest.newBuilder(URI.create(issuer + "/account/"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"username":"viewer.acme","email":"viewer.acme@example.test","firstName":"Vera","lastName":"Viewer",
                         "attributes":{"tenant_id":["globex"]}}"""))
                .build(), HttpResponse.BodyHandlers.ofString());

        assertThat(attempt.statusCode()).isGreaterThanOrEqualTo(400);
        assertThat(TenantId.of(decoder.decode(accessToken("viewer.acme")))).isEqualTo("acme");
    }
}
