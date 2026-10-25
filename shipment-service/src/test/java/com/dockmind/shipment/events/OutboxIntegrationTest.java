package com.dockmind.shipment.events;

import com.dockmind.common.web.CorrelationId;
import com.dockmind.shipment.TestcontainersConfiguration;
import com.dockmind.testsupport.TestSecurityConfiguration;
import com.dockmind.testsupport.TestTokens;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The outbox, end to end: an HTTP request changes a shipment, the event is written in the
 * same transaction, the relay publishes it, and it arrives on the real Kafka topic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
class OutboxIntegrationTest {

    private static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> post(String path, String body, String correlationId) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer " + TestTokens.token("tenant-a", "DISPATCHER"))
                .header("Content-Type", "application/json")
                .header(CorrelationId.HEADER, correlationId)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String booking(String reference) {
        return """
                {"reference":"%s","direction":"OUTBOUND","palletCount":20,"requiredCapabilities":["HAZMAT"],
                 "priority":"CRITICAL","windowStart":"2026-11-03T07:00:00Z","windowEnd":"2026-11-03T08:00:00Z"}"""
                .formatted(reference);
    }

    private Consumer<String, String> consumer() {
        Properties overrides = new Properties();
        overrides.put("auto.offset.reset", "earliest");
        Consumer<String, String> consumer = consumerFactory.createConsumer("outbox-test-" + UUID.randomUUID(), null, null, overrides);
        consumer.subscribe(List.of(ShipmentEvents.TOPIC));
        return consumer;
    }

    private static List<ConsumerRecord<String, String>> eventsFor(Consumer<String, String> consumer, String key, int expected) {
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 20_000;
        while (found.size() < expected && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(500)).forEach(r -> {
                if (key.equals(r.key())) {
                    found.add(r);
                }
            });
        }
        return found;
    }

    @Test
    void bookingAndCancellingPublishOrderedEventsWithTheCorrelationId() throws Exception {
        String reference = "OUT-" + UUID.randomUUID().toString().substring(0, 8);
        HttpResponse<String> booked = post("/api/v1/shipments", booking(reference), "corr-outbox-1");
        assertThat(booked.statusCode()).as(booked.body()).isEqualTo(201);
        Matcher m = ID.matcher(booked.body());
        assertThat(m.find()).isTrue();
        String shipmentId = m.group(1);
        assertThat(post("/api/v1/shipments/" + shipmentId + "/cancel", "", "corr-outbox-2").statusCode()).isEqualTo(200);

        try (Consumer<String, String> consumer = consumer()) {
            List<ConsumerRecord<String, String>> events = eventsFor(consumer, shipmentId, 2);

            assertThat(events).hasSize(2);
            assertThat(events.get(0).value()).contains("\"eventType\":\"ShipmentBooked\"", "\"eventVersion\":1",
                    "\"tenantId\":\"tenant-a\"", "\"reference\":\"" + reference + "\"", "\"requiredCapabilities\":[\"HAZMAT\"]");
            assertThat(events.get(1).value()).contains("\"eventType\":\"ShipmentCancelled\"", "\"status\":\"CANCELLED\"");
            assertThat(header(events.get(0), ShipmentEvents.CORRELATION_HEADER)).isEqualTo("corr-outbox-1");
            assertThat(header(events.get(1), ShipmentEvents.CORRELATION_HEADER)).isEqualTo("corr-outbox-2");
        }

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(outbox.findAll()).filteredOn(e -> e.getAggregateId().toString().equals(shipmentId))
                        .hasSize(2).allSatisfy(e -> assertThat(e.getPublishedAt()).isNotNull()));
    }

    @Test
    void aRejectedRequestWritesNoEvent() throws Exception {
        String reference = "DUP-" + UUID.randomUUID().toString().substring(0, 8);
        assertThat(post("/api/v1/shipments", booking(reference), "corr-dup-1").statusCode()).isEqualTo(201);
        long before = outbox.count();

        HttpResponse<String> duplicate = post("/api/v1/shipments", booking(reference), "corr-dup-2");

        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(outbox.count()).isEqualTo(before); // the transaction rolled back, so no event exists
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}
