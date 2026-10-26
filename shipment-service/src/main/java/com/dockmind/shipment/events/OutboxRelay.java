package com.dockmind.shipment.events;

import org.apache.kafka.clients.producer.ProducerRecord;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Publishes outbox rows to Kafka, oldest first, then marks them published.
 *
 * <p><b>Delivery guarantee: at least once.</b> If Kafka accepts an event but the database update
 * marking it published fails, the event is sent again on the next run. Consumers deduplicate
 * by {@code eventId}, so the end result is still exactly-once <i>processing</i>.
 *
 * <p>On a failure it stops the batch, so later events for the same shipment never overtake
 * an earlier one, and retries on the next tick. The number of attempts and the last error are
 * kept on the row for operators.
 */
@Component
@ConditionalOnProperty(name = "dockmind.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    static final int BATCH_SIZE = 100;
    static final long SEND_TIMEOUT_SECONDS = 10;

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;
    private final Counter publishedCounter;
    private final Counter failedCounter;

    public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka, TransactionTemplate tx,
                       MeterRegistry meters) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.tx = tx;
        this.publishedCounter = Counter.builder("dockmind.outbox.published")
                .description("Events relayed from the outbox to Kafka").register(meters);
        this.failedCounter = Counter.builder("dockmind.outbox.publish.failures")
                .description("Failed attempts to relay an outbox event (retried later)").register(meters);
        // How far behind the relay is. Should stay near 0; growth means Kafka is down or slow.
        Gauge.builder("dockmind.outbox.pending", outbox, OutboxRepository::countByPublishedAtIsNull)
                .description("Outbox events not yet published").register(meters);
    }

    /** Runs on a schedule. The explicit transaction holds the row locks while the batch is sent. */
    @Scheduled(fixedDelayString = "${dockmind.outbox.relay.interval-ms:500}")
    public void tick() {
        publishPending();
    }

    /** Publishes one batch and returns how many events were sent. Public so tests can drive it directly. */
    public int publishPending() {
        Integer published = tx.execute(status -> publishBatch());
        return published == null ? 0 : published;
    }

    private int publishBatch() {
        List<OutboxEvent> batch = outbox.lockNextUnpublished(BATCH_SIZE);
        int published = 0;
        for (OutboxEvent event : batch) {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    ShipmentEvents.TOPIC, event.getAggregateId().toString(), event.getEnvelope());
            if (event.getCorrelationId() != null) {
                record.headers().add(ShipmentEvents.CORRELATION_HEADER,
                        event.getCorrelationId().getBytes(StandardCharsets.UTF_8));
            }
            record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));
            try {
                kafka.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                event.markPublished(Instant.now());
                published++;
                publishedCounter.increment();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                event.recordFailure("interrupted");
                break;
            } catch (Exception e) {
                event.recordFailure(e.getClass().getSimpleName() + ": " + e.getMessage());
                failedCounter.increment();
                log.warn("Outbox publish failed for event {} (attempt {}); will retry", event.getId(), event.getAttempts());
                break;
            }
        }
        return published;
    }
}
