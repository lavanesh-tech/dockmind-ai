package com.dockmind.gateway;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * A tiny real HTTP server standing in for a backend service, using only the JDK. It echoes what
 * the gateway forwarded, and has a slow endpoint for testing timeouts.
 */
final class FakeBackend implements AutoCloseable {

    private final HttpServer server;

    FakeBackend() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/slow")) {
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            String correlation = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
            byte[] body = ("{\"path\":\"" + path + "\",\"authForwarded\":" + (auth != null && auth.startsWith("Bearer "))
                    + ",\"correlationId\":\"" + correlation + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    String uri() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
