package com.dockmind.ai.tools;

import com.dockmind.ai.AiProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.http.HttpClient;

/**
 * The only way the assistant reaches other services. It can send <b>GET</b> requests and
 * nothing else: there is no method for POST, PUT, PATCH or DELETE, so no tool, prompt or model
 * output can change a schedule. The user's own token is relayed, so the other services also
 * enforce that user's tenant and role.
 */
@Component
public class ReadOnlyBackend {

    public enum Service { DOCK_SCHEDULING, DISRUPTION_RECOVERY }

    private final RestClient dock;
    private final RestClient recovery;
    private final int maxChars;

    public ReadOnlyBackend(AiProperties properties) {
        this.dock = client(properties.dockSchedulingUrl(), properties);
        this.recovery = client(properties.disruptionRecoveryUrl(), properties);
        this.maxChars = properties.maxToolResultChars();
    }

    private static RestClient client(URI base, AiProperties properties) {
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().baseUrl(base.toString()).requestFactory(factory).build();
    }

    /**
     * GETs a resource and returns the JSON body as text for the model, or a short explanation if
     * it couldn't be read. Errors are returned to the model rather than thrown, so it can tell the
     * user what is missing instead of failing the whole answer.
     */
    public String get(Service service, String pathTemplate, String bearerToken, Object... uriVariables) {
        RestClient client = service == Service.DOCK_SCHEDULING ? dock : recovery;
        try {
            String body = client.get()
                    .uri(pathTemplate, uriVariables)
                    .header("Authorization", "Bearer " + bearerToken)
                    .retrieve()
                    .body(String.class);
            if (body == null) {
                return "{}";
            }
            return body.length() > maxChars ? body.substring(0, maxChars) + " ...[truncated]" : body;
        } catch (HttpClientErrorException e) {
            return switch (e.getStatusCode().value()) {
                case 404 -> "NOT_FOUND: no such record for this tenant (for a schedule: none computed for that date yet).";
                case 401, 403 -> "NOT_ALLOWED: the user's role cannot read this.";
                default -> "ERROR: request rejected (" + e.getStatusCode().value() + ").";
            };
        } catch (RestClientException e) {
            return "UNAVAILABLE: the " + service.name().toLowerCase().replace('_', ' ') + " service did not respond.";
        }
    }
}
