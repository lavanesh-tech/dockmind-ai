package com.dockmind.dock.events;

import com.dockmind.dock.Events;
import com.dockmind.dock.domain.ProcessedEventRepository;
import com.dockmind.dock.domain.ShipmentViewRepository;
import com.dockmind.testsupport.DockMindContainers;
import com.dockmind.testsupport.TestSecurityConfiguration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The consumer against a real Kafka broker: events build the local view, duplicates and stale
 * events change nothing, and messages that can never be processed land on the dead-letter topic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({DockMindContainers.class, TestSecurityConfiguration.class})
class ShipmentEventConsumerTest {

    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private ShipmentViewRepository views;

    @Autowired
    private ProcessedEventRepository processed;

    @Autowired
    private ConsumerFactory<String, String> consumerFactory;

    private void send(UUID key, String json) throws Exception {
        kafka.send(ShipmentEventListener.TOPIC, key.toString(), json).get();
    }

    @Test
    void bookedEventCreatesTheLocalView() throws Exception {
        String tenant = Events.newTenant();
        UUID shipment = UUID.randomUUID();

        send(shipment, Events.booked(tenant, shipment, "PO-1", "[\"REFRIGERATED\"]", "CRITICAL",
                "2026-11-02T08:00:00Z", "2026-11-02T09:00:00Z"));

        await().atMost(WAIT).untilAsserted(() -> assertThat(views.findById(shipment)).hasValueSatisfying(v -> {
            assertThat(v.getTenantId()).isEqualTo(tenant);
            assertThat(v.getStatus()).isEqualTo("BOOKED");
            assertThat(v.requiredCapabilityNames()).containsExactly("REFRIGERATED");
        }));
    }

    @Test
    void theSameEventDeliveredTwiceIsAppliedOnce() throws Exception {
        String tenant = Events.newTenant();
        UUID shipment = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant at = Instant.now();
        String event = Events.shipment(eventId, "ShipmentBooked", tenant, shipment, at, "PO-DUP", 10, "[]",
                "STANDARD", "2026-11-02T08:00:00Z", "2026-11-02T12:00:00Z", "BOOKED", null);

        send(shipment, event);
        send(shipment, event); // Kafka's at-least-once delivery: a duplicate is normal

        await().atMost(WAIT).untilAsserted(() -> assertThat(processed.existsById(eventId)).isTrue());
        // A later marker event on the same partition proves the duplicate was consumed (and skipped) by now.
        UUID marker = UUID.randomUUID();
        send(shipment, Events.shipment(marker, "ShipmentArrived", tenant, shipment, at.plusSeconds(60), "PO-DUP", 10, "[]",
                "STANDARD", "2026-11-02T08:00:00Z", "2026-11-02T12:00:00Z", "ARRIVED", "2026-11-02T08:10:00Z"));
        await().atMost(WAIT).untilAsserted(() -> assertThat(views.findById(shipment))
                .hasValueSatisfying(v -> assertThat(v.getLastEventId()).isEqualTo(marker)));
        assertThat(processed.findAll()).filteredOn(p -> p.getEventId().equals(eventId)).hasSize(1);
    }

    @Test
    void anOlderEventArrivingLateDoesNotOverwriteNewerState() throws Exception {
        String tenant = Events.newTenant();
        UUID shipment = UUID.randomUUID();
        Instant now = Instant.now();

        send(shipment, Events.shipment(UUID.randomUUID(), "ShipmentCancelled", tenant, shipment, now, "PO-OOO", 5, "[]",
                "STANDARD", "2026-11-02T08:00:00Z", "2026-11-02T12:00:00Z", "CANCELLED", null));
        UUID older = UUID.randomUUID();
        send(shipment, Events.shipment(older, "ShipmentBooked", tenant, shipment, now.minusSeconds(30), "PO-OOO", 5, "[]",
                "STANDARD", "2026-11-02T08:00:00Z", "2026-11-02T12:00:00Z", "BOOKED", null));

        await().atMost(WAIT).untilAsserted(() -> assertThat(processed.existsById(older)).isTrue());
        assertThat(views.findById(shipment)).hasValueSatisfying(v -> assertThat(v.getStatus()).isEqualTo("CANCELLED"));
    }

    @Test
    void unprocessableMessagesGoToTheDeadLetterTopic() throws Exception {
        UUID badJsonKey = UUID.randomUUID();
        UUID futureVersionKey = UUID.randomUUID();
        String futureVersion = Events.booked(Events.newTenant(), futureVersionKey, "PO-V2", "[]", "STANDARD",
                "2026-11-02T08:00:00Z", "2026-11-02T09:00:00Z").replace("\"eventVersion\":1", "\"eventVersion\":2");

        send(badJsonKey, "this is not json");
        send(futureVersionKey, futureVersion);

        try (Consumer<String, String> dlt = dltConsumer()) {
            List<String> keys = new ArrayList<>();
            long deadline = System.currentTimeMillis() + WAIT.toMillis();
            while (!(keys.contains(badJsonKey.toString()) && keys.contains(futureVersionKey.toString()))
                    && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> r : dlt.poll(Duration.ofMillis(500))) {
                    keys.add(r.key());
                }
            }
            assertThat(keys).contains(badJsonKey.toString(), futureVersionKey.toString());
        }
        assertThat(views.findById(futureVersionKey)).isEmpty();
    }

    private Consumer<String, String> dltConsumer() {
        Properties overrides = new Properties();
        overrides.put("auto.offset.reset", "earliest");
        Consumer<String, String> consumer = consumerFactory.createConsumer("dlt-test-" + UUID.randomUUID(), null, null, overrides);
        consumer.subscribe(List.of(KafkaConsumerConfiguration.DLT));
        return consumer;
    }
}
