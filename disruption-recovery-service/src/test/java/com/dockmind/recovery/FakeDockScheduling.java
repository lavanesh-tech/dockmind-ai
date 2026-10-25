package com.dockmind.recovery;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A stand-in for Dock Scheduling on a real port: serves a configured snapshot, records what was
 * applied and with which token, and can be told to answer apply with any status.
 */
public final class FakeDockScheduling {

    public final AtomicReference<String> snapshot = new AtomicReference<>();
    public final AtomicReference<Integer> snapshotStatus = new AtomicReference<>(200);
    public final AtomicReference<Integer> applyStatus = new AtomicReference<>(200);
    public final AtomicReference<String> lastApplyBody = new AtomicReference<>();
    public final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    public final AtomicReference<String> appliedRunId = new AtomicReference<>("7a0f0000-0000-0000-0000-000000000001");

    private final HttpServer server;

    public FakeDockScheduling() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/api/v1/schedules/", this::handle);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        String path = exchange.getRequestURI().getPath();
        if ("GET".equals(exchange.getRequestMethod()) && path.endsWith("/snapshot")) {
            respond(exchange, snapshotStatus.get(), snapshotStatus.get() == 200 ? snapshot.get() : "{}");
        } else if ("POST".equals(exchange.getRequestMethod()) && path.endsWith("/apply")) {
            lastApplyBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, applyStatus.get(), applyStatus.get() == 200
                    ? "{\"runId\":\"" + appliedRunId.get() + "\",\"algorithm\":\"MINIMAL_CHANGE\",\"appointments\":[]}"
                    : "{\"title\":\"Conflict\"}");
        } else {
            respond(exchange, 404, "{}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", status == 200 ? "application/json" : "application/problem+json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
