package com.dockmind.shipment.security;

import com.dockmind.common.web.ApiException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.regex.Pattern;

/**
 * Reads the caller's tenant from the verified access token.
 *
 * <p><b>The tenant always comes from the token, never from the request</b> (body, path, query
 * or header). The token is signed by the identity provider, so a client can't change it;
 * anything in the request could be forged to read another company's data.
 */
public final class TenantId {

    public static final String CLAIM = "tenant_id";
    private static final Pattern VALID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");

    private TenantId() {
    }

    public static String of(Jwt jwt) {
        Object value = jwt.getClaims().get(CLAIM);
        if (value instanceof String tenant && VALID.matcher(tenant).matches()) {
            return tenant;
        }
        throw ApiException.forbidden("The access token has no valid tenant.");
    }
}
