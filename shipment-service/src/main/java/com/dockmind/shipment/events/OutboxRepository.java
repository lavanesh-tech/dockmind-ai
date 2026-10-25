package com.dockmind.shipment.events;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
