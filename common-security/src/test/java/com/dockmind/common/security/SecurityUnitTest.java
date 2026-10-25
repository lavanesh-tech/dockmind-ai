package com.dockmind.common.security;

import com.dockmind.common.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fast checks of the token-to-identity rules, without starting the application. */
class SecurityUnitTest {

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "RS256").subject("user-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    void mapsFlatAndKeycloakRolesAndDropsUnknownOnes() {
        Jwt token = jwt(Map.of(
                "roles", List.of("VIEWER", "SUPERUSER"),
                "realm_access", Map.of("roles", List.of("DISPATCHER", "offline_access"))));

        List<String> authorities = new DockMindJwtConverter().convert(token).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();

        assertThat(authorities).containsExactly("ROLE_DISPATCHER", "ROLE_VIEWER");
    }

    @Test
    void principalNameIsTheSubject() {
        assertThat(new DockMindJwtConverter().convert(jwt(Map.of())).getName()).isEqualTo("user-1");
    }

    @Test
    void tenantComesFromTheTokenClaim() {
        assertThat(TenantId.of(jwt(Map.of("tenant_id", "acme-logistics")))).isEqualTo("acme-logistics");
    }

    @Test
    void missingOrMalformedTenantIsForbidden() {
        assertThatThrownBy(() -> TenantId.of(jwt(Map.of()))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> TenantId.of(jwt(Map.of("tenant_id", "Robert'); DROP TABLE"))))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> TenantId.of(jwt(Map.of("tenant_id", 42)))).isInstanceOf(ApiException.class);
    }

    @Test
    void problemJsonEscapesRequestValues() {
        assertThat(ProblemJsonSecurityHandlers.escape("/a\"b\\c\nd")).isEqualTo("/a\\\"b\\\\c\\u000ad");
    }
}
