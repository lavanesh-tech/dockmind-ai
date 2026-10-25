package com.dockmind.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Stand-ins for Dock Scheduling and Disruption Recovery that record every request they get. */
public final class FakeBackends {

    /** "METHOD path auth" for every request received, by either backend. */
    public final List<String> requests = new CopyOnWriteArrayList<>();
    public volatile String disruptionsBody = "[]";

    public static final String SCHEDULE = """
            {"runId":"7a0f0000-0000-0000-0000-000000000001","date":"2026-11-02","algorithm":"DISPATCH","scheduled":2,
             "unscheduled":0,"avgWaitMinutes":4.5,
             "appointments":[{"shipmentId":"a0000000-0000-0000-0000-00000000000a","reference":"PO-1001","dockCode":"D1",
                              "start":"2026-11-02T08:00:00Z","end":"2026-11-02T08:30:00Z"}],"notScheduled":[]}
            """;

    private final HttpServer dock = start("/api/v1/", this::dock);
    private final HttpServer recovery = start("/api/v1/", this::recovery);

    public String dockUrl() {
        return "http://127.0.0.1:" + dock.getAddress().getPort();
    }

    public String recoveryUrl() {
        return "http://127.0.0.1:" + recovery.getAddress().getPort();
    }

    private void dock(HttpExchange ex) throws IOException {
        record(ex);
        String path = ex.getRequestURI().getPath();
        if (path.equals("/api/v1/schedules/2026-11-02")) {
            respond(ex, 200, SCHEDULE);
        } else if (path.equals("/api/v1/docks")) {
            respond(ex, 200, "[{\"code\":\"D1\",\"capabilities\":[]}]");
        } else {
            respond(ex, 404, "{}");
        }
    }

    private void recovery(HttpExchange ex) throws IOException {
        record(ex);
        if (ex.getRequestURI().getPath().equals("/api/v1/disruptions")) {
            respond(ex, 200, disruptionsBody);
        } else {
            respond(ex, 404, "{}");
        }
    }

    private void record(HttpExchange ex) {
        requests.add(ex.getRequestMethod() + " " + ex.getRequestURI().getPath() + " "
                + ex.getRequestHeaders().getFirst("Authorization"));
    }

    private static HttpServer start(String context, com.sun.net.httpserver.HttpHandler handler) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(context, handler);
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
