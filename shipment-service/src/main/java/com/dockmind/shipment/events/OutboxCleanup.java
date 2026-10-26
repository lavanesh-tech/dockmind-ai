package com.dockmind.shipment.events;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Deletes outbox rows some time after they were published, so the table doesn't grow forever.
 * Unpublished rows are never deleted: they still have to be delivered.
 *
 * <p>The retention period is a safety margin for investigating delivery problems (the exact
 * JSON that was sent is still there). It doesn't affect consumers: they deduplicate by event id.
 */
@Component
public class OutboxCleanup {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanup.class);
    static final int BATCH_SIZE = 500;

    private final OutboxRepository outbox;
    private final TransactionTemplate tx;
    private final Duration retention;
    private final Clock clock;
    private final Counter deleted;

    @Autowired // two constructors: tell Spring which one to use (the other is for tests with a fixed clock)
    public OutboxCleanup(OutboxRepository outbox, TransactionTemplate tx,
                         @Value("${dockmind.outbox.retention:P7D}") Duration retention, MeterRegistry meters) {
        this(outbox, tx, retention, meters, Clock.systemUTC());
    }

    OutboxCleanup(OutboxRepository outbox, TransactionTemplate tx, Duration retention, MeterRegistry meters, Clock clock) {
        this.outbox = outbox;
        this.tx = tx;
        this.retention = retention;
        this.clock = clock;
        this.deleted = Counter.builder("dockmind.outbox.deleted")
                .description("Published outbox events removed after the retention period").register(meters);
    }

    @Scheduled(fixedDelayString = "${dockmind.outbox.cleanup.interval:PT1H}", initialDelayString = "PT1M")
    public void tick() {
        purge();
    }

    /** Deletes everything past retention, batch by batch, and returns how many rows were removed. */
    public int purge() {
        Instant cutoff = clock.instant().minus(retention);
        int total = 0;
        while (true) {
            Integer batch = tx.execute(status -> outbox.deletePublishedBefore(cutoff, BATCH_SIZE));
            int n = batch == null ? 0 : batch;
            total += n;
            if (n < BATCH_SIZE) {
                break;
            }
        }
        if (total > 0) {
            deleted.increment(total);
            log.info("Removed {} published outbox events older than {}", total, retention);
        }
        return total;
    }
}
