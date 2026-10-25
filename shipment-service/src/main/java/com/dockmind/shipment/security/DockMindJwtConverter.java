package com.dockmind.shipment.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Turns a validated JWT into a Spring Security authentication with DockMind roles.
 *
 * <p>Roles are read from a top-level {@code roles} claim, and also from Keycloak's standard
 * {@code realm_access.roles} claim, so the service works with Keycloak and with providers that
 * emit a flat list. Unknown role names are dropped: a token can't invent new permissions.
 */
public class DockMindJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        TreeSet<String> roles = new TreeSet<>();
        roles.addAll(stringList(jwt.getClaims().get("roles")));
        if (jwt.getClaims().get("realm_access") instanceof Map<?, ?> realmAccess) {
            roles.addAll(stringList(realmAccess.get("roles")));
        }
        List<GrantedAuthority> authorities = roles.stream()
                .filter(Roles.KNOWN::contains)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private static List<String> stringList(Object claim) {
        if (claim instanceof Collection<?> values) {
            return values.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        return List.of();
    }
}
