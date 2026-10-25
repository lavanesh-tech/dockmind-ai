package com.dockmind.shipment.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small HTTP helper so API tests read as requests and responses, nothing else. */
final class ApiClient {

    private static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final int port;

    ApiClient(int port) {
        this.port = port;
    }

    HttpResponse<String> get(String path, String token) {
        return send(builder(path, token).GET());
    }

    HttpResponse<String> post(String path, String token, String json) {
        return send(builder(path, token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    static String idOf(HttpResponse<String> response) {
        Matcher m = ID.matcher(response.body());
        if (!m.find()) {
            throw new AssertionError("No id in response: " + response.body());
        }
        return m.group(1);
    }

    private HttpRequest.Builder builder(String path, String token) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10));
        if (token != null) {
            b.header("Authorization", "Bearer " + token);
        }
        return b;
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        try {
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
