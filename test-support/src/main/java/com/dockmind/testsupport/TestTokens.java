package com.dockmind.testsupport;

import com.dockmind.common.security.TenantId;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Mints real RS256-signed JWTs for tests, the same kind an identity provider issues.
 * The service verifies them with the matching public key, so tests exercise the real
 * signature, expiry and issuer checks rather than a mock that skips them.
 */
public final class TestTokens {

    public static final String ISSUER = "https://idp.test/realms/dockmind";

    private static final KeyPair KEYS = newKeyPair();
    private static final KeyPair ATTACKER_KEYS = newKeyPair();

    private TestTokens() {
    }

    public static RSAPublicKey publicKey() {
        return (RSAPublicKey) KEYS.getPublic();
    }

    public static String token(String tenant, String... roles) {
        return sign(KEYS, claims(tenant, ISSUER, Instant.now().plus(Duration.ofMinutes(5)), roles));
    }

    public static String expiredToken(String tenant, String... roles) {
        return sign(KEYS, claims(tenant, ISSUER, Instant.now().minus(Duration.ofMinutes(10)), roles));
    }

    public static String tokenFromWrongIssuer(String tenant, String... roles) {
        return sign(KEYS, claims(tenant, "https://evil.example/realms/dockmind",
                Instant.now().plus(Duration.ofMinutes(5)), roles));
    }

    /** Correct claims, but signed with a key the service doesn't trust. */
    public static String forgedToken(String tenant, String... roles) {
        return sign(ATTACKER_KEYS, claims(tenant, ISSUER, Instant.now().plus(Duration.ofMinutes(5)), roles));
    }

    public static String tokenWithoutTenant(String... roles) {
        return sign(KEYS, claims(null, ISSUER, Instant.now().plus(Duration.ofMinutes(5)), roles));
    }

    /** Keycloak puts roles under realm_access.roles rather than a top-level claim. */
    public static String keycloakStyleToken(String tenant, String... roles) {
        JWTClaimsSet base = claims(tenant, ISSUER, Instant.now().plus(Duration.ofMinutes(5)));
        JWTClaimsSet withRealmRoles = new JWTClaimsSet.Builder(base)
                .claim("realm_access", java.util.Map.of("roles", List.of(roles)))
                .build();
        return sign(KEYS, withRealmRoles);
    }

    private static JWTClaimsSet claims(String tenant, String issuer, Instant expiry, String... roles) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject("user-" + UUID.randomUUID())
                .issueTime(Date.from(expiry.minus(Duration.ofMinutes(15))))
                .expirationTime(Date.from(expiry))
                .jwtID(UUID.randomUUID().toString());
        if (roles.length > 0) {
            builder.claim("roles", List.of(roles));
        }
        if (tenant != null) {
            builder.claim(TenantId.CLAIM, tenant);
        }
        return builder.build();
    }

    private static String sign(KeyPair keys, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(keys.getPrivate()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
