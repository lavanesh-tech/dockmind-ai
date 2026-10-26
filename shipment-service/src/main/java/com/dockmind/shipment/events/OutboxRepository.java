package com.dockmind.shipment.events;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Locks the oldest unpublished events. {@code FOR UPDATE SKIP LOCKED} lets several service
     * instances run the relay at once: each takes rows nobody else holds, so no event is sent
     * twice concurrently and no instance waits on another.
     */
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE published_at IS NULL
            ORDER BY occurred_at, id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockNextUnpublished(int limit);

    long countByPublishedAtIsNull();

    /**
     * Deletes up to {@code limit} events published before {@code cutoff}, oldest first. Deleting
     * in small batches keeps each transaction short and avoids locking the table for long.
     */
    @Modifying
    @Query(value = """
            DELETE FROM outbox_event WHERE id IN (
                SELECT id FROM outbox_event
                WHERE published_at IS NOT NULL AND published_at < :cutoff
                ORDER BY published_at
                LIMIT :limit)
            """, nativeQuery = true)
    int deletePublishedBefore(Instant cutoff, int limit);
}
