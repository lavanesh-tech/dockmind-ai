package com.dockmind.shipment.events;

import com.dockmind.shipment.TestcontainersConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Old published events are removed; recent and unpublished ones are kept. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "dockmind.outbox.relay.interval-ms=3600000")
@Import(TestcontainersConfiguration.class)
class OutboxCleanupTest {

    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private MeterRegistry meters;

    private UUID event(Instant occurred, Instant publishedAt) {
        UUID id = UUID.randomUUID();
        OutboxEvent e = new OutboxEvent(id, "t-cleanup", UUID.randomUUID(), "ShipmentBooked", 1, "{}", null, occurred);
        if (publishedAt != null) {
            e.markPublished(publishedAt);
        }
        tx.executeWithoutResult(s -> outbox.save(e));
        return id;
    }

    @Test
    void removesOnlyPublishedEventsOlderThanRetention() {
        UUID oldPublished = event(NOW.minus(Duration.ofDays(10)), NOW.minus(Duration.ofDays(10)));
        UUID recentPublished = event(NOW.minus(Duration.ofDays(1)), NOW.minus(Duration.ofDays(1)));
        UUID oldUnpublished = event(NOW.minus(Duration.ofDays(30)), null);

        OutboxCleanup cleanup = new OutboxCleanup(outbox, tx, Duration.ofDays(7), meters,
                Clock.fixed(NOW, ZoneOffset.UTC));
        int removed = cleanup.purge();

        assertThat(removed).isGreaterThanOrEqualTo(1);
        assertThat(outbox.findById(oldPublished)).isEmpty();
        assertThat(outbox.findById(recentPublished)).isPresent();
        assertThat(outbox.findById(oldUnpublished)).isPresent();
        assertThat(meters.find("dockmind.outbox.deleted").counter()).isNotNull();
    }

    @Test
    void deletesInBatchesUntilNothingIsLeft() {
        for (int i = 0; i < OutboxCleanup.BATCH_SIZE + 25; i++) {
            event(NOW.minus(Duration.ofDays(20)), NOW.minus(Duration.ofDays(20)));
        }
        OutboxCleanup cleanup = new OutboxCleanup(outbox, tx, Duration.ofDays(7), meters,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(cleanup.purge()).isGreaterThanOrEqualTo(OutboxCleanup.BATCH_SIZE + 25);
        assertThat(cleanup.purge()).isZero();
    }
}
