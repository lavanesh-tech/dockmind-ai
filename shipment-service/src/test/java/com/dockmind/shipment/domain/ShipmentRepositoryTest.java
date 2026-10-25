package com.dockmind.shipment.domain;

import com.dockmind.shipment.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against a real PostgreSQL (Testcontainers) with the real Flyway migrations, so it
 * checks the schema, the mappings and the database constraints together.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class ShipmentRepositoryTest {

    // PostgreSQL stores microseconds; truncating keeps round-trip comparisons exact.
    private static final Instant START = Instant.parse("2026-11-02T08:00:00Z").truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private ShipmentRepository repository;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    private static Shipment shipment(String tenant, String reference, Instant start) {
        return new Shipment(tenant, reference, Direction.OUTBOUND, 12,
                EnumSet.of(DockCapability.HAZMAT, DockCapability.SIDE_LOAD), Priority.EXPEDITED,
                start, start.plus(Duration.ofHours(1)));
    }

    @Test
    void savesAndReloadsAllFields() {
        Shipment saved = repository.save(shipment("tenant-a", "SO-1", START));

        Shipment loaded = repository.findByIdAndTenantId(saved.getId(), "tenant-a").orElseThrow();

        assertThat(loaded.getReference()).isEqualTo("SO-1");
        assertThat(loaded.getDirection()).isEqualTo(Direction.OUTBOUND);
        assertThat(loaded.getPalletCount()).isEqualTo(12);
        assertThat(loaded.getRequiredCapabilities())
                .containsExactlyInAnyOrder(DockCapability.HAZMAT, DockCapability.SIDE_LOAD);
        assertThat(loaded.getWindowStart()).isEqualTo(START);
        assertThat(loaded.getVersion()).isZero();
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void anotherTenantCannotReadTheShipment() {
        Shipment saved = repository.save(shipment("tenant-a", "SO-1", START));

        assertThat(repository.findByIdAndTenantId(saved.getId(), "tenant-b")).isEmpty();
    }

    @Test
    void referenceIsUniquePerTenantOnly() {
        repository.saveAndFlush(shipment("tenant-a", "SO-1", START));
        repository.saveAndFlush(shipment("tenant-b", "SO-1", START)); // other tenant: allowed

        assertThatThrownBy(() -> repository.saveAndFlush(shipment("tenant-a", "SO-1", START)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.existsByTenantIdAndReference("tenant-a", "SO-1")).isTrue();
    }

    @Test
    void findsTenantShipmentsInWindowOrderedByStart() {
        repository.save(shipment("tenant-a", "late", START.plus(Duration.ofHours(3))));
        repository.save(shipment("tenant-a", "early", START));
        repository.save(shipment("tenant-a", "outside", START.plus(Duration.ofDays(2))));
        repository.save(shipment("tenant-b", "other-tenant", START));

        List<Shipment> found = repository
                .findByTenantIdAndWindowStartGreaterThanEqualAndWindowStartLessThanOrderByWindowStartAsc(
                        "tenant-a", START, START.plus(Duration.ofDays(1)));

        assertThat(found).extracting(Shipment::getReference).containsExactly("early", "late");
    }

    @Test
    void concurrentUpdatesAreDetectedByOptimisticLocking() {
        UUID id = repository.save(shipment("tenant-a", "SO-1", START)).getId();

        // Two dispatchers load the same shipment at version 0...
        Shipment first = tx.execute(status -> repository.findById(id).orElseThrow());
        Shipment second = tx.execute(status -> repository.findById(id).orElseThrow());

        // ...the first saves successfully and the version moves to 1...
        first.markArrived(START);
        repository.save(first);

        // ...so the second, still holding version 0, must be rejected rather than overwrite it.
        second.cancel();
        assertThatThrownBy(() -> repository.save(second))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(ShipmentStatus.ARRIVED);
    }

    @Test
    void databaseRejectsInvalidRowsEvenWithoutTheEntity() {
        // Simulates a bug or a manual SQL insert that bypasses the Java validation.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO shipment (id, tenant_id, reference, direction, pallet_count, priority,
                    window_start, window_end, status, version, created_at, updated_at)
                VALUES (gen_random_uuid(), 't', 'bad', 'INBOUND', 0, 'STANDARD',
                    now(), now() + interval '1 hour', 'BOOKED', 0, now(), now())
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
